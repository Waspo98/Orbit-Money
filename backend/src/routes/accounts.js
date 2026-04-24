import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { dollarsToCents, moneyFieldsToDollars } from '../lib/money.js';
import { parseId, parseInteger, readIdParam } from '../lib/routeParams.js';

const router = express.Router();

const VALID_TYPES = ['checking', 'savings', 'credit', 'investment', 'loan', 'mortgage', 'cash', 'other'];
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const ACCOUNT_MONEY_FIELDS = ['current_balance', 'estimated_value'];

function serializeAccount(row) {
  return moneyFieldsToDollars(row, ACCOUNT_MONEY_FIELDS);
}

function isValidDateOnly(value) {
  if (typeof value !== 'string' || !DATE_RE.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function previousMonthEnd(value) {
  const [year, month] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return date.toISOString().slice(0, 10);
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
          LEFT JOIN transactions t ON t.account_id = a.id
         ${includeArchived ? '' : 'WHERE a.is_archived = 0'}
         GROUP BY a.id
         ORDER BY a.is_archived ASC, a.sort_order ASC, a.name ASC
      `
      )
      .all()
      .map(serializeAccount);

    sendOk(res, { items });
  } catch (err) {
    console.error('List accounts failed:', err);
    sendServerError(res, err);
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
    return sendBadRequest(res, 'orderedIds must be a non-empty array.');
  }

  // Validate each id is a number.
  const ids = orderedIds.map(parseInteger).filter((id) => id !== null);
  if (ids.length !== orderedIds.length) {
    return sendBadRequest(res, 'orderedIds must contain only numeric ids.');
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
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  const existing = db.prepare('SELECT id FROM accounts WHERE id = ?').get(id);
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
  values.push(id);

  try {
    db.prepare(`UPDATE accounts SET ${sets.join(', ')} WHERE id = ?`).run(...values);
    const updated = db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
    sendOk(res, { success: true, account: serializeAccount(updated) });
  } catch (err) {
    console.error('Update account failed:', err);
    sendBadRequest(res, err.message);
  }
});

router.post('/:id/archive', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 1, updated_at = datetime('now')
          WHERE id = ?`
      )
      .run(id);
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
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;
  try {
    const result = db
      .prepare(
        `UPDATE accounts SET is_archived = 0, updated_at = datetime('now')
          WHERE id = ?`
      )
      .run(id);
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

  const account = db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
  if (!account) {
    return sendNotFound(res, 'Account not found.');
  }

  try {
    const run = db.transaction(() => {
      const insertRecord = db.prepare(
        `
        INSERT INTO account_balance_records (account_id, record_date, balance)
        VALUES (?, ?, ?)
        ON CONFLICT(account_id, record_date) DO UPDATE SET
          balance = excluded.balance,
          updated_at = datetime('now')
      `
      );

      const priorRecords = db
        .prepare(
          `
          SELECT COUNT(*) AS count
            FROM account_balance_records
           WHERE account_id = ? AND record_date < ?
        `
        )
        .get(id, recordDate).count;

      if (priorRecords === 0 && previousBalanceCents !== null && previousBalanceCents !== balance) {
        const baselineDate = previousMonthEnd(recordDate);
        db.prepare(
          `
          INSERT OR IGNORE INTO account_balance_records (account_id, record_date, balance)
          VALUES (?, ?, ?)
        `
        ).run(id, baselineDate, previousBalanceCents);
      }

      insertRecord.run(id, recordDate, balance);

      const latestRecord = db
        .prepare(
          `
          SELECT record_date, balance
            FROM account_balance_records
           WHERE account_id = ?
           ORDER BY record_date DESC, id DESC
           LIMIT 1
        `
        )
        .get(id);

      if (latestRecord?.record_date === recordDate) {
        db.prepare(
          `
          UPDATE accounts
             SET current_balance = ?, is_manual = 1, updated_at = datetime('now')
           WHERE id = ?
        `
        ).run(balance, id);
      }

      return db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
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
  const sourceId = readIdParam(req, res, 'id', 'account');
  if (sourceId === null) return;
  const targetId = parseId(req.body?.targetId);

  if (targetId === null) {
    return sendBadRequest(res, 'Invalid account ids.');
  }
  if (sourceId === targetId) {
    return sendBadRequest(res, "Can't merge an account into itself.");
  }

  const source = db.prepare('SELECT * FROM accounts WHERE id = ?').get(sourceId);
  const target = db.prepare('SELECT * FROM accounts WHERE id = ?').get(targetId);
  if (!source) return sendNotFound(res, 'Source account not found.');
  if (!target) return sendNotFound(res, 'Target account not found.');

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

    sendOk(res, {
      success: true,
      transactionsMoved,
      mergedSourceName: source.name,
      intoTargetName: target.name
    });
  } catch (err) {
    console.error('Merge failed:', err);
    sendServerError(res, err);
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'account');
  if (id === null) return;

  try {
    const txnCount = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE account_id = ?')
      .get(id).c;

    if (txnCount > 0) {
      return sendBadRequest(
        res,
        `Can't delete an account with ${txnCount} transactions. Merge or archive instead.`
      );
    }

    const result = db.prepare('DELETE FROM accounts WHERE id = ?').run(id);
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
