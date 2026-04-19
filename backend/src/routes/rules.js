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
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  reapplyRulesToAllTransactions,
  countMatches,
  revertEditsForRule
} from '../services/ruleMatcher.js';

const router = express.Router();

function safeJsonParse(s, fallback) {
  try {
    return JSON.parse(s);
  } catch {
    return fallback;
  }
}

/**
 * GET /api/rules?withCounts=1
 */
router.get('/', requireAuth, (req, res) => {
  const withCounts = req.query.withCounts === '1';

  try {
    const rows = db
      .prepare(
        `SELECT id, name, conditions, actions, priority, enabled, created_at, updated_at
           FROM rules
           ORDER BY priority DESC, id ASC`
      )
      .all();

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
        item.match_count = countMatches(db, item.conditions);
      }
    }

    res.json({ items, total: items.length });
  } catch (err) {
    console.error('List rules failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * GET /api/rules/:id/match-count
 */
router.get('/:id/match-count', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid rule id.' });
  }
  const rule = db.prepare('SELECT conditions FROM rules WHERE id = ?').get(id);
  if (!rule) return res.status(404).json({ error: 'Rule not found.' });

  try {
    const count = countMatches(db, safeJsonParse(rule.conditions, []));
    res.json({ count });
  } catch (err) {
    console.error('Match count failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/rules/preview
 * Body: { conditions, actions? }
 * Returns { count } — how many existing transactions satisfy the conditions.
 * `actions` is accepted for backward compat but ignored (match count is
 * purely condition-driven in v12).
 */
router.post('/preview', requireAuth, (req, res) => {
  const { conditions } = req.body || {};
  try {
    const count = countMatches(db, conditions);
    res.json({ count });
  } catch (err) {
    console.error('Rule preview failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/rules
 */
router.post('/', requireAuth, (req, res) => {
  const { name, conditions, actions, priority = 0, enabled = true } = req.body || {};

  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error: 'name is required.' });
  }
  if (!Array.isArray(conditions) || conditions.length === 0) {
    return res.status(400).json({ error: 'At least one condition is required.' });
  }
  if (!Array.isArray(actions) || actions.length === 0) {
    return res.status(400).json({ error: 'At least one action is required.' });
  }

  try {
    const result = db
      .prepare(
        `INSERT INTO rules (name, conditions, actions, priority, enabled)
         VALUES (?, ?, ?, ?, ?)`
      )
      .run(
        name.trim(),
        JSON.stringify(conditions),
        JSON.stringify(actions),
        parseInt(priority, 10) || 0,
        enabled ? 1 : 0
      );

    // Sync reapply — by the time we return, transactions reflect the new
    // rule. Client code that reloads after `await api.post` gets the
    // post-application state without a race condition. For Neal's dataset
    // (~8K transactions), this adds a second or two to the save but makes
    // the UI predictable.
    try {
      reapplyRulesToAllTransactions(db);
    } catch (err) {
      console.error('Auto-reapply after rule create failed:', err);
    }

    res.json({ success: true, id: result.lastInsertRowid });
  } catch (err) {
    console.error('Create rule failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * PUT /api/rules/:id
 */
router.put('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid rule id.' });
  }

  const existing = db.prepare('SELECT id FROM rules WHERE id = ?').get(id);
  if (!existing) return res.status(404).json({ error: 'Rule not found.' });

  const body = req.body || {};
  const sets = [];
  const values = [];

  if (body.name !== undefined) {
    if (typeof body.name !== 'string' || !body.name.trim()) {
      return res.status(400).json({ error: 'name cannot be empty.' });
    }
    sets.push('name = ?');
    values.push(body.name.trim());
  }
  if (body.conditions !== undefined) {
    if (!Array.isArray(body.conditions) || body.conditions.length === 0) {
      return res.status(400).json({ error: 'At least one condition is required.' });
    }
    sets.push('conditions = ?');
    values.push(JSON.stringify(body.conditions));
  }
  if (body.actions !== undefined) {
    if (!Array.isArray(body.actions) || body.actions.length === 0) {
      return res.status(400).json({ error: 'At least one action is required.' });
    }
    sets.push('actions = ?');
    values.push(JSON.stringify(body.actions));
  }
  if (body.priority !== undefined) {
    sets.push('priority = ?');
    values.push(parseInt(body.priority, 10) || 0);
  }
  if (body.enabled !== undefined) {
    sets.push('enabled = ?');
    values.push(body.enabled ? 1 : 0);
  }

  if (sets.length === 0) {
    return res.status(400).json({ error: 'No fields to update.' });
  }

  sets.push("updated_at = datetime('now')");
  values.push(id);

  try {
    db.prepare(`UPDATE rules SET ${sets.join(', ')} WHERE id = ?`).run(...values);

    // Synchronous reapply — see POST note above.
    try {
      reapplyRulesToAllTransactions(db);
    } catch (err) {
      console.error('Auto-reapply after rule update failed:', err);
    }

    res.json({ success: true });
  } catch (err) {
    console.error('Update rule failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * PATCH /api/rules/:id/enabled
 */
router.patch('/:id/enabled', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid rule id.' });
  }
  const enabled = req.body?.enabled ? 1 : 0;

  try {
    const result = db
      .prepare(`UPDATE rules SET enabled = ?, updated_at = datetime('now') WHERE id = ?`)
      .run(enabled, id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Rule not found.' });
    }

    // Synchronous reapply so the response reflects settled state.
    try {
      reapplyRulesToAllTransactions(db);
    } catch (err) {
      console.error('Auto-reapply after toggle failed:', err);
    }

    res.json({ success: true, enabled: !!enabled });
  } catch (err) {
    console.error('Toggle rule failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * DELETE /api/rules/all
 * Wipes every rule in the database, then reverts all rule-owned edits so
 * transactions fall back to originals. User edits are preserved.
 */
router.delete('/all', requireAuth, (req, res) => {
  try {
    const run = db.transaction(() => {
      const result = db.prepare('DELETE FROM rules').run();

      // Clear every rule-sourced edit across all four editable fields.
      db.prepare(
        `UPDATE transactions
            SET edited_merchant = NULL, edited_merchant_source = NULL
          WHERE edited_merchant_source LIKE 'rule:%'`
      ).run();
      db.prepare(
        `UPDATE transactions
            SET edited_category_id = NULL, edited_category_id_source = NULL
          WHERE edited_category_id_source LIKE 'rule:%'`
      ).run();
      db.prepare(
        `UPDATE transactions
            SET edited_is_transfer = NULL, edited_is_transfer_source = NULL
          WHERE edited_is_transfer_source LIKE 'rule:%'`
      ).run();
      db.prepare(
        `UPDATE transactions
            SET edited_is_ignored = NULL, edited_is_ignored_source = NULL
          WHERE edited_is_ignored_source LIKE 'rule:%'`
      ).run();

      return result.changes;
    });

    const deleted = run();
    res.json({ success: true, deleted });
  } catch (err) {
    console.error('Wipe all rules failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * DELETE /api/rules/:id
 * Deletes the rule AND reverts every edit whose source was `rule:{id}`,
 * then re-runs remaining rules to re-populate any gaps.
 */
router.delete('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid rule id.' });
  }
  try {
    const run = db.transaction(() => {
      const result = db.prepare('DELETE FROM rules WHERE id = ?').run(id);
      return result.changes;
    });

    const changes = run();
    if (changes === 0) {
      return res.status(404).json({ error: 'Rule not found.' });
    }

    // Revert this rule's edits, then re-apply remaining rules. This is
    // synchronous so the response reflects settled state — the client can
    // refetch confidently right after.
    revertEditsForRule(db, id);

    res.json({ success: true });
  } catch (err) {
    console.error('Delete rule failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/rules/reapply-all
 */
router.post('/reapply-all', requireAuth, (req, res) => {
  try {
    const start = Date.now();
    const result = reapplyRulesToAllTransactions(db);
    const elapsedMs = Date.now() - start;
    res.json({ success: true, elapsedMs, ...result });
  } catch (err) {
    console.error('Reapply all rules failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
