// Rocket Money CSV import pipeline with preview, commit, and undo support.

import Papa from 'papaparse';
import crypto from 'crypto';
import {
  reapplyRulesToAllTransactions,
  revertEditsForRule
} from './ruleMatcher.js';
import { dollarsToCents } from '../lib/money.js';

const REQUIRED_COLUMNS = [
  'Date',
  'Account Type',
  'Account Name',
  'Institution Name',
  'Name',
  'Amount',
  'Description',
  'Category'
];

function guessAccountType(rmType, accountName, institution) {
  if (rmType === 'Credit Card') return 'credit';
  if (rmType !== 'Cash') return 'other';

  const hay = `${accountName || ''} ${institution || ''}`.toLowerCase();
  if (/betterment|vanguard|fidelity|schwab|ira|401k|roth|brokerage|taxable/.test(hay)) {
    return 'investment';
  }
  if (/savings|reserve/.test(hay)) return 'savings';
  return 'checking';
}

function accountKey(row) {
  return `${row['Institution Name'] || ''}|${row['Account Name'] || ''}|${row['Account Number'] || ''}`;
}

function canMatchExistingAccount(acct) {
  return Boolean(acct.institution && acct.account_number_last4);
}

function computeContentHash({ date, amountCents, originalDescription, accountKey }) {
  const input = `${date}|${amountCents}|${originalDescription}|${accountKey}`;
  return crypto.createHash('sha256').update(input).digest('hex').slice(0, 16);
}

function parseRocketMoneyRows(csvBuffer) {
  const csvText = csvBuffer.toString('utf-8');
  const parsed = Papa.parse(csvText, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (h) => h.trim()
  });

  const rows = parsed.data;
  if (rows.length === 0) {
    throw new Error('CSV is empty or unparseable.');
  }

  const missing = REQUIRED_COLUMNS.filter((c) => !(c in rows[0]));
  if (missing.length > 0) {
    throw new Error(
      `This doesn't look like a Rocket Money export. Missing columns: ${missing.join(', ')}`
    );
  }

  return { rows, parseWarnings: parsed.errors.length };
}

function readCategories(db, householdId) {
  const categories = db
    .prepare('SELECT id, name FROM categories WHERE household_id = ?')
    .all(householdId);
  const categoryIdByName = new Map(categories.map((c) => [c.name.toLowerCase(), c.id]));
  const uncategorizedId = categoryIdByName.get('uncategorized') ?? categories[0]?.id ?? null;
  return { categoryIdByName, uncategorizedId };
}

export function buildRocketMoneyImportPreview(db, csvBuffer, householdId = 1, filename = null) {
  const { rows, parseWarnings } = parseRocketMoneyRows(csvBuffer);
  const { categoryIdByName, uncategorizedId } = readCategories(db, householdId);

  const accountsMap = new Map();
  for (const row of rows) {
    const key = accountKey(row);
    if (accountsMap.has(key)) {
      accountsMap.get(key).rowCount += 1;
      continue;
    }
    accountsMap.set(key, {
      key,
      rowCount: 1,
      name: (row['Account Name'] || '').trim() || 'Unknown Account',
      institution: (row['Institution Name'] || '').trim() || null,
      account_number_last4: (row['Account Number'] || '').trim() || null,
      type: guessAccountType(row['Account Type'], row['Account Name'], row['Institution Name'])
    });
  }

  const findAccount = db.prepare(`
    SELECT id, name FROM accounts
     WHERE household_id = ?
       AND institution IS ? AND account_number_last4 IS ?
  `);

  const accounts = Array.from(accountsMap.values()).map((acct) => {
    const existing = canMatchExistingAccount(acct)
      ? findAccount.get(householdId, acct.institution, acct.account_number_last4)
      : null;
    return {
      ...acct,
      existingAccountId: existing?.id || null,
      existingAccountName: existing?.name || null,
      willCreate: !existing
    };
  });
  const accountByKey = new Map(accounts.map((acct) => [acct.key, acct]));

  const rulesMap = new Map();
  for (const row of rows) {
    const customName = (row['Custom Name'] || '').trim();
    const name = (row['Name'] || '').trim();
    if (!customName || !name || customName === name) continue;
    rulesMap.set(name, customName);
  }

  const findRule = db.prepare('SELECT id FROM rules WHERE household_id = ? AND name = ?');
  const rules = Array.from(rulesMap.entries()).map(([ruleName, customName]) => {
    const friendlyName = `Auto: ${ruleName} -> ${customName}`;
    const existing = findRule.get(householdId, friendlyName);
    return {
      ruleName,
      customName,
      friendlyName,
      existingRuleId: existing?.id || null,
      willCreate: !existing
    };
  });

  const findExistingTxn = db.prepare(
    `SELECT id FROM transactions
      WHERE household_id = ?
        AND source = 'csv_import'
        AND external_id = ?`
  );

  const seenExternalIds = new Set();
  const transactions = [];
  let invalidRows = 0;
  let duplicateRows = 0;

  for (const row of rows) {
    const key = accountKey(row);
    const acct = accountByKey.get(key);
    const rmAmount = parseFloat(row['Amount']);
    const date = (row['Date'] || '').trim();
    if (!acct || !Number.isFinite(rmAmount) || !date) {
      invalidRows += 1;
      continue;
    }

    const amountCents = dollarsToCents(-rmAmount);
    const originalDescription = (row['Description'] || '').trim();
    const externalId = computeContentHash({
      date,
      amountCents,
      originalDescription,
      accountKey: key
    });
    const duplicateInFile = seenExternalIds.has(externalId);
    seenExternalIds.add(externalId);
    const duplicateInDatabase = Boolean(findExistingTxn.get(householdId, externalId));
    if (duplicateInFile || duplicateInDatabase) duplicateRows += 1;

    const categoryName = (row['Category'] || '').trim().toLowerCase();
    transactions.push({
      accountKey: key,
      date,
      amountCents,
      originalMerchant: (row['Name'] || '').trim() || 'Unknown',
      originalDescription,
      categoryId: categoryIdByName.get(categoryName) || uncategorizedId,
      notes: (row['Note'] || '').trim() || null,
      isIgnored: (row['Ignored From'] || '').trim() ? 1 : 0,
      externalId,
      duplicateInFile,
      duplicateInDatabase
    });
  }

  const summary = {
    source: 'rocket_money',
    filename,
    totalRows: rows.length,
    validRows: transactions.length,
    invalidRows,
    parseWarnings,
    duplicateRows,
    estimatedInserted: transactions.filter((txn) => !txn.duplicateInFile && !txn.duplicateInDatabase).length,
    accountsFound: accounts.length,
    accountsCreated: accounts.filter((acct) => acct.willCreate).length,
    accountsMatched: accounts.filter((acct) => !acct.willCreate).length,
    rulesFound: rules.length,
    rulesCreated: rules.filter((rule) => rule.willCreate).length
  };

  return {
    summary,
    preview: {
      source: 'rocket_money',
      filename,
      createdAt: new Date().toISOString(),
      accounts,
      rules,
      transactions
    }
  };
}

export function createRocketMoneyImportBatch(db, csvBuffer, householdId = 1, filename = null) {
  const { summary, preview } = buildRocketMoneyImportPreview(db, csvBuffer, householdId, filename);
  const result = db
    .prepare(
      `INSERT INTO import_batches (household_id, source, filename, status, preview_json, summary_json)
       VALUES (?, 'rocket_money', ?, 'pending', ?, ?)`
    )
    .run(householdId, filename, JSON.stringify(preview), JSON.stringify(summary));

  return {
    batchId: result.lastInsertRowid,
    ...summary,
    accounts: preview.accounts.map((acct) => ({
      name: acct.name,
      institution: acct.institution,
      account_number_last4: acct.account_number_last4,
      type: acct.type,
      rowCount: acct.rowCount,
      willCreate: acct.willCreate,
      existingAccountName: acct.existingAccountName
    })),
    rules: preview.rules.map((rule) => ({
      name: rule.friendlyName,
      willCreate: rule.willCreate
    }))
  };
}

function recordBatchItem(db, householdId, importBatchId, tableName, rowId) {
  db.prepare(
    `INSERT OR IGNORE INTO import_batch_items (household_id, import_batch_id, table_name, row_id)
     VALUES (?, ?, ?, ?)`
  ).run(householdId, importBatchId, tableName, rowId);
}

export function applyRocketMoneyImportBatch(db, importBatchId, householdId = 1) {
  const batch = db
    .prepare(
      `SELECT *
         FROM import_batches
        WHERE id = ?
          AND household_id = ?`
    )
    .get(importBatchId, householdId);
  if (!batch) throw new Error('Import preview was not found.');
  if (batch.status !== 'pending') {
    throw new Error('This import preview has already been used.');
  }

  const preview = JSON.parse(batch.preview_json || '{}');
  const accounts = Array.isArray(preview.accounts) ? preview.accounts : [];
  const rules = Array.isArray(preview.rules) ? preview.rules : [];
  const transactions = Array.isArray(preview.transactions) ? preview.transactions : [];

  const findAccount = db.prepare(`
    SELECT id FROM accounts
     WHERE household_id = ?
       AND institution IS ? AND account_number_last4 IS ?
  `);
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

  const deletedRuleIds = [];
  const run = db.transaction(() => {
    let accountsCreated = 0;
    let rulesCreated = 0;
    let inserted = 0;
    let skipped = 0;
    const accountIdByKey = new Map();

    for (const acct of accounts) {
      const existing = canMatchExistingAccount(acct)
        ? findAccount.get(householdId, acct.institution, acct.account_number_last4)
        : null;
      if (existing) {
        accountIdByKey.set(acct.key, existing.id);
        continue;
      }
      const result = insertAccount.run(
        householdId,
        acct.name,
        acct.type,
        acct.institution,
        acct.account_number_last4,
        householdId
      );
      accountIdByKey.set(acct.key, result.lastInsertRowid);
      accountsCreated += 1;
      recordBatchItem(db, householdId, importBatchId, 'accounts', result.lastInsertRowid);
    }

    for (const rule of rules) {
      if (findRule.get(householdId, rule.friendlyName)) continue;
      const result = insertRule.run(
        householdId,
        rule.friendlyName,
        JSON.stringify([{ field: 'merchant', operator: 'contains', value: rule.ruleName }]),
        JSON.stringify([{ type: 'rename', value: rule.customName }])
      );
      rulesCreated += 1;
      recordBatchItem(db, householdId, importBatchId, 'rules', result.lastInsertRowid);
    }

    for (const txn of transactions) {
      const accountId = accountIdByKey.get(txn.accountKey);
      if (!accountId) {
        skipped += 1;
        continue;
      }
      const result = insertTxn.run(
        accountId,
        txn.date,
        txn.amountCents,
        txn.originalMerchant,
        txn.originalDescription,
        txn.categoryId,
        txn.notes,
        txn.isIgnored ? 1 : 0,
        txn.externalId,
        householdId
      );
      if (result.changes === 1) {
        inserted += 1;
        recordBatchItem(db, householdId, importBatchId, 'transactions', result.lastInsertRowid);
      } else {
        skipped += 1;
      }
    }

    const summary = {
      inserted,
      skipped,
      accountsCreated,
      rulesCreated,
      totalRows: transactions.length,
      parseWarnings: JSON.parse(batch.summary_json || '{}').parseWarnings || 0
    };
    db.prepare(
      `UPDATE import_batches
          SET status = 'applied',
              summary_json = ?,
              applied_at = datetime('now'),
              updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(JSON.stringify(summary), importBatchId, householdId);
    return summary;
  });

  const summary = run();

  let reapplyResult = { processed: 0, updated: 0 };
  try {
    reapplyResult = reapplyRulesToAllTransactions(db, householdId);
  } catch (err) {
    console.error('Post-import reapply failed (non-fatal):', err);
  }

  return {
    batchId: importBatchId,
    ...summary,
    reapplyUpdated: reapplyResult.updated
  };
}

export function undoImportBatch(db, importBatchId, householdId = 1) {
  const batch = db
    .prepare(
      `SELECT *
         FROM import_batches
        WHERE id = ?
          AND household_id = ?`
    )
    .get(importBatchId, householdId);
  if (!batch) throw new Error('Import batch was not found.');
  if (batch.status !== 'applied') {
    throw new Error('Only applied imports can be undone.');
  }

  const items = db
    .prepare(
      `SELECT table_name, row_id
         FROM import_batch_items
        WHERE household_id = ?
          AND import_batch_id = ?
        ORDER BY id DESC`
    )
    .all(householdId, importBatchId);

  const idsByTable = new Map();
  for (const item of items) {
    if (!idsByTable.has(item.table_name)) idsByTable.set(item.table_name, []);
    idsByTable.get(item.table_name).push(item.row_id);
  }

  const deletedRuleIds = [];
  const run = db.transaction(() => {
    let transactionsDeleted = 0;
    let rulesDeleted = 0;
    let accountsDeleted = 0;
    let accountsKept = 0;

    for (const id of idsByTable.get('transactions') || []) {
      transactionsDeleted += db
        .prepare('DELETE FROM transactions WHERE id = ? AND household_id = ?')
        .run(id, householdId).changes;
    }

    for (const id of idsByTable.get('rules') || []) {
      const changes = db
        .prepare('DELETE FROM rules WHERE id = ? AND household_id = ?')
        .run(id, householdId).changes;
      if (changes) {
        rulesDeleted += changes;
        deletedRuleIds.push(id);
      }
    }

    for (const id of idsByTable.get('accounts') || []) {
      const remainingTransactions = db
        .prepare('SELECT COUNT(*) AS count FROM transactions WHERE account_id = ? AND household_id = ?')
        .get(id, householdId).count;
      const remainingRecords = db
        .prepare('SELECT COUNT(*) AS count FROM account_balance_records WHERE account_id = ? AND household_id = ?')
        .get(id, householdId).count;
      if (remainingTransactions || remainingRecords) {
        accountsKept += 1;
        continue;
      }
      accountsDeleted += db
        .prepare('DELETE FROM accounts WHERE id = ? AND household_id = ?')
        .run(id, householdId).changes;
    }

    const summary = { transactionsDeleted, rulesDeleted, accountsDeleted, accountsKept };
    db.prepare(
      `UPDATE import_batches
          SET status = 'undone',
              summary_json = ?,
              undone_at = datetime('now'),
              updated_at = datetime('now')
        WHERE id = ? AND household_id = ?`
    ).run(JSON.stringify(summary), importBatchId, householdId);
    return summary;
  });

  const summary = run();
  for (const ruleId of deletedRuleIds) {
    revertEditsForRule(db, ruleId, householdId);
  }

  return {
    batchId: importBatchId,
    ...summary
  };
}

export function importRocketMoneyCSV(db, csvBuffer, householdId = 1) {
  const preview = createRocketMoneyImportBatch(db, csvBuffer, householdId, null);
  return applyRocketMoneyImportBatch(db, preview.batchId, householdId);
}
