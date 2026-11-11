import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { addMonthsToLocalMonth, formatLocalMonth } from '../lib/localDate.js';
import { sendOk, sendServerError } from '../lib/http.js';
import { parseBoundedInteger } from '../lib/routeParams.js';

const router = express.Router();

const CASH_TYPES = new Set(['checking', 'savings', 'cash']);
const INVESTMENT_TYPES = new Set(['investment']);
const CREDIT_TYPES = new Set(['credit']);
const LOAN_TYPES = new Set(['loan', 'mortgage']);

function monthKey(date) {
  return String(date || '').slice(0, 7);
}

function addMonths(key, amount) {
  return addMonthsToLocalMonth(key, amount);
}

function isPropertyAnchor(account) {
  return (
    account.is_property === 1 ||
    (account.type === 'mortgage' && account.estimated_value != null && !account.linked_property_account_id)
  );
}

function contributionForAccount(account, balance, activeAnchorIds = null) {
  const value = Number(balance) || 0;
  if (account.is_property === 1) {
    return Number(account.estimated_value) || value || 0;
  }
  if (account.linked_property_account_id && (!activeAnchorIds || activeAnchorIds.has(account.linked_property_account_id))) {
    return -Math.abs(value);
  }
  if (account.type === 'mortgage') {
    const estimatedValue = Number(account.estimated_value) || 0;
    return estimatedValue - Math.abs(value);
  }
  if (CREDIT_TYPES.has(account.type) || LOAN_TYPES.has(account.type)) {
    return -Math.abs(value);
  }
  return value;
}

function categoryForAccount(account, activeAnchorIds = null) {
  if (
    account.is_property === 1 ||
    account.type === 'mortgage' ||
    (account.linked_property_account_id && (!activeAnchorIds || activeAnchorIds.has(account.linked_property_account_id)))
  ) {
    return 'realEstate';
  }
  if (CASH_TYPES.has(account.type)) return 'cash';
  if (INVESTMENT_TYPES.has(account.type)) return 'investments';
  if (CREDIT_TYPES.has(account.type)) return 'credit';
  if (LOAN_TYPES.has(account.type)) return 'loans';
  return 'other';
}

function emptyTotals() {
  return {
    cash: 0,
    investments: 0,
    realEstate: 0,
    credit: 0,
    loans: 0,
    other: 0,
    assets: 0,
    liabilities: 0,
    netWorth: 0
  };
}

function accumulateSnapshotTotals(accounts, getBalance) {
  const totals = emptyTotals();
  const anchors = accounts.filter(isPropertyAnchor);
  const activeAnchorIds = new Set(anchors.map((a) => a.id));
  const childrenByAnchor = new Map();

  for (const acct of accounts) {
    if (acct.linked_property_account_id && activeAnchorIds.has(acct.linked_property_account_id)) {
      if (!childrenByAnchor.has(acct.linked_property_account_id)) {
        childrenByAnchor.set(acct.linked_property_account_id, []);
      }
      childrenByAnchor.get(acct.linked_property_account_id).push(acct);
    }
  }

  for (const anchor of anchors) {
    const anchorBal = Number(getBalance(anchor)) || 0;
    const propVal = anchor.is_property === 1
      ? (Number(anchor.estimated_value) || anchorBal || 0)
      : (Number(anchor.estimated_value) || 0);
    let debt = anchor.type === 'mortgage' ? Math.abs(anchorBal) : 0;
    for (const child of childrenByAnchor.get(anchor.id) || []) {
      debt += Math.abs(Number(getBalance(child)) || 0);
    }
    const equity = propVal - debt;
    totals.realEstate += equity;
    if (equity >= 0) {
      totals.assets += equity;
    } else {
      totals.liabilities += Math.abs(equity);
    }
    totals.netWorth += equity;
  }

  for (const account of accounts) {
    if (activeAnchorIds.has(account.id)) continue;
    if (account.linked_property_account_id && activeAnchorIds.has(account.linked_property_account_id)) continue;

    const contribution = contributionForAccount(account, getBalance(account), activeAnchorIds);
    const bucket = categoryForAccount(account, activeAnchorIds);
    totals[bucket] += contribution;

    if (bucket === 'credit' || bucket === 'loans') {
      totals.liabilities += Math.abs(Math.min(0, contribution));
    } else if (contribution >= 0) {
      totals.assets += contribution;
    } else {
      totals.liabilities += Math.abs(contribution);
    }

    totals.netWorth += contribution;
  }

  return { totals, activeAnchorIds };
}

function buildCurrentSummary(accounts) {
  const { totals, activeAnchorIds } = accumulateSnapshotTotals(accounts, (a) => a.current_balance);
  const breakdown = [];

  for (const account of accounts) {
    const contribution = contributionForAccount(account, account.current_balance, activeAnchorIds);
    breakdown.push({
      id: account.id,
      name: account.name,
      type: account.type,
      institution: account.institution,
      is_property: account.is_property ?? 0,
      linked_property_account_id: account.linked_property_account_id ?? null,
      current_balance: account.current_balance,
      estimated_value: account.estimated_value,
      contribution,
      category: categoryForAccount(account, activeAnchorIds)
    });
  }

  breakdown.sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution));
  return { totals, breakdown };
}

function buildHistory(accounts, monthlyDeltas, balanceRecords, firstMonth, latestMonth) {
  if (!latestMonth) return [];

  const accountById = new Map(accounts.map((a) => [a.id, a]));
  const balancesByAccount = new Map(
    accounts.map((a) => [a.id, Number(a.current_balance) || 0])
  );

  const byMonth = new Map();
  for (const row of monthlyDeltas) {
    if (!byMonth.has(row.month)) byMonth.set(row.month, []);
    byMonth.get(row.month).push(row);
  }

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
  for (const records of recordsByAccount.values()) {
    records.sort((a, b) => b.date.localeCompare(a.date));
  }

  function snapshotBalanceForMonth(accountId, month) {
    const records = recordsByAccount.get(accountId);
    if (!records) return null;
    const record = records.find((item) => item.month <= month);
    return record ? record.balance : null;
  }

  const rows = [];
  let cursor = latestMonth;

  while (cursor >= firstMonth) {
    const { totals } = accumulateSnapshotTotals(accounts, (account) => {
      const snapshotBalance = snapshotBalanceForMonth(account.id, cursor);
      return snapshotBalance ?? (balancesByAccount.get(account.id) || 0);
    });

    rows.push({
      month: cursor,
      cash: totals.cash,
      investments: totals.investments,
      realEstate: totals.realEstate,
      credit: totals.credit,
      loans: totals.loans,
      other: totals.other,
      assets: totals.assets,
      liabilities: totals.liabilities,
      netWorth: totals.netWorth
    });

    for (const delta of byMonth.get(cursor) || []) {
      const account = accountById.get(delta.account_id);
      if (!account) continue;
      balancesByAccount.set(
        delta.account_id,
        (balancesByAccount.get(delta.account_id) || 0) - (Number(delta.amount) || 0)
      );
    }

    cursor = addMonths(cursor, -1);
  }

  return rows.reverse();
}

function findYearStartRow(history, latestMonth) {
  const yearStartMonth = `${latestMonth.slice(0, 4)}-01`;
  return history.find((row) => row.month === yearStartMonth) ?? history[0] ?? null;
}

router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const allTime = String(req.query.months || '').toLowerCase() === 'all';
    const months = parseBoundedInteger(req.query.months, {
      fallback: 24,
      min: 3,
      max: 240
    });

    const accounts = db
      .prepare(
        `
        SELECT id, name, type, institution,
               is_property, linked_property_account_id,
               current_balance / 100.0 AS current_balance,
               estimated_value / 100.0 AS estimated_value
          FROM accounts
         WHERE household_id = ?
           AND is_archived = 0
         ORDER BY sort_order ASC, name ASC
      `
      )
      .all(householdId);

    const latestTransaction = db
      .prepare(
        `
        SELECT
          MAX(date) AS latest,
          MIN(date) AS earliest
          FROM transactions
         WHERE household_id = ?
      `
      )
      .get(householdId);

    const latestRecord = db
      .prepare(
        `
        SELECT
          MAX(record_date) AS latest,
          MIN(record_date) AS earliest
          FROM account_balance_records
         WHERE household_id = ?
      `
      )
      .get(householdId);

    const todayMonth = formatLocalMonth();
    const latestTransactionMonth = monthKey(latestTransaction.latest);
    const latestRecordMonth = monthKey(latestRecord.latest);
    const latestDataMonth = [latestTransactionMonth, latestRecordMonth, todayMonth]
      .filter(Boolean)
      .sort()
      .at(-1);
    const latestMonth = latestDataMonth > todayMonth ? latestDataMonth : todayMonth;
    const earliestTransactionMonth = monthKey(latestTransaction.earliest);
    const earliestRecordMonth = monthKey(latestRecord.earliest);
    const earliestMonth = [earliestTransactionMonth, earliestRecordMonth]
      .filter(Boolean)
      .sort()[0] || latestMonth;
    const floorMonth = addMonths(latestMonth, -(months - 1));
    const firstMonth = allTime
      ? earliestMonth
      : earliestMonth > floorMonth
        ? earliestMonth
        : floorMonth;

    const monthlyDeltas = db
      .prepare(
        `
        SELECT account_id, substr(date, 1, 7) AS month, SUM(amount) / 100.0 AS amount
          FROM transactions
         WHERE household_id = ?
           AND date >= ?
         GROUP BY account_id, substr(date, 1, 7)
         ORDER BY month ASC
      `
      )
      .all(householdId, `${firstMonth}-01`);

    const balanceRecords = db
      .prepare(
        `
        SELECT account_id, record_date, balance / 100.0 AS balance
          FROM account_balance_records
         WHERE household_id = ?
           AND record_date <= ?
         ORDER BY account_id ASC, record_date DESC
      `
      )
      .all(householdId, `${latestMonth}-31`);

    const current = buildCurrentSummary(accounts);
    const history = buildHistory(
      accounts,
      monthlyDeltas,
      balanceRecords,
      firstMonth,
      latestMonth
    );
    const previous = history.length > 1 ? history[history.length - 2] : null;
    const first = history[0] || null;
    const yearStartRow = findYearStartRow(history, latestMonth);

    sendOk(res, {
      summary: {
        ...current.totals,
        accountCount: accounts.length,
        monthOverMonth: previous ? current.totals.netWorth - previous.netWorth : 0,
        periodChange: first ? current.totals.netWorth - first.netWorth : 0,
        yearToDateChange: yearStartRow ? current.totals.netWorth - yearStartRow.netWorth : 0
      },
      history,
      breakdown: current.breakdown
    });
  } catch (err) {
    console.error('Net worth failed:', err);
    sendServerError(res, err);
  }
});

export default router;
