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
import { centsToDollars, dollarsToCents } from '../lib/money.js';
import { parseId, readIdParam } from '../lib/routeParams.js';

const router = express.Router();

function parseMonth(s) {
  if (!s || typeof s !== 'string') return null;
  return /^\d{4}-\d{2}$/.test(s) ? s : null;
}

function currentMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function monthBounds(yyyymm) {
  const [y, m] = yyyymm.split('-').map(Number);
  const start = `${yyyymm}-01`;
  const endDate = new Date(y, m, 0);
  const end = `${y}-${String(m).padStart(2, '0')}-${String(endDate.getDate()).padStart(2, '0')}`;
  return { start, end };
}

// ---------------------------------------------------------------------------
// GET /api/budgets?month=YYYY-MM
// Monthly overview. Budgets are global per category; spending is scoped to
// the requested month.
// ---------------------------------------------------------------------------
router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const month = parseMonth(req.query.month) || currentMonth();
  const { start, end } = monthBounds(month);

  try {
    // Per-category net spending for the month (-SUM handles refunds).
    const spending = db
      .prepare(
        `SELECT
           COALESCE(t.edited_category_id, t.category_id) AS cat_id,
           -SUM(t.amount) AS spent,
           COUNT(*) AS n
         FROM transactions t
         JOIN categories c ON c.id = COALESCE(t.edited_category_id, t.category_id)
         WHERE t.household_id = ?
           AND c.household_id = ?
           AND t.date >= ? AND t.date <= ?
           AND COALESCE(t.edited_is_ignored,  t.is_ignored)  = 0
           AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
           AND c.is_income = 0
           AND c.is_transfer = 0
         GROUP BY cat_id`
      )
      .all(householdId, householdId, start, end);

    const spendByCat = new Map(spending.map((r) => [r.cat_id, r]));

    // Overall income and expenses for the month (also used by the stats
    // row at the top of the Budgets page).
    const flow = db
      .prepare(
        `SELECT
           SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS income,
           SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END) AS expenses
         FROM transactions
         WHERE household_id = ?
           AND date >= ? AND date <= ?
           AND COALESCE(edited_is_ignored,  is_ignored)  = 0
           AND COALESCE(edited_is_transfer, is_transfer) = 0`
      )
      .get(householdId, start, end);

    const total_income = centsToDollars(flow?.income || 0);
    const total_expenses = centsToDollars(flow?.expenses || 0);

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_income, is_transfer, sort_order
          FROM categories
          WHERE household_id = ?
            AND is_transfer = 0 AND is_income = 0
          ORDER BY sort_order, name`
      )
      .all(householdId);

    const budgets = db
      .prepare(
        `SELECT b.id, b.category_id, b.amount, b.rollover
          FROM budgets b
          JOIN categories c ON c.id = b.category_id
          WHERE b.household_id = ?
            AND c.household_id = ?
            AND c.is_transfer = 0 AND c.is_income = 0`
      )
      .all(householdId, householdId);
    const budgetByCat = new Map(budgets.map((b) => [b.category_id, b]));

    const budgetedItems = [];
    const unbudgetedItems = [];
    const inactiveItems = [];

    for (const cat of categories) {
      const budget = budgetByCat.get(cat.id);
      const spendRow = spendByCat.get(cat.id);
      const spent = spendRow ? centsToDollars(spendRow.spent) : 0;
      const txnCount = spendRow ? spendRow.n : 0;

      const item = {
        category: {
          id: cat.id,
          name: cat.name,
          color: cat.color,
          icon: cat.icon,
          is_income: !!cat.is_income,
          is_transfer: !!cat.is_transfer
        },
        budget_id: budget?.id ?? null,
        amount: budget ? centsToDollars(budget.amount) : null,
        rollover: budget ? (budget.rollover ? 1 : 0) : null,
        spent,
        transaction_count: txnCount
      };

      if (budget) {
        budgetedItems.push(item);
      } else if (spent > 0 || txnCount > 0) {
        unbudgetedItems.push(item);
      } else {
        inactiveItems.push(item);
      }
    }

    budgetedItems.sort((a, b) => {
      const ra = a.amount > 0 ? a.spent / a.amount : Infinity;
      const rb = b.amount > 0 ? b.spent / b.amount : Infinity;
      return rb - ra;
    });
    unbudgetedItems.sort((a, b) => b.spent - a.spent);
    inactiveItems.sort((a, b) =>
      a.category.name.localeCompare(b.category.name)
    );

    const total_budgeted = budgetedItems.reduce((s, i) => s + i.amount, 0);
    const total_spent_in_budgets = budgetedItems.reduce((s, i) => s + i.spent, 0);
    const total_spent_unbudgeted = unbudgetedItems.reduce(
      (s, i) => s + i.spent,
      0
    );

    sendOk(res, {
      month,
      budgeted: budgetedItems,
      unbudgeted: unbudgetedItems,
      inactive: inactiveItems,
      summary: {
        total_budgeted,
        total_spent_in_budgets,
        total_spent_unbudgeted,
        total_spent: total_spent_in_budgets + total_spent_unbudgeted,
        total_income,
        total_expenses,
        total_net: total_income - total_expenses
      }
    });
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
