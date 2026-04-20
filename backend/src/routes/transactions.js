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
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { reapplyRulesToTransaction } from '../services/ruleMatcher.js';

const router = express.Router();

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
    .map((x) => parseInt(x, 10))
    .filter(Number.isFinite);
}

function parseCategoryList(csv) {
  // Categories can include the literal 'uncategorized' to match NULL rows.
  if (!csv) return { ids: [], includeUncategorized: false };
  const tokens = csv.split(',').map((x) => x.trim()).filter(Boolean);
  const ids = tokens
    .filter((t) => t !== 'uncategorized')
    .map((t) => parseInt(t, 10))
    .filter(Number.isFinite);
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

function currentMonthBounds() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const start = `${year}-${String(month).padStart(2, '0')}-01`;
  const lastDay = new Date(year, month, 0).getDate();
  const end = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
  return { start, end };
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
function buildFilterWhere(q) {
  const wheres = [];
  const args = [];

  // ----- Account filter -----
  // `accounts` (multi) takes precedence over `account_id` (legacy single).
  const multiAccounts = parseIntList(q.accounts);
  if (multiAccounts.length > 0) {
    wheres.push(`account_id IN (${multiAccounts.map(() => '?').join(',')})`);
    args.push(...multiAccounts);
  } else if (q.account_id) {
    const single = parseInt(q.account_id, 10);
    if (Number.isFinite(single)) {
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
    args.push(amountMin);
  }
  const amountMax = parseNumber(q.amount_max);
  if (amountMax !== null && amountMax >= 0) {
    wheres.push('ABS(amount) <= ?');
    args.push(amountMax);
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
  const page = Math.max(1, parseInt(req.query.page, 10) || 1);
  const limit = Math.min(200, Math.max(1, parseInt(req.query.limit, 10) || 50));
  const offset = (page - 1) * limit;

  const sortKey = String(req.query.sort || 'date_desc').toLowerCase();
  const orderBy = SORT_MAP[sortKey] || SORT_MAP.date_desc;

  try {
    const { clause, args } = buildFilterWhere(req.query);

    const total = db
      .prepare(`SELECT COUNT(*) AS c FROM transactions ${clause}`)
      .get(...args).c;

    // Unfiltered total — useful for the UI to say "12 of 8,428".
    const grandTotal = db
      .prepare('SELECT COUNT(*) AS c FROM transactions')
      .get().c;

    const { start: monthStart, end: monthEnd } = currentMonthBounds();
    const monthlyTotal = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE date >= ? AND date <= ?')
      .get(monthStart, monthEnd).c;

    const items = db
      .prepare(
        `SELECT ${SELECT_COLS}
           FROM transactions
           ${clause}
           ORDER BY ${orderBy}
           LIMIT ? OFFSET ?`
      )
      .all(...args, limit, offset)
      .map(hydrate);

    res.json({
      items,
      page,
      limit,
      total,
      grandTotal,
      monthlyTotal,
      totalPages: Math.max(1, Math.ceil(total / limit)),
      sort: sortKey in SORT_MAP ? sortKey : 'date_desc'
    });
  } catch (err) {
    console.error('List transactions failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// GET /api/transactions/:id
// ---------------------------------------------------------------------------
router.get('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid transaction id.' });
  }
  try {
    const row = db
      .prepare(`SELECT ${SELECT_COLS}, created_at, updated_at
                  FROM transactions WHERE id = ?`)
      .get(id);
    if (!row) return res.status(404).json({ error: 'Transaction not found.' });
    res.json(hydrate(row));
  } catch (err) {
    console.error('Get transaction failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// PATCH /api/transactions/:id
// Writes edits into the edited_* columns with source='user'. If a value
// equals the original, the edit is cleared and any matching rule can
// re-claim the field.
// ---------------------------------------------------------------------------
router.patch('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid transaction id.' });
  }

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
         FROM transactions WHERE id = ?`
    )
    .get(id);
  if (!existing) return res.status(404).json({ error: 'Transaction not found.' });

  const body = req.body || {};
  const sets = [];
  const values = [];

  if (body.merchant !== undefined) {
    if (typeof body.merchant !== 'string' || !body.merchant.trim()) {
      return res.status(400).json({ error: 'merchant cannot be empty.' });
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
    const cid = body.category_id === null ? null : parseInt(body.category_id, 10);
    if (cid !== null && !Number.isFinite(cid)) {
      return res.status(400).json({ error: 'category_id must be an integer or null.' });
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
    return res.status(400).json({ error: 'No fields to update.' });
  }

  sets.push("updated_at = datetime('now')");
  values.push(id);

  try {
    db.prepare(`UPDATE transactions SET ${sets.join(', ')} WHERE id = ?`).run(...values);

    const anyCleared =
      (body.merchant !== undefined && body.merchant.trim() === existing.original_merchant) ||
      (body.category_id !== undefined &&
        (body.category_id === null ? null : parseInt(body.category_id, 10)) ===
          existing.original_category_id) ||
      (body.is_transfer !== undefined &&
        (body.is_transfer ? 1 : 0) === existing.original_is_transfer) ||
      (body.is_ignored !== undefined &&
        (body.is_ignored ? 1 : 0) === existing.original_is_ignored);

    if (anyCleared) {
      reapplyRulesToTransaction(db, id);
    }

    const updated = db
      .prepare(`SELECT ${SELECT_COLS} FROM transactions WHERE id = ?`)
      .get(id);
    res.json({ success: true, transaction: hydrate(updated) });
  } catch (err) {
    console.error('Update transaction failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// POST /api/transactions/:id/reset
// Clears edits on the specified fields, then re-runs rules so any matching
// rule can re-claim the field.
// ---------------------------------------------------------------------------
router.post('/:id/reset', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid transaction id.' });
  }

  const allowed = new Set(['merchant', 'category_id', 'is_transfer', 'is_ignored', 'mha_eligible']);
  const fields = Array.isArray(req.body?.fields)
    ? req.body.fields.filter((f) => allowed.has(f))
    : [];

  if (fields.length === 0) {
    return res
      .status(400)
      .json({ error: 'fields must include at least one editable field.' });
  }

  const clauses = [];
  for (const f of fields) {
    clauses.push(`edited_${f} = NULL`);
    clauses.push(`edited_${f}_source = NULL`);
  }
  clauses.push("updated_at = datetime('now')");

  try {
    db.prepare(`UPDATE transactions SET ${clauses.join(', ')} WHERE id = ?`).run(id);
    reapplyRulesToTransaction(db, id);

    const updated = db
      .prepare(`SELECT ${SELECT_COLS} FROM transactions WHERE id = ?`)
      .get(id);
    if (!updated) return res.status(404).json({ error: 'Transaction not found.' });
    res.json({ success: true, transaction: hydrate(updated) });
  } catch (err) {
    console.error('Reset transaction failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// DELETE /api/transactions/:id
// ---------------------------------------------------------------------------
router.delete('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid transaction id.' });
  }
  try {
    const result = db.prepare('DELETE FROM transactions WHERE id = ?').run(id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Transaction not found.' });
    }
    res.json({ success: true });
  } catch (err) {
    console.error('Delete transaction failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
