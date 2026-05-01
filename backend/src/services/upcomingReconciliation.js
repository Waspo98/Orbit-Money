import { formatLocalDate } from '../lib/localDate.js';
import { addDays, datesForItemBetween, daysBetween } from '../lib/upcomingSchedule.js';

export const UPCOMING_RECONCILIATION_GRACE_DAYS = 4;
const LOOKBACK_DAYS = 45;
const LOOKAHEAD_DAYS = 62;
const MATCH_EARLY_DAYS = 2;
const MIN_MATCH_SCORE = 70;
const MIN_MERCHANT_SCORE = 35;

function blankTotals() {
  return {
    households: 0,
    occurrences_ensured: 0,
    occurrences_created: 0,
    occurrences_updated: 0,
    matched: 0,
    missed: 0
  };
}

function addTotals(target, source) {
  for (const key of Object.keys(target)) {
    target[key] += Number(source[key] || 0);
  }
  return target;
}

export function normalizeMatchText(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

function textTokens(value) {
  return normalizeMatchText(value)
    .split(' ')
    .filter((token) => token.length > 2);
}

export function textMatchScore(expected, actual) {
  const expectedText = normalizeMatchText(expected);
  const actualText = normalizeMatchText(actual);
  if (!expectedText || !actualText) return 0;
  if (expectedText === actualText) return 60;
  if (
    expectedText.length >= 4 &&
    actualText.length >= 4 &&
    (expectedText.includes(actualText) || actualText.includes(expectedText))
  ) {
    return 50;
  }

  const expectedTokens = textTokens(expectedText);
  const actualTokens = new Set(textTokens(actualText));
  if (expectedTokens.length === 0 || actualTokens.size === 0) return 0;
  const overlap = expectedTokens.filter((token) => actualTokens.has(token)).length / expectedTokens.length;
  if (overlap >= 0.75) return 45;
  if (overlap >= 0.5) return 30;
  return 0;
}

function amountScore(expectedCents, actualCents) {
  const expected = Math.abs(Number(expectedCents) || 0);
  const actual = Math.abs(Number(actualCents) || 0);
  if (expected <= 0 || actual <= 0) return 0;
  const diff = Math.abs(expected - actual);
  if (diff <= Math.max(100, Math.round(expected * 0.15))) return 20;
  if (diff <= Math.max(250, Math.round(expected * 0.35))) return 8;
  return -20;
}

export function scoreOccurrenceCandidate(occurrence, transaction) {
  const merchantScore = Math.max(
    textMatchScore(occurrence.merchant, transaction.merchant),
    textMatchScore(occurrence.name, transaction.merchant)
  );
  if (merchantScore < MIN_MERCHANT_SCORE) return { score: 0, merchantScore };

  const dateDistance = Math.abs(daysBetween(occurrence.expected_date, transaction.date) ?? 99);
  const dateScore = dateDistance === 0 ? 10 : dateDistance <= 2 ? 6 : 2;
  const categoryScore = occurrence.category_id && transaction.category_id === occurrence.category_id ? 15 : 0;
  const accountScore = occurrence.account_id
    ? (transaction.account_id === occurrence.account_id ? 10 : -20)
    : 3;

  return {
    score: merchantScore + dateScore + categoryScore + accountScore + amountScore(occurrence.amount, transaction.amount),
    merchantScore
  };
}

function fetchHouseholdIds(db, householdId) {
  if (householdId != null) return [householdId];
  return db.prepare('SELECT id FROM households').all().map((row) => row.id);
}

function fetchActiveUpcomingItems(db, householdId) {
  return db
    .prepare(
      `SELECT id, household_id, name, merchant, amount, direction, frequency_type,
              frequency_interval, frequency_unit, recurrence_rule, next_date,
              category_id, account_id
         FROM upcoming_items
        WHERE household_id = ?
          AND status = 'active'`
    )
    .all(householdId);
}

function ensureOccurrences(db, householdId, { today, graceDays, lookbackDays, lookaheadDays }) {
  const windowStart = addDays(today, -lookbackDays);
  const windowEnd = addDays(today, lookaheadDays);
  const items = fetchActiveUpcomingItems(db, householdId);
  const selectExisting = db.prepare(
    `SELECT id, status, grace_until
       FROM upcoming_occurrences
      WHERE household_id = ?
        AND upcoming_item_id = ?
        AND expected_date = ?`
  );
  const insertOccurrence = db.prepare(
    `INSERT INTO upcoming_occurrences (
       household_id, upcoming_item_id, expected_date, grace_until, status
     ) VALUES (?, ?, ?, ?, 'pending')`
  );
  const updatePendingGrace = db.prepare(
    `UPDATE upcoming_occurrences
        SET grace_until = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND status = 'pending'`
  );

  let occurrencesEnsured = 0;
  let occurrencesCreated = 0;
  let occurrencesUpdated = 0;

  for (const item of items) {
    for (const expectedDate of datesForItemBetween(item, windowStart, windowEnd)) {
      const graceUntil = addDays(expectedDate, graceDays);
      const existing = selectExisting.get(householdId, item.id, expectedDate);
      occurrencesEnsured++;
      if (!existing) {
        insertOccurrence.run(householdId, item.id, expectedDate, graceUntil);
        occurrencesCreated++;
      } else if (existing.status === 'pending' && existing.grace_until !== graceUntil) {
        occurrencesUpdated += updatePendingGrace.run(graceUntil, existing.id).changes;
      }
    }
  }

  return {
    occurrences_ensured: occurrencesEnsured,
    occurrences_created: occurrencesCreated,
    occurrences_updated: occurrencesUpdated
  };
}

function fetchMatchCandidates(db, occurrence) {
  const windowStart = addDays(occurrence.expected_date, -MATCH_EARLY_DAYS);
  return db
    .prepare(
      `SELECT t.id,
              t.date,
              t.amount,
              t.account_id,
              COALESCE(t.edited_category_id, t.category_id) AS category_id,
              COALESCE(t.edited_merchant, t.original_merchant, '') AS merchant
         FROM transactions t
        WHERE t.household_id = @household_id
          AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
          AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
          AND t.date >= @window_start
          AND t.date <= @window_end
          AND (
            (@direction = 'income' AND t.amount > 0)
            OR (@direction = 'expense' AND t.amount < 0)
          )
          AND (@account_id IS NULL OR t.account_id = @account_id)
          AND NOT EXISTS (
            SELECT 1
              FROM upcoming_occurrences existing
             WHERE existing.household_id = t.household_id
               AND existing.matched_transaction_id = t.id
               AND existing.id != @occurrence_id
          )
        ORDER BY ABS(julianday(t.date) - julianday(@expected_date)) ASC,
                 ABS(ABS(t.amount) - @amount) ASC,
                 t.id DESC
        LIMIT 25`
    )
    .all({
      household_id: occurrence.household_id,
      occurrence_id: occurrence.id,
      expected_date: occurrence.expected_date,
      window_start: windowStart,
      window_end: occurrence.grace_until,
      direction: occurrence.direction,
      account_id: occurrence.account_id,
      amount: Math.abs(Number(occurrence.amount) || 0)
    });
}

function matchOccurrences(db, householdId, { today, lookbackDays }) {
  const windowStart = addDays(today, -lookbackDays);
  const eligibleEnd = addDays(today, MATCH_EARLY_DAYS);
  const occurrences = db
    .prepare(
      `SELECT o.id, o.household_id, o.upcoming_item_id, o.expected_date, o.grace_until,
              ui.name, ui.merchant, ui.amount, ui.direction, ui.category_id, ui.account_id
         FROM upcoming_occurrences o
         JOIN upcoming_items ui
           ON ui.id = o.upcoming_item_id
          AND ui.household_id = o.household_id
        WHERE o.household_id = ?
          AND o.status IN ('pending', 'missed')
          AND o.expected_date >= ?
          AND o.expected_date <= ?
          AND ui.status = 'active'
        ORDER BY o.expected_date ASC, o.id ASC`
    )
    .all(householdId, windowStart, eligibleEnd);
  const markMatched = db.prepare(
    `UPDATE upcoming_occurrences
        SET status = 'matched',
            matched_transaction_id = ?,
            matched_at = datetime('now'),
            missed_at = NULL,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?
        AND status IN ('pending', 'missed')`
  );

  let matched = 0;
  for (const occurrence of occurrences) {
    let best = null;
    for (const transaction of fetchMatchCandidates(db, occurrence)) {
      const score = scoreOccurrenceCandidate(occurrence, transaction);
      if (score.score < MIN_MATCH_SCORE) continue;
      if (!best || score.score > best.score) {
        best = { ...score, transaction };
      }
    }
    if (best) {
      matched += markMatched.run(best.transaction.id, occurrence.id, householdId).changes;
    }
  }

  return { matched };
}

function markMissed(db, householdId, today) {
  const result = db
    .prepare(
      `UPDATE upcoming_occurrences
          SET status = 'missed',
              missed_at = datetime('now'),
              updated_at = datetime('now')
        WHERE household_id = ?
          AND status = 'pending'
          AND grace_until < ?`
    )
    .run(householdId, today);
  return { missed: result.changes };
}

export function resetPendingUpcomingOccurrences(db, { householdId, itemId, fromDate = formatLocalDate() }) {
  return db
    .prepare(
      `DELETE FROM upcoming_occurrences
        WHERE household_id = ?
          AND upcoming_item_id = ?
          AND status = 'pending'
          AND expected_date >= ?`
    )
    .run(householdId, itemId, fromDate).changes;
}

export function reconcileUpcomingTransactions(db, options = {}) {
  const today = options.today || formatLocalDate();
  const graceDays = Number.isFinite(Number(options.graceDays))
    ? Math.max(1, Number(options.graceDays))
    : UPCOMING_RECONCILIATION_GRACE_DAYS;
  const lookbackDays = Number.isFinite(Number(options.lookbackDays))
    ? Math.max(graceDays + MATCH_EARLY_DAYS, Number(options.lookbackDays))
    : LOOKBACK_DAYS;
  const lookaheadDays = Number.isFinite(Number(options.lookaheadDays))
    ? Math.max(0, Number(options.lookaheadDays))
    : LOOKAHEAD_DAYS;
  const totals = blankTotals();

  const run = db.transaction(() => {
    for (const householdId of fetchHouseholdIds(db, options.householdId)) {
      totals.households++;
      addTotals(totals, ensureOccurrences(db, householdId, { today, graceDays, lookbackDays, lookaheadDays }));
      addTotals(totals, matchOccurrences(db, householdId, { today, lookbackDays }));
      addTotals(totals, markMissed(db, householdId, today));
    }
  });
  run();

  return totals;
}
