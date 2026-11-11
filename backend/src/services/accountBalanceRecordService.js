import { parseDateParts } from '../lib/localDate.js';

function previousMonthEnd(value) {
  const { year, month } = parseDateParts(value);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return date.toISOString().slice(0, 10);
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
