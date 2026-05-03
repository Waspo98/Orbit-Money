// =============================================================================
// ruleMatcher.js — applies rules to transactions (v12 architecture)
// =============================================================================
// KEY INVARIANTS (new in v12):
//   1. Rule conditions evaluate against the ORIGINAL_* columns only — they
//      never see values produced by earlier rules. This makes match counts
//      stable and rule behavior independent of execution order.
//
//   2. Rule actions write to EDITED_* columns, never to the originals. The
//      original values captured at import time are preserved forever.
//
//   3. Each edit carries a source tag: 'user' (manual edit, sticky — rules
//      never override) or 'rule:{id}' (attributable, reverts cleanly on
//      rule delete).
//
//   4. Conflict resolution: rules run in priority DESC, id ASC order. The
//      FIRST rule (in that order) to target a given field wins — lower-
//      priority rules skip fields already claimed by higher-priority ones.
//
//   5. Display value = COALESCE(edited_X, original_X), computed at the API
//      layer. Callers see one "merchant"/"category_id"/etc., regardless of
//      whether it's a rule, a user edit, or the original bank value.
// =============================================================================

import { centsToDollars } from '../lib/money.js';

function safeJsonParse(s, fallback) {
  try {
    return JSON.parse(s);
  } catch {
    return fallback;
  }
}

function safeRegex(pattern) {
  try {
    return new RegExp(pattern, 'i');
  } catch {
    return null;
  }
}

function textCandidates(values) {
  const seen = new Set();
  const candidates = [];
  for (const value of values) {
    if (value === null || value === undefined) continue;
    const text = String(value);
    const key = text.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    candidates.push(text);
  }
  return candidates.length ? candidates : [''];
}

function matchesAnyTextCandidate(values, operator, value) {
  const candidates = textCandidates(values);
  const expected = String(value);
  const expectedLower = expected.toLowerCase();

  switch (operator) {
    case 'equals':
      return candidates.some((actual) => actual.toLowerCase() === expectedLower);

    case 'contains':
      return candidates.some((actual) => actual.toLowerCase().includes(expectedLower));

    case 'starts_with':
      return candidates.some((actual) => actual.toLowerCase().startsWith(expectedLower));

    case 'regex': {
      const re = safeRegex(value);
      return re ? candidates.some((actual) => re.test(actual)) : false;
    }

    case 'is':
      // eslint-disable-next-line eqeqeq
      return candidates.some((actual) => actual == value);

    case 'is_not':
      // eslint-disable-next-line eqeqeq
      return candidates.every((actual) => actual != value);

    default:
      return false;
  }
}

function parseCategoryActionValue(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function sanitizeActions(actions, validCategoryIds) {
  if (!Array.isArray(actions)) return null;
  const sanitized = [];
  for (const action of actions) {
    if (!action || typeof action !== 'object') continue;
    if (action.type === 'categorize') {
      const categoryId = parseCategoryActionValue(action.value);
      if (categoryId === null || !validCategoryIds.has(categoryId)) continue;
      sanitized.push({ ...action, value: categoryId });
      continue;
    }
    sanitized.push(action);
  }
  return sanitized;
}

/**
 * Fetch all enabled rules from the DB, parse JSON, filter out malformed ones.
 * Returns rules ready for matching. Extra metadata is harmless to callers that
 * only need id/conditions/actions.
 */
export function loadRules(db, householdId = 1) {
  const validCategoryIds = new Set(
    db
      .prepare('SELECT id FROM categories WHERE household_id = ?')
      .all(householdId)
      .map((row) => row.id)
  );
  const rows = db
    .prepare(
      `SELECT id, name, conditions, actions, priority
         FROM rules
        WHERE household_id = ?
          AND enabled = 1
        ORDER BY priority DESC, id ASC`
    )
    .all(householdId);

  return rows
    .map((r) => ({
      id: r.id,
      name: r.name,
      conditions: safeJsonParse(r.conditions, null),
      actions: sanitizeActions(safeJsonParse(r.actions, null), validCategoryIds),
      priority: r.priority
    }))
    .filter(
      (r) =>
        Array.isArray(r.conditions) &&
        r.conditions.length > 0 &&
        Array.isArray(r.actions) &&
        r.actions.length > 0
    );
}

/**
 * Evaluate a single condition against a transaction's ORIGINAL values.
 */
function matchesCondition(transaction, condition) {
  const { field, operator, value } = condition;

  if (field === 'merchant') {
    // SimpleFIN can provide a cleaned payee plus a fuller raw description.
    // Check both so merchant rules survive month-to-month payee wording shifts.
    return matchesAnyTextCandidate(
      [transaction.original_merchant, transaction.original_description],
      operator,
      value
    );
  }

  let actual;
  switch (field) {
    case 'original_description':
      actual = transaction.original_description ?? '';
      break;
    case 'amount':
      // Rule amounts are evaluated in absolute-value terms so users can
      // reason about "amount > 15" as "$15-or-more" regardless of sign.
      // Stored amounts are negative for expenses, positive for income;
      // comparing the signed value would make `> 15` never match an
      // expense.
      actual = Math.abs(centsToDollars(transaction.amount));
      break;
    case 'account_id':
      actual = transaction.account_id;
      break;
    case 'category_id':
      actual = transaction.original_category_id;
      break;
    default:
      return false;
  }

  switch (operator) {
    case 'equals':
      if (typeof actual === 'string' && typeof value === 'string') {
        return actual.toLowerCase() === value.toLowerCase();
      }
      // eslint-disable-next-line eqeqeq
      return actual == value;

    case 'contains':
      return String(actual).toLowerCase().includes(String(value).toLowerCase());

    case 'starts_with':
      return String(actual).toLowerCase().startsWith(String(value).toLowerCase());

    case 'regex': {
      const re = safeRegex(value);
      return re ? re.test(String(actual)) : false;
    }

    case 'greater_than':
      return Number(actual) > Number(value);

    case 'less_than':
      return Number(actual) < Number(value);

    case 'between':
      if (!Array.isArray(value) || value.length !== 2) return false;
      return Number(actual) >= Number(value[0]) && Number(actual) <= Number(value[1]);

    case 'is':
      // eslint-disable-next-line eqeqeq
      return actual == value;

    case 'is_not':
      // eslint-disable-next-line eqeqeq
      return actual != value;

    default:
      return false;
  }
}

export function evalConditions(transaction, conditions) {
  if (!Array.isArray(conditions) || conditions.length === 0) return false;
  return conditions.every((c) => matchesCondition(transaction, c));
}

/**
 * Given a transaction row (with original_* AND existing edited_* / source
 * columns loaded) and the rules list, compute what the edited_* fields
 * SHOULD be after reapplying.
 *
 * Returns { edits, changed } where `edits` is a plain object with the eight
 * edit columns, and `changed` is true iff any of them differ from the
 * transaction's currently-stored values.
 */
export function computeEdits(transaction, rules) {
  // Start from scratch, preserving only user-locked fields.
  const edits = {
    edited_merchant: null,
    edited_merchant_source: null,
    edited_category_id: null,
    edited_category_id_source: null,
    edited_is_transfer: null,
    edited_is_transfer_source: null,
    edited_is_ignored: null,
    edited_is_ignored_source: null
  };

  if (
    transaction.edited_merchant_source === 'user' ||
    transaction.edited_merchant_source?.startsWith('system:')
  ) {
    edits.edited_merchant = transaction.edited_merchant;
    edits.edited_merchant_source = transaction.edited_merchant_source;
  }
  if (
    transaction.edited_category_id_source === 'user' ||
    transaction.edited_category_id_source?.startsWith('system:')
  ) {
    edits.edited_category_id = transaction.edited_category_id;
    edits.edited_category_id_source = transaction.edited_category_id_source;
  }
  if (
    transaction.edited_is_transfer_source === 'user' ||
    transaction.edited_is_transfer_source?.startsWith('system:')
  ) {
    edits.edited_is_transfer = transaction.edited_is_transfer;
    edits.edited_is_transfer_source = transaction.edited_is_transfer_source;
  }
  if (
    transaction.edited_is_ignored_source === 'user' ||
    transaction.edited_is_ignored_source?.startsWith('system:')
  ) {
    edits.edited_is_ignored = transaction.edited_is_ignored;
    edits.edited_is_ignored_source = transaction.edited_is_ignored_source;
  }

  // Iterate rules in priority DESC, id ASC (order provided by loadRules).
  // First rule to claim a field wins — lower-priority rules skip it.
  for (const rule of rules) {
    if (!evalConditions(transaction, rule.conditions)) continue;
    const source = `rule:${rule.id}`;

    for (const action of rule.actions) {
      if (!action || typeof action !== 'object') continue;

      switch (action.type) {
        case 'rename': {
          if (edits.edited_merchant_source !== null) break; // already claimed
          if (typeof action.value !== 'string') break;
          const newVal = action.value;
          if (newVal === transaction.original_merchant) break; // no-op
          edits.edited_merchant = newVal;
          edits.edited_merchant_source = source;
          break;
        }

        case 'categorize': {
          if (edits.edited_category_id_source !== null) break;
          const catId =
            typeof action.value === 'number'
              ? action.value
              : parseInt(action.value, 10);
          if (!Number.isFinite(catId)) break;
          if (catId === transaction.original_category_id) break;
          edits.edited_category_id = catId;
          edits.edited_category_id_source = source;
          break;
        }

        case 'mark_transfer': {
          if (edits.edited_is_transfer_source !== null) break;
          if (transaction.original_is_transfer === 1) break;
          edits.edited_is_transfer = 1;
          edits.edited_is_transfer_source = source;
          break;
        }

        case 'mark_ignored': {
          if (edits.edited_is_ignored_source !== null) break;
          if (transaction.original_is_ignored === 1) break;
          edits.edited_is_ignored = 1;
          edits.edited_is_ignored_source = source;
          break;
        }

        default:
          break; // unknown action type
      }
    }
  }

  // Compare against the transaction's currently-stored edit state.
  const changed =
    edits.edited_merchant !== (transaction.edited_merchant ?? null) ||
    edits.edited_merchant_source !== (transaction.edited_merchant_source ?? null) ||
    edits.edited_category_id !== (transaction.edited_category_id ?? null) ||
    edits.edited_category_id_source !== (transaction.edited_category_id_source ?? null) ||
    edits.edited_is_transfer !== (transaction.edited_is_transfer ?? null) ||
    edits.edited_is_transfer_source !== (transaction.edited_is_transfer_source ?? null) ||
    edits.edited_is_ignored !== (transaction.edited_is_ignored ?? null) ||
    edits.edited_is_ignored_source !== (transaction.edited_is_ignored_source ?? null);

  return { edits, changed };
}

/**
 * Count how many transactions a given condition set would match.
 * Purely based on the original values — no side effects, no writes.
 */
export function countMatches(db, conditions, householdId = 1) {
  if (!Array.isArray(conditions) || conditions.length === 0) return 0;

  const rows = db
    .prepare(
      `SELECT account_id,
              amount,
              original_merchant,
              original_description,
              category_id AS original_category_id,
              is_transfer AS original_is_transfer,
              is_ignored  AS original_is_ignored
         FROM transactions
        WHERE household_id = ?`
    )
    .all(householdId);

  let n = 0;
  for (const r of rows) {
    if (evalConditions(r, conditions)) n++;
  }
  return n;
}

const PREVIEW_LIMIT = 25;
const PREVIEW_RULE_ID = '__preview__';
const EDIT_FIELDS = {
  merchant: {
    label: 'Merchant',
    editKey: 'edited_merchant',
    sourceKey: 'edited_merchant_source',
    originalKey: 'original_merchant'
  },
  category: {
    label: 'Category',
    editKey: 'edited_category_id',
    sourceKey: 'edited_category_id_source',
    originalKey: 'original_category_id'
  },
  transfer: {
    label: 'Transfer',
    editKey: 'edited_is_transfer',
    sourceKey: 'edited_is_transfer_source',
    originalKey: 'original_is_transfer'
  },
  ignored: {
    label: 'Ignored',
    editKey: 'edited_is_ignored',
    sourceKey: 'edited_is_ignored_source',
    originalKey: 'original_is_ignored'
  }
};

function ruleSortId(rule) {
  if (Number.isFinite(rule.sort_id)) return rule.sort_id;
  const id = Number(rule.id);
  return Number.isFinite(id) ? id : Number.MAX_SAFE_INTEGER;
}

function sortRulesForApplication(rules) {
  return [...rules].sort((a, b) => (
    (Number(b.priority) || 0) - (Number(a.priority) || 0) ||
    ruleSortId(a) - ruleSortId(b)
  ));
}

function ruleSourceId(source) {
  if (typeof source !== 'string' || !source.startsWith('rule:')) return null;
  const raw = source.slice('rule:'.length);
  const id = Number(raw);
  return Number.isFinite(id) ? id : raw;
}

function samePreviewValue(a, b) {
  if (a === null || a === undefined || a === '') return b === null || b === undefined || b === '';
  if (b === null || b === undefined || b === '') return false;
  return String(a) === String(b);
}

function currentDisplayValue(transaction, field) {
  const meta = EDIT_FIELDS[field];
  return transaction[meta.editKey] ?? transaction[meta.originalKey] ?? null;
}

function displayValueFromEdits(transaction, edits, field) {
  const meta = EDIT_FIELDS[field];
  return edits[meta.editKey] ?? transaction[meta.originalKey] ?? null;
}

function actionImpact(action, transaction) {
  if (!action || typeof action !== 'object') return null;

  switch (action.type) {
    case 'rename': {
      if (typeof action.value !== 'string') return null;
      if (action.value === transaction.original_merchant) return null;
      return {
        field: 'merchant',
        label: EDIT_FIELDS.merchant.label,
        to: action.value
      };
    }

    case 'categorize': {
      const categoryId =
        typeof action.value === 'number' ? action.value : parseInt(action.value, 10);
      if (!Number.isFinite(categoryId)) return null;
      if (categoryId === transaction.original_category_id) return null;
      return {
        field: 'category',
        label: EDIT_FIELDS.category.label,
        to: categoryId
      };
    }

    case 'mark_transfer':
      if (transaction.original_is_transfer === 1) return null;
      return {
        field: 'transfer',
        label: EDIT_FIELDS.transfer.label,
        to: 1
      };

    case 'mark_ignored':
      if (transaction.original_is_ignored === 1) return null;
      return {
        field: 'ignored',
        label: EDIT_FIELDS.ignored.label,
        to: 1
      };

    default:
      return null;
  }
}

function transactionPreviewRow(row) {
  return {
    ...row,
    amount: centsToDollars(row.amount),
    has_edits:
      row.edited_merchant_source !== null ||
      row.edited_category_id_source !== null ||
      row.edited_is_transfer_source !== null ||
      row.edited_is_ignored_source !== null
  };
}

function addAffectedItem(map, transaction) {
  const id = transaction.id;
  if (!map.has(id)) {
    map.set(id, {
      transaction: transactionPreviewRow(transaction),
      fields: [],
      rules: []
    });
  }
  return map.get(id);
}

function addPreviewItem(map, transaction, field) {
  const id = transaction.id;
  const existing = map.get(id);
  if (existing) {
    existing.fields.push(field);
    return;
  }

  map.set(id, {
    transaction: transactionPreviewRow(transaction),
    fields: [field]
  });
}

function addConflictItem(map, transaction, field, rule) {
  const id = transaction.id;
  const existing = map.get(id);
  const ruleSummary = { id: rule.id, name: rule.name || `Rule ${rule.id}` };
  const fieldSummary = {
    ...field,
    ruleId: ruleSummary.id,
    ruleName: ruleSummary.name
  };

  if (existing) {
    existing.fields.push(fieldSummary);
    existing.rules ||= [];
    if (!existing.rules.some((item) => item.id === ruleSummary.id)) {
      existing.rules.push(ruleSummary);
    }
    return;
  }

  map.set(id, {
    transaction: transactionPreviewRow(transaction),
    fields: [fieldSummary],
    rules: [ruleSummary]
  });
}

/**
 * Preview a draft rule without writing anything. The response exposes every
 * transaction affected by the draft, then annotates visible changes/conflicts.
 */
export function previewRuleImpact(db, draft, householdId = 1, options = {}) {
  const limit = parseInt(options.limit, 10) || PREVIEW_LIMIT;
  const conditions = Array.isArray(draft?.conditions) ? draft.conditions : [];
  const rawRuleId = draft?.ruleId;
  const parsedRuleId =
    rawRuleId === undefined || rawRuleId === null || rawRuleId === '' ? null : Number(rawRuleId);
  const ruleId = Number.isInteger(parsedRuleId) && parsedRuleId > 0 ? parsedRuleId : null;
  const enabled = draft?.enabled !== false;
  const existingRule = ruleId
    ? db
      .prepare('SELECT id, name, priority FROM rules WHERE id = ? AND household_id = ?')
      .get(ruleId, householdId)
    : null;
  const priority = Number.isFinite(Number(draft?.priority))
    ? Number(draft.priority)
    : existingRule?.priority ?? 0;
  const validCategoryIds = new Set(
    db
      .prepare('SELECT id FROM categories WHERE household_id = ?')
      .all(householdId)
      .map((row) => row.id)
  );
  const actions = sanitizeActions(Array.isArray(draft?.actions) ? draft.actions : [], validCategoryIds) || [];
  const proposedId = ruleId ?? PREVIEW_RULE_ID;
  const proposedSource = `rule:${proposedId}`;
  const proposedRule = {
    id: proposedId,
    name: existingRule?.name || 'This rule',
    conditions,
    actions,
    priority,
    sort_id: ruleId ?? Number.MAX_SAFE_INTEGER
  };

  const existingRules = loadRules(db, householdId).filter((rule) => rule.id !== ruleId);
  const rulesById = new Map(existingRules.map((rule) => [rule.id, rule]));
  const rulesWithDraft = enabled
    ? sortRulesForApplication([...existingRules, proposedRule])
    : existingRules;

  const rows = db
    .prepare(
      `SELECT id,
              account_id,
              date,
              amount,
              COALESCE(edited_merchant, original_merchant) AS merchant,
              COALESCE(edited_category_id, category_id) AS category_id,
              COALESCE(edited_is_transfer, is_transfer) AS is_transfer,
              COALESCE(edited_is_ignored, is_ignored) AS is_ignored,
              original_merchant,
              original_description,
              category_id AS original_category_id,
              is_transfer AS original_is_transfer,
              is_ignored AS original_is_ignored,
              edited_merchant,
              edited_merchant_source,
              edited_category_id,
              edited_category_id_source,
              edited_is_transfer,
              edited_is_transfer_source,
              edited_is_ignored,
              edited_is_ignored_source,
              notes,
              transfer_pair_id
         FROM transactions
        WHERE household_id = ?
        ORDER BY date DESC, id DESC`
    )
    .all(householdId);

  const willChange = new Map();
  const conflicts = new Map();
  const affected = new Map();
  let matchCount = 0;

  for (const transaction of rows) {
    const matchesDraft = evalConditions(transaction, conditions);
    if (matchesDraft) {
      matchCount++;
      addAffectedItem(affected, transaction);
    }

    const actionImpacts = new Map();
    if (matchesDraft && enabled && actions.length > 0) {
      for (const action of actions) {
        const impact = actionImpact(action, transaction);
        if (!impact || actionImpacts.has(impact.field)) continue;
        actionImpacts.set(impact.field, impact);
      }
    }

    const { edits } = computeEdits(transaction, rulesWithDraft);

    for (const [field, meta] of Object.entries(EDIT_FIELDS)) {
      const currentSource = transaction[meta.sourceKey] ?? null;
      const nextSource = edits[meta.sourceKey] ?? null;
      const currentDisplay = currentDisplayValue(transaction, field);
      const nextDisplay = displayValueFromEdits(transaction, edits, field);
      const displayChanged = !samePreviewValue(currentDisplay, nextDisplay);
      const ruleCurrentlyOwnsField = currentSource === proposedSource;
      const draftWillOwnField = nextSource === proposedSource;

      if (draftWillOwnField && displayChanged) {
        const previewField = {
          field,
          label: meta.label,
          from: currentDisplay,
          to: nextDisplay
        };
        addPreviewItem(willChange, transaction, previewField);
        addPreviewItem(affected, transaction, previewField);
        continue;
      }

      if (!ruleId || (!ruleCurrentlyOwnsField && !draftWillOwnField)) continue;

      const previewField = {
        field,
        label: meta.label,
        from: currentDisplay,
        to: nextDisplay,
        applied: !displayChanged && ruleCurrentlyOwnsField && draftWillOwnField
      };
      addPreviewItem(willChange, transaction, previewField);
      addPreviewItem(affected, transaction, previewField);
    }

    for (const impact of actionImpacts.values()) {
      const meta = EDIT_FIELDS[impact.field];
      const nextSource = edits[meta.sourceKey] ?? null;
      if (nextSource === proposedSource) continue;

      const winningRuleId = ruleSourceId(nextSource);
      if (winningRuleId === null || winningRuleId === proposedId) continue;
      const winningRule = rulesById.get(winningRuleId);
      if (!winningRule) continue;
      addConflictItem(conflicts, transaction, impact, winningRule);
      addConflictItem(affected, transaction, impact, winningRule);
    }
  }

  const willChangeItems = Array.from(willChange.values());
  const conflictItems = Array.from(conflicts.values());
  const affectedItems = Array.from(affected.values());

  return {
    count: matchCount,
    affectedCount: affectedItems.length,
    willChangeCount: willChangeItems.length,
    conflictCount: conflictItems.length,
    affected: affectedItems.slice(0, limit),
    willChange: willChangeItems.slice(0, limit),
    conflicts: conflictItems.slice(0, limit),
    limit
  };
}

/**
 * Re-run all enabled rules against every transaction in the DB.
 * Returns { processed, updated }.
 */
export function reapplyRulesToAllTransactions(db, householdId = 1) {
  const rules = loadRules(db, householdId);

  const rows = db
    .prepare(
      `SELECT id,
              account_id,
              amount,
              original_merchant,
              original_description,
              category_id        AS original_category_id,
              is_transfer        AS original_is_transfer,
              is_ignored         AS original_is_ignored,
              edited_merchant,
              edited_merchant_source,
              edited_category_id,
              edited_category_id_source,
              edited_is_transfer,
              edited_is_transfer_source,
              edited_is_ignored,
              edited_is_ignored_source
         FROM transactions
        WHERE household_id = ?`
    )
    .all(householdId);

  const updateStmt = db.prepare(`
    UPDATE transactions
       SET edited_merchant           = ?,
           edited_merchant_source    = ?,
           edited_category_id        = ?,
           edited_category_id_source = ?,
           edited_is_transfer        = ?,
           edited_is_transfer_source = ?,
           edited_is_ignored         = ?,
           edited_is_ignored_source  = ?,
           updated_at                = datetime('now')
     WHERE id = ?
       AND household_id = ?
  `);

  const run = db.transaction(() => {
    let updated = 0;
    for (const r of rows) {
      const { edits, changed } = computeEdits(r, rules);
      if (!changed) continue;
      updateStmt.run(
        edits.edited_merchant,
        edits.edited_merchant_source,
        edits.edited_category_id,
        edits.edited_category_id_source,
        edits.edited_is_transfer,
        edits.edited_is_transfer_source,
        edits.edited_is_ignored,
        edits.edited_is_ignored_source,
        r.id,
        householdId
      );
      updated++;
    }
    return { processed: rows.length, updated };
  });

  return run();
}

/**
 * Apply rules to a single transaction row by id. Used after a targeted PATCH
 * to re-evaluate just that one transaction.
 */
export function reapplyRulesToTransaction(db, id, householdId = 1) {
  const rules = loadRules(db, householdId);
  const r = db
    .prepare(
      `SELECT id,
              account_id,
              amount,
              original_merchant,
              original_description,
              category_id        AS original_category_id,
              is_transfer        AS original_is_transfer,
              is_ignored         AS original_is_ignored,
              edited_merchant,
              edited_merchant_source,
              edited_category_id,
              edited_category_id_source,
              edited_is_transfer,
              edited_is_transfer_source,
              edited_is_ignored,
              edited_is_ignored_source
         FROM transactions
        WHERE id = ?
          AND household_id = ?`
    )
    .get(id, householdId);
  if (!r) return { updated: 0 };

  const { edits, changed } = computeEdits(r, rules);
  if (!changed) return { updated: 0 };

  db.prepare(
    `UPDATE transactions
        SET edited_merchant           = ?,
            edited_merchant_source    = ?,
            edited_category_id        = ?,
            edited_category_id_source = ?,
            edited_is_transfer        = ?,
            edited_is_transfer_source = ?,
            edited_is_ignored         = ?,
            edited_is_ignored_source  = ?,
            updated_at                = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  ).run(
    edits.edited_merchant,
    edits.edited_merchant_source,
    edits.edited_category_id,
    edits.edited_category_id_source,
    edits.edited_is_transfer,
    edits.edited_is_transfer_source,
    edits.edited_is_ignored,
    edits.edited_is_ignored_source,
    id,
    householdId
  );
  return { updated: 1 };
}

/**
 * Clear every edit whose source is `rule:{ruleId}` and re-run remaining rules
 * to fill any gaps. Used when a rule is deleted.
 */
export function revertEditsForRule(db, ruleId, householdId = 1) {
  const source = `rule:${ruleId}`;

  const run = db.transaction(() => {
    db.prepare(
      `UPDATE transactions
          SET edited_merchant = NULL, edited_merchant_source = NULL
        WHERE edited_merchant_source = ?
          AND household_id = ?`
    ).run(source, householdId);
    db.prepare(
      `UPDATE transactions
          SET edited_category_id = NULL, edited_category_id_source = NULL
        WHERE edited_category_id_source = ?
          AND household_id = ?`
    ).run(source, householdId);
    db.prepare(
      `UPDATE transactions
          SET edited_is_transfer = NULL, edited_is_transfer_source = NULL
        WHERE edited_is_transfer_source = ?
          AND household_id = ?`
    ).run(source, householdId);
    db.prepare(
      `UPDATE transactions
          SET edited_is_ignored = NULL, edited_is_ignored_source = NULL
        WHERE edited_is_ignored_source = ?
          AND household_id = ?`
    ).run(source, householdId);
  });

  run();
  return reapplyRulesToAllTransactions(db, householdId);
}

/**
 * Build an insert-ready transaction object with rule-driven edits pre-populated.
 * Used by SimpleFIN sync and any future manual-entry path.
 *
 * `draft` must have: account_id, date, amount in cents, original_merchant,
 * original_description, original_category_id, original_is_transfer (0/1),
 * original_is_ignored (0/1).
 */
export function applyRulesToDraft(draft, rules) {
  const stub = {
    ...draft,
    edited_merchant: null,
    edited_merchant_source: null,
    edited_category_id: null,
    edited_category_id_source: null,
    edited_is_transfer: null,
    edited_is_transfer_source: null,
    edited_is_ignored: null,
    edited_is_ignored_source: null
  };
  const { edits } = computeEdits(stub, rules);
  return { ...draft, ...edits };
}
