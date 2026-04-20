import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

const router = express.Router();

const CASH_TYPES = new Set(['checking', 'savings', 'cash']);
const INVESTMENT_TYPES = new Set(['investment']);
const CREDIT_TYPES = new Set(['credit']);
const LOAN_TYPES = new Set(['loan', 'mortgage']);

function monthKey(date) {
  return String(date || '').slice(0, 7);
}

function addMonths(key, amount) {
  const [year, month] = key.split('-').map(Number);
  const d = new Date(year, month - 1 + amount, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function contributionForAccount(account, balance) {
  const value = Number(balance) || 0;
  if (account.type === 'mortgage') {
    const estimatedValue = Number(account.estimated_value) || 0;
    return estimatedValue - Math.abs(value);
  }
  return value;
}

function categoryForAccount(account) {
  if (CASH_TYPES.has(account.type)) return 'cash';
  if (INVESTMENT_TYPES.has(account.type)) return 'investments';
  if (account.type === 'mortgage') return 'realEstate';
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

function addContribution(totals, account, contribution) {
  const bucket = categoryForAccount(account);
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

function buildCurrentSummary(accounts) {
  const totals = emptyTotals();
  const breakdown = [];

  for (const account of accounts) {
    const contribution = contributionForAccount(account, account.current_balance);
    addContribution(totals, account, contribution);
    breakdown.push({
      id: account.id,
      name: account.name,
      type: account.type,
      institution: account.institution,
      current_balance: account.current_balance,
      estimated_value: account.estimated_value,
      contribution,
      category: categoryForAccount(account)
    });
  }

  breakdown.sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution));
  return { totals, breakdown };
}

function buildHistory(accounts, monthlyDeltas, firstMonth, latestMonth) {
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

  const rows = [];
  let cursor = latestMonth;

  while (cursor >= firstMonth) {
    const totals = emptyTotals();

    for (const account of accounts) {
      const balance = balancesByAccount.get(account.id) || 0;
      addContribution(totals, account, contributionForAccount(account, balance));
    }

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

router.get('/', requireAuth, (req, res) => {
  try {
    const requestedMonths = parseInt(req.query.months, 10);
    const months = Math.min(60, Math.max(3, Number.isFinite(requestedMonths) ? requestedMonths : 24));

    const accounts = db
      .prepare(
        `
        SELECT id, name, type, institution, current_balance, estimated_value
          FROM accounts
         WHERE is_archived = 0
         ORDER BY sort_order ASC, name ASC
      `
      )
      .all();

    const latestTransaction = db
      .prepare('SELECT MAX(date) AS latest, MIN(date) AS earliest FROM transactions')
      .get();

    const todayMonth = new Date().toISOString().slice(0, 7);
    const latestDataMonth = monthKey(latestTransaction.latest) || todayMonth;
    const latestMonth = latestDataMonth > todayMonth ? latestDataMonth : todayMonth;
    const floorMonth = addMonths(latestMonth, -(months - 1));
    const earliestMonth = monthKey(latestTransaction.earliest) || latestMonth;
    const firstMonth = earliestMonth > floorMonth ? earliestMonth : floorMonth;

    const monthlyDeltas = db
      .prepare(
        `
        SELECT account_id, substr(date, 1, 7) AS month, SUM(amount) AS amount
          FROM transactions
         WHERE date >= ?
         GROUP BY account_id, substr(date, 1, 7)
         ORDER BY month ASC
      `
      )
      .all(`${firstMonth}-01`);

    const current = buildCurrentSummary(accounts);
    const history = buildHistory(accounts, monthlyDeltas, firstMonth, latestMonth);
    const previous = history.length > 1 ? history[history.length - 2] : null;
    const first = history[0] || null;

    res.json({
      summary: {
        ...current.totals,
        accountCount: accounts.length,
        monthOverMonth: previous ? current.totals.netWorth - previous.netWorth : 0,
        periodChange: first ? current.totals.netWorth - first.netWorth : 0
      },
      history,
      breakdown: current.breakdown
    });
  } catch (err) {
    console.error('Net worth failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
