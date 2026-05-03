import assert from 'node:assert/strict';
import test from 'node:test';

import {
  UNCATEGORIZED_BUDGET_CATEGORY,
  buildBudgetItem,
  buildBudgetSummary,
  serializeBudgetItem
} from '../src/services/budgetOverview.js';

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
