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

/**
 * Fetch all enabled rules from the DB, parse JSON, filter out malformed ones.
 * Returns [{ id, conditions, actions }] ready for matching.
 */
export function loadRules(db) {
  const rows = db
    .prepare(
      `SELECT id, conditions, actions
         FROM rules
        WHERE enabled = 1
        ORDER BY priority DESC, id ASC`
    )
    .all();

  return rows
    .map((r) => ({
      id: r.id,
      conditions: safeJsonParse(r.conditions, null),
      actions: safeJsonParse(r.actions, null)
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

  let actual;
  switch (field) {
    case 'merchant':
      actual = transaction.original_merchant ?? '';
      break;
    case 'original_description':
      actual = transaction.original_description ?? '';
      break;
    case 'amount':
      // Rule amounts are evaluated in absolute-value terms so users can
      // reason about "amount > 15" as "$15-or-more" regardless of sign.
      // Stored amounts are negative for expenses, positive for income;
      // comparing the signed value would make `> 15` never match an
      // expense.
      actual = Math.abs(Number(transaction.amount) || 0);
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

  if (transaction.edited_merchant_source === 'user') {
    edits.edited_merchant = transaction.edited_merchant;
    edits.edited_merchant_source = 'user';
  }
  if (transaction.edited_category_id_source === 'user') {
    edits.edited_category_id = transaction.edited_category_id;
    edits.edited_category_id_source = 'user';
  }
  if (transaction.edited_is_transfer_source === 'user') {
    edits.edited_is_transfer = transaction.edited_is_transfer;
    edits.edited_is_transfer_source = 'user';
  }
  if (transaction.edited_is_ignored_source === 'user') {
    edits.edited_is_ignored = transaction.edited_is_ignored;
    edits.edited_is_ignored_source = 'user';
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
export function countMatches(db, conditions) {
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
         FROM transactions`
    )
    .all();

  let n = 0;
  for (const r of rows) {
    if (evalConditions(r, conditions)) n++;
  }
  return n;
}

/**
 * Re-run all enabled rules against every transaction in the DB.
 * Returns { processed, updated }.
 */
export function reapplyRulesToAllTransactions(db) {
  const rules = loadRules(db);

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
         FROM transactions`
    )
    .all();

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
        r.id
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
export function reapplyRulesToTransaction(db, id) {
  const rules = loadRules(db);
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
        WHERE id = ?`
    )
    .get(id);
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
      WHERE id = ?`
  ).run(
    edits.edited_merchant,
    edits.edited_merchant_source,
    edits.edited_category_id,
    edits.edited_category_id_source,
    edits.edited_is_transfer,
    edits.edited_is_transfer_source,
    edits.edited_is_ignored,
    edits.edited_is_ignored_source,
    id
  );
  return { updated: 1 };
}

/**
 * Clear every edit whose source is `rule:{ruleId}` and re-run remaining rules
 * to fill any gaps. Used when a rule is deleted.
 */
export function revertEditsForRule(db, ruleId) {
  const source = `rule:${ruleId}`;

  const run = db.transaction(() => {
    db.prepare(
      `UPDATE transactions
          SET edited_merchant = NULL, edited_merchant_source = NULL
        WHERE edited_merchant_source = ?`
    ).run(source);
    db.prepare(
      `UPDATE transactions
          SET edited_category_id = NULL, edited_category_id_source = NULL
        WHERE edited_category_id_source = ?`
    ).run(source);
    db.prepare(
      `UPDATE transactions
          SET edited_is_transfer = NULL, edited_is_transfer_source = NULL
        WHERE edited_is_transfer_source = ?`
    ).run(source);
    db.prepare(
      `UPDATE transactions
          SET edited_is_ignored = NULL, edited_is_ignored_source = NULL
        WHERE edited_is_ignored_source = ?`
    ).run(source);
  });

  run();
  return reapplyRulesToAllTransactions(db);
}

/**
 * Build an insert-ready transaction object with rule-driven edits pre-populated.
 * Used by SimpleFIN sync and any future manual-entry path.
 *
 * `draft` must have: account_id, date, amount, original_merchant,
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
