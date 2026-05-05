import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { moneyFieldsToDollars } from '../lib/money.js';
import { parseBooleanField, parseInteger, readIdParam } from '../lib/routeParams.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';
import {
  formatMhaTransaction,
  MHA_SAVINGS_RATE,
  summarizeMhaTransactions
} from '../services/mhaSummary.js';
import {
  estimateMhaTaxSavings,
  MHA_TAX_PROFILE_KEY,
  normalizeMhaTaxProfile,
  parseMhaTaxProfile,
  taxDataMeta,
  taxReferencePayload
} from '../services/mhaTaxEstimate.js';

const router = express.Router();
const EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');
const SETTING_KEY = 'mha_tracker_enabled';
const ACCOUNT_MONEY_FIELDS = ['current_balance'];

function getSetting(householdId, key) {
  return db.prepare('SELECT value FROM app_settings WHERE household_id = ? AND key = ?').get(householdId, key)?.value;
}

function putSetting(householdId, key, value) {
  db.prepare(
    `INSERT INTO app_settings (household_id, key, value, updated_at)
     VALUES (?, ?, ?, datetime('now'))
     ON CONFLICT(household_id, key) DO UPDATE SET
       value = excluded.value,
       updated_at = datetime('now')`
  ).run(householdId, key, value);
}

function boolFlag(value) {
  return value ? 1 : 0;
}

function getEnabled(householdId) {
  return getSetting(householdId, SETTING_KEY) === '1';
}

function getTaxProfile(householdId) {
  return parseMhaTaxProfile(getSetting(householdId, MHA_TAX_PROFILE_KEY));
}

function getHouseholdTaxMembers(householdId) {
  return db
    .prepare(
      `SELECT gross_income_annual, employee_contribution_percent, employee_contribution_annual,
              health_premium_per_month, hsa_contribution_annual,
              dependent_care_fsa_annual, other_benefits_annual
         FROM household_members
        WHERE household_id = ?`
    )
    .all(householdId);
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

function getYears(householdId) {
  const years = db
    .prepare(
      `SELECT DISTINCT substr(date, 1, 4) AS year
         FROM transactions
       WHERE date IS NOT NULL
         AND household_id = ?
        ORDER BY year DESC`
    )
    .all(householdId)
    .map((row) => parseInteger(row.year))
    .filter((year) => year !== null);
  const set = new Set(years);
  set.add(currentYear());
  return Array.from(set).sort((a, b) => b - a);
}

router.get('/settings', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    sendOk(res, { enabled: getEnabled(householdId) });
  } catch (err) {
    console.error('Get MHA settings failed:', err);
    sendServerError(res, err);
  }
});

router.put('/settings', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const enabled = parseBooleanField(req.body, 'enabled');
  if (enabled === null) {
    return sendBadRequest(res, 'enabled must be a boolean.');
  }

  try {
    const value = enabled ? '1' : '0';
    putSetting(householdId, SETTING_KEY, value);
    sendOk(res, { enabled });
  } catch (err) {
    console.error('Update MHA settings failed:', err);
    sendServerError(res, err);
  }
});

router.put('/tax-profile', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const profile = normalizeMhaTaxProfile(req.body || {});
    putSetting(householdId, MHA_TAX_PROFILE_KEY, JSON.stringify(profile));
    sendOk(res, {
      taxProfile: profile,
      taxReference: taxReferencePayload()
    });
  } catch (err) {
    console.error('Update MHA tax profile failed:', err);
    sendServerError(res, err);
  }
});

router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
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
          WHERE household_id = ?
            AND is_archived = 0
          ORDER BY sort_order ASC, name ASC`
      )
      .all(householdId);

    const categories = db
      .prepare(
        `SELECT id, name, color, icon, is_transfer, is_income, sort_order,
                mha_default_eligible, mha_default_ignored
           FROM categories
          WHERE household_id = ?
          ORDER BY sort_order ASC, name COLLATE NOCASE ASC`
      )
      .all(householdId);

    const transactions = db
      .prepare(
        `SELECT t.id, t.account_id, t.date, t.amount / 100.0 AS amount,
                COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
                t.original_description,
                t.notes,
                ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} AS category_id,
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
           LEFT JOIN categories c ON c.id = ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL}
          WHERE t.household_id = ?
            AND a.household_id = ?
            AND (c.id IS NULL OR c.household_id = ?)
            AND COALESCE(
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
      .all(householdId, householdId, householdId, startDate, endDate)
      .map(formatMhaTransaction);
    const baseSummary = summarizeMhaTransactions(transactions, MHA_SAVINGS_RATE);
    const taxProfile = getTaxProfile(householdId);
    const taxEstimate = estimateMhaTaxSavings({
      transactionTotal: baseSummary.transactionTotal,
      profile: taxProfile,
      householdMembers: getHouseholdTaxMembers(householdId)
    });
    const summary = {
      ...baseSummary,
      savings: taxEstimate.savings,
      savingsRate: taxEstimate.effectiveRate
    };

    sendOk(res, {
      enabled: getEnabled(householdId),
      savingsRate: taxEstimate.effectiveRate,
      taxProfile,
      taxEstimate,
      taxReference: taxReferencePayload(),
      taxDataMeta: taxDataMeta(),
      year,
      years: getYears(householdId),
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
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  const mhaDefaultEligible = parseBooleanField(req.body, 'mha_default_eligible');
  if (mhaDefaultEligible === null) {
    return sendBadRequest(res, 'mha_default_eligible must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!existing) return sendNotFound(res, 'Account not found.');

    db.prepare(
      `UPDATE accounts
          SET mha_default_eligible = ?, updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(boolFlag(mhaDefaultEligible), id, householdId);

    const account = moneyFieldsToDollars(
      db.prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId),
      ACCOUNT_MONEY_FIELDS
    );
    sendOk(res, { success: true, account });
  } catch (err) {
    console.error('Update MHA account default failed:', err);
    sendServerError(res, err);
  }
});

router.put('/categories/:id/default', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;
  const mhaDefaultEligible = parseBooleanField(req.body, 'mha_default_eligible');
  if (mhaDefaultEligible === null) {
    return sendBadRequest(res, 'mha_default_eligible must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!existing) return sendNotFound(res, 'Category not found.');

    const include = boolFlag(mhaDefaultEligible);
    db.prepare(
      `UPDATE categories
          SET mha_default_eligible = ?,
              mha_default_ignored = CASE WHEN ? = 1 THEN 0 ELSE mha_default_ignored END
        WHERE id = ? AND household_id = ?`
    ).run(include, include, id, householdId);

    const category = db.prepare('SELECT * FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
    sendOk(res, { success: true, category });
  } catch (err) {
    console.error('Update MHA category default failed:', err);
    sendServerError(res, err);
  }
});

router.put('/categories/:id/ignore-default', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;
  const mhaDefaultIgnored = parseBooleanField(req.body, 'mha_default_ignored');
  if (mhaDefaultIgnored === null) {
    return sendBadRequest(res, 'mha_default_ignored must be a boolean.');
  }

  try {
    const existing = db.prepare('SELECT id FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!existing) return sendNotFound(res, 'Category not found.');

    const ignored = boolFlag(mhaDefaultIgnored);
    db.prepare(
      `UPDATE categories
          SET mha_default_ignored = ?,
              mha_default_eligible = CASE WHEN ? = 1 THEN 0 ELSE mha_default_eligible END
        WHERE id = ? AND household_id = ?`
    ).run(ignored, ignored, id, householdId);

    const category = db.prepare('SELECT * FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
    sendOk(res, { success: true, category });
  } catch (err) {
    console.error('Update MHA category ignore default failed:', err);
    sendServerError(res, err);
  }
});

export default router;
