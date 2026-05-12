import { centsToDollars } from '../lib/money.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';
import {
  addMonthsToLocalMonth,
  formatLocalMonth,
  getLocalMonthBounds,
  isValidMonthOnly
} from '../lib/localDate.js';

export const SPENDING_TREND_MONTH_OPTIONS = [3, 6, 12];
export const DEFAULT_SPENDING_TREND_MONTHS = 6;

export const UNCATEGORIZED_SPENDING_TREND_CATEGORY = {
  id: null,
  name: 'Uncategorized',
  color: '#9E9E9E',
  icon: '?',
  is_income: false,
  is_transfer: false
};

const DISPLAY_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');

export function buildSpendingTrendRowsSql() {
  return `SELECT
           ${DISPLAY_CATEGORY_ID_SQL} AS category_id,
           strftime('%Y-%m', t.date) AS month,
           -SUM(t.amount) AS spent,
           COUNT(*) AS transaction_count
         FROM transactions t
         LEFT JOIN categories c
           ON c.id = ${DISPLAY_CATEGORY_ID_SQL}
          AND c.household_id = ?
         WHERE t.household_id = ?
           AND t.date >= ? AND t.date <= ?
           AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
           AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
           AND (c.id IS NULL OR (c.is_income = 0 AND c.is_transfer = 0))
           AND (c.id IS NOT NULL OR t.amount < 0)
         GROUP BY ${DISPLAY_CATEGORY_ID_SQL}, month
         ORDER BY month`;
}

export function parseMonth(value) {
  return typeof value === 'string' && isValidMonthOnly(value) ? value : null;
}

export function currentMonth(date = new Date()) {
  return formatLocalMonth(date);
}

export function normalizeTrendMonths(value) {
  const parsed = Number(value);
  return SPENDING_TREND_MONTH_OPTIONS.includes(parsed)
    ? parsed
    : DEFAULT_SPENDING_TREND_MONTHS;
}

export function addMonths(month, delta) {
  const parsed = parseMonth(month);
  if (!parsed) return currentMonth();
  return addMonthsToLocalMonth(parsed, delta);
}

export function monthBounds(month) {
  const parsed = parseMonth(month) || currentMonth();
  return getLocalMonthBounds(parsed);
}

export function buildMonthKeys({ endMonth = currentMonth(), months = DEFAULT_SPENDING_TREND_MONTHS } = {}) {
  const normalizedMonths = normalizeTrendMonths(months);
  const normalizedEndMonth = parseMonth(endMonth) || currentMonth();

  return Array.from({ length: normalizedMonths }, (_, index) =>
    addMonths(normalizedEndMonth, index - normalizedMonths + 1)
  );
}

function dollars(value) {
  return centsToDollars(value || 0);
}

function roundDollars(value) {
  return centsToDollars(Math.round(Number(value || 0)));
}

function median(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  if (sorted.length % 2) return sorted[middle];
  return Math.round((sorted[middle - 1] + sorted[middle]) / 2);
}

export function buildFlowSeries(months, flowRows = []) {
  const byMonth = new Map(flowRows.map((row) => [row.month, row]));

  return months.map((month) => {
    const row = byMonth.get(month) || {};
    const incomeCents = Number(row.income) || 0;
    const expensesCents = Number(row.expenses) || 0;

    return {
      month,
      income: dollars(incomeCents),
      expenses: dollars(expensesCents),
      net: dollars(incomeCents - expensesCents)
    };
  });
}

export function buildCategoryTrend({
  category,
  budget = null,
  months,
  spendingRows = []
}) {
  const byMonth = new Map(spendingRows.map((row) => [row.month, row]));
  const budgetAmountCents = budget ? Number(budget.amount) || 0 : null;
  const series = months.map((month) => {
    const row = byMonth.get(month) || {};
    const spentCents = Number(row.spent) || 0;
    const transactionCount = Number(row.transaction_count) || 0;

    return {
      month,
      spent: dollars(spentCents),
      transaction_count: transactionCount,
      over_budget: budgetAmountCents !== null && budgetAmountCents > 0
        ? spentCents > budgetAmountCents
        : false,
      spentCents
    };
  });
  const totalSpentCents = series.reduce((sum, item) => sum + item.spentCents, 0);
  const totalTransactions = series.reduce((sum, item) => sum + item.transaction_count, 0);
  const highest = series.reduce(
    (best, item) => (item.spentCents > best.spentCents ? item : best),
    series[0] || { month: null, spentCents: 0, spent: 0 }
  );
  const overBudgetCount = series.filter((item) => item.over_budget).length;
  const medianSpentCents = median(series.map((item) => item.spentCents));

  return {
    category,
    budget_id: budget?.id ?? null,
    budget_amount: budgetAmountCents === null ? null : dollars(budgetAmountCents),
    months: series.map(({ spentCents, ...item }) => item),
    total_spent: dollars(totalSpentCents),
    average_spent: roundDollars(totalSpentCents / Math.max(1, months.length)),
    median_spent: dollars(medianSpentCents),
    highest_month: {
      month: highest.month,
      spent: dollars(highest.spentCents)
    },
    over_budget_count: overBudgetCount,
    transaction_count: totalTransactions
  };
}

export function buildSpendingTrendSummary({ flow = [], categories = [] } = {}) {
  const monthCount = Math.max(1, flow.length);
  const totalIncomeCents = flow.reduce(
    (sum, row) => sum + Math.round(Number(row.income || 0) * 100),
    0
  );
  const totalExpensesCents = flow.reduce(
    (sum, row) => sum + Math.round(Number(row.expenses || 0) * 100),
    0
  );
  const topCategory = [...categories]
    .filter((item) => Number(item.total_spent || 0) > 0)
    .sort((a, b) => Number(b.total_spent || 0) - Number(a.total_spent || 0))[0] || null;

  return {
    total_income: dollars(totalIncomeCents),
    total_expenses: dollars(totalExpensesCents),
    total_net: dollars(totalIncomeCents - totalExpensesCents),
    average_income: roundDollars(totalIncomeCents / monthCount),
    average_expenses: roundDollars(totalExpensesCents / monthCount),
    average_net: roundDollars((totalIncomeCents - totalExpensesCents) / monthCount),
    top_category: topCategory
      ? {
          category: topCategory.category,
          total_spent: topCategory.total_spent,
          average_spent: topCategory.average_spent
        }
      : null
  };
}
