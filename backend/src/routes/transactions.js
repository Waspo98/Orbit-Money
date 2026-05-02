// =============================================================================
// transactions.js — CRUD + filtering for transactions (v13)
// =============================================================================
// v13 adds full filter/search/sort support via query params. All comparisons
// against merchant/category/is_transfer/is_ignored use the COALESCE'd
// display value (edited over original), which matches what users see on
// screen. Raw originals are also matchable via the search text field (which
// scans merchant, original_merchant, original_description, and notes).
//
// Filter query params (all optional):
//   account_id       — single account (legacy, from Accounts page link)
//   accounts         — CSV of account ids (multi-select, overrides account_id)
//   categories       — CSV of category ids; use 'uncategorized' literal to
//                      match rows with category_id IS NULL
//   q                — free-text search (merchant OR original_merchant OR
//                      original_description OR notes)
//   date_from        — YYYY-MM-DD inclusive
//   date_to          — YYYY-MM-DD inclusive
//   amount_min       — absolute value
//   amount_max       — absolute value
//   type             — all | income | expense | transfer
//   include_ignored  — 0 to hide (default 1)
//   include_transfers — 0 to hide (default 1)
//   has_edits        — yes | no | any (default any)
//   sort             — date_desc | date_asc | amount_desc | amount_asc |
//                      abs_amount_desc | abs_amount_asc | merchant_asc
//   page, limit      — pagination
// =============================================================================

import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { reapplyRulesToTransaction } from '../services/ruleMatcher.js';
import { attachMerchantLogos } from '../services/merchantLogos.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { centsToDollars, dollarsToCents } from '../lib/money.js';
import {
  parseBoundedInteger,
  parseId,
  parseInteger,
  readIdParam
} from '../lib/routeParams.js';

const router = express.Router();
const CREDIT_PAYMENT_HINTS = [
  'credit card payment',
  'card payment',
  'cc payment',
  'payment thank you',
  'autopay payment',
  'card ending'
];

// ---------------------------------------------------------------------------
// SELECT projection used by every read endpoint. Returns both the
// COALESCE'd display values (what the UI renders) and the raw originals +
// edit metadata (so the UI can show "edited" annotations and reset links).
// ---------------------------------------------------------------------------
const SELECT_COLS = `
  id,
  account_id,
  date,
  amount,
  COALESCE(edited_merchant,    original_merchant)    AS merchant,
  COALESCE(edited_category_id, category_id)          AS category_id,
  COALESCE(edited_is_transfer, is_transfer)          AS is_transfer,
  COALESCE(edited_is_ignored,  is_ignored)           AS is_ignored,
  original_merchant,
  original_description,
  category_id    AS original_category_id,
  is_transfer    AS original_is_transfer,
  is_ignored     AS original_is_ignored,
  edited_merchant,
  edited_merchant_source,
  edited_category_id,
  edited_category_id_source,
  edited_is_transfer,
  edited_is_transfer_source,
  edited_is_ignored,
  edited_is_ignored_source,
  COALESCE(
    edited_mha_eligible,
    CASE
      WHEN (SELECT mha_default_ignored
              FROM categories c
             WHERE c.id = COALESCE(transactions.edited_category_id, transactions.category_id)) = 1
      THEN 0
      WHEN (SELECT mha_default_eligible FROM accounts a WHERE a.id = transactions.account_id) = 1
        OR (SELECT mha_default_eligible
              FROM categories c
             WHERE c.id = COALESCE(transactions.edited_category_id, transactions.category_id)) = 1
      THEN 1
      ELSE 0
    END
  ) AS mha_eligible,
  edited_mha_eligible,
  edited_mha_eligible_source,
  notes,
  transfer_pair_id
`;

function hydrate(row) {
  if (!row) return row;
  row.amount = centsToDollars(row.amount);
  row.has_edits =
    row.edited_merchant_source !== null ||
    row.edited_category_id_source !== null ||
    row.edited_is_transfer_source !== null ||
    row.edited_is_ignored_source !== null ||
    row.edited_mha_eligible_source !== null;
  return row;
}

// ---------------------------------------------------------------------------
// Filter helpers
// ---------------------------------------------------------------------------

function parseIntList(csv) {
  if (!csv) return [];
  return csv
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
    .map(parseInteger)
    .filter((value) => value !== null);
}

function parseCategoryList(csv) {
  // Categories can include the literal 'uncategorized' to match NULL rows.
  if (!csv) return { ids: [], includeUncategorized: false };
  const tokens = csv.split(',').map((x) => x.trim()).filter(Boolean);
  const ids = tokens
    .filter((t) => t !== 'uncategorized')
    .map(parseInteger)
    .filter((value) => value !== null);
  const includeUncategorized = tokens.includes('uncategorized');
  return { ids, includeUncategorized };
}

function parseNumber(s) {
  if (s === undefined || s === null || s === '') return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function isoDate(s) {
  // Accept YYYY-MM-DD only. Invalid → null.
  if (!s || typeof s !== 'string') return null;
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : null;
}

function isValidDateOnly(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function currentMonthBounds() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const start = `${year}-${String(month).padStart(2, '0')}-01`;
  const lastDay = new Date(year, month, 0).getDate();
  const end = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
  return { start, end };
}

const MERCHANT_NOISE_HINTS = [
  'checkcard',
  'debit card',
  'online transfer',
  'pos debit',
  'purchase authorized',
  'recurring debit',
  'web authorized'
];

function normalizeMerchant(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[#*]\d+/g, ' ')
    .replace(/\b\d{2,}\b/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\b(llc|inc|co|corp|store|pos|debit|card|purchase)\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function textForReview(row) {
  return `${row.merchant || ''} ${row.original_merchant || ''} ${row.original_description || ''}`.toLowerCase();
}

function looksCreditCardPayment(row) {
  const text = textForReview(row);
  if (CREDIT_PAYMENT_HINTS.some((hint) => text.includes(hint))) return true;
  return /payment\s+to\s+.+\bcard\s+ending\b/.test(text) ||
    /\bcard\s+ending\s+(in\s+)?[#*\d]{2,}/.test(text);
}

function looksNoisyMerchant(row) {
  const merchant = String(row.merchant || '').trim();
  if (!merchant) return true;
  if (looksCreditCardPayment(row)) return false;
  const text = textForReview(row);
  if (MERCHANT_NOISE_HINTS.some((hint) => text.includes(hint))) return true;
  if (merchant.length > 34 && /\d{3,}/.test(merchant)) return true;
  if (/[#*]\d{3,}/.test(merchant)) return true;
  if ((merchant.match(/\d/g) || []).length >= 6) return true;
  return false;
}

function buildMerchantPatterns(rows) {
  const patterns = new Map();
  for (const row of rows) {
    if (!row.category_id || row.is_transfer || row.is_ignored) continue;
    const key = normalizeMerchant(row.merchant);
    if (!key || key.length < 3) continue;
    if (!patterns.has(key)) patterns.set(key, { total: 0, categories: new Map() });
    const pattern = patterns.get(key);
    pattern.total += 1;
    pattern.categories.set(row.category_id, (pattern.categories.get(row.category_id) || 0) + 1);
  }

  for (const pattern of patterns.values()) {
    let dominantCategoryId = null;
    let dominantCount = 0;
    for (const [categoryId, count] of pattern.categories.entries()) {
      if (count > dominantCount) {
        dominantCategoryId = categoryId;
        dominantCount = count;
      }
    }
    pattern.dominantCategoryId = dominantCategoryId;
    pattern.dominantCount = dominantCount;
  }
  return patterns;
}

function changedCategoryPattern(row, patterns) {
  if (!row.category_id) return null;
  const pattern = patterns.get(normalizeMerchant(row.merchant));
  if (!pattern || pattern.total < 3 || pattern.dominantCount < 2) return null;
  if (pattern.dominantCategoryId === row.category_id) return null;
  const currentCount = pattern.categories.get(row.category_id) || 0;
  if (pattern.dominantCount - currentCount < 2) return null;
  return pattern.dominantCategoryId;
}

const SORT_MAP = {
  date_desc:        'date DESC, id DESC',
  date_asc:         'date ASC, id ASC',
  amount_desc:      'amount DESC, id DESC',
  amount_asc:       'amount ASC, id DESC',
  abs_amount_desc:  'ABS(amount) DESC, id DESC',
  abs_amount_asc:   'ABS(amount) ASC, id DESC',
  merchant_asc:     "COALESCE(edited_merchant, original_merchant) COLLATE NOCASE ASC, id DESC"
};

/**
 * Build the SQL WHERE clause + arg array from request query params.
 * Returns { clause, args } where clause is either '' or 'WHERE ...'.
 */
function buildFilterWhere(q, householdId) {
  const wheres = ['household_id = ?'];
  const args = [householdId];

  // ----- Account filter -----
  // `accounts` (multi) takes precedence over `account_id` (legacy single).
  const multiAccounts = parseIntList(q.accounts);
  if (multiAccounts.length > 0) {
    wheres.push(`account_id IN (${multiAccounts.map(() => '?').join(',')})`);
    args.push(...multiAccounts);
  } else if (q.account_id) {
    const single = parseId(q.account_id);
    if (single !== null) {
      wheres.push('account_id = ?');
      args.push(single);
    }
  }

  // ----- Category filter -----
  const { ids: catIds, includeUncategorized } = parseCategoryList(q.categories);
  if (catIds.length > 0 || includeUncategorized) {
    const parts = [];
    if (catIds.length > 0) {
      parts.push(
        `COALESCE(edited_category_id, category_id) IN (${catIds
          .map(() => '?')
          .join(',')})`
      );
      args.push(...catIds);
    }
    if (includeUncategorized) {
      parts.push('COALESCE(edited_category_id, category_id) IS NULL');
    }
    wheres.push(`(${parts.join(' OR ')})`);
  }

  // ----- Text search -----
  // Scans display merchant, original merchant, original description, notes.
  // LIKE with % wildcards — case-insensitive via ASCII-range LOWER().
  // (NOCASE collation is limited to ASCII in SQLite, which is fine for
  // typical bank-data merchant names.)
  if (q.q && typeof q.q === 'string' && q.q.trim()) {
    const needle = `%${q.q.trim().toLowerCase()}%`;
    wheres.push(`(
      LOWER(COALESCE(edited_merchant, original_merchant)) LIKE ? OR
      LOWER(COALESCE(original_merchant, '')) LIKE ? OR
      LOWER(COALESCE(original_description, '')) LIKE ? OR
      LOWER(COALESCE(notes, '')) LIKE ?
    )`);
    args.push(needle, needle, needle, needle);
  }

  // ----- Date range -----
  const dateFrom = isoDate(q.date_from);
  if (dateFrom) {
    wheres.push('date >= ?');
    args.push(dateFrom);
  }
  const dateTo = isoDate(q.date_to);
  if (dateTo) {
    wheres.push('date <= ?');
    args.push(dateTo);
  }

  // ----- Amount range (always applied to absolute value) -----
  const amountMin = parseNumber(q.amount_min);
  if (amountMin !== null && amountMin >= 0) {
    wheres.push('ABS(amount) >= ?');
    args.push(dollarsToCents(amountMin));
  }
  const amountMax = parseNumber(q.amount_max);
  if (amountMax !== null && amountMax >= 0) {
    wheres.push('ABS(amount) <= ?');
    args.push(dollarsToCents(amountMax));
  }

  // ----- Transaction type -----
  const type = String(q.type || 'all').toLowerCase();
  const isTransferExpr = 'COALESCE(edited_is_transfer, is_transfer) = 1';

  if (type === 'income') {
    wheres.push(`amount > 0 AND NOT (${isTransferExpr})`);
  } else if (type === 'expense') {
    wheres.push(`amount < 0 AND NOT (${isTransferExpr})`);
  } else if (type === 'transfer') {
    wheres.push(isTransferExpr);
  }

  // ----- Include flags -----
  // Default: show everything (matches pre-filter behavior).
  // Only apply exclusion when the user explicitly opts out via '0'.
  if (q.include_ignored === '0') {
    wheres.push('COALESCE(edited_is_ignored, is_ignored) = 0');
  }
  if (q.include_transfers === '0' && type !== 'transfer') {
    wheres.push(`NOT (${isTransferExpr})`);
  }
  if (q.exclude_credit_card_payments === '1') {
    const textExpr = `LOWER(
      COALESCE(edited_merchant, original_merchant, '') || ' ' ||
      COALESCE(original_description, '')
    )`;
    const categoryExpr = `LOWER(COALESCE((
      SELECT c.name
        FROM categories c
       WHERE c.id = COALESCE(transactions.edited_category_id, transactions.category_id)
    ), ''))`;
    const parts = [];
    for (const hint of CREDIT_PAYMENT_HINTS) {
      parts.push(`${textExpr} LIKE ?`);
      args.push(`%${hint}%`);
    }
    parts.push(`(${categoryExpr} LIKE ? AND ${categoryExpr} LIKE ?)`);
    args.push('%credit%', '%payment%');
    wheres.push(`NOT (${parts.join(' OR ')})`);
  }

  // ----- Has edits -----
  const hasEdits = String(q.has_edits || 'any').toLowerCase();
  if (hasEdits === 'yes') {
    wheres.push(`(
      edited_merchant_source IS NOT NULL OR
      edited_category_id_source IS NOT NULL OR
      edited_is_transfer_source IS NOT NULL OR
      edited_is_ignored_source IS NOT NULL OR
      edited_mha_eligible_source IS NOT NULL
    )`);
  } else if (hasEdits === 'no') {
    wheres.push(`(
      edited_merchant_source IS NULL AND
      edited_category_id_source IS NULL AND
      edited_is_transfer_source IS NULL AND
      edited_is_ignored_source IS NULL AND
      edited_mha_eligible_source IS NULL
    )`);
  }

  const clause = wheres.length > 0 ? 'WHERE ' + wheres.join(' AND ') : '';
  return { clause, args };
}

// ---------------------------------------------------------------------------
// GET /api/transactions
// ---------------------------------------------------------------------------
router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const page = parseBoundedInteger(req.query.page, { fallback: 1, min: 1 });
  const limit = parseBoundedInteger(req.query.limit, { fallback: 50, min: 1, max: 200 });
  const offset = (page - 1) * limit;

  const sortKey = String(req.query.sort || 'date_desc').toLowerCase();
  const orderBy = SORT_MAP[sortKey] || SORT_MAP.date_desc;

  try {
    const { clause, args } = buildFilterWhere(req.query, householdId);

    const total = db
      .prepare(`SELECT COUNT(*) AS c FROM transactions ${clause}`)
      .get(...args).c;

    // Unfiltered total — useful for the UI to say "12 of 8,428".
    const grandTotal = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE household_id = ?')
      .get(householdId).c;

    const { start: monthStart, end: monthEnd } = currentMonthBounds();
    const monthlyTotal = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE household_id = ? AND date >= ? AND date <= ?')
      .get(householdId, monthStart, monthEnd).c;
    const monthlyFlow = db
      .prepare(
        `SELECT
            COALESCE(SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END), 0) AS expenses
           FROM transactions
          WHERE household_id = ?
            AND date >= ?
            AND date <= ?
            AND COALESCE(edited_is_ignored, is_ignored) = 0
            AND COALESCE(edited_is_transfer, is_transfer) = 0`
      )
      .get(householdId, monthStart, monthEnd);

    const monthCounts = db
      .prepare(
        `SELECT substr(date, 1, 7) AS month, COUNT(*) AS count
           FROM transactions
           ${clause}
           GROUP BY substr(date, 1, 7)`
      )
      .all(...args);

    const rows = db
      .prepare(
        `SELECT ${SELECT_COLS}
           FROM transactions
           ${clause}
           ORDER BY ${orderBy}
           LIMIT ? OFFSET ?`
      )
      .all(...args, limit, offset)
      .map(hydrate);
    const items = attachMerchantLogos(db, rows);

    sendOk(res, {
      items,
      page,
      limit,
      total,
      grandTotal,
      monthlyTotal,
      monthlyIncome: centsToDollars(monthlyFlow.income),
      monthlyExpenses: centsToDollars(monthlyFlow.expenses),
      monthlyNet: centsToDollars(monthlyFlow.income - monthlyFlow.expenses),
      monthCounts,
      totalPages: Math.max(1, Math.ceil(total / limit)),
      sort: sortKey in SORT_MAP ? sortKey : 'date_desc'
    });
  } catch (err) {
    console.error('List transactions failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// GET /api/transactions/review-queue
// Recent transactions that deserve a quick category confirmation.
// ---------------------------------------------------------------------------
router.get('/review-queue', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const limit = parseBoundedInteger(req.query.limit, { fallback: 10, min: 1, max: 25 });
  const excludedIds = new Set(parseIntList(req.query.exclude));

  try {
    const categoryRows = db
      .prepare('SELECT id, name, color, icon FROM categories WHERE household_id = ?')
      .all(householdId);
    const categoriesById = new Map(categoryRows.map((category) => [category.id, category]));

    const accountRows = db
      .prepare('SELECT id, name FROM accounts WHERE household_id = ?')
      .all(householdId);
    const accountsById = new Map(accountRows.map((account) => [account.id, account]));

    const rows = db
      .prepare(
        `SELECT ${SELECT_COLS}
           FROM transactions
          WHERE household_id = ?
            AND COALESCE(edited_is_ignored, is_ignored) = 0
          ORDER BY date DESC, id DESC
          LIMIT 1000`
      )
      .all(householdId)
      .map(hydrate);

    const patterns = buildMerchantPatterns(rows);
    const candidates = [];

    for (const row of rows) {
      const category = categoriesById.get(row.category_id);
      const categoryName = category?.name || null;
      const reasons = [];
      const suggestedCategoryId = changedCategoryPattern(row, patterns);

      if (!row.category_id || String(categoryName || '').toLowerCase() === 'uncategorized') {
        reasons.push('uncategorized');
      }
      if (looksNoisyMerchant(row)) {
        reasons.push('merchant_review');
      }
      if (suggestedCategoryId) {
        reasons.push('changed_pattern');
      }

      if (reasons.length === 0) continue;

      candidates.push({
        ...row,
        category_name: categoryName,
        category_color: category?.color || null,
        category_icon: category?.icon || null,
        account_name: accountsById.get(row.account_id)?.name || null,
        review_reasons: reasons,
        suggested_category_id: suggestedCategoryId
      });
    }

    const remaining = candidates.filter((row) => !excludedIds.has(row.id));
    sendOk(res, {
      items: attachMerchantLogos(db, remaining.slice(0, limit)),
      total: remaining.length,
      limit
    });
  } catch (err) {
    console.error('Review queue failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// POST /api/transactions
// Creates a manual transaction. Date and amount are original values because
// the user is the source of truth for manual entries.
// ---------------------------------------------------------------------------
router.post('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const body = req.body || {};
  const accountId = parseId(body.account_id);
  const merchant = typeof body.merchant === 'string' ? body.merchant.trim() : '';
  const date = String(body.date || '').trim();
  const amountValue = Number(body.amount);

  if (accountId === null) {
    return sendBadRequest(res, 'account_id is required.');
  }
  if (!isValidDateOnly(date)) {
    return sendBadRequest(res, 'date must be a valid YYYY-MM-DD date.');
  }
  if (!Number.isFinite(amountValue) || amountValue === 0) {
    return sendBadRequest(res, 'amount must be a non-zero number.');
  }
  if (!merchant) {
    return sendBadRequest(res, 'merchant is required.');
  }

  const account = db
    .prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ? AND is_archived = 0')
    .get(accountId, householdId);
  if (!account) {
    return sendBadRequest(res, 'account_id does not exist in this household.');
  }

  const categoryId = body.category_id === null || body.category_id === '' || body.category_id === undefined
    ? null
    : parseId(body.category_id);
  if (categoryId === null && body.category_id !== null && body.category_id !== '' && body.category_id !== undefined) {
    return sendBadRequest(res, 'category_id must be an integer or null.');
  }
  if (categoryId !== null) {
    const category = db
      .prepare('SELECT id FROM categories WHERE id = ? AND household_id = ?')
      .get(categoryId, householdId);
    if (!category) {
      return sendBadRequest(res, 'category_id does not exist in this household.');
    }
  }

  try {
    const result = db
      .prepare(
        `INSERT INTO transactions (
           household_id, account_id, date, amount, original_merchant,
           original_description, category_id, notes, is_transfer, is_ignored, source
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'manual')`
      )
      .run(
        householdId,
        accountId,
        date,
        dollarsToCents(amountValue),
        merchant,
        typeof body.description === 'string' ? body.description.trim() : null,
        categoryId,
        typeof body.notes === 'string' ? body.notes : '',
        body.is_transfer ? 1 : 0,
        body.is_ignored ? 1 : 0
      );

    const row = db
      .prepare(`SELECT ${SELECT_COLS} FROM transactions WHERE id = ? AND household_id = ?`)
      .get(result.lastInsertRowid, householdId);
    sendOk(res, {
      success: true,
      transaction: attachMerchantLogos(db, [hydrate(row)])[0]
    });
  } catch (err) {
    console.error('Create manual transaction failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// GET /api/transactions/:id
// ---------------------------------------------------------------------------
router.get('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'transaction');
  if (id === null) return;
  try {
    const row = db
      .prepare(`SELECT ${SELECT_COLS}, created_at, updated_at
                  FROM transactions
                 WHERE id = ? AND household_id = ?`)
      .get(id, householdId);
    if (!row) return sendNotFound(res, 'Transaction not found.');
    sendOk(res, attachMerchantLogos(db, [hydrate(row)])[0]);
  } catch (err) {
    console.error('Get transaction failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// PATCH /api/transactions/:id
// Writes edits into the edited_* columns with source='user'. If a value
// equals the original, the edit is cleared and any matching rule can
// re-claim the field.
// ---------------------------------------------------------------------------
router.patch('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'transaction');
  if (id === null) return;

  try {
    const existing = db
      .prepare(
        `SELECT id, original_merchant,
                category_id   AS original_category_id,
                is_transfer   AS original_is_transfer,
                is_ignored    AS original_is_ignored,
                CASE
                  WHEN (SELECT mha_default_ignored
                          FROM categories c
                         WHERE c.id = COALESCE(transactions.edited_category_id, transactions.category_id)) = 1
                  THEN 0
                  WHEN (SELECT mha_default_eligible FROM accounts a WHERE a.id = transactions.account_id) = 1
                    OR (SELECT mha_default_eligible
                          FROM categories c
                         WHERE c.id = COALESCE(transactions.edited_category_id, transactions.category_id)) = 1
                  THEN 1
                  ELSE 0
                END AS default_mha_eligible
           FROM transactions WHERE id = ?
             AND household_id = ?`
      )
      .get(id, householdId);
    if (!existing) return sendNotFound(res, 'Transaction not found.');

    const body = req.body || {};
    const sets = [];
    const values = [];

    if (body.merchant !== undefined) {
      if (typeof body.merchant !== 'string' || !body.merchant.trim()) {
        return sendBadRequest(res, 'merchant cannot be empty.');
      }
      const trimmed = body.merchant.trim();
      if (trimmed === existing.original_merchant) {
        sets.push('edited_merchant = NULL');
        sets.push('edited_merchant_source = NULL');
      } else {
        sets.push('edited_merchant = ?');
        values.push(trimmed);
        sets.push("edited_merchant_source = 'user'");
      }
    }

    if (body.category_id !== undefined) {
      const cid = body.category_id === null ? null : parseId(body.category_id);
      if (cid === null && body.category_id !== null) {
        return sendBadRequest(res, 'category_id must be an integer or null.');
      }
      if (cid !== null) {
        const category = db
          .prepare('SELECT id FROM categories WHERE id = ? AND household_id = ?')
          .get(cid, householdId);
        if (!category) {
          return sendBadRequest(res, 'category_id does not exist in this household.');
        }
      }
      if (cid === existing.original_category_id) {
        sets.push('edited_category_id = NULL');
        sets.push('edited_category_id_source = NULL');
      } else {
        sets.push('edited_category_id = ?');
        values.push(cid);
        sets.push("edited_category_id_source = 'user'");
      }
    }

    if (body.is_transfer !== undefined) {
      const flag = body.is_transfer ? 1 : 0;
      if (flag === existing.original_is_transfer) {
        sets.push('edited_is_transfer = NULL');
        sets.push('edited_is_transfer_source = NULL');
      } else {
        sets.push('edited_is_transfer = ?');
        values.push(flag);
        sets.push("edited_is_transfer_source = 'user'");
      }
    }

    if (body.is_ignored !== undefined) {
      const flag = body.is_ignored ? 1 : 0;
      if (flag === existing.original_is_ignored) {
        sets.push('edited_is_ignored = NULL');
        sets.push('edited_is_ignored_source = NULL');
      } else {
        sets.push('edited_is_ignored = ?');
        values.push(flag);
        sets.push("edited_is_ignored_source = 'user'");
      }
    }

    if (body.mha_eligible !== undefined) {
      const flag = body.mha_eligible ? 1 : 0;
      const defaultMhaEligible = existing.default_mha_eligible ? 1 : 0;
      if (flag === defaultMhaEligible) {
        sets.push('edited_mha_eligible = NULL');
        sets.push('edited_mha_eligible_source = NULL');
      } else {
        sets.push('edited_mha_eligible = ?');
        values.push(flag);
        sets.push("edited_mha_eligible_source = 'user'");
      }
    }

    if (body.notes !== undefined) {
      sets.push('notes = ?');
      values.push(typeof body.notes === 'string' ? body.notes : '');
    }

    if (sets.length === 0) {
      return sendBadRequest(res, 'No fields to update.');
    }

    sets.push("updated_at = datetime('now')");
    values.push(id, householdId);

    db.prepare(`UPDATE transactions SET ${sets.join(', ')} WHERE id = ? AND household_id = ?`).run(...values);

    const anyCleared =
      (body.merchant !== undefined && body.merchant.trim() === existing.original_merchant) ||
      (body.category_id !== undefined &&
        (body.category_id === null ? null : parseId(body.category_id)) ===
          existing.original_category_id) ||
      (body.is_transfer !== undefined &&
        (body.is_transfer ? 1 : 0) === existing.original_is_transfer) ||
      (body.is_ignored !== undefined &&
        (body.is_ignored ? 1 : 0) === existing.original_is_ignored);

    if (anyCleared) {
      reapplyRulesToTransaction(db, id, householdId);
    }

    const updated = db
      .prepare(`SELECT ${SELECT_COLS} FROM transactions WHERE id = ? AND household_id = ?`)
      .get(id, householdId);
    sendOk(res, {
      success: true,
      transaction: attachMerchantLogos(db, [hydrate(updated)])[0]
    });
  } catch (err) {
    console.error('Update transaction failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// POST /api/transactions/:id/reset
// Clears edits on the specified fields, then re-runs rules so any matching
// rule can re-claim the field.
// ---------------------------------------------------------------------------
router.post('/:id/reset', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'transaction');
  if (id === null) return;

  const allowed = new Set(['merchant', 'category_id', 'is_transfer', 'is_ignored', 'mha_eligible']);
  const fields = Array.isArray(req.body?.fields)
    ? req.body.fields.filter((f) => allowed.has(f))
    : [];

  if (fields.length === 0) {
    return sendBadRequest(res, 'fields must include at least one editable field.');
  }

  const clauses = [];
  for (const f of fields) {
    clauses.push(`edited_${f} = NULL`);
    clauses.push(`edited_${f}_source = NULL`);
  }
  clauses.push("updated_at = datetime('now')");

  try {
    db.prepare(`UPDATE transactions SET ${clauses.join(', ')} WHERE id = ? AND household_id = ?`).run(id, householdId);
    reapplyRulesToTransaction(db, id, householdId);

    const updated = db
      .prepare(`SELECT ${SELECT_COLS} FROM transactions WHERE id = ? AND household_id = ?`)
      .get(id, householdId);
    if (!updated) return sendNotFound(res, 'Transaction not found.');
    sendOk(res, {
      success: true,
      transaction: attachMerchantLogos(db, [hydrate(updated)])[0]
    });
  } catch (err) {
    console.error('Reset transaction failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// DELETE /api/transactions/:id
// ---------------------------------------------------------------------------
router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'transaction');
  if (id === null) return;
  try {
    const result = db.prepare('DELETE FROM transactions WHERE id = ? AND household_id = ?').run(id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Transaction not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete transaction failed:', err);
    sendServerError(res, err);
  }
});

export default router;
