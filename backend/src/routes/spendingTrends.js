import express from 'express';
import { requireHouseholdId } from '../auth.js';
import { sendOk, sendServerError } from '../lib/http.js';
import { db } from '../db/index.js';
import {
  UNCATEGORIZED_SPENDING_TREND_CATEGORY,
  buildCategoryTrend,
  buildFlowSeries,
  buildMonthKeys,
  buildSpendingTrendRowsSql,
  buildSpendingTrendSummary,
  currentMonth,
  monthBounds,
  normalizeTrendMonths,
  parseMonth
} from '../services/spendingTrends.js';

const router = express.Router();

function serializeCategory(row) {
  return {
    id: row.id,
    name: row.name,
    color: row.color,
    icon: row.icon,
    is_income: !!row.is_income,
    is_transfer: !!row.is_transfer
  };
}

// GET /api/spending-trends?months=3|6|12&endMonth=YYYY-MM
router.get('/', (req, res) => {
  const householdId = requireHouseholdId(req);
  const monthsCount = normalizeTrendMonths(req.query.months);
  const endMonth = parseMonth(req.query.endMonth) || currentMonth();
  const months = buildMonthKeys({ endMonth, months: monthsCount });
  const { start } = monthBounds(months[0]);
  const { end } = monthBounds(months[months.length - 1]);

  try {
    const flowRows = db
      .prepare(
        `SELECT
           strftime('%Y-%m', date) AS month,
           SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS income,
           SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END) AS expenses
         FROM transactions
         WHERE household_id = ?
           AND date >= ? AND date <= ?
           AND COALESCE(edited_is_ignored, is_ignored) = 0
           AND COALESCE(edited_is_transfer, is_transfer) = 0
         GROUP BY month
         ORDER BY month`
      )
      .all(householdId, start, end);

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_income, is_transfer, sort_order
           FROM categories
          WHERE household_id = ?
            AND is_transfer = 0
            AND is_income = 0
          ORDER BY sort_order, name`
      )
      .all(householdId)
      .map(serializeCategory);

    const budgets = db
      .prepare(
        `SELECT b.id, b.category_id, b.amount, b.rollover
           FROM budgets b
           JOIN categories c ON c.id = b.category_id
          WHERE b.household_id = ?
            AND c.household_id = ?
            AND c.is_transfer = 0
            AND c.is_income = 0`
      )
      .all(householdId, householdId);
    const budgetByCategory = new Map(budgets.map((budget) => [budget.category_id, budget]));

    const spendingRows = db
      .prepare(buildSpendingTrendRowsSql())
      .all(householdId, householdId, start, end);

    const spendingByCategory = new Map();
    for (const row of spendingRows) {
      const key = row.category_id == null ? null : Number(row.category_id);
      const group = spendingByCategory.get(key) || [];
      group.push(row);
      spendingByCategory.set(key, group);
    }

    const categoryTrends = categories
      .map((category) =>
        buildCategoryTrend({
          category,
          budget: budgetByCategory.get(category.id) || null,
          months,
          spendingRows: spendingByCategory.get(category.id) || []
        })
      )
      .filter((item) => item.budget_id !== null || item.transaction_count > 0);

    if (spendingByCategory.has(null)) {
      categoryTrends.push(
        buildCategoryTrend({
          category: UNCATEGORIZED_SPENDING_TREND_CATEGORY,
          budget: null,
          months,
          spendingRows: spendingByCategory.get(null)
        })
      );
    }

    categoryTrends.sort((a, b) => {
      const bySpend = Number(b.total_spent || 0) - Number(a.total_spent || 0);
      if (bySpend !== 0) return bySpend;
      return a.category.name.localeCompare(b.category.name);
    });

    const flow = buildFlowSeries(months, flowRows);

    sendOk(res, {
      range: {
        months: monthsCount,
        start,
        end,
        end_month: endMonth
      },
      months,
      flow,
      categories: categoryTrends,
      summary: buildSpendingTrendSummary({ flow, categories: categoryTrends })
    });
  } catch (err) {
    console.error('List spending trends failed:', err);
    sendServerError(res, err);
  }
});

export default router;
