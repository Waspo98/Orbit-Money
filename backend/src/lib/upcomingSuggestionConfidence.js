import { formatLocalDate } from './localDate.js';
import { daysBetween } from './upcomingSchedule.js';

const MAX_CONFIDENCE = 95;

const GIVING_CATEGORY_HINTS = [
  'charitable',
  'charity',
  'church',
  'donation',
  'donations',
  'giving',
  'nonprofit',
  'non profit',
  'tithe'
];

const BILL_CATEGORY_HINTS = [
  'bill',
  'bills',
  'electric',
  'gas',
  'insurance',
  'internet',
  'loan',
  'mortgage',
  'rent',
  'utility',
  'utilities',
  'water'
];

const SOFTWARE_CATEGORY_HINTS = [
  'app',
  'apps',
  'cloud',
  'hosting',
  'saas',
  'software',
  'subscription software',
  'tech',
  'technology'
];

const INCOME_CATEGORY_HINTS = [
  'income',
  'paycheck',
  'payroll',
  'salary',
  'wages'
];

const GROCERIES_CATEGORY_HINTS = ['grocery', 'groceries'];
const SHOPPING_CATEGORY_HINTS = ['shop', 'shopping'];
const DINING_CATEGORY_HINTS = ['dining', 'drink', 'drinks', 'restaurant', 'restaurants'];

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function normalizeCategory(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

function includesAny(text, hints) {
  return hints.some((hint) => text.includes(hint));
}

export function categoryConfidenceScore(categoryName, direction) {
  const text = normalizeCategory(categoryName);
  if (direction === 'income' || includesAny(text, INCOME_CATEGORY_HINTS)) return 25;
  if (includesAny(text, GIVING_CATEGORY_HINTS)) return 24;
  if (includesAny(text, BILL_CATEGORY_HINTS)) return 22;
  if (includesAny(text, SOFTWARE_CATEGORY_HINTS)) return 18;
  if (includesAny(text, DINING_CATEGORY_HINTS)) return -28;
  if (includesAny(text, GROCERIES_CATEGORY_HINTS)) return -25;
  if (includesAny(text, SHOPPING_CATEGORY_HINTS)) return -22;
  return 0;
}

export function frequencyConfidenceScore(averageInterval) {
  const interval = Number(averageInterval);
  if (!Number.isFinite(interval) || interval <= 0) return -25;

  if (interval >= 6 && interval <= 8) return 22;
  if (interval >= 13 && interval <= 15) return 24;
  if (interval >= 27 && interval <= 34) return 25;
  if (interval >= 358 && interval <= 372) return 18;

  if (
    (interval >= 5 && interval <= 9) ||
    (interval >= 12 && interval <= 18) ||
    (interval >= 24 && interval <= 38) ||
    (interval >= 330 && interval <= 400)
  ) {
    return 8;
  }

  return -25;
}

export function repeatConfidenceScore(transactionCount) {
  const count = Number(transactionCount) || 0;
  if (count >= 5) return 20;
  if (count >= 4) return 15;
  if (count >= 3) return 10;
  if (count >= 2) return 4;
  return 0;
}

export function amountConsistencyScore(amounts) {
  const values = (Array.isArray(amounts) ? amounts : [])
    .map((amount) => Math.abs(Number(amount) || 0))
    .filter((amount) => amount > 0);
  if (values.length < 2) return 0;

  const average = values.reduce((sum, amount) => sum + amount, 0) / values.length;
  if (average <= 0) return 0;

  const maxDeviation = values.reduce(
    (max, amount) => Math.max(max, Math.abs(amount - average) / average),
    0
  );
  if (maxDeviation <= 0.1) return 15;
  if (maxDeviation <= 0.25) return 8;
  return -10;
}

export function recencyConfidenceScore(latestDate, today = formatLocalDate()) {
  const ageDays = daysBetween(latestDate, today);
  if (ageDays === null) return 0;
  if (ageDays <= 45) return 8;
  if (ageDays <= 90) return 4;
  return -8;
}

export function scoreUpcomingSuggestion({
  categoryName,
  direction,
  averageInterval,
  transactionCount,
  amounts,
  latestDate,
  today
}) {
  return clamp(
    20 +
      categoryConfidenceScore(categoryName, direction) +
      frequencyConfidenceScore(averageInterval) +
      repeatConfidenceScore(transactionCount) +
      amountConsistencyScore(amounts) +
      recencyConfidenceScore(latestDate, today),
    0,
    MAX_CONFIDENCE
  );
}
