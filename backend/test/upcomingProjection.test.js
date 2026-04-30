import assert from 'node:assert/strict';
import test from 'node:test';

import { summarizeHistorySamples } from '../src/lib/upcomingProjection.js';

test('summarizeHistorySamples averages by matching transaction, not by month', () => {
  const summary = summarizeHistorySamples([
    { total_cents: 570518, transaction_count: 2 },
    { total_cents: 285259, transaction_count: 1 }
  ]);

  assert.equal(summary.totalCents, 855777);
  assert.equal(summary.sampleCount, 3);
  assert.equal(summary.averageCents, 285259);
});

test('summarizeHistorySamples falls back when no transactions are available', () => {
  assert.deepEqual(
    summarizeHistorySamples([{ total_cents: 0, transaction_count: 0 }], 12345),
    { totalCents: 0, sampleCount: 0, averageCents: 12345 }
  );
});
