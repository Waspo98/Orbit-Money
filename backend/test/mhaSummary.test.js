import test from 'node:test';
import assert from 'node:assert/strict';
import { formatMhaTransaction, summarizeMhaTransactions } from '../src/services/mhaSummary.js';

test('formatMhaTransaction exposes absolute amount and integer eligibility flag', () => {
  assert.deepEqual(
    formatMhaTransaction({ id: 1, amount: -12.34, mha_eligible: true }),
    { id: 1, amount: -12.34, mha_eligible: 1, amount_abs: 12.34 }
  );
});

test('summarizeMhaTransactions excludes ignored and transfer rows', () => {
  const summary = summarizeMhaTransactions([
    { amount: -100, is_ignored: 0, is_transfer: 0 },
    { amount: 25, is_ignored: 0, is_transfer: 0 },
    { amount: -999, is_ignored: 1, is_transfer: 0 },
    { amount: -500, is_ignored: 0, is_transfer: 1 }
  ]);

  assert.deepEqual(summary, {
    transactionCount: 2,
    transactionTotal: 125,
    savings: 33.75
  });
});
