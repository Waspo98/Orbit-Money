import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { moneyFieldsToDollars } from '../lib/money.js';
import { parseBooleanField, parseInteger, readIdParam } from '../lib/routeParams.js';
import {
  formatMhaTransaction,
  MHA_SAVINGS_RATE,
  summarizeMhaTransactions
} from '../services/mhaSummary.js';

const router = express.Router();
const SETTING_KEY = 'mha_tracker_enabled';
const ACCOUNT_MONEY_FIELDS = ['current_balance'];

function boolFlag(value) {
  return value ? 1 : 0;
}

function getEnabled() {
  const row = db.prepare('SELECT value FROM app_settings WHERE key = ?').get(SETTING_KEY);
  return row?.value === '1';
}

function currentYear() {
  return new Date().getFullYear();
}

function parseYear(value) {
  const year = parseInteger(value);
  if (year === null || year < 1900 || year > 2200) {
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
    .map((row) => parseInteger(row.year))
    .filter((year) => year !== null);
  const set = new Set(years);
  set.add(currentYear());
  return Array.from(set).sort((a, b) => b - a);
}

router.get('/settings', requireAuth, (req, res) => {
  try {
    sendOk(res, { enabled: getEnabled() });
  } catch (err) {
    console.error('Get MHA settings failed:', err);
    sendServerError(res, err);
  }
});

router.put('/settings', requireAuth, (req, res) => {
  const enabled = parseBooleanField(req.body, 'enabled');
  if (enabled === null) {
    return sendBadRequest(res, 'enabled must be a boolean.');
  }

  try {
    const value = enabled ? '1' : '0';
    db.prepare(
      `INSERT INTO app_settings (key, value, updated_at)
       VALUES (?, ?, datetime('now'))
       ON CONFLICT(key) DO UPDATE SET
         value = excluded.value,
         updated_at = datetime('now')`
    ).run(SETTING_KEY, value);
    sendOk(res, { enabled });
  } catch (err) {
    console.error('Update MHA settings failed:', err);
    sendServerError(res, err);
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
                current_balance / 100.0 AS current_balance,
                is_archived, sort_order, mha_default_eligible
           FROM accounts
          WHERE is_archived = 0
          ORDER BY sort_order ASC, name ASC`
      )
      .all();

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_transfer, is_income, sort_order,
                mha_default_eligible, mha_default_ignored
           FROM categories
          ORDER BY sort_order ASC, name COLLATE NOCASE ASC`
      )
      .all();

    const transactions = db
      .prepare(
        `SELECT t.id, t.account_id, t.date, t.amount / 100.0 AS amount,
                COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
                t.original_description,
                t.notes,
                COALESCE(t.edited_category_id, t.category_id) AS category_id,
                COALESCE(t.edited_is_transfer, t.is_transfer) AS is_transfer,
                COALESCE(t.edited_is_ignored, t.is_ignored) AS is_ignored,
                COALESCE(
                  t.edited_mha_eligible,
                  CASE
                    WHEN COALESCE(c.mha_default_ignored, 0) = 1 THEN 0
                    WHEN a.mha_default_eligible = 1
                      OR COALESCE(c.mha_default_eligible, 0) = 1
                    THEN 1
                    ELSE 0
                  END
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
                  CASE
                    WHEN COALESCE(c.mha_default_ignored, 0) = 1 THEN 0
                    WHEN a.mha_default_eligible = 1
                      OR COALESCE(c.mha_default_eligible, 0) = 1
                    THEN 1
                    ELSE 0
                  END
                ) = 1
            AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
            AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
            AND COALESCE(c.is_transfer, 0) = 0
            AND t.date >= ?
            AND t.date < ?
          ORDER BY t.date DESC, t.id DESC`
      )
      .all(startDate, endDate)
      .map(formatMhaTransaction);
    const summary = summarizeMhaTransactions(transactions, MHA_SAVINGS_RATE);

    sendOk(res, {
      enabled: getEnabled(),
      savingsRate: MHA_SAVINGS_RATE,
      year,
      years: getYears(),
      accounts,
      categories,
      transactions,
      summary
    });
  } catch (err) {
    console.error('Get MHA tracker failed:', err);
    sendServerError(res, err);
  }
});

router.put('/accounts/:id/default', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  const mhaDefaultEligible = parseBooleanField(req.body, 'mha_default_eligible');
  if (mhaDefaultEligible === null) {
    return sendBadRequest(res, 'mha_default_eligible must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM accounts WHERE id = ?').get(id);
    if (!existing) return sendNotFound(res, 'Account not found.');

    db.prepare(
      `UPDATE accounts
          SET mha_default_eligible = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).run(boolFlag(mhaDefaultEligible), id);

    const account = moneyFieldsToDollars(
      db.prepare('SELECT * FROM accounts WHERE id = ?').get(id),
      ACCOUNT_MONEY_FIELDS
    );
    sendOk(res, { success: true, account });
  } catch (err) {
    console.error('Update MHA account default failed:', err);
    sendServerError(res, err);
  }
});

router.put('/categories/:id/default', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;
  const mhaDefaultEligible = parseBooleanField(req.body, 'mha_default_eligible');
  if (mhaDefaultEligible === null) {
    return sendBadRequest(res, 'mha_default_eligible must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM categories WHERE id = ?').get(id);
    if (!existing) return sendNotFound(res, 'Category not found.');

    const include = boolFlag(mhaDefaultEligible);
    db.prepare(
      `UPDATE categories
          SET mha_default_eligible = ?,
              mha_default_ignored = CASE WHEN ? = 1 THEN 0 ELSE mha_default_ignored END
        WHERE id = ?`
    ).run(include, include, id);

    const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
    sendOk(res, { success: true, category });
  } catch (err) {
    console.error('Update MHA category default failed:', err);
    sendServerError(res, err);
  }
});

router.put('/categories/:id/ignore-default', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;
  const mhaDefaultIgnored = parseBooleanField(req.body, 'mha_default_ignored');
  if (mhaDefaultIgnored === null) {
    return sendBadRequest(res, 'mha_default_ignored must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM categories WHERE id = ?').get(id);
    if (!existing) return sendNotFound(res, 'Category not found.');

    const ignored = boolFlag(mhaDefaultIgnored);
    db.prepare(
      `UPDATE categories
          SET mha_default_ignored = ?,
              mha_default_eligible = CASE WHEN ? = 1 THEN 0 ELSE mha_default_eligible END
        WHERE id = ?`
    ).run(ignored, ignored, id);

    const category = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
    sendOk(res, { success: true, category });
  } catch (err) {
    console.error('Update MHA category ignore default failed:', err);
    sendServerError(res, err);
  }
});

export default router;
