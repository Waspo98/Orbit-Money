import { dollarsToCents } from '../lib/money.js';
import { parseInteger } from '../lib/routeParams.js';

const VALID_RESOLUTIONS = new Set([
  'sold_property',
  'sold_or_closed',
  'paid_off_keep_property',
  'refinanced_keep_property',
  'dismiss'
]);

function badRequest(message) {
  const err = new Error(message);
  err.status = 400;
  return err;
}

function notFound(message) {
  const err = new Error(message);
  err.status = 404;
  return err;
}

function normalizeOptionalText(value) {
  if (value === null || value === undefined) return null;
  const text = typeof value === 'string' ? value.trim() : '';
  return text || null;
}

/**
 * Validates and normalizes optional real-estate and closure columns on POST/PUT /api/accounts.
 */
export function parseRealEstateAccountFields(db, { householdId, accountId = null, effectiveType, body = {} }) {
  const parsed = {};

  if (body.estimated_value !== undefined) {
    const raw = body.estimated_value;
    if (raw === '' || raw === null) {
      parsed.estimated_value = null;
    } else {
      const num = Number(raw);
      if (!Number.isFinite(num) || num < 0) {
        throw badRequest('estimated_value must be a non-negative number.');
      }
      parsed.estimated_value = dollarsToCents(num);
    }
  }

  if (body.is_property !== undefined) {
    const flag = body.is_property === true || body.is_property === 1 || body.is_property === '1' ? 1 : 0;
    if (flag === 1 && !['other', 'mortgage'].includes(effectiveType)) {
      throw badRequest('Only other or mortgage accounts can be marked as a property.');
    }
    parsed.is_property = flag;
  }

  if (body.property_name !== undefined) {
    parsed.property_name = normalizeOptionalText(body.property_name);
  }

  if (body.linked_property_account_id !== undefined) {
    const rawLink = body.linked_property_account_id;
    if (rawLink === null || rawLink === '' || rawLink === 0) {
      parsed.linked_property_account_id = null;
    } else {
      const targetId = parseInteger(rawLink);
      if (targetId === null || targetId <= 0) {
        throw badRequest('linked_property_account_id must be a positive account id or null.');
      }
      if (accountId !== null && targetId === accountId) {
        throw badRequest('An account cannot be linked to itself.');
      }
      if (!['loan', 'mortgage'].includes(effectiveType)) {
        throw badRequest('Only loan or mortgage accounts can link to a property.');
      }
      const target = db
        .prepare(
          `SELECT id, type, is_property, linked_property_account_id
             FROM accounts
            WHERE id = ? AND household_id = ?`
        )
        .get(targetId, householdId);
      if (!target) {
        throw badRequest('Linked property account was not found.');
      }
      if (target.linked_property_account_id !== null) {
        throw badRequest('Cannot link to an account that is already linked to another property.');
      }
      if (target.is_property !== 1 && target.type !== 'mortgage') {
        throw badRequest('Target account must be a real estate property or mortgage.');
      }
      parsed.linked_property_account_id = targetId;
    }
  }

  if (body.closure_prompt_dismissed_at !== undefined) {
    parsed.closure_prompt_dismissed_at = normalizeOptionalText(body.closure_prompt_dismissed_at);
  }

  return parsed;
}

/**
 * Flags unarchived SimpleFIN-linked accounts that vanished from a clean SimpleFIN payload
 * while guarding against temporary per-institution sync errors.
 */
export function markMissingSimpleFinAccounts(
  db,
  { householdId, seenAccountIds, syncedInstitutions = [], sfErrors = [] }
) {
  const seen = seenAccountIds instanceof Set ? seenAccountIds : new Set(seenAccountIds || []);
  const syncedInstSet = new Set(
    Array.from(syncedInstitutions)
      .map((s) => (typeof s === 'string' ? s.trim().toLowerCase() : ''))
      .filter(Boolean)
  );
  const errorStrings = (Array.isArray(sfErrors) ? sfErrors : [])
    .map((e) => String(e || '').toLowerCase())
    .filter(Boolean);

  if (seen.size === 0 && errorStrings.length > 0) {
    return 0;
  }

  const activeLinked = db
    .prepare(
      `SELECT id, institution
         FROM accounts
        WHERE household_id = ?
          AND simplefin_account_id IS NOT NULL
          AND is_archived = 0`
    )
    .all(householdId);

  const markMissing = db.prepare(
    `UPDATE accounts
        SET simplefin_missing_since = COALESCE(simplefin_missing_since, datetime('now')),
            updated_at = datetime('now')
      WHERE id = ? AND household_id = ?`
  );

  let flagged = 0;
  for (const row of activeLinked) {
    if (seen.has(row.id)) continue;

    const instKey = typeof row.institution === 'string' ? row.institution.trim().toLowerCase() : '';
    const instErrored = instKey
      ? errorStrings.some((msg) => msg.includes(instKey))
      : errorStrings.length > 0;
    const instConfirmedHealthy = !instErrored && (errorStrings.length === 0 || syncedInstSet.has(instKey));

    if (instConfirmedHealthy) {
      const res = markMissing.run(row.id, householdId);
      flagged += res.changes;
    }
  }
  return flagged;
}

/**
 * Atomically resolves a SimpleFIN closed / paid-off account prompt.
 */
export function resolveAccountClosure(db, { householdId, accountId, resolution }) {
  if (!VALID_RESOLUTIONS.has(resolution)) {
    throw badRequest(`resolution must be one of: ${Array.from(VALID_RESOLUTIONS).join(', ')}`);
  }

  const existing = db
    .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
    .get(accountId, householdId);
  if (!existing) {
    throw notFound('Account not found.');
  }

  const tx = db.transaction(() => {
    if (resolution === 'dismiss') {
      db.prepare(
        `UPDATE accounts
            SET closure_prompt_dismissed_at = datetime('now'),
                updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(accountId, householdId);
      return { createdPropertyAccountId: null };
    }

    if (resolution === 'sold_property') {
      const anchorId = existing.linked_property_account_id || existing.id;
      db.prepare(
        `UPDATE accounts
            SET is_archived = 1,
                simplefin_missing_since = NULL,
                closure_prompt_dismissed_at = datetime('now'),
                updated_at = datetime('now')
          WHERE household_id = ?
            AND (id = ? OR id = ? OR linked_property_account_id = ?)`
      ).run(householdId, accountId, anchorId, anchorId);
      return { createdPropertyAccountId: null };
    }

    if (resolution === 'sold_or_closed') {
      db.prepare(
        `UPDATE accounts
            SET is_archived = 1,
                simplefin_missing_since = NULL,
                closure_prompt_dismissed_at = datetime('now'),
                updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(accountId, householdId);

      if (existing.is_property === 1 || (existing.type === 'mortgage' && !existing.linked_property_account_id)) {
        db.prepare(
          `UPDATE accounts
              SET is_archived = 1,
                  simplefin_missing_since = NULL,
                  closure_prompt_dismissed_at = datetime('now'),
                  updated_at = datetime('now')
            WHERE household_id = ? AND linked_property_account_id = ?`
        ).run(householdId, accountId);
      }
      return { createdPropertyAccountId: null };
    }

    // 'paid_off_keep_property' or 'refinanced_keep_property'
    if (existing.linked_property_account_id) {
      db.prepare(
        `UPDATE accounts
            SET is_archived = 1,
                simplefin_missing_since = NULL,
                closure_prompt_dismissed_at = datetime('now'),
                updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(accountId, householdId);
      return { createdPropertyAccountId: existing.linked_property_account_id };
    }

    const estValue = Number(existing.estimated_value) || 0;
    if (existing.type === 'mortgage' && estValue > 0) {
      const propTitle = normalizeOptionalText(existing.property_name) || existing.name;
      const insertRes = db
        .prepare(
          `INSERT INTO accounts (
             household_id, name, type, is_property, is_manual,
             current_balance, estimated_value, sort_order
           ) VALUES (?, ?, 'other', 1, 1, 0, ?, ?)`
        )
        .run(householdId, propTitle, estValue, existing.sort_order ?? 0);
      const newPropertyId = Number(insertRes.lastInsertRowid);

      db.prepare(
        `UPDATE accounts
            SET linked_property_account_id = ?, updated_at = datetime('now')
          WHERE household_id = ? AND linked_property_account_id = ?`
      ).run(newPropertyId, householdId, accountId);

      db.prepare(
        `UPDATE accounts
            SET is_archived = 1,
                estimated_value = NULL,
                linked_property_account_id = ?,
                simplefin_missing_since = NULL,
                closure_prompt_dismissed_at = datetime('now'),
                updated_at = datetime('now')
          WHERE id = ? AND household_id = ?`
      ).run(newPropertyId, accountId, householdId);

      return { createdPropertyAccountId: newPropertyId };
    }

    db.prepare(
      `UPDATE accounts
          SET closure_prompt_dismissed_at = datetime('now'),
              simplefin_missing_since = NULL,
              updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(accountId, householdId);
    return { createdPropertyAccountId: null };
  });

  const outcome = tx();
  const updatedAccount = db
    .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
    .get(accountId, householdId);
  const createdProperty = outcome.createdPropertyAccountId
    ? db
        .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
        .get(outcome.createdPropertyAccountId, householdId)
    : null;

  return { updatedAccount, createdProperty };
}
