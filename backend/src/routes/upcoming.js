import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendCreated,
  sendNotFound,
  sendOk,
  sendRouteError,
  sendServerError
} from '../lib/http.js';
import { formatLocalDate } from '../lib/localDate.js';
import { centsToDollars } from '../lib/money.js';
import { parseId, readIdParam } from '../lib/routeParams.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';
import { summarizeHistorySamples } from '../lib/upcomingProjection.js';
import { scoreUpcomingSuggestion } from '../lib/upcomingSuggestionConfidence.js';
import {
  addDays,
  addInterval,
  addMonthsClamped,
  advanceToUpcoming,
  datesForItemBetween,
  daysBetween,
  endOfMonth,
  isoDate,
  monthlyFactor,
  parseRecurrenceRule
} from '../lib/upcomingSchedule.js';
import {
  AMOUNT_STRATEGIES,
  MAX_LOOKBACK_MONTHS,
  clampInteger,
  extractKindOverride,
  normalizeKind,
  normalizeUpcomingBody,
  upcomingError
} from '../services/upcomingItemValidation.js';
import {
  reconcileUpcomingTransactions,
  resetPendingUpcomingOccurrences
} from '../services/upcomingReconciliation.js';

const router = express.Router();
const EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');
const MIN_SUGGESTION_CONFIDENCE = 40;

function reconcileHouseholdUpcoming(householdId) {
  try {
    return reconcileUpcomingTransactions(db, { householdId });
  } catch (err) {
    console.error('Upcoming reconciliation failed:', err.message || err);
    return null;
  }
}

function normalizeBody(body, householdId) {
  return normalizeUpcomingBody(db, body, householdId);
}

function projectAmount(row, householdId) {
  const fallbackCents = Math.max(0, Number(row.amount) || 0);
  const strategy = AMOUNT_STRATEGIES.has(row.amount_strategy) ? row.amount_strategy : 'fixed';
  const lookbackMonths = clampInteger(row.amount_lookback_months, 1, MAX_LOOKBACK_MONTHS, 6);
  const fallback = {
    cents: fallbackCents,
    strategy,
    lookback_months: lookbackMonths,
    sample_count: 0,
    sample_months: 0,
    total: 0,
    average: centsToDollars(fallbackCents),
    fallback: true
  };

  if (strategy !== 'history_average') {
    return {
      ...fallback,
      fallback: false,
      sample_months: null,
      average: centsToDollars(fallbackCents)
    };
  }

  const merchant = String(row.merchant || row.name || '').trim().toLowerCase();
  const categoryId = parseId(row.category_id);
  if (!merchant && categoryId === null) return fallback;

  const today = formatLocalDate();
  const startDate = addMonthsClamped(today, -lookbackMonths);
  const sampleRows = db
    .prepare(
      `SELECT substr(t.date, 1, 7) AS month,
              SUM(ABS(t.amount)) AS total_cents,
              COUNT(*) AS transaction_count
         FROM transactions t
        WHERE t.household_id = @household_id
          AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
          AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
          AND t.date >= @start_date
          AND t.date < @today
          AND (
            (@direction = 'income' AND t.amount > 0)
            OR (@direction = 'expense' AND t.amount < 0)
          )
          AND (
            (@merchant != '' AND lower(COALESCE(t.edited_merchant, t.original_merchant, '')) = @merchant)
            OR (@merchant = '' AND @category_id IS NOT NULL AND ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} = @category_id)
          )
        GROUP BY substr(t.date, 1, 7)
        ORDER BY month DESC`
    )
    .all({
      household_id: householdId,
      start_date: startDate,
      today,
      direction: row.direction === 'income' ? 'income' : 'expense',
      merchant,
      category_id: categoryId
    });

  if (sampleRows.length === 0) return fallback;

  const { totalCents, sampleCount, averageCents } = summarizeHistorySamples(sampleRows, fallbackCents);

  return {
    cents: averageCents,
    strategy,
    lookback_months: lookbackMonths,
    sample_count: sampleCount,
    sample_months: sampleRows.length,
    total: centsToDollars(totalCents),
    average: centsToDollars(averageCents),
    fallback: false
  };
}

function serializeItem(row, householdId) {
  const projection = projectAmount(row, householdId);
  const kindOverride = extractKindOverride(row.recurrence_rule);
  return {
    ...row,
    kind: kindOverride || row.kind,
    amount: centsToDollars(row.amount),
    projected_amount: centsToDollars(projection.cents),
    recurrence_rule: parseRecurrenceRule(row.recurrence_rule),
    amount_strategy: AMOUNT_STRATEGIES.has(row.amount_strategy) ? row.amount_strategy : 'fixed',
    amount_lookback_months: clampInteger(row.amount_lookback_months, 1, MAX_LOOKBACK_MONTHS, 6),
    frequency_interval: Number(row.frequency_interval) || 1,
    projection: {
      strategy: projection.strategy,
      lookback_months: projection.lookback_months,
      sample_count: projection.sample_count,
      sample_months: projection.sample_months,
      total: projection.total,
      average: projection.average,
      fallback: projection.fallback
    }
  };
}

function fetchItems(householdId) {
  return db
    .prepare(
      `SELECT ui.*,
              c.name AS category_name, c.color AS category_color, c.icon AS category_icon,
              a.name AS account_name
         FROM upcoming_items ui
         LEFT JOIN categories c ON c.id = ui.category_id AND c.household_id = ui.household_id
         LEFT JOIN accounts a ON a.id = ui.account_id AND a.household_id = ui.household_id
        WHERE ui.household_id = ?
          AND ui.status = 'active'
        ORDER BY ui.next_date ASC, ui.id ASC`
    )
    .all(householdId)
    .map((row) => serializeItem(row, householdId));
}

function fetchDismissedKeys(householdId) {
  return new Set(
    db.prepare('SELECT suggestion_key FROM upcoming_dismissed_suggestions WHERE household_id = ?').all(householdId).map((row) => row.suggestion_key)
  );
}

function existingMerchantKeys(householdId) {
  return new Set(
    db
      .prepare(
        `SELECT lower(COALESCE(merchant, name)) || '|' || direction AS key
          FROM upcoming_items
          WHERE household_id = ?
            AND status = 'active'`
      )
      .all(householdId)
      .map((row) => row.key)
  );
}

function inferFrequency(averageInterval) {
  if (averageInterval >= 5 && averageInterval <= 9) return { frequency_type: 'weekly', frequency_interval: 1, frequency_unit: 'weeks' };
  if (averageInterval >= 12 && averageInterval <= 18) return { frequency_type: 'biweekly', frequency_interval: 2, frequency_unit: 'weeks' };
  if (averageInterval >= 24 && averageInterval <= 38) return { frequency_type: 'monthly', frequency_interval: 1, frequency_unit: 'months' };
  if (averageInterval >= 50 && averageInterval <= 75) return { frequency_type: 'bimonthly', frequency_interval: 2, frequency_unit: 'months' };
  if (averageInterval >= 330 && averageInterval <= 400) return { frequency_type: 'yearly', frequency_interval: 1, frequency_unit: 'months' };
  return { frequency_type: 'custom', frequency_interval: Math.round(averageInterval), frequency_unit: 'days' };
}

function buildSuggestions(householdId) {
  const rows = db
    .prepare(
      `SELECT t.id, t.account_id, t.date, t.amount / 100.0 AS amount,
              COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
              ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} AS category_id,
              COALESCE(t.edited_is_transfer, t.is_transfer) AS is_transfer,
              COALESCE(t.edited_is_ignored, t.is_ignored) AS is_ignored,
              c.name AS category_name
         FROM transactions t
         LEFT JOIN categories c ON c.id = ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL}
          AND c.household_id = t.household_id
        WHERE t.household_id = ?
          AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
          AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
          AND t.date >= date('now', '-18 months')
        ORDER BY t.date ASC, t.id ASC`
    )
    .all(householdId);

  const groups = new Map();
  for (const row of rows) {
    const merchant = String(row.merchant || '').trim();
    if (!merchant) continue;
    const direction = Number(row.amount) >= 0 ? 'income' : 'expense';
    const key = `${merchant.toLowerCase()}|${direction}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push({ ...row, direction });
  }

  const dismissed = fetchDismissedKeys(householdId);
  const existing = existingMerchantKeys(householdId);

  return Array.from(groups.entries())
    .map(([key, items]) => {
      if (items.length < 2 || existing.has(key) || dismissed.has(key)) return null;
      const months = new Set(items.map((item) => String(item.date).slice(0, 7)));
      if (months.size < 2) return null;

      const intervals = [];
      for (let i = 1; i < items.length; i += 1) {
        const interval = daysBetween(items[i - 1].date, items[i].date);
        if (interval !== null && interval > 0) intervals.push(interval);
      }
      if (intervals.length === 0) return null;
      const averageInterval = intervals.reduce((sum, value) => sum + value, 0) / intervals.length;
      if (averageInterval < 5 || averageInterval > 400) return null;

      const latest = items[items.length - 1];
      const frequency = inferFrequency(averageInterval);
      const nextDate = advanceToUpcoming(addInterval(latest.date, frequency), frequency);
      const amount = items.reduce((sum, item) => sum + Math.abs(Number(item.amount) || 0), 0) / items.length;
      const confidence = scoreUpcomingSuggestion({
        categoryName: latest.category_name,
        direction: latest.direction,
        averageInterval,
        transactionCount: items.length,
        amounts: items.map((item) => item.amount),
        latestDate: latest.date
      });
      if (confidence < MIN_SUGGESTION_CONFIDENCE) return null;
      return {
        key,
        name: latest.merchant,
        merchant: latest.merchant,
        kind: normalizeKind(null, latest.direction, latest.category_name),
        direction: latest.direction,
        amount: centsToDollars(dollarsToCents(amount)),
        next_date: nextDate,
        category_id: latest.category_id,
        category_name: latest.category_name,
        account_id: latest.account_id,
        source_transaction_id: latest.id,
        transaction_count: items.length,
        confidence,
        ...frequency
      };
    })
    .filter(Boolean)
    .sort((a, b) => b.confidence - a.confidence || a.next_date.localeCompare(b.next_date))
    .slice(0, 20);
}

function createItem(householdId, payload) {
  const result = db
    .prepare(
      `INSERT INTO upcoming_items (
         household_id, name, kind, source, merchant, amount, direction, frequency_type,
         frequency_interval, frequency_unit, recurrence_rule, amount_strategy,
         amount_lookback_months, next_date, category_id, account_id, source_transaction_id, notes
       ) VALUES (
         @household_id, @name, @kind, @source, @merchant, @amount, @direction, @frequency_type,
         @frequency_interval, @frequency_unit, @recurrence_rule, @amount_strategy,
         @amount_lookback_months, @next_date, @category_id, @account_id, @source_transaction_id, @notes
       )`
    )
    .run({ household_id: householdId, ...payload });
  return result.lastInsertRowid;
}

function occurrenceFromItem(item, date) {
  const amount = Number(item.projected_amount ?? item.amount ?? 0);
  return {
    id: `${item.id}:${date}`,
    item_id: item.id,
    name: item.name,
    merchant: item.merchant,
    kind: item.kind,
    direction: item.direction,
    amount,
    stored_amount: Number(item.amount || 0),
    projected_amount: Number(item.projected_amount ?? amount),
    is_projected_amount: item.amount_strategy === 'history_average',
    date,
    next_date: date,
    category_id: item.category_id,
    category_name: item.category_name,
    category_color: item.category_color,
    category_icon: item.category_icon,
    account_id: item.account_id,
    account_name: item.account_name,
    frequency_type: item.frequency_type,
    frequency_interval: item.frequency_interval,
    frequency_unit: item.frequency_unit,
    recurrence_rule: item.recurrence_rule,
    amount_strategy: item.amount_strategy,
    amount_lookback_months: item.amount_lookback_months,
    projection: item.projection
  };
}

function buildOccurrences(items, startDate, endDate) {
  return items
    .flatMap((item) => (
      datesForItemBetween(item, startDate, endDate)
        .map((date) => occurrenceFromItem(item, date))
    ))
    .sort((a, b) => a.next_date.localeCompare(b.next_date) || String(a.name).localeCompare(String(b.name)));
}

function summarizeOccurrences(occurrences) {
  const income = occurrences
    .filter((occurrence) => occurrence.direction === 'income')
    .reduce((sum, occurrence) => sum + Number(occurrence.amount || 0), 0);
  const expenses = occurrences
    .filter((occurrence) => occurrence.direction !== 'income')
    .reduce((sum, occurrence) => sum + Number(occurrence.amount || 0), 0);
  return {
    income,
    expenses,
    net: income - expenses,
    occurrence_count: occurrences.length
  };
}

router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const items = fetchItems(householdId);
    const suggestions = buildSuggestions(householdId);
    const today = formatLocalDate();
    const monthEnd = endOfMonth(today);
    const occurrenceWindowEnd = addDays(today, 62);
    const restOfMonth = buildOccurrences(items, today, monthEnd);
    const occurrences = buildOccurrences(items, today, occurrenceWindowEnd);
    const cashFlow = summarizeOccurrences(restOfMonth);
    const upcoming = occurrences.slice(0, 8);
    const monthlyExpenses = items
      .filter((item) => item.direction === 'expense')
      .reduce((sum, item) => sum + Number(item.projected_amount ?? item.amount ?? 0) * monthlyFactor(item), 0);
    const monthlyIncome = items
      .filter((item) => item.direction === 'income')
      .reduce((sum, item) => sum + Number(item.projected_amount ?? item.amount ?? 0) * monthlyFactor(item), 0);

    sendOk(res, {
      items,
      suggestions,
      occurrences,
      rest_of_month: restOfMonth,
      upcoming,
      cash_flow: {
        start_date: today,
        end_date: monthEnd,
        ...cashFlow
      },
      summary: {
        active_count: items.length,
        suggestion_count: suggestions.length,
        monthly_expenses: monthlyExpenses,
        monthly_income: monthlyIncome,
        rest_of_month_income: cashFlow.income,
        rest_of_month_expenses: cashFlow.expenses,
        rest_of_month_net: cashFlow.net
      }
    });
  } catch (err) {
    console.error('List upcoming failed:', err);
    sendServerError(res, err);
  }
});

router.post('/estimate', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const payload = normalizeBody({
      ...(req.body || {}),
      amount_strategy: 'history_average'
    }, householdId);
    const projection = projectAmount(payload, householdId);
    sendOk(res, {
      amount: centsToDollars(projection.cents),
      projection: {
        strategy: projection.strategy,
        lookback_months: projection.lookback_months,
        sample_count: projection.sample_count,
        sample_months: projection.sample_months,
        total: projection.total,
        average: projection.average,
        fallback: projection.fallback
      }
    });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const id = createItem(householdId, normalizeBody(req.body || {}, householdId));
    reconcileHouseholdUpcoming(householdId);
    sendCreated(res, { success: true, id, items: fetchItems(householdId) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/from-transaction', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = parseId(req.body?.transaction_id);
  if (id === null) return sendBadRequest(res, 'transaction_id must be an integer.');
  try {
    const row = db
      .prepare(
        `SELECT t.id, t.account_id, t.date, t.amount / 100.0 AS amount,
                COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
                ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} AS category_id,
                c.name AS category_name
           FROM transactions t
           LEFT JOIN categories c ON c.id = ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL}
            AND c.household_id = t.household_id
          WHERE t.id = ? AND t.household_id = ?`
      )
      .get(id, householdId);
    if (!row) return sendNotFound(res, 'Transaction not found.');

    const direction = Number(row.amount) >= 0 ? 'income' : 'expense';
    const payload = normalizeBody({
      name: row.merchant,
      merchant: row.merchant,
      amount: Math.abs(Number(row.amount) || 0),
      direction,
      kind: normalizeKind(null, direction, row.category_name),
      category_name: row.category_name,
      category_id: row.category_id,
      account_id: row.account_id,
      next_date: advanceToUpcoming(addInterval(row.date, { frequency_type: 'monthly' }), { frequency_type: 'monthly' }),
      frequency_type: 'monthly',
      source: 'transaction',
      source_transaction_id: row.id
    }, householdId);
    const itemId = createItem(householdId, payload);
    reconcileHouseholdUpcoming(householdId);
    sendCreated(res, { success: true, id: itemId, items: fetchItems(householdId) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/suggestions/accept', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const suggestion = normalizeBody({ ...(req.body || {}), source: 'suggestion' }, householdId);
    const id = createItem(householdId, suggestion);
    reconcileHouseholdUpcoming(householdId);
    if (req.body?.key) {
      db.prepare(
        `INSERT INTO upcoming_dismissed_suggestions (household_id, suggestion_key)
         VALUES (?, ?)
         ON CONFLICT(household_id, suggestion_key) DO UPDATE SET dismissed_at = datetime('now')`
      ).run(householdId, String(req.body.key));
    }
    sendCreated(res, { success: true, id, items: fetchItems(householdId) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/suggestions/dismiss', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const key = String(req.body?.key || '').trim();
  if (!key) return sendBadRequest(res, 'Suggestion key is required.');
  try {
    db.prepare(
      `INSERT INTO upcoming_dismissed_suggestions (household_id, suggestion_key)
       VALUES (?, ?)
       ON CONFLICT(household_id, suggestion_key) DO UPDATE SET dismissed_at = datetime('now')`
    ).run(householdId, key);
    sendOk(res, { success: true });
  } catch (err) {
    sendServerError(res, err);
  }
});

router.put('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'upcoming item');
  if (id === null) return;
  try {
    const payload = normalizeBody(req.body || {}, householdId);
    const result = db
      .prepare(
        `UPDATE upcoming_items
            SET name = @name,
                kind = @kind,
                source = @source,
                merchant = @merchant,
                amount = @amount,
                direction = @direction,
                frequency_type = @frequency_type,
                frequency_interval = @frequency_interval,
                frequency_unit = @frequency_unit,
                recurrence_rule = @recurrence_rule,
                amount_strategy = @amount_strategy,
                amount_lookback_months = @amount_lookback_months,
                next_date = @next_date,
                category_id = @category_id,
                account_id = @account_id,
                source_transaction_id = @source_transaction_id,
                notes = @notes,
                updated_at = datetime('now')
          WHERE id = @id AND household_id = @household_id`
      )
      .run({ id, household_id: householdId, ...payload });
    if (result.changes === 0) return sendNotFound(res, 'Upcoming item not found.');
    resetPendingUpcomingOccurrences(db, { householdId, itemId: id });
    reconcileHouseholdUpcoming(householdId);
    sendOk(res, { success: true, items: fetchItems(householdId) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'upcoming item');
  if (id === null) return;
  try {
    const result = db.prepare('DELETE FROM upcoming_items WHERE id = ? AND household_id = ?').run(id, householdId);
    if (result.changes === 0) return sendNotFound(res, 'Upcoming item not found.');
    sendOk(res, { success: true, items: fetchItems(householdId) });
  } catch (err) {
    sendServerError(res, err);
  }
});

export default router;
