import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { isValidDateOnly } from '../lib/localDate.js';
import { dollarsToCents, moneyFieldsToDollars } from '../lib/money.js';
import { parseId, parseInteger, readIdParam } from '../lib/routeParams.js';
import { recordManualAccountBalance } from '../services/accountBalanceRecordService.js';
import { mergeAccounts } from '../services/accountMerge.js';
import {
  parseRealEstateAccountFields,
  resolveAccountClosure
} from '../services/accountRealEstateService.js';
import { applyBenefitUsage } from '../services/creditCardBenefitUsage.js';
import {
  BENEFIT_PERIOD_KEY_REGEX,
  attachCreditCardProfiles,
  normalizeCreditCardProfileBody,
  serializeCreditCardProfile,
  upsertCreditCardProfile
} from '../services/creditCardProfileService.js';
import { attachInstitutionLogo, attachInstitutionLogos } from '../services/institutionLogos.js';

const router = express.Router();

const VALID_TYPES = ['checking', 'savings', 'credit', 'investment', 'loan', 'mortgage', 'cash', 'other'];
const ACCOUNT_MONEY_FIELDS = ['current_balance', 'estimated_value'];

function serializeAccount(row) {
  return attachInstitutionLogo(db, moneyFieldsToDollars(row, ACCOUNT_MONEY_FIELDS));
}

function normalizeOptionalText(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text || null;
}

/**
 * GET /api/accounts?includeArchived=1
 *
 * Returns accounts ordered by sort_order (user-controlled via drag-and-drop).
 * Archived accounts go to the bottom (is_archived ASC first). Within the same
 * archived-state, sort_order controls the order.
 */
router.get('/', requireAuth, (req, res) => {
  const includeArchived = req.query.includeArchived === '1';
  const householdId = requireHouseholdId(req);
  try {
    const items = db
      .prepare(
        `
        SELECT a.id, a.name, a.type, a.institution, a.account_number_last4,
               a.current_balance, a.estimated_value,
               a.is_property, a.property_name, a.linked_property_account_id,
               a.simplefin_last_seen_at, a.simplefin_missing_since, a.closure_prompt_dismissed_at,
               a.is_manual, a.is_archived, a.sort_order, a.mha_default_eligible,
               a.simplefin_account_id, a.created_at, a.updated_at,
               COUNT(t.id) AS transaction_count
          FROM accounts a
          LEFT JOIN transactions t ON t.account_id = a.id AND t.household_id = ?
         WHERE a.household_id = ?
           ${includeArchived ? '' : 'AND a.is_archived = 0'}
         GROUP BY a.id
         ORDER BY a.is_archived ASC, a.sort_order ASC, a.name ASC
      `
      )
      .all(householdId, householdId)
      .map((row) => moneyFieldsToDollars(row, ACCOUNT_MONEY_FIELDS));

    sendOk(res, { items: attachInstitutionLogos(db, attachCreditCardProfiles(db, householdId, items)) });
  } catch (err) {
    console.error('List accounts failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/accounts
 * Creates a user-managed account and optional first balance snapshot.
 */
router.post('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const body = req.body || {};
  const name = typeof body.name === 'string' ? body.name.trim() : '';
  const type = typeof body.type === 'string' ? body.type : 'other';

  if (!name) {
    return sendBadRequest(res, 'name is required.');
  }
  if (!VALID_TYPES.includes(type)) {
    return sendBadRequest(res, `type must be one of: ${VALID_TYPES.join(', ')}`);
  }

  const balanceDollars = body.balance === undefined || body.balance === ''
    ? (body.current_balance === undefined || body.current_balance === '' ? 0 : Number(body.current_balance))
    : Number(body.balance);
  if (!Number.isFinite(balanceDollars)) {
    return sendBadRequest(res, 'balance must be a valid number.');
  }

  const recordDate = String(body.recordDate || '').trim();
  if (recordDate && !isValidDateOnly(recordDate)) {
    return sendBadRequest(res, 'recordDate must be a valid YYYY-MM-DD date.');
  }

  let realEstateFields;
  try {
    realEstateFields = parseRealEstateAccountFields(db, { householdId, effectiveType: type, body });
  } catch (err) {
    return sendBadRequest(res, err.message);
  }

  const balance = dollarsToCents(balanceDollars);

  try {
    const run = db.transaction(() => {
      const result = db
        .prepare(
          `INSERT INTO accounts (
             household_id, name, type, institution, account_number_last4,
             is_manual, current_balance, estimated_value,
             is_property, property_name, linked_property_account_id, sort_order
           ) VALUES (
             ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?,
             COALESCE((SELECT MAX(sort_order) + 10 FROM accounts WHERE household_id = ?), 0)
           )`
        )
        .run(
          householdId,
          name,
          type,
          normalizeOptionalText(body.institution),
          normalizeOptionalText(body.account_number_last4),
          balance,
          realEstateFields.estimated_value ?? null,
          realEstateFields.is_property ?? 0,
          realEstateFields.property_name ?? null,
          realEstateFields.linked_property_account_id ?? null,
          householdId
        );

      if (recordDate) {
        db.prepare(
          `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
           VALUES (?, ?, ?, ?, 'manual')`
        ).run(householdId, result.lastInsertRowid, recordDate, balance);
      }

      return db
        .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
        .get(result.lastInsertRowid, householdId);
    });

    const account = run();
    sendOk(res, { success: true, account: serializeAccount(account) });
  } catch (err) {
    console.error('Create account failed:', err);
    sendBadRequest(res, err.message || 'Create account failed.');
  }
});

/**
 * POST /api/accounts/reorder
 * Body: { orderedIds: [1, 5, 3, 8, ...] }
 *
 * Assigns new sort_order values in multiples of 10 so future insertions have
 * room. Only updates accounts present in the array; missing accounts keep
 * their existing order.
 *
 * Wrapped in a transaction so a partial failure doesn't leave the order half
 * written.
 */
router.post('/reorder', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const orderedIds = Array.isArray(req.body?.orderedIds) ? req.body.orderedIds : null;
  if (!orderedIds || orderedIds.length === 0) {
    return sendBadRequest(res, 'orderedIds must be a non-empty array.');
  }

  // Validate each id is a number.
  const ids = orderedIds.map(parseInteger).filter((id) => id !== null);
  if (ids.length !== orderedIds.length) {
    return sendBadRequest(res, 'orderedIds must contain only numeric ids.');
  }

  try {
    const update = db.prepare(
      `UPDATE accounts
          SET sort_order = ?, updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    );

    const run = db.transaction(() => {
      ids.forEach((id, index) => {
        update.run(index * 10, id, householdId);
      });
    });

    run();
    sendOk(res, { success: true, reordered: ids.length });
  } catch (err) {
    console.error('Reorder failed:', err);
    sendServerError(res, err);
  }
});

/**
 * PUT /api/accounts/:id
 */
router.put('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const existing = db
    .prepare('SELECT id, type FROM accounts WHERE id = ? AND household_id = ?')
    .get(id, householdId);
  if (!existing) {
    return sendNotFound(res, 'Account not found.');
  }

  const body = req.body || {};

  if (body.type !== undefined && !VALID_TYPES.includes(body.type)) {
    return sendBadRequest(res, `type must be one of: ${VALID_TYPES.join(', ')}`);
  }

  if (body.name !== undefined && (typeof body.name !== 'string' || !body.name.trim())) {
    return sendBadRequest(res, 'name cannot be empty.');
  }

  const effectiveType = body.type !== undefined ? body.type : existing.type;
  let realEstateFields;
  try {
    realEstateFields = parseRealEstateAccountFields(db, {
      householdId,
      accountId: id,
      effectiveType,
      body
    });
  } catch (err) {
    return sendBadRequest(res, err.message);
  }

  const sets = [];
  const values = [];

  const allowed = [
    ['name', 'name'],
    ['type', 'type'],
    ['institution', 'institution'],
    ['account_number_last4', 'account_number_last4'],
    ['current_balance', 'current_balance'],
    ['simplefin_account_id', 'simplefin_account_id'],
    ['mha_default_eligible', 'mha_default_eligible']
  ];

  for (const [key, col] of allowed) {
    if (body[key] !== undefined) {
      sets.push(`${col} = ?`);
      let v = body[key];
      if (typeof v === 'string' && v.trim() === '' &&
          ['institution', 'account_number_last4', 'simplefin_account_id'].includes(col)) {
        v = null;
      }
      if (col === 'name' && typeof v === 'string') {
        v = v.trim();
      }
      if (col === 'current_balance') {
        v = Number(v);
        if (!Number.isFinite(v)) {
          return sendBadRequest(res, 'current_balance must be a valid number.');
        }
        v = dollarsToCents(v);
      }
      if (col === 'mha_default_eligible') {
        v = v === true || v === 1 || v === '1' ? 1 : 0;
      }
      values.push(v);
    }
  }

  for (const [col, val] of Object.entries(realEstateFields)) {
    sets.push(`${col} = ?`);
    values.push(val);
  }

  if (sets.length === 0) {
    return sendBadRequest(res, 'No fields to update.');
  }

  sets.push("updated_at = datetime('now')");
  values.push(id, householdId);

  try {
    db.prepare(`UPDATE accounts SET ${sets.join(', ')} WHERE id = ? AND household_id = ?`).run(...values);
    const updated = db.prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId);
    sendOk(res, { success: true, account: serializeAccount(updated) });
  } catch (err) {
    console.error('Update account failed:', err);
    sendBadRequest(res, err.message);
  }
});

router.put('/:id/credit-card-profile', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const account = db
    .prepare('SELECT id, type FROM accounts WHERE id = ? AND household_id = ?')
    .get(id, householdId);
  if (!account) return sendNotFound(res, 'Account not found.');
  if (account.type !== 'credit') {
    return sendBadRequest(res, 'Credit card details can only be saved on credit accounts.');
  }

  let profile;
  try {
    profile = normalizeCreditCardProfileBody(req.body || {});
  } catch (err) {
    return sendBadRequest(res, err.message || 'Invalid credit card profile.');
  }

  try {
    upsertCreditCardProfile(db, { householdId, accountId: id, profile });

    const updated = db
      .prepare('SELECT * FROM credit_card_profiles WHERE account_id = ? AND household_id = ?')
      .get(id, householdId);
    const updatedAccount = db
      .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
      .get(id, householdId);
    sendOk(res, {
      success: true,
      account: serializeAccount(updatedAccount),
      profile: serializeCreditCardProfile(updated)
    });
  } catch (err) {
    console.error('Save credit card profile failed:', err);
    sendServerError(res, err);
  }
});

router.put('/:id/credit-card-profile/benefits/:benefitId/usage', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const benefitId = typeof req.params.benefitId === 'string' ? req.params.benefitId.trim() : '';
  if (!benefitId || benefitId.length > 64) {
    return sendBadRequest(res, 'Invalid benefitId.');
  }

  const periodKey = typeof req.body?.period_key === 'string'
    ? req.body.period_key.trim()
    : (typeof req.body?.periodKey === 'string' ? req.body.periodKey.trim() : '');
  if (!periodKey || !BENEFIT_PERIOD_KEY_REGEX.test(periodKey)) {
    return sendBadRequest(res, 'period_key must match YYYY, YYYY-MM, YYYY-Qn, YYYY-Hn, or CM-YYYY-MM-DD.');
  }

  let amount = null;
  const rawAmount = req.body?.amount;
  if (rawAmount !== null && rawAmount !== undefined && rawAmount !== '') {
    const num = Number(rawAmount);
    if (!Number.isFinite(num) || num < 0) {
      return sendBadRequest(res, 'amount must be a non-negative number or null.');
    }
    amount = Math.round(num * 100) / 100;
  }

  let usedOn = null;
  const rawUsedOn = req.body?.used_on ?? req.body?.usedOn;
  if (rawUsedOn !== null && rawUsedOn !== undefined && rawUsedOn !== '') {
    if (typeof rawUsedOn !== 'string' || !isValidDateOnly(rawUsedOn.trim())) {
      return sendBadRequest(res, 'used_on must be a valid YYYY-MM-DD date or null.');
    }
    usedOn = rawUsedOn.trim();
  }

  try {
    const updated = applyBenefitUsage(db, {
      householdId,
      accountId: id,
      benefitId,
      periodKey,
      amount,
      usedOn
    });
    if (!updated) {
      return sendNotFound(res, 'Credit card profile or benefit not found.');
    }
    sendOk(res, {
      success: true,
      profile: serializeCreditCardProfile(updated)
    });
  } catch (err) {
    console.error('Update benefit usage failed:', err);
    sendServerError(res, err);
  }
});

router.delete('/:id/credit-card-profile', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  try {
    db.prepare(
      `DELETE FROM credit_card_profiles
        WHERE account_id = ?
          AND household_id = ?`
    ).run(id, householdId);
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete credit card profile failed:', err);
    sendServerError(res, err);
  }
});

router.post('/:id/archive', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 1, updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      )
      .run(id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Account not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Archive failed:', err);
    sendServerError(res, err);
  }
});

router.post('/:id/unarchive', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 0, updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      )
      .run(id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Account not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Unarchive failed:', err);
    sendServerError(res, err);
  }
});

router.post('/:id/records', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const recordDate = String(req.body?.date || '').trim();
  if (!isValidDateOnly(recordDate)) {
    return sendBadRequest(res, 'date must be a valid YYYY-MM-DD date.');
  }

  const balanceDollars = Number(req.body?.balance);
  if (!Number.isFinite(balanceDollars)) {
    return sendBadRequest(res, 'balance must be a valid number.');
  }
  const balance = dollarsToCents(balanceDollars);
  const previousBalance =
    req.body?.previousBalance === undefined ? null : Number(req.body.previousBalance);
  if (previousBalance !== null && !Number.isFinite(previousBalance)) {
    return sendBadRequest(res, 'previousBalance must be a valid number.');
  }
  const previousBalanceCents = previousBalance === null ? null : dollarsToCents(previousBalance);

  const account = db.prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId);
  if (!account) {
    return sendNotFound(res, 'Account not found.');
  }

  try {
    const updated = recordManualAccountBalance(db, {
      householdId,
      accountId: id,
      recordDate,
      balanceCents: balance,
      previousBalanceCents
    });
    sendOk(res, {
      success: true,
      account: serializeAccount(updated),
      record: {
        account_id: id,
        date: recordDate,
        balance: balanceDollars
      }
    });
  } catch (err) {
    console.error('Add account record failed:', err);
    sendServerError(res, err);
  }
});

router.post('/:id/resolve-closure', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const resolution = typeof req.body?.resolution === 'string' ? req.body.resolution.trim() : '';
  try {
    const { updatedAccount, createdProperty } = resolveAccountClosure(db, {
      householdId,
      accountId: id,
      resolution
    });
    sendOk(res, {
      success: true,
      account: serializeAccount(updatedAccount),
      created_property: createdProperty ? serializeAccount(createdProperty) : null
    });
  } catch (err) {
    if (err.status === 404) return sendNotFound(res, err.message);
    if (err.status === 400) return sendBadRequest(res, err.message);
    console.error('Resolve closure failed:', err);
    sendServerError(res, err);
  }
});

router.post('/:id/merge', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const sourceId = readIdParam(req, res, 'id', 'account');
  if (sourceId === null) return;
  const targetId = parseId(req.body?.targetId);

  if (targetId === null) {
    return sendBadRequest(res, 'Invalid account ids.');
  }
  if (sourceId === targetId) {
    return sendBadRequest(res, "Can't merge an account into itself.");
  }

  try {
    sendOk(res, {
      success: true,
      ...mergeAccounts(db, { householdId, sourceId, targetId })
    });
  } catch (err) {
    if (err.status === 404) return sendNotFound(res, err.message);
    console.error('Merge failed:', err);
    sendServerError(res, err);
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  try {
    const txnCount = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE account_id = ? AND household_id = ?')
      .get(id, householdId).c;

    if (txnCount > 0) {
      return sendBadRequest(
        res,
        `Can't delete an account with ${txnCount} transactions. Merge or archive instead.`
      );
    }

    const result = db.prepare('DELETE FROM accounts WHERE id = ? AND household_id = ?').run(id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Account not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete failed:', err);
    sendServerError(res, err);
  }
});

export default router;
