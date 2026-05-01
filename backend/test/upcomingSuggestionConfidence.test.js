import test from 'node:test';
import assert from 'node:assert/strict';
import {
  frequencyConfidenceScore,
  scoreUpcomingSuggestion
} from '../src/lib/upcomingSuggestionConfidence.js';

test('scoreUpcomingSuggestion prioritizes clean utility and software patterns', () => {
  const utilityScore = scoreUpcomingSuggestion({
    categoryName: 'Bills & Utilities',
    direction: 'expense',
    averageInterval: 30,
    transactionCount: 3,
    amounts: [-9800, -10000, -10200],
    latestDate: '2026-04-20',
    today: '2026-05-01'
  });

  const softwareScore = scoreUpcomingSuggestion({
    categoryName: 'Software & Tech',
    direction: 'expense',
    averageInterval: 14,
    transactionCount: 2,
    amounts: [-1200, -1200],
    latestDate: '2026-04-25',
    today: '2026-05-01'
  });

  assert.equal(utilityScore, 95);
  assert.equal(softwareScore, 89);
});

test('scoreUpcomingSuggestion deprioritizes repeated grocery and dining noise', () => {
  const groceryScore = scoreUpcomingSuggestion({
    categoryName: 'Groceries',
    direction: 'expense',
    averageInterval: 14,
    transactionCount: 4,
    amounts: [-6305, -9145, -4770, -12633],
    latestDate: '2026-04-25',
    today: '2026-05-01'
  });

  const diningScore = scoreUpcomingSuggestion({
    categoryName: 'Dining & Drinks',
    direction: 'expense',
    averageInterval: 14,
    transactionCount: 3,
    amounts: [-2652, -1811, -3840],
    latestDate: '2026-04-25',
    today: '2026-05-01'
  });

  assert.equal(groceryScore, 32);
  assert.equal(diningScore, 24);
});

test('frequencyConfidenceScore rewards tight weekly, biweekly, monthly, and yearly cadences', () => {
  assert.equal(frequencyConfidenceScore(7), 22);
  assert.equal(frequencyConfidenceScore(14), 24);
  assert.equal(frequencyConfidenceScore(30), 25);
  assert.equal(frequencyConfidenceScore(365), 18);
  assert.equal(frequencyConfidenceScore(20), -25);
});
