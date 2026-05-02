// =============================================================================
// rules.js — CRUD for rules + apply/preview endpoints (v12)
// =============================================================================
// Changes from v11:
//
//   - Match counting is now condition-only (against original values). Counts
//     are stable — a rule that matches 12 rows today will still report 12
//     tomorrow, regardless of whether another rule overrides the same field.
//
//   - DELETE /:id now clears every edit whose source was that rule, then
//     re-runs the remaining rules. Deleting a rule reliably reverts its
//     effects.
//
//   - Create/Update/Toggle still trigger a background reapply via
//     setImmediate so the response isn't blocked.
// =============================================================================

import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  reapplyRulesToAllTransactions,
  countMatches,
  revertEditsForRule,
  previewRuleImpact
} from '../services/ruleMatcher.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { parseInteger, readIdParam } from '../lib/routeParams.js';

const router = express.Router();

function safeJsonParse(s, fallback) {
  try {
    return JSON.parse(s);
  } catch {
    return fallback;
  }
}

function parseCategoryActionValue(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function validateActions(householdId, actions) {
  const categoryIds = [];
  for (const action of actions) {
    if (action?.type !== 'categorize') continue;
    const categoryId = parseCategoryActionValue(action.value);
    if (categoryId === null) {
      return 'Categorize actions must choose a valid category.';
    }
    categoryIds.push(categoryId);
  }

  const uniqueIds = Array.from(new Set(categoryIds));
  if (uniqueIds.length === 0) return null;

  const placeholders = uniqueIds.map(() => '?').join(',');
  const rows = db
    .prepare(`SELECT id FROM categories WHERE household_id = ? AND id IN (${placeholders})`)
    .all(householdId, ...uniqueIds);
  if (rows.length !== uniqueIds.length) {
    return 'Categorize actions can only use categories from this household.';
  }
  return null;
}

/**
 * GET /api/rules?withCounts=1
 */
router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const withCounts = req.query.withCounts === '1';

  try {
    const rows = db
      .prepare(
        `SELECT id, name, conditions, actions, priority, enabled, created_at, updated_at
           FROM rules
          WHERE household_id = ?
          ORDER BY priority DESC, id ASC`
      )
      .all(householdId);

    const items = rows.map((r) => ({
      id: r.id,
      name: r.name,
      conditions: safeJsonParse(r.conditions, []),
      actions: safeJsonParse(r.actions, []),
      priority: r.priority,
      enabled: !!r.enabled,
      created_at: r.created_at,
      updated_at: r.updated_at
    }));

    if (withCounts) {
      for (const item of items) {
        item.match_count = countMatches(db, item.conditions, householdId);
      }
    }

    sendOk(res, { items, total: items.length });
  } catch (err) {
    console.error('List rules failed:', err);
    sendServerError(res, err);
  }
});

/**
 * GET /api/rules/:id/match-count
 */
router.get('/:id/match-count', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'rule');
  if (id === null) return;

  try {
    const rule = db.prepare('SELECT conditions FROM rules WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!rule) return sendNotFound(res, 'Rule not found.');
    const count = countMatches(db, safeJsonParse(rule.conditions, []), householdId);
    sendOk(res, { count });
  } catch (err) {
    console.error('Match count failed:', err);
    sendServerError(res, err);
  }
});

/**
 * GET /api/rules/:id
 */
router.get('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'rule');
  if (id === null) return;

  try {
    const row = db
      .prepare(
        `SELECT id, name, conditions, actions, priority, enabled, created_at, updated_at
           FROM rules
          WHERE id = ? AND household_id = ?`
      )
      .get(id, householdId);
    if (!row) return sendNotFound(res, 'Rule not found.');

    sendOk(res, {
      rule: {
        id: row.id,
        name: row.name,
        conditions: safeJsonParse(row.conditions, []),
        actions: safeJsonParse(row.actions, []),
        priority: row.priority,
        enabled: !!row.enabled,
        created_at: row.created_at,
        updated_at: row.updated_at
      }
    });
  } catch (err) {
    console.error('Fetch rule failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/rules/preview
 * Body: { ruleId?, conditions, actions?, enabled? }
 * Returns the condition match count plus actionable preview buckets.
 */
router.post('/preview', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    sendOk(res, previewRuleImpact(db, req.body || {}, householdId));
  } catch (err) {
    console.error('Rule preview failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/rules
 */
router.post('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const { name, conditions, actions, priority = 0, enabled = true } = req.body || {};

  if (!name || typeof name !== 'string' || !name.trim()) {
    return sendBadRequest(res, 'name is required.');
  }
  if (!Array.isArray(conditions) || conditions.length === 0) {
    return sendBadRequest(res, 'At least one condition is required.');
  }
  if (!Array.isArray(actions) || actions.length === 0) {
    return sendBadRequest(res, 'At least one action is required.');
  }
  const actionError = validateActions(householdId, actions);
  if (actionError) {
    return sendBadRequest(res, actionError);
  }

  try {
    const result = db
      .prepare(
        `INSERT INTO rules (household_id, name, conditions, actions, priority, enabled)
         VALUES (?, ?, ?, ?, ?, ?)`
      )
      .run(
        householdId,
        name.trim(),
        JSON.stringify(conditions),
        JSON.stringify(actions),
        parseInteger(priority) || 0,
        enabled ? 1 : 0
      );

    // Sync reapply — by the time we return, transactions reflect the new
    // rule. Client code that reloads after `await api.post` gets the
    // post-application state without a race condition. For larger datasets
    // (~8K transactions), this adds a second or two to the save but makes
    // the UI predictable.
    try {
      reapplyRulesToAllTransactions(db, householdId);
    } catch (err) {
      console.error('Auto-reapply after rule create failed:', err);
    }

    sendOk(res, { success: true, id: result.lastInsertRowid });
  } catch (err) {
    console.error('Create rule failed:', err);
    sendServerError(res, err);
  }
});

/**
 * PUT /api/rules/:id
 */
router.put('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'rule');
  if (id === null) return;

  const body = req.body || {};
  const sets = [];
  const values = [];

  if (body.name !== undefined) {
    if (typeof body.name !== 'string' || !body.name.trim()) {
      return sendBadRequest(res, 'name cannot be empty.');
    }
    sets.push('name = ?');
    values.push(body.name.trim());
  }
  if (body.conditions !== undefined) {
    if (!Array.isArray(body.conditions) || body.conditions.length === 0) {
      return sendBadRequest(res, 'At least one condition is required.');
    }
    sets.push('conditions = ?');
    values.push(JSON.stringify(body.conditions));
  }
  if (body.actions !== undefined) {
    if (!Array.isArray(body.actions) || body.actions.length === 0) {
      return sendBadRequest(res, 'At least one action is required.');
    }
    const actionError = validateActions(householdId, body.actions);
    if (actionError) {
      return sendBadRequest(res, actionError);
    }
    sets.push('actions = ?');
    values.push(JSON.stringify(body.actions));
  }
  if (body.priority !== undefined) {
    sets.push('priority = ?');
    values.push(parseInteger(body.priority) || 0);
  }
  if (body.enabled !== undefined) {
    sets.push('enabled = ?');
    values.push(body.enabled ? 1 : 0);
  }

  if (sets.length === 0) {
    return sendBadRequest(res, 'No fields to update.');
  }

  sets.push("updated_at = datetime('now')");
  values.push(id, householdId);

  try {
    const existing = db.prepare('SELECT id FROM rules WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!existing) return sendNotFound(res, 'Rule not found.');
    db.prepare(`UPDATE rules SET ${sets.join(', ')} WHERE id = ? AND household_id = ?`).run(...values);

    // Synchronous reapply — see POST note above.
    try {
      reapplyRulesToAllTransactions(db, householdId);
    } catch (err) {
      console.error('Auto-reapply after rule update failed:', err);
    }

    sendOk(res, { success: true });
  } catch (err) {
    console.error('Update rule failed:', err);
    sendServerError(res, err);
  }
});

/**
 * PATCH /api/rules/:id/enabled
 */
router.patch('/:id/enabled', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'rule');
  if (id === null) return;
  const enabled = req.body?.enabled ? 1 : 0;

  try {
    const result = db
      .prepare(`UPDATE rules SET enabled = ?, updated_at = datetime('now') WHERE id = ? AND household_id = ?`)
      .run(enabled, id, householdId);
    if (result.changes === 0) {
      return sendNotFound(res, 'Rule not found.');
    }

    // Synchronous reapply so the response reflects settled state.
    try {
      reapplyRulesToAllTransactions(db, householdId);
    } catch (err) {
      console.error('Auto-reapply after toggle failed:', err);
    }

    sendOk(res, { success: true, enabled: !!enabled });
  } catch (err) {
    console.error('Toggle rule failed:', err);
    sendServerError(res, err);
  }
});

/**
 * DELETE /api/rules/all
 * Wipes every rule in the database, then reverts all rule-owned edits so
 * transactions fall back to originals. User edits are preserved.
 */
router.delete('/all', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const run = db.transaction(() => {
      const result = db.prepare('DELETE FROM rules WHERE household_id = ?').run(householdId);

      // Clear every rule-sourced edit across all four editable fields.
      db.prepare(
        `UPDATE transactions
            SET edited_merchant = NULL, edited_merchant_source = NULL
          WHERE edited_merchant_source LIKE 'rule:%'
            AND household_id = ?`
      ).run(householdId);
      db.prepare(
        `UPDATE transactions
            SET edited_category_id = NULL, edited_category_id_source = NULL
          WHERE edited_category_id_source LIKE 'rule:%'
            AND household_id = ?`
      ).run(householdId);
      db.prepare(
        `UPDATE transactions
            SET edited_is_transfer = NULL, edited_is_transfer_source = NULL
          WHERE edited_is_transfer_source LIKE 'rule:%'
            AND household_id = ?`
      ).run(householdId);
      db.prepare(
        `UPDATE transactions
            SET edited_is_ignored = NULL, edited_is_ignored_source = NULL
          WHERE edited_is_ignored_source LIKE 'rule:%'
            AND household_id = ?`
      ).run(householdId);

      return result.changes;
    });

    const deleted = run();
    sendOk(res, { success: true, deleted });
  } catch (err) {
    console.error('Wipe all rules failed:', err);
    sendServerError(res, err);
  }
});

/**
 * DELETE /api/rules/:id
 * Deletes the rule AND reverts every edit whose source was `rule:{id}`,
 * then re-runs remaining rules to re-populate any gaps.
 */
router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'rule');
  if (id === null) return;
  try {
    const run = db.transaction(() => {
      const result = db.prepare('DELETE FROM rules WHERE id = ? AND household_id = ?').run(id, householdId);
      return result.changes;
    });

    const changes = run();
    if (changes === 0) {
      return sendNotFound(res, 'Rule not found.');
    }

    // Revert this rule's edits, then re-apply remaining rules. This is
    // synchronous so the response reflects settled state — the client can
    // refetch confidently right after.
    revertEditsForRule(db, id, householdId);

    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete rule failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/rules/reapply-all
 */
router.post('/reapply-all', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const start = Date.now();
    const result = reapplyRulesToAllTransactions(db, householdId);
    const elapsedMs = Date.now() - start;
    sendOk(res, { success: true, elapsedMs, ...result });
  } catch (err) {
    console.error('Reapply all rules failed:', err);
    sendServerError(res, err);
  }
});

export default router;
