import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import {
  UNCATEGORIZED_BUDGET_CATEGORY,
  buildBudgetItem,
  buildBudgetSummary,
  serializeBudgetItem
} from '../src/services/budgetOverview.js';
import { buildMonthlyBudgetOverview } from '../src/services/monthlyBudgetOverview.js';

function makeMonthlyBudgetDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      color TEXT,
      icon TEXT,
      is_income INTEGER NOT NULL DEFAULT 0,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      sort_order INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE budgets (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      category_id INTEGER NOT NULL,
      amount INTEGER NOT NULL DEFAULT 0,
      rollover INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      category_id INTEGER,
      edited_category_id INTEGER,
      edited_category_id_source TEXT,
      is_ignored INTEGER NOT NULL DEFAULT 0,
      edited_is_ignored INTEGER,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      edited_is_transfer INTEGER
    );
  `);
  return db;
}

test('budget overview keeps summary math in integer cents', () => {
  const budgetedItems = [
    buildBudgetItem(
      { id: 1, name: 'Groceries', color: '#66BB6A', icon: '', is_income: false, is_transfer: false },
      { id: 10, amount: 10010, rollover: 0 },
      { spent: 7555, n: 3 }
    )
  ];
  const unbudgetedItems = [
    buildBudgetItem(UNCATEGORIZED_BUDGET_CATEGORY, null, { spent: 335, n: 1 })
  ];

  assert.deepEqual(
    buildBudgetSummary({
      budgetedItems,
      unbudgetedItems,
      incomeCents: 20000,
      expensesCents: 7890
    }),
    {
      total_budgeted: 100.1,
      total_spent_in_budgets: 75.55,
      total_spent_unbudgeted: 3.35,
      total_spent: 78.9,
      total_income: 200,
      total_expenses: 78.9,
      total_net: 121.1
    }
  );
});

test('budget overview serializes uncategorized spending without internal cents fields', () => {
  const item = buildBudgetItem(UNCATEGORIZED_BUDGET_CATEGORY, null, { spent: 1299, n: 2 });

  assert.deepEqual(serializeBudgetItem(item), {
    category: UNCATEGORIZED_BUDGET_CATEGORY,
    budget_id: null,
    amount: null,
    rollover: null,
    spent: 12.99,
    transaction_count: 2
  });
});

test('monthly budget overview includes true uncategorized spending', () => {
  const db = makeMonthlyBudgetDb();
  db.prepare(
    `INSERT INTO categories (id, household_id, name, color, icon, is_income, is_transfer, sort_order)
     VALUES (1, 1, 'Groceries', '#66BB6A', '', 0, 0, 1)`
  ).run();
  db.prepare(
    `INSERT INTO transactions (
      id, household_id, date, amount, category_id, edited_category_id, edited_category_id_source,
      is_ignored, edited_is_ignored, is_transfer, edited_is_transfer
    ) VALUES (?, 1, ?, ?, ?, NULL, NULL, 0, NULL, 0, NULL)`
  ).run(1, '2026-05-04', -1299, null);

  const overview = buildMonthlyBudgetOverview(db, {
    householdId: 1,
    month: '2026-05'
  });

  assert.deepEqual(overview.unbudgeted, [
    {
      category: UNCATEGORIZED_BUDGET_CATEGORY,
      budget_id: null,
      amount: null,
      rollover: null,
      spent: 12.99,
      transaction_count: 1
    }
  ]);
  assert.equal(overview.summary.total_spent_unbudgeted, 12.99);
});
