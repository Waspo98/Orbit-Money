import assert from 'node:assert/strict';
import test from 'node:test';

import { evalConditions } from '../src/services/ruleMatcher.js';

function transaction(overrides = {}) {
  return {
    account_id: 1,
    amount: -6999,
    original_merchant: 'Broadband',
    original_description: 'I3 Broadband monthly payment',
    original_category_id: null,
    ...overrides
  };
}

test('merchant contains rules also scan original descriptions', () => {
  assert.equal(
    evalConditions(transaction(), [
      { field: 'merchant', operator: 'contains', value: 'I3 Broadband' }
    ]),
    true
  );
});

test('merchant text rules still match direct merchant names', () => {
  assert.equal(
    evalConditions(transaction(), [
      { field: 'merchant', operator: 'equals', value: 'Broadband' }
    ]),
    true
  );
});
