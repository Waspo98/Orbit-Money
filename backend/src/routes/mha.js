import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

const router = express.Router();
const SETTING_KEY = 'mha_tracker_enabled';
const SAVINGS_RATE = 0.27;

function boolFlag(value) {
  return value ? 1 : 0;
}

function getEnabled() {
  const row = db.prepare('SELECT value FROM app_settings WHERE key = ?').get(SETTING_KEY);
  return row?.value === '1';
}

function formatTransaction(row) {
  return {
    ...row,
    mha_eligible: row.mha_eligible ? 1 : 0,
    amount_abs: Math.abs(Number(row.amount || 0))
  };
}

function currentYear() {
  return new Date().getFullYear();
}

function parseYear(value) {
  const year = parseInt(value, 10);
  if (!Number.isFinite(year) || year < 1900 || year > 2200) {
    return currentYear();
  }
  return year;
}

function getYears() {
  const years = db
    .prepare(
      `SELECT DISTINCT substr(date, 1, 4) AS year
         FROM transactions
        WHERE date IS NOT NULL
        ORDER BY year DESC`
    )
    .all()
    .map((row) => parseInt(row.year, 10))
    .filter(Number.isFinite);
  const set = new Set(years);
  set.add(currentYear());
  return Array.from(set).sort((a, b) => b - a);
}

router.get('/settings', requireAuth, (req, res) => {
  try {
    res.json({ enabled: getEnabled() });
  } catch (err) {
    console.error('Get MHA settings failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.put('/settings', requireAuth, (req, res) => {
  if (typeof req.body?.enabled !== 'boolean') {
    return res.status(400).json({ error: 'enabled must be a boolean.' });
  }

  try {
    const value = req.body.enabled ? '1' : '0';
    db.prepare(
      `INSERT INTO app_settings (key, value, updated_at)
       VALUES (?, ?, datetime('now'))
       ON CONFLICT(key) DO UPDATE SET
         value = excluded.value,
         updated_at = datetime('now')`
    ).run(SETTING_KEY, value);
    res.json({ enabled: req.body.enabled });
  } catch (err) {
    console.error('Update MHA settings failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.get('/', requireAuth, (req, res) => {
  try {
    const year = parseYear(req.query.year);
    const startDate = `${year}-01-01`;
    const endDate = `${year + 1}-01-01`;

    const accounts = db
      .prepare(
        `SELECT id, name, type, institution, account_number_last4,
                current_balance, is_archived, sort_order, mha_default_eligible
           FROM accounts
          WHERE is_archived = 0
          ORDER BY sort_order ASC, name ASC`
      )
      .all();

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_transfer, is_income, sort_order,
                mha_default_eligible
           FROM categories
          ORDER BY sort_order ASC, name COLLATE NOCASE ASC`
      )
      .all();

    const transactions = db
      .prepare(
        `SELECT t.id, t.account_id, t.date, t.amount,
                COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
                t.original_description,
                t.notes,
                COALESCE(t.edited_category_id, t.category_id) AS category_id,
                COALESCE(t.edited_is_transfer, t.is_transfer) AS is_transfer,
                COALESCE(t.edited_is_ignored, t.is_ignored) AS is_ignored,
                COALESCE(
                  t.edited_mha_eligible,
                  CASE WHEN a.mha_default_eligible = 1
                         OR COALESCE(c.mha_default_eligible, 0) = 1
                       THEN 1 ELSE 0 END
                ) AS mha_eligible,
                t.edited_mha_eligible,
                t.edited_mha_eligible_source,
                a.name AS account_name,
                a.type AS account_type,
                c.name AS category_name,
                c.color AS category_color,
                c.icon AS category_icon
           FROM transactions t
           JOIN accounts a ON a.id = t.account_id
           LEFT JOIN categories c ON c.id = COALESCE(t.edited_category_id, t.category_id)
          WHERE COALESCE(
                  t.edited_mha_eligible,
                  CASE WHEN a.mha_default_eligible = 1
                         OR COALESCE(c.mha_default_eligible, 0) = 1
                       THEN 1 ELSE 0 END
                ) = 1
            AND t.date >= ?
            AND t.date < ?
          ORDER BY t.date DESC, t.id DESC`
      )
      .all(startDate, endDate)
      .map(formatTransaction);

    const transactionTotal = transactions.reduce(
      (sum, t) => sum + Math.abs(Number(t.amount || 0)),
      0
    );

    res.json({
      enabled: getEnabled(),
      savingsRate: SAVINGS_RATE,
      year,
      years: getYears(),
      accounts,
      categories,
      transactions,
      summary: {
        transactionCount: transactions.length,
        transactionTotal,
        savings: transactionTotal * SAVINGS_RATE
      }
    });
  } catch (err) {
    console.error('Get MHA tracker failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.put('/accounts/:id/default', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid account id.' });
  }
  if (typeof req.body?.mha_default_eligible !== 'boolean') {
    return res.status(400).json({ error: 'mha_default_eligible must be a boolean.' });
  }

  try {
    const existing = db.prepare('SELECT id FROM accounts WHERE id = ?').get(id);
    if (!existing) return res.status(404).json({ error: 'Account not found.' });

    db.prepare(
      `UPDATE accounts
          SET mha_default_eligible = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).run(boolFlag(req.body.mha_default_eligible), id);

    const account = db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
    res.json({ success: true, account });
  } catch (err) {
    console.error('Update MHA account default failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.put('/categories/:id/default', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid category id.' });
  }
  if (typeof req.body?.mha_default_eligible !== 'boolean') {
    return res.status(400).json({ error: 'mha_default_eligible must be a boolean.' });
  }

  try {
    const existing = db.prepare('SELECT id FROM categories WHERE id = ?').get(id);
    if (!existing) return res.status(404).json({ error: 'Category not found.' });

    db.prepare(
      `UPDATE categories
          SET mha_default_eligible = ?
        WHERE id = ?`
    ).run(boolFlag(req.body.mha_default_eligible), id);

    const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
    res.json({ success: true, category });
  } catch (err) {
    console.error('Update MHA category default failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
