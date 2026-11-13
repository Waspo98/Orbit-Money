import { formatLocalDate, isValidDateOnly, parseDateParts } from '../lib/localDate.js';
import { centsToDollars } from '../lib/money.js';

function previousMonthEnd(value) {
  const { year, month } = parseDateParts(value);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return date.toISOString().slice(0, 10);
}

export function serializeBalanceRecord(row) {
  return {
    id: row.id,
    account_id: row.account_id,
    record_date: row.record_date,
    date: row.record_date,
    balance: centsToDollars(row.balance),
    source: row.source || 'manual'
  };
}

/**
 * Inserts or updates a manual balance snapshot for an account and updates current_balance
 * when the snapshot is the most recent record.
 */
export function recordManualAccountBalance(
  db,
  { householdId, accountId, recordDate, balanceCents, previousBalanceCents = null }
) {
  const run = db.transaction(() => {
    const insertRecord = db.prepare(
      `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
       VALUES (?, ?, ?, ?, 'manual')
       ON CONFLICT(household_id, account_id, record_date) DO UPDATE SET
         balance = excluded.balance,
         source = 'manual',
         updated_at = datetime('now')`
    );

    const priorRecords = db
      .prepare(
        `SELECT COUNT(*) AS count
           FROM account_balance_records
          WHERE household_id = ? AND account_id = ? AND record_date < ?`
      )
      .get(householdId, accountId, recordDate).count;

    if (priorRecords === 0 && previousBalanceCents !== null && previousBalanceCents !== balanceCents) {
      const baselineDate = previousMonthEnd(recordDate);
      db.prepare(
        `INSERT OR IGNORE INTO account_balance_records (household_id, account_id, record_date, balance)
         VALUES (?, ?, ?, ?)`
      ).run(householdId, accountId, baselineDate, previousBalanceCents);
    }

    insertRecord.run(householdId, accountId, recordDate, balanceCents);

    const latestRecord = db
      .prepare(
        `SELECT record_date, balance
           FROM account_balance_records
          WHERE household_id = ? AND account_id = ?
          ORDER BY record_date DESC, id DESC
          LIMIT 1`
      )
      .get(householdId, accountId);

    if (latestRecord?.record_date === recordDate) {
      db.prepare(
        `UPDATE accounts
            SET current_balance = ?, is_manual = 1, updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(balanceCents, accountId, householdId);
    }

    return db.prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?').get(accountId, householdId);
  });

  return run();
}

/**
 * Automatically captures an on-change or monthly-anchor balance checkpoint for an active
 * non-property account while preserving user-entered manual records on the same date.
 */
export function upsertAutoAccountBalance(
  db,
  { householdId, accountId, recordDate = formatLocalDate(), balanceCents, source = 'manual' }
) {
  const normalizedSource = source === 'simplefin' ? 'simplefin' : 'manual';
  if (!isValidDateOnly(recordDate) || !Number.isFinite(balanceCents)) {
    return { recorded: false };
  }

  const account = db
    .prepare(
      `SELECT id, is_archived, COALESCE(is_property, 0) AS is_property
         FROM accounts
        WHERE id = ? AND household_id = ?`
    )
    .get(accountId, householdId);
  if (!account || account.is_archived === 1 || account.is_property === 1) {
    return { recorded: false };
  }

  const sameDay = db
    .prepare(
      `SELECT id, balance, source
         FROM account_balance_records
        WHERE household_id = ? AND account_id = ? AND record_date = ?`
    )
    .get(householdId, accountId, recordDate);

  if (sameDay) {
    if (sameDay.source === 'manual' && normalizedSource === 'simplefin') {
      return { recorded: false };
    }
    if (sameDay.balance === balanceCents && sameDay.source === normalizedSource) {
      return { recorded: false };
    }
    db.prepare(
      `UPDATE account_balance_records
          SET balance = ?, source = ?, updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(balanceCents, normalizedSource, sameDay.id, householdId);
    return { recorded: true, updated: true };
  }

  const prior = db
    .prepare(
      `SELECT record_date, balance
         FROM account_balance_records
        WHERE household_id = ? AND account_id = ? AND record_date < ?
        ORDER BY record_date DESC, id DESC
        LIMIT 1`
    )
    .get(householdId, accountId, recordDate);

  const shouldInsert = !prior
    ? (balanceCents !== 0 || normalizedSource === 'simplefin')
    : (prior.balance !== balanceCents || prior.record_date.slice(0, 7) !== recordDate.slice(0, 7));

  if (!shouldInsert) {
    return { recorded: false };
  }

  db.prepare(
    `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
     VALUES (?, ?, ?, ?, ?)`
  ).run(householdId, accountId, recordDate, balanceCents, normalizedSource);

  return { recorded: true, inserted: true };
}

/**
 * Captures daily/monthly baseline checkpoints across all active non-sample household accounts.
 */
export function captureAllHouseholdsDailyCheckpoints(db, recordDate = formatLocalDate()) {
  const accounts = db
    .prepare(
      `SELECT a.id, a.household_id, a.current_balance, a.simplefin_account_id
         FROM accounts a
        WHERE a.is_archived = 0
          AND COALESCE(a.is_property, 0) = 0
          AND NOT EXISTS (
            SELECT 1
              FROM household_memberships hm
              JOIN users u ON u.id = hm.user_id
             WHERE hm.household_id = a.household_id
             GROUP BY hm.household_id
            HAVING COUNT(*) > 0
               AND SUM(CASE WHEN u.username LIKE 'sample:%' THEN 0 ELSE 1 END) = 0
          )`
    )
    .all();

  let recorded = 0;
  const run = db.transaction(() => {
    for (const row of accounts) {
      const res = upsertAutoAccountBalance(db, {
        householdId: row.household_id,
        accountId: row.id,
        recordDate,
        balanceCents: row.current_balance,
        source: row.simplefin_account_id ? 'simplefin' : 'manual'
      });
      if (res.recorded) recorded += 1;
    }
  });
  run();

  return { checked: accounts.length, recorded };
}

export function listHouseholdBalanceRecords(db, householdId) {
  return db
    .prepare(
      `SELECT id, account_id, record_date, balance, source
         FROM account_balance_records
        WHERE household_id = ?
        ORDER BY record_date DESC, id DESC`
    )
    .all(householdId)
    .map(serializeBalanceRecord);
}

export function listAccountBalanceRecords(db, { householdId, accountId }) {
  const account = db
    .prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ?')
    .get(accountId, householdId);
  if (!account) return null;

  return db
    .prepare(
      `SELECT id, account_id, record_date, balance, source
         FROM account_balance_records
        WHERE household_id = ? AND account_id = ?
        ORDER BY record_date DESC, id DESC`
    )
    .all(householdId, accountId)
    .map(serializeBalanceRecord);
}

export function deleteAccountBalanceRecord(db, { householdId, accountId, recordId }) {
  return (
    db
      .prepare(
        `DELETE FROM account_balance_records
          WHERE id = ? AND account_id = ? AND household_id = ?`
      )
      .run(recordId, accountId, householdId).changes > 0
  );
}
