import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import {
  buildCategoryTrend,
  buildFlowSeries,
  buildMonthKeys,
  buildSpendingTrendRowsSql,
  buildSpendingTrendSummary,
  normalizeTrendMonths
} from '../src/services/spendingTrends.js';

function makeTrendQueryDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      is_income INTEGER NOT NULL DEFAULT 0,
      is_transfer INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      category_id INTEGER,
      edited_category_id INTEGER,
      is_ignored INTEGER NOT NULL DEFAULT 0,
      edited_is_ignored INTEGER,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      edited_is_transfer INTEGER
    );
  `);
  db.prepare(
    `INSERT INTO categories (id, household_id, name, is_income, is_transfer)
     VALUES (?, 1, ?, 0, 0)`
  ).run(7, 'Dining & Drinks');
  db.prepare(
    `INSERT INTO categories (id, household_id, name, is_income, is_transfer)
     VALUES (?, 1, ?, 0, 0)`
  ).run(14, 'Groceries');
  return db;
}

test('spending trends builds a continuous month range', () => {
  assert.deepEqual(
    buildMonthKeys({ endMonth: '2026-05', months: 3 }),
    ['2026-03', '2026-04', '2026-05']
  );
  assert.equal(normalizeTrendMonths('7'), 6);
  assert.equal(normalizeTrendMonths('12'), 12);
  assert.equal(normalizeTrendMonths('24'), 6);
});

test('spending trends fills missing months and keeps cash flow in cents', () => {
  const flow = buildFlowSeries(['2026-04', '2026-05'], [
    { month: '2026-04', income: 300001, expenses: 123456 }
  ]);

  assert.deepEqual(flow, [
    { month: '2026-04', income: 3000.01, expenses: 1234.56, net: 1765.45 },
    { month: '2026-05', income: 0, expenses: 0, net: 0 }
  ]);

  assert.deepEqual(buildSpendingTrendSummary({ flow, categories: [] }), {
    total_income: 3000.01,
    total_expenses: 1234.56,
    total_net: 1765.45,
    average_income: 1500.01,
    average_expenses: 617.28,
    average_net: 882.73,
    top_category: null
  });
});

test('spending trends computes category averages and budget overages', () => {
  const trend = buildCategoryTrend({
    category: {
      id: 1,
      name: 'Dining',
      color: '#f97316',
      icon: 'D',
      is_income: false,
      is_transfer: false
    },
    budget: { id: 4, amount: 40000 },
    months: ['2026-03', '2026-04', '2026-05'],
    spendingRows: [
      { month: '2026-03', spent: 32525, transaction_count: 8 },
      { month: '2026-05', spent: 50190, transaction_count: 11 }
    ]
  });

  assert.equal(trend.budget_amount, 400);
  assert.equal(trend.total_spent, 827.15);
  assert.equal(trend.average_spent, 275.72);
  assert.equal(trend.median_spent, 325.25);
  assert.deepEqual(trend.highest_month, { month: '2026-05', spent: 501.9 });
  assert.equal(trend.over_budget_count, 1);
  assert.equal(trend.transaction_count, 19);
  assert.deepEqual(
    trend.months.map((month) => ({
      month: month.month,
      spent: month.spent,
      over_budget: month.over_budget
    })),
    [
      { month: '2026-03', spent: 325.25, over_budget: false },
      { month: '2026-04', spent: 0, over_budget: false },
      { month: '2026-05', spent: 501.9, over_budget: true }
    ]
  );
});

test('spending trends groups edited category spending by display category', () => {
  const db = makeTrendQueryDb();
  const insert = db.prepare(
    `INSERT INTO transactions (
       id, household_id, date, amount, category_id, edited_category_id,
       is_ignored, edited_is_ignored, is_transfer, edited_is_transfer
     ) VALUES (?, 1, ?, ?, ?, ?, 0, NULL, 0, NULL)`
  );

  insert.run(1, '2026-04-02', -2500, 14, 7);
  insert.run(2, '2026-04-03', -10000, 14, null);

  const rows = db
    .prepare(buildSpendingTrendRowsSql())
    .all(1, 1, '2026-04-01', '2026-04-30')
    .map((row) => ({
      category_id: row.category_id,
      month: row.month,
      spent: row.spent,
      transaction_count: row.transaction_count
    }))
    .sort((a, b) => a.category_id - b.category_id);

  assert.deepEqual(rows, [
    { category_id: 7, month: '2026-04', spent: 2500, transaction_count: 1 },
    { category_id: 14, month: '2026-04', spent: 10000, transaction_count: 1 }
  ]);
});
