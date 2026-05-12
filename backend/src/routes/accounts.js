import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { isValidDateOnly, parseDateParts } from '../lib/localDate.js';
import { dollarsToCents, moneyFieldsToDollars } from '../lib/money.js';
import { parseId, parseInteger, readIdParam } from '../lib/routeParams.js';
import { mergeAccounts } from '../services/accountMerge.js';

const router = express.Router();

const VALID_TYPES = ['checking', 'savings', 'credit', 'investment', 'loan', 'mortgage', 'cash', 'other'];
const ACCOUNT_MONEY_FIELDS = ['current_balance', 'estimated_value'];
const CREDIT_CARD_PROFILE_MONEY_FIELDS = ['annual_fee', 'credit_limit'];

function serializeAccount(row) {
  return moneyFieldsToDollars(row, ACCOUNT_MONEY_FIELDS);
}

function safeJsonParse(value, fallback) {
  if (typeof value !== 'string') return fallback;
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

function serializeCreditCardProfile(row) {
  if (!row) return null;
  const profile = moneyFieldsToDollars(row, CREDIT_CARD_PROFILE_MONEY_FIELDS);
  profile.authorized_users = safeJsonParse(profile.authorized_users_json, []);
  profile.reward_categories = safeJsonParse(profile.reward_categories_json, []);
  profile.benefits = safeJsonParse(profile.benefits_json, []);
  delete profile.authorized_users_json;
  delete profile.reward_categories_json;
  delete profile.benefits_json;
  return profile;
}

function attachCreditCardProfiles(householdId, accounts) {
  if (accounts.length === 0) return accounts;
  const profiles = db
    .prepare('SELECT * FROM credit_card_profiles WHERE household_id = ?')
    .all(householdId)
    .map(serializeCreditCardProfile);
  const profileByAccountId = new Map(
    profiles.map((profile) => [profile.account_id, profile])
  );
  return accounts.map((account) => ({
    ...account,
    credit_card_profile: profileByAccountId.get(account.id) || null
  }));
}

function previousMonthEnd(value) {
  const { year, month } = parseDateParts(value);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return date.toISOString().slice(0, 10);
}

function normalizeOptionalText(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text || null;
}

function normalizeOptionalTextLength(value, maxLength = 500) {
  const text = normalizeOptionalText(value);
  return text ? text.slice(0, maxLength) : null;
}

function normalizeOptionalDateOnly(value) {
  const text = normalizeOptionalText(value);
  if (!text) return null;
  if (!isValidDateOnly(text)) {
    throw new Error('annual_fee_post_date must be a valid YYYY-MM-DD date.');
  }
  return text;
}

function normalizeOptionalMoney(value, fieldName) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${fieldName} must be a non-negative number.`);
  }
  return dollarsToCents(number);
}

function normalizeTextList(value, maxItems = 12, maxLength = 80) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .slice(0, maxItems)
    .map((item) => item.slice(0, maxLength));
}

function normalizeRewardCategories(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === 'string') {
        const label = item.trim();
        return label ? { label: label.slice(0, 80) } : null;
      }
      if (!item || typeof item !== 'object') return null;
      const label = String(item.label || item.category || item.name || '').trim();
      if (!label) return null;
      const rate = item.rate === null || item.rate === undefined || item.rate === ''
        ? null
        : Number(item.rate);
      return {
        label: label.slice(0, 80),
        category: normalizeOptionalTextLength(item.category, 80),
        rate: Number.isFinite(rate) ? rate : null,
        type: normalizeOptionalTextLength(item.type, 40),
        notes: normalizeOptionalTextLength(item.notes || item.note, 180)
      };
    })
    .filter(Boolean)
    .slice(0, 12);
}

function normalizeBenefits(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === 'string') {
        const name = item.trim();
        return name ? { name: name.slice(0, 120) } : null;
      }
      if (!item || typeof item !== 'object') return null;
      const name = String(item.name || item.label || '').trim();
      if (!name) return null;
      return {
        name: name.slice(0, 120),
        value: item.value === null || item.value === undefined || item.value === ''
          ? null
          : Number(item.value),
        description: normalizeOptionalTextLength(item.description || item.notes, 300),
        category: normalizeOptionalTextLength(item.category, 80)
      };
    })
    .filter(Boolean)
    .slice(0, 20);
}

function stringifyProfileJson(value, fieldName) {
  const text = JSON.stringify(value);
  if (Buffer.byteLength(text, 'utf8') > 20000) {
    throw new Error(`${fieldName} is too large.`);
  }
  return text;
}

function normalizeCreditCardProfileBody(body = {}) {
  const authorizedUsers = normalizeTextList(body.authorized_users);
  const rewardCategories = normalizeRewardCategories(body.reward_categories);
  const benefits = normalizeBenefits(body.benefits);
  const accountName = typeof body.account_name === 'string' ? body.account_name.trim() : null;

  if (!accountName) {
    throw new Error('account_name cannot be empty.');
  }

  return {
    account_name: accountName,
    account_institution: normalizeOptionalTextLength(body.account_institution, 180),
    card_name: normalizeOptionalTextLength(body.card_name, 220),
    issuer_name: normalizeOptionalTextLength(body.issuer_name, 180),
    network: normalizeOptionalTextLength(body.network, 40),
    image_url: normalizeOptionalTextLength(body.image_url, 1000),
    annual_fee: normalizeOptionalMoney(body.annual_fee, 'annual_fee'),
    annual_fee_post_date: normalizeOptionalDateOnly(body.annual_fee_post_date),
    credit_limit: normalizeOptionalMoney(body.credit_limit, 'credit_limit'),
    authorized_users_json: stringifyProfileJson(authorizedUsers, 'authorized_users'),
    reward_categories_json: stringifyProfileJson(rewardCategories, 'reward_categories'),
    benefits_json: stringifyProfileJson(benefits, 'benefits'),
    notes: normalizeOptionalTextLength(body.notes, 2000)
  };
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
      .map(serializeAccount);

    sendOk(res, { items: attachCreditCardProfiles(householdId, items) });
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
    ? 0
    : Number(body.balance);
  if (!Number.isFinite(balanceDollars)) {
    return sendBadRequest(res, 'balance must be a valid number.');
  }

  const recordDate = String(body.recordDate || '').trim();
  if (recordDate && !isValidDateOnly(recordDate)) {
    return sendBadRequest(res, 'recordDate must be a valid YYYY-MM-DD date.');
  }

  const balance = dollarsToCents(balanceDollars);

  try {
    const run = db.transaction(() => {
      const result = db
        .prepare(
          `INSERT INTO accounts (
             household_id, name, type, institution, account_number_last4,
             is_manual, current_balance, sort_order
           ) VALUES (
             ?, ?, ?, ?, ?, 1, ?,
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

  const existing = db.prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId);
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

  const sets = [];
  const values = [];

  const allowed = [
    ['name', 'name'],
    ['type', 'type'],
    ['institution', 'institution'],
    ['account_number_last4', 'account_number_last4'],
    ['current_balance', 'current_balance'],
    ['estimated_value', 'estimated_value'],
    ['simplefin_account_id', 'simplefin_account_id']
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
      if (col === 'estimated_value') {
        if (v === '' || v === null) {
          v = null;
        } else {
          v = Number(v);
          if (!Number.isFinite(v) || v < 0) {
            return sendBadRequest(res, 'estimated_value must be a non-negative number.');
          }
          v = dollarsToCents(v);
        }
      }
      values.push(v);
    }
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
    db.transaction(() => {
      db.prepare(
        `UPDATE accounts
            SET name = ?,
                institution = ?,
                updated_at = datetime('now')
          WHERE id = ?
            AND household_id = ?`
      ).run(profile.account_name, profile.account_institution, id, householdId);

      db.prepare(
        `INSERT INTO credit_card_profiles (
           household_id, account_id, card_name, issuer_name, network, image_url,
           annual_fee, annual_fee_post_date, credit_limit, authorized_users_json,
           reward_categories_json, benefits_json, notes
         ) VALUES (
           ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
         )
         ON CONFLICT(household_id, account_id) DO UPDATE SET
           card_name = excluded.card_name,
           issuer_name = excluded.issuer_name,
           network = excluded.network,
           image_url = excluded.image_url,
           annual_fee = excluded.annual_fee,
           annual_fee_post_date = excluded.annual_fee_post_date,
           credit_limit = excluded.credit_limit,
           authorized_users_json = excluded.authorized_users_json,
           reward_categories_json = excluded.reward_categories_json,
           benefits_json = excluded.benefits_json,
           notes = excluded.notes,
           updated_at = datetime('now')`
      ).run(
        householdId,
        id,
        profile.card_name,
        profile.issuer_name,
        profile.network,
        profile.image_url,
        profile.annual_fee,
        profile.annual_fee_post_date,
        profile.credit_limit,
        profile.authorized_users_json,
        profile.reward_categories_json,
        profile.benefits_json,
        profile.notes
      );
    })();

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
    const run = db.transaction(() => {
      const insertRecord = db.prepare(
        `
        INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
        VALUES (?, ?, ?, ?, 'manual')
        ON CONFLICT(household_id, account_id, record_date) DO UPDATE SET
          balance = excluded.balance,
          source = 'manual',
          updated_at = datetime('now')
      `
      );

      const priorRecords = db
        .prepare(
          `
          SELECT COUNT(*) AS count
            FROM account_balance_records
           WHERE household_id = ? AND account_id = ? AND record_date < ?
        `
        )
        .get(householdId, id, recordDate).count;

      if (priorRecords === 0 && previousBalanceCents !== null && previousBalanceCents !== balance) {
        const baselineDate = previousMonthEnd(recordDate);
        db.prepare(
          `
          INSERT OR IGNORE INTO account_balance_records (household_id, account_id, record_date, balance)
          VALUES (?, ?, ?, ?)
        `
        ).run(householdId, id, baselineDate, previousBalanceCents);
      }

      insertRecord.run(householdId, id, recordDate, balance);

      const latestRecord = db
        .prepare(
          `
          SELECT record_date, balance
            FROM account_balance_records
           WHERE household_id = ? AND account_id = ?
           ORDER BY record_date DESC, id DESC
           LIMIT 1
        `
        )
        .get(householdId, id);

      if (latestRecord?.record_date === recordDate) {
        db.prepare(
          `
          UPDATE accounts
             SET current_balance = ?, is_manual = 1, updated_at = datetime('now')
           WHERE id = ? AND household_id = ?
        `
        ).run(balance, id, householdId);
      }

      return db.prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?').get(id, householdId);
    });

    const updated = run();
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
