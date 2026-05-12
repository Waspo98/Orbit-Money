// =============================================================================
// budgets.js — Phase 1 budgets, v15 shape
// =============================================================================
// v14 used per-month budget rows. v15 reverts to one-row-per-category
// (globally applied) — the amount is the user's monthly target that applies
// to every month. The Budgets page still takes a `month` query param
// because SPENDING is naturally per-month, but the budget AMOUNT is
// category-global.
//
// Endpoints:
//   GET  /api/budgets?month=YYYY-MM   monthly overview
//   GET  /api/budgets/months          distinct months with activity
//   PUT  /api/budgets                 upsert { category_id, amount, rollover? }
//   DELETE /api/budgets/:id
//
// Summary block now includes total_income and total_expenses (computed for
// the requested month from the same transaction pool budgets measure
// against — non-ignored, non-transfer).
// =============================================================================

import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { formatLocalMonth, isValidMonthOnly } from '../lib/localDate.js';
import { dollarsToCents } from '../lib/money.js';
import { parseId, readIdParam } from '../lib/routeParams.js';
import { buildMonthlyBudgetOverview } from '../services/monthlyBudgetOverview.js';

const router = express.Router();

function parseMonth(s) {
  if (!s || typeof s !== 'string') return null;
  return isValidMonthOnly(s) ? s : null;
}

function currentMonth() {
  return formatLocalMonth();
}

// ---------------------------------------------------------------------------
// GET /api/budgets?month=YYYY-MM
// Monthly overview. Budgets are global per category; spending is scoped to
// the requested month.
// ---------------------------------------------------------------------------
router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const month = parseMonth(req.query.month) || currentMonth();

  try {
    sendOk(res, buildMonthlyBudgetOverview(db, { householdId, month }));
  } catch (err) {
    console.error('List budgets failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// GET /api/budgets/months
// Distinct months that have activity. Populates the month picker.
// ---------------------------------------------------------------------------
router.get('/months', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const rows = db
      .prepare(
        `SELECT strftime('%Y-%m', date) AS month
           FROM transactions
          WHERE household_id = ?
          GROUP BY month
          ORDER BY month DESC`
      )
      .all(householdId);
    sendOk(res, { items: rows.map((r) => r.month) });
  } catch (err) {
    console.error('List budget months failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// PUT /api/budgets
// Upsert a single global budget. Body: { category_id, amount, rollover? }
// ---------------------------------------------------------------------------
router.put('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const { category_id, amount, rollover = 0 } = req.body || {};

  const catId = parseId(category_id);
  if (catId === null) {
    return sendBadRequest(res, 'category_id must be an integer.');
  }
  const amt = Number(amount);
  if (!Number.isFinite(amt) || amt < 0) {
    return sendBadRequest(res, 'amount must be a non-negative number.');
  }
  const amountCents = dollarsToCents(amt);

  try {
    const category = db
      .prepare('SELECT is_income, is_transfer FROM categories WHERE id = ? AND household_id = ?')
      .get(catId, householdId);
    if (!category) {
      return sendBadRequest(res, 'category_id does not exist.');
    }
    if (category.is_income || category.is_transfer) {
      return sendBadRequest(res, 'Budgets can only be created for spending categories.');
    }

    const existing = db
      .prepare('SELECT id FROM budgets WHERE category_id = ? AND household_id = ?')
      .get(catId, householdId);

    if (existing) {
      db.prepare(
        `UPDATE budgets
            SET amount = ?, rollover = ?, updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(amountCents, rollover ? 1 : 0, existing.id, householdId);
      sendOk(res, { success: true, id: existing.id, created: false });
    } else {
      const result = db
        .prepare(
          `INSERT INTO budgets (household_id, category_id, amount, rollover)
           VALUES (?, ?, ?, ?)`
        )
        .run(householdId, catId, amountCents, rollover ? 1 : 0);
      sendOk(res, { success: true, id: result.lastInsertRowid, created: true });
    }
  } catch (err) {
    console.error('Upsert budget failed:', err);
    sendServerError(res, err);
  }
});

// ---------------------------------------------------------------------------
// DELETE /api/budgets/:id
// ---------------------------------------------------------------------------
router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'budget');
  if (id === null) return;
  try {
    const result = db.prepare('DELETE FROM budgets WHERE id = ? AND household_id = ?').run(id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Budget not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete budget failed:', err);
    sendServerError(res, err);
  }
});

export default router;
