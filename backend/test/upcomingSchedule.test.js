import test from 'node:test';
import assert from 'node:assert/strict';
import { monthlyFactor } from '../src/lib/upcomingSchedule.js';

test('monthlyFactor counts rule-based recurrence dates across the next year', () => {
  assert.equal(
    monthlyFactor({
      recurrence_rule: { type: 'month_days', days: [1, 15] }
    }, '2026-05-01'),
    2
  );
});

test('monthlyFactor supports custom interval schedules', () => {
  assert.equal(
    monthlyFactor({
      frequency_type: 'custom',
      frequency_interval: 2,
      frequency_unit: 'weeks'
    }, '2026-05-01'),
    52 / 24
  );
});
