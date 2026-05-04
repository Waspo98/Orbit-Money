import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendCreated,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { readIdParam } from '../lib/routeParams.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';

const router = express.Router();
const EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');
const HEX_COLOR_RE = /^#[0-9a-fA-F]{6}$/;

function normalizeName(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeIcon(value) {
  if (value === undefined) return undefined;
  if (value === null) return null;
  const trimmed = String(value).trim();
  return trimmed || null;
}

function normalizeColor(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function categoryRuleCount(categoryId, householdId) {
  const rules = db
    .prepare('SELECT conditions, actions FROM rules WHERE household_id = ?')
    .all(householdId);
  return rules.reduce((count, rule) => {
    let referencesCategory = false;
    try {
      const conditions = JSON.parse(rule.conditions || '[]');
      referencesCategory =
        referencesCategory ||
        conditions.some(
          (condition) =>
            condition?.field === 'category_id' &&
            Number(condition.value) === categoryId
        );
    } catch {
      // Ignore malformed historical rows; rule editing/reapply will surface them.
    }
    try {
      const actions = JSON.parse(rule.actions || '[]');
      referencesCategory =
        referencesCategory ||
        actions.some(
          (action) =>
            action?.type === 'categorize' &&
            Number(action.value) === categoryId
        );
    } catch {
      // Ignore malformed historical rows; rule editing/reapply will surface them.
    }
    return referencesCategory ? count + 1 : count;
  }, 0);
}

function serializeCategory(row, householdId) {
  if (!row) return null;
  return {
    ...row,
    is_transfer: !!row.is_transfer,
    is_income: !!row.is_income,
    mha_default_eligible: !!row.mha_default_eligible,
    mha_default_ignored: !!row.mha_default_ignored,
    transaction_count: Number(row.transaction_count || 0),
    budget_count: Number(row.budget_count || 0),
    rule_count: categoryRuleCount(Number(row.id), householdId)
  };
}

/**
 * GET /api/categories
 * Returns all categories ordered by sort_order. Same rationale as /accounts:
 * frontend caches this, uses it for lookup when rendering transactions.
 */
router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const rows = db
      .prepare(
        `
        SELECT c.id, c.name, c.color, c.icon, c.is_transfer, c.is_income,
               c.sort_order, c.created_at,
               c.mha_default_eligible, c.mha_default_ignored,
               COUNT(DISTINCT t.id) AS transaction_count,
               COUNT(DISTINCT b.id) AS budget_count
          FROM categories c
          LEFT JOIN transactions t
             ON ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} = c.id
           AND t.household_id = ?
          LEFT JOIN budgets b
            ON b.category_id = c.id
           AND b.household_id = ?
         WHERE c.household_id = ?
         GROUP BY c.id
         ORDER BY c.sort_order ASC, c.name ASC
      `
      )
      .all(householdId, householdId, householdId);
    sendOk(res, { items: rows.map((row) => serializeCategory(row, householdId)) });
  } catch (err) {
    console.error('List categories failed:', err);
    sendServerError(res, err);
  }
});

router.post('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const body = req.body || {};
  const name = normalizeName(body.name);
  const color = normalizeColor(body.color || '#888888');
  const icon = normalizeIcon(body.icon);
  const mhaDefaultEligible =
    typeof body.mha_default_eligible === 'boolean'
      ? (body.mha_default_eligible ? 1 : 0)
      : 0;

  if (!name) {
    return sendBadRequest(res, 'name cannot be empty.');
  }
  if (!HEX_COLOR_RE.test(color)) {
    return sendBadRequest(res, 'color must be a 6-digit hex value.');
  }
  if (icon && icon.length > 24) {
    return sendBadRequest(res, 'emoji must be 24 characters or fewer.');
  }

  try {
    const nextOrder =
      (db.prepare('SELECT COALESCE(MAX(sort_order), 0) AS max_order FROM categories WHERE household_id = ?').get(householdId)
        .max_order || 0) + 10;
    const result = db
      .prepare(
        `
        INSERT INTO categories (household_id, name, color, icon, sort_order, mha_default_eligible)
        VALUES (?, ?, ?, ?, ?, ?)
      `
      )
      .run(householdId, name, color, icon, nextOrder, mhaDefaultEligible);
    const row = db
      .prepare(
        `
        SELECT c.*, 0 AS transaction_count, 0 AS budget_count
         FROM categories c
         WHERE c.id = ? AND c.household_id = ?
      `
      )
      .get(result.lastInsertRowid, householdId);
    sendCreated(res, { success: true, category: serializeCategory(row, householdId) });
  } catch (err) {
    console.error('Create category failed:', err);
    sendBadRequest(res, err.message);
  }
});

router.put('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;

  const existing = db.prepare('SELECT id FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
  if (!existing) {
    return sendNotFound(res, 'Category not found.');
  }

  const body = req.body || {};
  const sets = [];
  const values = [];

  if (body.name !== undefined) {
    const name = normalizeName(body.name);
    if (!name) {
      return sendBadRequest(res, 'name cannot be empty.');
    }
    sets.push('name = ?');
    values.push(name);
  }

  if (body.color !== undefined) {
    const color = normalizeColor(body.color);
    if (!HEX_COLOR_RE.test(color)) {
      return sendBadRequest(res, 'color must be a 6-digit hex value.');
    }
    sets.push('color = ?');
    values.push(color);
  }

  if (body.icon !== undefined) {
    const icon = normalizeIcon(body.icon);
    if (icon && icon.length > 24) {
      return sendBadRequest(res, 'emoji must be 24 characters or fewer.');
    }
    sets.push('icon = ?');
    values.push(icon);
  }

  if (body.mha_default_eligible !== undefined) {
    if (typeof body.mha_default_eligible !== 'boolean') {
      return sendBadRequest(res, 'mha_default_eligible must be a boolean.');
    }
    const eligible = body.mha_default_eligible ? 1 : 0;
    sets.push('mha_default_eligible = ?');
    values.push(eligible);
    if (eligible) {
      sets.push('mha_default_ignored = 0');
    }
  }

  if (sets.length === 0) {
    return sendBadRequest(res, 'No fields to update.');
  }

  try {
    db.prepare(`UPDATE categories SET ${sets.join(', ')} WHERE id = ? AND household_id = ?`).run(
      ...values,
      id,
      householdId
    );
    const row = db
      .prepare(
        `
        SELECT c.*,
               COUNT(DISTINCT t.id) AS transaction_count,
               COUNT(DISTINCT b.id) AS budget_count
          FROM categories c
          LEFT JOIN transactions t
             ON ${EFFECTIVE_TRANSACTION_CATEGORY_ID_SQL} = c.id
           AND t.household_id = ?
          LEFT JOIN budgets b
            ON b.category_id = c.id
           AND b.household_id = ?
         WHERE c.id = ? AND c.household_id = ?
         GROUP BY c.id
      `
      )
      .get(householdId, householdId, id, householdId);
    sendOk(res, { success: true, category: serializeCategory(row, householdId) });
  } catch (err) {
    console.error('Update category failed:', err);
    sendBadRequest(res, err.message);
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'category');
  if (id === null) return;

  try {
    const category = db.prepare('SELECT id, name FROM categories WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!category) {
      return sendNotFound(res, 'Category not found.');
    }

    const ruleCount = categoryRuleCount(id, householdId);
    if (ruleCount > 0) {
      return sendBadRequest(
        res,
        `Can't delete "${category.name}" while ${ruleCount} rule${
          ruleCount === 1 ? '' : 's'
        } still reference it. Update or delete those rules first.`
      );
    }

    const run = db.transaction(() => {
      const originalTransactions = db
        .prepare('UPDATE transactions SET category_id = NULL WHERE category_id = ? AND household_id = ?')
        .run(id, householdId).changes;
      const editedTransactions = db
        .prepare(
          `
          UPDATE transactions
             SET edited_category_id = NULL,
                 edited_category_id_source = NULL
           WHERE edited_category_id = ?
             AND household_id = ?
        `
        )
        .run(id, householdId).changes;
      const budgetsRemoved = db
        .prepare('SELECT COUNT(*) AS c FROM budgets WHERE category_id = ? AND household_id = ?')
        .get(id, householdId).c;
      const result = db.prepare('DELETE FROM categories WHERE id = ? AND household_id = ?').run(id, householdId);
      return {
        deleted: result.changes,
        originalTransactions,
        editedTransactions,
        budgetsRemoved
      };
    });

    const result = run();
    if (result.deleted === 0) {
      return sendNotFound(res, 'Category not found.');
    }
    sendOk(res, { success: true, ...result });
  } catch (err) {
    console.error('Delete category failed:', err);
    sendServerError(res, err);
  }
});

export default router;
