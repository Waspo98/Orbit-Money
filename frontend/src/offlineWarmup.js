import { api } from './api.js';
import { formatLocalMonth } from './lib/localDate.js';

const WARMUP_DELAY_MS = 120;

let inFlight = null;
let lastWarmupKey = '';

function currentYear() {
  return new Date().getFullYear();
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function unique(items) {
  return Array.from(new Set(items));
}

function buildWarmupPaths({ mhaTrackerEnabled = false } = {}) {
  const month = formatLocalMonth();
  const year = currentYear();

  const paths = [
    '/api/accounts',
    '/api/accounts?includeArchived=0',
    '/api/categories',
    '/api/preferences',
    '/api/mha/settings',

    // Exact default paths used by the Transactions and Budgets pages.
    '/api/transactions?limit=50&page=1',
    '/api/transactions?page=2&limit=50',
    `/api/budgets?month=${encodeURIComponent(month)}`,
    '/api/budgets/months',

    // Other offline-readable pages that benefit from a first-pass snapshot.
    '/api/goals?months=24',
    '/api/upcoming',
    '/api/net-worth?months=24',
    '/api/household',
    '/api/rules?withCounts=1'
  ];

  if (mhaTrackerEnabled) {
    paths.push(`/api/mha?year=${encodeURIComponent(year)}`);
  }

  return unique(paths);
}

export function warmOfflineReadCache(options = {}) {
  const warmupKey = JSON.stringify({
    month: formatLocalMonth(),
    year: currentYear(),
    mhaTrackerEnabled: !!options.mhaTrackerEnabled
  });

  if (inFlight) return inFlight;
  if (lastWarmupKey === warmupKey) return Promise.resolve();
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return Promise.resolve();
  }

  lastWarmupKey = warmupKey;
  const paths = buildWarmupPaths(options);

  inFlight = (async () => {
    for (const path of paths) {
      if (typeof navigator !== 'undefined' && navigator.onLine === false) break;
      try {
        await api.get(path);
      } catch (err) {
        console.warn(`Offline read warmup failed for ${path}:`, err);
      }
      await delay(WARMUP_DELAY_MS);
    }
  })().finally(() => {
    inFlight = null;
  });

  return inFlight;
}

