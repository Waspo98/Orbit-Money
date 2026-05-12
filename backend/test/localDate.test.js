import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addMonthsToLocalMonth,
  getLocalMonthBounds,
  isValidDateOnly,
  isValidMonthOnly,
  isValidOptionalDateOnly
} from '../src/lib/localDate.js';

test('isValidDateOnly rejects impossible calendar dates', () => {
  assert.equal(isValidDateOnly('2026-02-28'), true);
  assert.equal(isValidDateOnly('2024-02-29'), true);
  assert.equal(isValidDateOnly('2026-02-29'), false);
  assert.equal(isValidDateOnly('2026-13-01'), false);
  assert.equal(isValidDateOnly('2026-00-10'), false);
  assert.equal(isValidDateOnly('not-a-date'), false);
});

test('isValidOptionalDateOnly allows blank optional dates', () => {
  assert.equal(isValidOptionalDateOnly(null), true);
  assert.equal(isValidOptionalDateOnly(''), true);
  assert.equal(isValidOptionalDateOnly('2026-04-24'), true);
  assert.equal(isValidOptionalDateOnly('2026-04-31'), false);
});

test('month helpers validate and compute local month ranges', () => {
  assert.equal(isValidMonthOnly('2026-05'), true);
  assert.equal(isValidMonthOnly('2026-13'), false);
  assert.equal(isValidMonthOnly('2026-05-01'), false);
  assert.equal(addMonthsToLocalMonth('2026-12', 1), '2027-01');
  assert.deepEqual(getLocalMonthBounds('2026-02'), {
    start: '2026-02-01',
    end: '2026-02-28'
  });
});
