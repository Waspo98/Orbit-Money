import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';
import { getLocalMonthBounds } from '../lib/localDate.js';
import {
  UNCATEGORIZED_BUDGET_CATEGORY,
  buildBudgetItem,
  buildBudgetSummary,
  serializeBudgetItem
} from './budgetOverview.js';

const EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');

export function monthBounds(yyyymm) {
  return getLocalMonthBounds(yyyymm);
}

export function buildMonthlyBudgetOverview(database, { householdId, month }) {
  const { start, end } = monthBounds(month);

  // Per-category net spending for the month (-SUM handles refunds).
  const spending = database
    .prepare(
      `SELECT
         ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} AS cat_id,
         -SUM(t.amount) AS spent,
         COUNT(*) AS n
       FROM transactions t
       LEFT JOIN categories c
         ON c.id = ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL}
        AND c.household_id = ?
       WHERE t.household_id = ?
         AND t.date >= ? AND t.date <= ?
         AND COALESCE(t.edited_is_ignored,  t.is_ignored)  = 0
         AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
         AND (c.id IS NULL OR (c.is_income = 0 AND c.is_transfer = 0))
         AND (c.id IS NOT NULL OR t.amount < 0)
       GROUP BY cat_id`
    )
    .all(householdId, householdId, start, end);

  const spendByCat = new Map(spending.map((r) => [r.cat_id, r]));

  // Overall income and expenses for the month (also used by the stats row at
  // the top of the Budgets page).
  const flow = database
    .prepare(
      `SELECT
         SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS income,
         SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END) AS expenses
       FROM transactions
       WHERE household_id = ?
         AND date >= ? AND date <= ?
         AND COALESCE(edited_is_ignored,  is_ignored)  = 0
         AND COALESCE(edited_is_transfer, is_transfer) = 0`
    )
    .get(householdId, start, end);

  const incomeCents = flow?.income || 0;
  const expensesCents = flow?.expenses || 0;

  const categories = database
    .prepare(
      `SELECT id, name, color, icon, is_income, is_transfer, sort_order
        FROM categories
        WHERE household_id = ?
          AND is_transfer = 0 AND is_income = 0
        ORDER BY sort_order, name`
    )
    .all(householdId);

  const budgets = database
    .prepare(
      `SELECT b.id, b.category_id, b.amount, b.rollover
        FROM budgets b
        JOIN categories c ON c.id = b.category_id
        WHERE b.household_id = ?
          AND c.household_id = ?
          AND c.is_transfer = 0 AND c.is_income = 0`
    )
    .all(householdId, householdId);
  const budgetByCat = new Map(budgets.map((b) => [b.category_id, b]));

  const budgetedItems = [];
  const unbudgetedItems = [];
  const inactiveItems = [];

  for (const cat of categories) {
    const budget = budgetByCat.get(cat.id);
    const spendRow = spendByCat.get(cat.id);
    const item = buildBudgetItem(
      {
        id: cat.id,
        name: cat.name,
        color: cat.color,
        icon: cat.icon,
        is_income: !!cat.is_income,
        is_transfer: !!cat.is_transfer
      },
      budget,
      spendRow
    );

    if (budget) {
      budgetedItems.push(item);
    } else if (item.spent > 0 || item.transaction_count > 0) {
      unbudgetedItems.push(item);
    } else {
      inactiveItems.push(item);
    }
  }

  const uncategorizedSpend = spendByCat.get(null);
  if (uncategorizedSpend) {
    const item = buildBudgetItem(UNCATEGORIZED_BUDGET_CATEGORY, null, uncategorizedSpend);
    if (item.spent > 0 || item.transaction_count > 0) {
      unbudgetedItems.push(item);
    }
  }

  budgetedItems.sort((a, b) => {
    const ra = a.amount > 0 ? a.spent / a.amount : Infinity;
    const rb = b.amount > 0 ? b.spent / b.amount : Infinity;
    return rb - ra;
  });
  unbudgetedItems.sort((a, b) => b.spent - a.spent);
  inactiveItems.sort((a, b) =>
    a.category.name.localeCompare(b.category.name)
  );

  return {
    month,
    budgeted: budgetedItems.map(serializeBudgetItem),
    unbudgeted: unbudgetedItems.map(serializeBudgetItem),
    inactive: inactiveItems.map(serializeBudgetItem),
    summary: buildBudgetSummary({
      budgetedItems,
      unbudgetedItems,
      incomeCents,
      expensesCents
    })
  };
}
