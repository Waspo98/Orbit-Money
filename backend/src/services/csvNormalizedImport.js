import { reapplyRulesToAllTransactions } from './ruleMatcher.js';
import { dollarsToCents } from '../lib/money.js';
import {
  computeContentHash,
  readCategories,
  recordBatchItem
} from './csvImportHelpers.js';

const VALID_CSV_SOURCES = new Set(['rocket_money', 'mint', 'monarch', 'ynab', 'csv']);
const VALID_ACCOUNT_TYPES = new Set(['checking', 'savings', 'credit', 'investment', 'loan', 'mortgage', 'other']);

export function importNormalizedCsvTransactions(db, householdId = 1, payload = {}) {
  const source = VALID_CSV_SOURCES.has(payload.source) ? payload.source : 'csv';
  const filename = typeof payload.fileName === 'string' ? payload.fileName.trim().slice(0, 200) || null : null;
  const rawAccounts = Array.isArray(payload.accounts) ? payload.accounts : [];
  const rawTransactions = Array.isArray(payload.transactions) ? payload.transactions : [];
  const rawRules = Array.isArray(payload.rules) ? payload.rules : [];
  const fallbackAccountId = Number.isInteger(payload.fallbackAccountId) && payload.fallbackAccountId > 0
    ? payload.fallbackAccountId
    : null;

  if (rawTransactions.length === 0) {
    throw new Error('No valid transactions to import.');
  }

  const { categoryIdByName, validCategoryIds, uncategorizedId } = readCategories(db, householdId);
  const findAccountByKey = db.prepare(`
    SELECT id FROM accounts
     WHERE household_id = ?
       AND lower(name) = lower(?)
       AND COALESCE(lower(institution), '') = COALESCE(lower(?), '')
     LIMIT 1
  `);
  const findAccountById = db.prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ?');
  const insertAccount = db.prepare(`
    INSERT INTO accounts (household_id, name, type, institution, account_number_last4, is_manual, current_balance, sort_order)
    VALUES (?, ?, ?, ?, ?, 1, 0, COALESCE((SELECT MAX(sort_order) + 10 FROM accounts WHERE household_id = ?), 0))
  `);
  const findRule = db.prepare('SELECT id FROM rules WHERE household_id = ? AND name = ?');
  const insertRule = db.prepare(`
    INSERT INTO rules (household_id, name, conditions, actions, priority, enabled)
    VALUES (?, ?, ?, ?, 0, 1)
  `);
  const insertTxn = db.prepare(`
    INSERT OR IGNORE INTO transactions
      (account_id, date, amount, original_merchant, original_description,
       category_id, notes, is_ignored, source, external_id, household_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'csv_import', ?, ?)
  `);

  const run = db.transaction(() => {
    const batchResult = db
      .prepare(
        `INSERT INTO import_batches (household_id, source, filename, status, preview_json, summary_json)
         VALUES (?, ?, ?, 'applied', '{}', '{}')`
      )
      .run(householdId, source, filename);
    const importBatchId = batchResult.lastInsertRowid;

    let accountsCreated = 0;
    let rulesCreated = 0;
    let inserted = 0;
    let skipped = 0;
    const accountIdByKey = new Map();

    for (const acct of rawAccounts) {
      if (!acct || typeof acct !== 'object') continue;
      const key = String(acct.key || '').trim();
      const name = String(acct.name || '').trim() || 'Imported Account';
      const institution = acct.institution ? String(acct.institution).trim() : null;
      const last4 = acct.last4 ? String(acct.last4).trim().slice(-4) : null;
      const type = VALID_ACCOUNT_TYPES.has(String(acct.type || '').toLowerCase())
        ? String(acct.type).toLowerCase()
        : 'checking';

      const existing = findAccountByKey.get(householdId, name, institution);
      if (existing) {
        if (key) accountIdByKey.set(key, existing.id);
        continue;
      }

      const res = insertAccount.run(householdId, name, type, institution, last4, householdId);
      if (key) accountIdByKey.set(key, res.lastInsertRowid);
      accountsCreated += 1;
      recordBatchItem(db, householdId, importBatchId, 'accounts', res.lastInsertRowid);
    }

    let resolvedFallbackAccountId = fallbackAccountId && findAccountById.get(fallbackAccountId, householdId)
      ? fallbackAccountId
      : null;

    const ensureDefaultAccount = () => {
      if (resolvedFallbackAccountId) return resolvedFallbackAccountId;
      const firstExisting = db.prepare('SELECT id FROM accounts WHERE household_id = ? ORDER BY sort_order ASC, id ASC LIMIT 1').get(householdId);
      if (firstExisting) {
        resolvedFallbackAccountId = firstExisting.id;
        return resolvedFallbackAccountId;
      }
      const created = insertAccount.run(householdId, 'Imported Checking', 'checking', null, null, householdId);
      resolvedFallbackAccountId = created.lastInsertRowid;
      accountsCreated += 1;
      recordBatchItem(db, householdId, importBatchId, 'accounts', resolvedFallbackAccountId);
      return resolvedFallbackAccountId;
    };

    for (const rule of rawRules) {
      if (!rule || typeof rule !== 'object') continue;
      const originalName = String(rule.originalName || '').trim();
      const customName = String(rule.customName || '').trim();
      if (!originalName || !customName) continue;
      const friendlyName = String(rule.friendlyRuleName || `Auto: ${originalName} -> ${customName}`).trim();
      if (findRule.get(householdId, friendlyName)) continue;
      const res = insertRule.run(
        householdId,
        friendlyName,
        JSON.stringify([{ field: 'merchant', operator: 'contains', value: originalName }]),
        JSON.stringify([{ type: 'rename', value: customName }])
      );
      rulesCreated += 1;
      recordBatchItem(db, householdId, importBatchId, 'rules', res.lastInsertRowid);
    }

    for (const txn of rawTransactions) {
      if (!txn || typeof txn !== 'object') {
        skipped += 1;
        continue;
      }
      const date = String(txn.date || '').trim();
      const hasExplicitAmount =
        txn.amount !== null &&
        txn.amount !== undefined &&
        String(txn.amount).trim() !== '';
      const parsedAmount = hasExplicitAmount ? Number(txn.amount) : Number.NaN;
      const amountCents = Number.isInteger(txn.amountCents)
        ? txn.amountCents
        : Number.isFinite(parsedAmount)
          ? dollarsToCents(parsedAmount)
          : null;
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || amountCents === null) {
        skipped += 1;
        continue;
      }

      const acctKey = String(txn.accountKey || '').trim();
      const accountId = (acctKey && accountIdByKey.get(acctKey)) || ensureDefaultAccount();
      const merchant = String(txn.merchant || txn.description || 'Imported Transaction').trim().slice(0, 200);
      const description = String(txn.description || merchant).trim().slice(0, 400);
      const requestedCategoryId = Number.isInteger(txn.categoryId) && txn.categoryId > 0
        ? txn.categoryId
        : null;
      const categoryId = (requestedCategoryId && validCategoryIds.has(requestedCategoryId))
        ? requestedCategoryId
        : (txn.categoryName ? categoryIdByName.get(String(txn.categoryName).trim().toLowerCase()) : null) || uncategorizedId;
      const externalId = String(txn.externalId || '').trim() || computeContentHash({
        date,
        amountCents,
        originalDescription: description,
        accountKey: acctKey || String(accountId)
      });
      const notes = txn.notes ? String(txn.notes).trim().slice(0, 500) : null;

      const res = insertTxn.run(
        accountId,
        date,
        amountCents,
        merchant,
        description,
        categoryId,
        notes,
        txn.isIgnored ? 1 : 0,
        externalId,
        householdId
      );
      if (res.changes === 1) {
        inserted += 1;
        recordBatchItem(db, householdId, importBatchId, 'transactions', res.lastInsertRowid);
      } else {
        skipped += 1;
      }
    }

    const summary = {
      source,
      inserted,
      skipped,
      accountsCreated,
      rulesCreated,
      totalRows: rawTransactions.length,
      parseWarnings: 0
    };
    db.prepare(
      `UPDATE import_batches
          SET summary_json = ?,
              applied_at = datetime('now'),
              updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(JSON.stringify(summary), importBatchId, householdId);

    return { batchId: importBatchId, ...summary };
  });

  const result = run();
  let reapplyResult = { processed: 0, updated: 0 };
  try {
    reapplyResult = reapplyRulesToAllTransactions(db, householdId);
  } catch (err) {
    console.error('Post-import reapply failed (non-fatal):', err);
  }
  return { ...result, reapplyUpdated: reapplyResult.updated };
}
