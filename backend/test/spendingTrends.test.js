import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildCategoryTrend,
  buildFlowSeries,
  buildMonthKeys,
  buildSpendingTrendSummary,
  normalizeTrendMonths
} from '../src/services/spendingTrends.js';

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
