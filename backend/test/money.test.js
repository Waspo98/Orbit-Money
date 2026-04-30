import assert from 'node:assert/strict';
import test from 'node:test';

import {
  centsToDollars,
  dollarsToCents,
  moneyFieldsToDollars,
  roundMoney,
  sumMoney
} from '../src/lib/money.js';

test('dollarsToCents rounds positive and negative dollar values to integer cents', () => {
  assert.equal(dollarsToCents(12.345), 1235);
  assert.equal(dollarsToCents(12.344), 1234);
  assert.equal(dollarsToCents(-12.345), -1235);
  assert.equal(dollarsToCents('$12'), 0);
});

test('centsToDollars and moneyFieldsToDollars convert stored cents at API boundaries', () => {
  assert.equal(centsToDollars(12345), 123.45);
  assert.deepEqual(
    moneyFieldsToDollars(
      { amount: 12345, optional: null, untouched: 'value' },
      ['amount', 'optional']
    ),
    { amount: 123.45, optional: null, untouched: 'value' }
  );
});

test('roundMoney and sumMoney avoid floating point tails in derived totals', () => {
  assert.equal(roundMoney(0.1 + 0.2), 0.3);
  assert.equal(sumMoney([0.1, 0.2, '0.335']), 0.64);
});
