import {
  addMonthsToLocalMonth,
  formatLocalMonth
} from '../lib/localDate.js';
import { parseId } from '../lib/routeParams.js';

function accountBalance(row) {
  const estimated = Number(row.estimated_value);
  if (Number.isFinite(estimated) && estimated > 0) return estimated;
  const current = Number(row.current_balance);
  return Number.isFinite(current) ? current : 0;
}

function monthKey(date) {
  return String(date || '').slice(0, 7);
}

/**
 * Parses an optional CSV `accountIds` query param into null (auto default),
 * an empty array (explicitly zero selected accounts), or deduplicated positive IDs.
 */
export function parseAccountIdsQuery(raw) {
  if (raw === undefined || raw === null) return null;
  const joined = Array.isArray(raw) ? raw.join(',') : String(raw);
  const text = joined.trim();
  if (!text) return [];

  const seen = new Set();
  const ids = [];
  for (const segment of text.split(',')) {
    const id = parseId(segment);
    if (id === null) {
      const err = new Error('accountIds must be a comma-separated list of positive account IDs.');
      err.status = 400;
      throw err;
    }
    if (!seen.has(id)) {
      seen.add(id);
      ids.push(id);
    }
  }
  return ids;
}

function resolveHistoryAccounts(db, householdId, customAccountIds) {
  let rows;
  if (Array.isArray(customAccountIds)) {
    if (customAccountIds.length === 0) return [];
    const placeholders = customAccountIds.map(() => '?').join(', ');
    rows = db
      .prepare(
        `SELECT a.id, a.name, a.type, a.institution,
                MIN(hra.account_kind) AS account_kind,
                a.current_balance / 100.0 AS current_balance,
                a.estimated_value / 100.0 AS estimated_value
           FROM accounts a
           LEFT JOIN household_retirement_accounts hra
             ON hra.account_id = a.id
            AND hra.household_id = a.household_id
          WHERE a.household_id = ?
            AND a.is_archived = 0
            AND a.id IN (${placeholders})
          GROUP BY a.id
          ORDER BY a.sort_order ASC, a.name ASC`
      )
      .all(householdId, ...customAccountIds);
  } else {
    rows = db
      .prepare(
        `SELECT a.id, a.name, a.type, a.institution,
                MIN(hra.account_kind) AS account_kind,
                a.current_balance / 100.0 AS current_balance,
                a.estimated_value / 100.0 AS estimated_value
           FROM household_retirement_accounts hra
           JOIN accounts a ON a.id = hra.account_id
          WHERE hra.household_id = ?
            AND a.household_id = ?
            AND a.is_archived = 0
          GROUP BY a.id
          ORDER BY MIN(hra.account_kind) ASC, a.sort_order ASC, a.name ASC`
      )
      .all(householdId, householdId);

    if (rows.length === 0) {
      rows = db
        .prepare(
          `SELECT a.id, a.name, a.type, a.institution,
                  NULL AS account_kind,
                  a.current_balance / 100.0 AS current_balance,
                  a.estimated_value / 100.0 AS estimated_value
             FROM accounts a
            WHERE a.household_id = ?
              AND a.is_archived = 0
              AND a.type = 'investment'
            ORDER BY a.sort_order ASC, a.name ASC`
        )
        .all(householdId);
    }

    if (rows.length === 0) {
      const retirementGoal = db
        .prepare(
          `SELECT id, linked_account_id
             FROM goals
            WHERE household_id = ?
              AND lower(trim(name)) = 'retirement'
            LIMIT 1`
        )
        .get(householdId);
      if (retirementGoal) {
        rows = db
          .prepare(
            `SELECT a.id, a.name, a.type, a.institution,
                    NULL AS account_kind,
                    a.current_balance / 100.0 AS current_balance,
                    a.estimated_value / 100.0 AS estimated_value
               FROM goal_account_allocations gaa
               JOIN accounts a ON a.id = gaa.account_id
              WHERE gaa.household_id = ?
                AND gaa.goal_id = ?
                AND a.household_id = ?
                AND a.is_archived = 0
              GROUP BY a.id
              ORDER BY a.sort_order ASC, a.name ASC`
          )
          .all(householdId, retirementGoal.id, householdId);

        if (rows.length === 0 && retirementGoal.linked_account_id) {
          rows = db
            .prepare(
              `SELECT a.id, a.name, a.type, a.institution,
                      NULL AS account_kind,
                      a.current_balance / 100.0 AS current_balance,
                      a.estimated_value / 100.0 AS estimated_value
                 FROM accounts a
                WHERE a.household_id = ?
                  AND a.id = ?
                  AND a.is_archived = 0`
            )
            .all(householdId, retirementGoal.linked_account_id);
        }
      }
    }
  }

  return rows.map((row) => ({
    ...row,
    balance: accountBalance(row)
  }));
}

export function buildRetirementHistory(db, householdId, months, customAccountIds = null) {
  const accounts = resolveHistoryAccounts(db, householdId, customAccountIds);
  if (accounts.length === 0) {
    return { accounts: [], history: [] };
  }

  const accountIds = accounts.map((account) => account.id);
  const placeholders = accountIds.map(() => '?').join(', ');
  const todayMonth = formatLocalMonth();

  const latestTransaction = db
    .prepare(
      `SELECT MAX(date) AS latest, MIN(date) AS earliest
         FROM transactions
        WHERE household_id = ?
          AND account_id IN (${placeholders})`
    )
    .get(householdId, ...accountIds);

  const latestRecord = db
    .prepare(
      `SELECT MAX(record_date) AS latest, MIN(record_date) AS earliest
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id IN (${placeholders})`
    )
    .get(householdId, ...accountIds);

  const latestMonth =
    [monthKey(latestTransaction.latest), monthKey(latestRecord.latest), todayMonth]
      .filter(Boolean)
      .sort()
      .at(-1) || todayMonth;
  const earliestMonth =
    [monthKey(latestTransaction.earliest), monthKey(latestRecord.earliest)]
      .filter(Boolean)
      .sort()[0] || latestMonth;
  const floorMonth = addMonthsToLocalMonth(latestMonth, -(months - 1));
  const firstMonth = earliestMonth > floorMonth ? earliestMonth : floorMonth;

  const monthlyDeltas = db
    .prepare(
      `SELECT account_id, substr(date, 1, 7) AS month, SUM(amount) / 100.0 AS amount
         FROM transactions
        WHERE household_id = ?
          AND account_id IN (${placeholders})
          AND date >= ?
        GROUP BY account_id, substr(date, 1, 7)
        ORDER BY month ASC`
    )
    .all(householdId, ...accountIds, `${firstMonth}-01`);

  const balanceRecords = db
    .prepare(
      `SELECT account_id, record_date, balance / 100.0 AS balance
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id IN (${placeholders})
          AND record_date <= ?
        ORDER BY account_id ASC, record_date DESC`
    )
    .all(householdId, ...accountIds, `${latestMonth}-31`);

  const balancesByAccount = new Map(accounts.map((account) => [account.id, Number(account.balance) || 0]));
  const recordsByAccount = new Map();
  for (const record of balanceRecords) {
    const accountId = Number(record.account_id);
    if (!recordsByAccount.has(accountId)) recordsByAccount.set(accountId, []);
    recordsByAccount.get(accountId).push({
      month: monthKey(record.record_date),
      date: record.record_date,
      balance: Number(record.balance) || 0
    });
  }

  function snapshotBalanceForMonth(accountId, month) {
    const records = recordsByAccount.get(accountId);
    if (!records) return null;
    const record = records.find((item) => item.month === month);
    return record ? record.balance : null;
  }

  const deltasByMonth = new Map();
  for (const row of monthlyDeltas) {
    if (!deltasByMonth.has(row.month)) deltasByMonth.set(row.month, []);
    deltasByMonth.get(row.month).push(row);
  }

  const rows = [];
  const accountRows = [];
  let cursor = latestMonth;
  while (cursor >= firstMonth) {
    const accountBalances = accounts.map((account) => {
      const snapshot = snapshotBalanceForMonth(account.id, cursor);
      const balance = snapshot ?? balancesByAccount.get(account.id) ?? 0;
      if (snapshot !== null) balancesByAccount.set(account.id, balance);
      return {
        account_id: account.id,
        balance
      };
    });
    const total = accountBalances.reduce((sum, account) => sum + account.balance, 0);
    accountRows.push({ month: cursor, accounts: accountBalances });
    rows.push({ month: cursor, balance: total, accounts: accountBalances });

    for (const delta of deltasByMonth.get(cursor) || []) {
      balancesByAccount.set(
        delta.account_id,
        (balancesByAccount.get(delta.account_id) || 0) - (Number(delta.amount) || 0)
      );
    }

    cursor = addMonthsToLocalMonth(cursor, -1);
  }

  const accountRowsAsc = accountRows.reverse();
  const latestAccountRow = accountRowsAsc.at(-1) || null;
  const latestYear = String(latestMonth).slice(0, 4);
  const ytdStartRow =
    accountRowsAsc.find((row) => row.month >= `${latestYear}-01`) ||
    accountRowsAsc[0] ||
    null;
  const balanceForAccount = (row, accountId) =>
    row?.accounts.find((account) => Number(account.account_id) === Number(accountId))?.balance ?? null;

  return {
    accounts: accounts.map((account) => {
      const currentBalance = balanceForAccount(latestAccountRow, account.id) ?? account.balance;
      const ytdStartBalance = balanceForAccount(ytdStartRow, account.id);
      const ytdChange = ytdStartBalance === null ? null : currentBalance - ytdStartBalance;
      const ytdGrowthPercent = ytdStartBalance > 0 ? (ytdChange / ytdStartBalance) * 100 : null;
      return {
        id: account.id,
        name: account.name,
        type: account.type,
        institution: account.institution,
        account_kind: account.account_kind,
        balance: account.balance,
        ytd_change: ytdChange,
        ytd_growth_percent: ytdGrowthPercent,
        ytd_start_balance: ytdStartBalance
      };
    }),
    history: rows.reverse()
  };
}
