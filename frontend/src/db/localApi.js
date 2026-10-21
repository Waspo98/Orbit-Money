// frontend/src/db/localApi.js
// Routes local API requests asynchronously to the SQLite Web Worker.

import { callWorker, flushDatabase, getLocalDatabase } from './localDb.js';

export { flushDatabase, getLocalDatabase };

function getPathname(url) {
  const queryIndex = url.indexOf('?');
  return queryIndex === -1 ? url : url.slice(0, queryIndex);
}

/**
 * Handles REST-like API requests in STORAGE_MODE_LOCAL by forwarding to sqlite.worker.js.
 * Provides fallback structures for household endpoints and aligns transaction pagination metadata.
 */
export async function handleLocalApiRequest(url, options = {}) {
  let requestUrl = url;

  // Alias /api/user/preferences -> /api/preferences
  if (requestUrl === '/api/user/preferences') {
    requestUrl = '/api/preferences';
  } else if (requestUrl.startsWith('/api/user/preferences/')) {
    requestUrl = requestUrl.replace('/api/user/preferences/', '/api/preferences/');
  } else if (requestUrl.startsWith('/api/user/preferences?')) {
    requestUrl = requestUrl.replace('/api/user/preferences?', '/api/preferences?');
  }

  let result;
  try {
    result = await callWorker('API_REQUEST', { url: requestUrl, options });
  } catch (err) {
    const pathname = getPathname(requestUrl);
    // Graceful fallbacks for household endpoints if an error occurs
    if (pathname === '/api/household') {
      return {
        summary: {
          member_count: 0,
          earners: 0,
          gross_income_annual: 0,
          net_pay_annual: 0,
          employee_retirement_annual: 0,
          employer_retirement_annual: 0,
          total_benefits_annual: 0,
          retirement_account_balance: 0,
          hsa_account_balance: 0,
          linked_retirement_account_count: 0
        },
        members: [],
        records: [],
        retirement_accounts: []
      };
    }
    if (pathname === '/api/household/retirement-history') {
      return { accounts: [], history: [] };
    }
    throw err;
  }

  const pathname = getPathname(requestUrl);

  // Align Transactions pagination contract so Transactions.jsx properly detects empty states
  // and avoids broken "Page 1 of " pagination text
  if (pathname === '/api/transactions' && result && typeof result === 'object') {
    const total = result.total !== undefined ? result.total : (result.totalCount !== undefined ? result.totalCount : (result.items?.length || 0));
    const totalPages = Math.max(1, Number(result.totalPages ?? result.pageCount) || 1);

    result.total = total;
    result.totalCount = total;
    result.totalPages = totalPages;
    result.pageCount = totalPages;
    if (result.grandTotal === undefined) {
      result.grandTotal = total;
    }
    if (!result.monthCounts) {
      result.monthCounts = [];
    }
  }

  // Graceful fallback if worker returned unhandled for household
  if (pathname === '/api/household' && (!result || typeof result !== 'object' || !result.summary)) {
    return {
      summary: {
        member_count: 0,
        earners: 0,
        gross_income_annual: 0,
        net_pay_annual: 0,
        employee_retirement_annual: 0,
        employer_retirement_annual: 0,
        total_benefits_annual: 0,
        retirement_account_balance: 0,
        hsa_account_balance: 0,
        linked_retirement_account_count: 0
      },
      members: [],
      records: [],
      retirement_accounts: []
    };
  }

  if (pathname === '/api/household/retirement-history' && (!result || typeof result !== 'object' || !Array.isArray(result.history))) {
    return { accounts: [], history: [] };
  }

  return result;
}
