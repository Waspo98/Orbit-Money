import { roundMoney, toFiniteNumber } from '../lib/money.js';

export const MHA_SAVINGS_RATE = 0.27;

export function formatMhaTransaction(row) {
  const amount = toFiniteNumber(row?.amount);
  return {
    ...row,
    mha_eligible: row?.mha_eligible ? 1 : 0,
    amount_abs: Math.abs(amount)
  };
}

export function summarizeMhaTransactions(transactions, savingsRate = MHA_SAVINGS_RATE) {
  const transactionTotal = roundMoney(
    transactions.reduce((sum, transaction) => {
      if (transaction?.is_ignored || transaction?.is_transfer) return sum;
      return sum + Math.abs(toFiniteNumber(transaction?.amount));
    }, 0)
  );

  return {
    transactionCount: transactions.filter(
      (transaction) => !transaction?.is_ignored && !transaction?.is_transfer
    ).length,
    transactionTotal,
    savings: roundMoney(transactionTotal * savingsRate)
  };
}
