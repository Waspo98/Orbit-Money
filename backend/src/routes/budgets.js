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
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

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
  const month = parseMonth(req.query.month) || currentMonth();
  const { start, end } = monthBounds(month);

  try {
    // Per-category net spending for the month (-SUM handles refunds).
    const spending = db
      .prepare(
        `SELECT
           COALESCE(edited_category_id, category_id) AS cat_id,
           -SUM(amount) AS spent,
           COUNT(*) AS n
         FROM transactions
         WHERE date >= ? AND date <= ?
           AND COALESCE(edited_is_ignored,  is_ignored)  = 0
           AND COALESCE(edited_is_transfer, is_transfer) = 0
           AND COALESCE(edited_category_id, category_id) IS NOT NULL
         GROUP BY cat_id`
      )
      .all(start, end);

    const spendByCat = new Map(spending.map((r) => [r.cat_id, r]));

    // Overall income and expenses for the month (also used by the stats
    // row at the top of the Budgets page).
    const flow = db
      .prepare(
        `SELECT
           SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS income,
           SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END) AS expenses
         FROM transactions
         WHERE date >= ? AND date <= ?
           AND COALESCE(edited_is_ignored,  is_ignored)  = 0
           AND COALESCE(edited_is_transfer, is_transfer) = 0`
      )
      .get(start, end);

    const total_income = Number(flow?.income || 0);
    const total_expenses = Number(flow?.expenses || 0);

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_income, is_transfer, sort_order
           FROM categories
          WHERE is_transfer = 0
          ORDER BY sort_order, name`
      )
      .all();

    const budgets = db
      .prepare('SELECT id, category_id, amount, rollover FROM budgets')
      .all();
    const budgetByCat = new Map(budgets.map((b) => [b.category_id, b]));

    const budgetedItems = [];
    const unbudgetedItems = [];
    const inactiveItems = [];

    for (const cat of categories) {
      const budget = budgetByCat.get(cat.id);
      const spendRow = spendByCat.get(cat.id);
      const spent = spendRow ? Number(spendRow.spent) : 0;
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
        amount: budget ? Number(budget.amount) : null,
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

    res.json({
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
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// GET /api/budgets/months
// Distinct months that have activity. Populates the month picker.
// ---------------------------------------------------------------------------
router.get('/months', requireAuth, (req, res) => {
  try {
    const rows = db
      .prepare(
        `SELECT strftime('%Y-%m', date) AS month
           FROM transactions
          GROUP BY month
          ORDER BY month DESC`
      )
      .all();
    res.json({ items: rows.map((r) => r.month) });
  } catch (err) {
    console.error('List budget months failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// PUT /api/budgets
// Upsert a single global budget. Body: { category_id, amount, rollover? }
// ---------------------------------------------------------------------------
router.put('/', requireAuth, (req, res) => {
  const { category_id, amount, rollover = 0 } = req.body || {};

  const catId = parseInt(category_id, 10);
  if (!Number.isFinite(catId)) {
    return res.status(400).json({ error: 'category_id must be an integer.' });
  }
  const amt = Number(amount);
  if (!Number.isFinite(amt) || amt < 0) {
    return res
      .status(400)
      .json({ error: 'amount must be a non-negative number.' });
  }

  try {
    const existing = db
      .prepare('SELECT id FROM budgets WHERE category_id = ?')
      .get(catId);

    if (existing) {
      db.prepare(
        `UPDATE budgets
            SET amount = ?, rollover = ?, updated_at = datetime('now')
          WHERE id = ?`
      ).run(amt, rollover ? 1 : 0, existing.id);
      res.json({ success: true, id: existing.id, created: false });
    } else {
      const result = db
        .prepare(
          `INSERT INTO budgets (category_id, amount, rollover)
           VALUES (?, ?, ?)`
        )
        .run(catId, amt, rollover ? 1 : 0);
      res.json({ success: true, id: result.lastInsertRowid, created: true });
    }
  } catch (err) {
    console.error('Upsert budget failed:', err);
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// DELETE /api/budgets/:id
// ---------------------------------------------------------------------------
router.delete('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid budget id.' });
  }
  try {
    const result = db.prepare('DELETE FROM budgets WHERE id = ?').run(id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Budget not found.' });
    }
    res.json({ success: true });
  } catch (err) {
    console.error('Delete budget failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
