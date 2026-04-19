import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

const router = express.Router();

const VALID_TYPES = ['checking', 'savings', 'credit', 'investment', 'loan', 'mortgage', 'cash', 'other'];

/**
 * GET /api/accounts?includeArchived=1
 *
 * Returns accounts ordered by sort_order (user-controlled via drag-and-drop).
 * Archived accounts go to the bottom (is_archived ASC first). Within the same
 * archived-state, sort_order controls the order.
 */
router.get('/', requireAuth, (req, res) => {
  const includeArchived = req.query.includeArchived === '1';
  try {
    const items = db
      .prepare(
        `
        SELECT a.id, a.name, a.type, a.institution, a.account_number_last4,
               a.current_balance, a.estimated_value,
               a.is_manual, a.is_archived, a.sort_order,
               a.simplefin_account_id, a.created_at, a.updated_at,
               COUNT(t.id) AS transaction_count
          FROM accounts a
          LEFT JOIN transactions t ON t.account_id = a.id
         ${includeArchived ? '' : 'WHERE a.is_archived = 0'}
         GROUP BY a.id
         ORDER BY a.is_archived ASC, a.sort_order ASC, a.name ASC
      `
      )
      .all();

    res.json({ items });
  } catch (err) {
    console.error('List accounts failed:', err);
    res.status(500).json({ error: err.message });
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
  const orderedIds = Array.isArray(req.body?.orderedIds) ? req.body.orderedIds : null;
  if (!orderedIds || orderedIds.length === 0) {
    return res.status(400).json({ error: 'orderedIds must be a non-empty array.' });
  }

  // Validate each id is a number.
  const ids = orderedIds.map((id) => parseInt(id, 10)).filter(Number.isFinite);
  if (ids.length !== orderedIds.length) {
    return res.status(400).json({ error: 'orderedIds must contain only numeric ids.' });
  }

  try {
    const update = db.prepare(
      `UPDATE accounts SET sort_order = ?, updated_at = datetime('now') WHERE id = ?`
    );

    const run = db.transaction(() => {
      ids.forEach((id, index) => {
        update.run(index * 10, id);
      });
    });

    run();
    res.json({ success: true, reordered: ids.length });
  } catch (err) {
    console.error('Reorder failed:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * PUT /api/accounts/:id
 */
router.put('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid account id.' });
  }

  const existing = db.prepare('SELECT id FROM accounts WHERE id = ?').get(id);
  if (!existing) {
    return res.status(404).json({ error: 'Account not found.' });
  }

  const body = req.body || {};

  if (body.type !== undefined && !VALID_TYPES.includes(body.type)) {
    return res
      .status(400)
      .json({ error: `type must be one of: ${VALID_TYPES.join(', ')}` });
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
      if (col === 'estimated_value') {
        if (v === '' || v === null) {
          v = null;
        } else {
          v = Number(v);
          if (!Number.isFinite(v) || v < 0) {
            return res.status(400).json({ error: 'estimated_value must be a positive number.' });
          }
        }
      }
      values.push(v);
    }
  }

  if (sets.length === 0) {
    return res.status(400).json({ error: 'No fields to update.' });
  }

  sets.push("updated_at = datetime('now')");
  values.push(id);

  try {
    db.prepare(`UPDATE accounts SET ${sets.join(', ')} WHERE id = ?`).run(...values);
    const updated = db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
    res.json({ success: true, account: updated });
  } catch (err) {
    console.error('Update account failed:', err);
    res.status(400).json({ error: err.message });
  }
});

router.post('/:id/archive', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid account id.' });
  }
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 1, updated_at = datetime('now')
          WHERE id = ?`
      )
      .run(id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Account not found.' });
    }
    res.json({ success: true });
  } catch (err) {
    console.error('Archive failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.post('/:id/unarchive', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid account id.' });
  }
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 0, updated_at = datetime('now')
          WHERE id = ?`
      )
      .run(id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Account not found.' });
    }
    res.json({ success: true });
  } catch (err) {
    console.error('Unarchive failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.post('/:id/merge', requireAuth, (req, res) => {
  const sourceId = parseInt(req.params.id, 10);
  const targetId = parseInt(req.body?.targetId, 10);

  if (!Number.isFinite(sourceId) || !Number.isFinite(targetId)) {
    return res.status(400).json({ error: 'Invalid account ids.' });
  }
  if (sourceId === targetId) {
    return res.status(400).json({ error: "Can't merge an account into itself." });
  }

  const source = db.prepare('SELECT * FROM accounts WHERE id = ?').get(sourceId);
  const target = db.prepare('SELECT * FROM accounts WHERE id = ?').get(targetId);
  if (!source) return res.status(404).json({ error: 'Source account not found.' });
  if (!target) return res.status(404).json({ error: 'Target account not found.' });

  try {
    const run = db.transaction(() => {
      const moved = db
        .prepare('UPDATE transactions SET account_id = ? WHERE account_id = ?')
        .run(targetId, sourceId).changes;
      if (source.estimated_value != null && target.estimated_value == null) {
        db.prepare(
          `UPDATE accounts
              SET estimated_value = ?, updated_at = datetime('now')
            WHERE id = ?`
        ).run(source.estimated_value, targetId);
      }
      db.prepare('DELETE FROM accounts WHERE id = ?').run(sourceId);
      return moved;
    });
    const transactionsMoved = run();

    res.json({
      success: true,
      transactionsMoved,
      mergedSourceName: source.name,
      intoTargetName: target.name
    });
  } catch (err) {
    console.error('Merge failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) {
    return res.status(400).json({ error: 'Invalid account id.' });
  }

  const txnCount = db
    .prepare('SELECT COUNT(*) AS c FROM transactions WHERE account_id = ?')
    .get(id).c;

  if (txnCount > 0) {
    return res.status(400).json({
      error: `Can't delete an account with ${txnCount} transactions. Merge or archive instead.`
    });
  }

  try {
    const result = db.prepare('DELETE FROM accounts WHERE id = ?').run(id);
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Account not found.' });
    }
    res.json({ success: true });
  } catch (err) {
    console.error('Delete failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
