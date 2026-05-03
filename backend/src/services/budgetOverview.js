import { centsToDollars } from '../lib/money.js';

export const UNCATEGORIZED_BUDGET_CATEGORY = {
  id: null,
  name: 'Uncategorized',
  color: '#9E9E9E',
  icon: '?',
  is_income: false,
  is_transfer: false
};

export function buildBudgetItem(category, budget, spendRow) {
  const amountCents = budget ? budget.amount : 0;
  const spentCents = spendRow ? spendRow.spent : 0;
  return {
    category,
    budget_id: budget?.id ?? null,
    amount: budget ? centsToDollars(amountCents) : null,
    rollover: budget ? (budget.rollover ? 1 : 0) : null,
    spent: centsToDollars(spentCents),
    transaction_count: spendRow ? spendRow.n : 0,
    amountCents,
    spentCents
  };
}

export function serializeBudgetItem(item) {
  const { amountCents, spentCents, ...publicItem } = item;
  return publicItem;
}

export function sumItemCents(items, field) {
  return items.reduce((sum, item) => sum + (Number(item[field]) || 0), 0);
}

export function buildBudgetSummary({
  budgetedItems,
  unbudgetedItems,
  incomeCents = 0,
  expensesCents = 0
}) {
  const totalBudgetedCents = sumItemCents(budgetedItems, 'amountCents');
  const totalSpentInBudgetCents = sumItemCents(budgetedItems, 'spentCents');
  const totalSpentUnbudgetedCents = sumItemCents(unbudgetedItems, 'spentCents');

  return {
    total_budgeted: centsToDollars(totalBudgetedCents),
    total_spent_in_budgets: centsToDollars(totalSpentInBudgetCents),
    total_spent_unbudgeted: centsToDollars(totalSpentUnbudgetedCents),
    total_spent: centsToDollars(totalSpentInBudgetCents + totalSpentUnbudgetedCents),
    total_income: centsToDollars(incomeCents),
    total_expenses: centsToDollars(expensesCents),
    total_net: centsToDollars(incomeCents - expensesCents)
  };
}
