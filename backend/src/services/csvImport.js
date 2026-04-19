// =============================================================================
// csvImport.js — Rocket Money CSV import pipeline (v12)
// =============================================================================
// Responsibilities:
//   1. Parse the 15-column Rocket Money export format
//   2. Discover unique accounts, auto-guess types, upsert
//   3. Extract (Name → Custom Name) rename pairs, create rules
//   4. Insert transactions with original_merchant = raw Name (NOT the RM
//      Custom Name override) so originals are preserved forever
//   5. After all inserts, reapply all rules — rule-owned edits get populated
//      (edited_merchant = customName, edited_merchant_source = 'rule:{id}')
//
// Sign convention: RM uses positive=expense, we use negative=expense.
// All DB work happens inside a single transaction — atomic, all-or-nothing.
// =============================================================================

import Papa from 'papaparse';
import crypto from 'crypto';
import { reapplyRulesToAllTransactions } from './ruleMatcher.js';

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
  return crypto
    .createHash('sha256')
    .update(input)
    .digest('hex')
    .slice(0, 16);
}

export function importRocketMoneyCSV(db, csvBuffer) {
  // --- 1. Parse -------------------------------------------------------------
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

  // --- 2. Discover accounts -------------------------------------------------
  const accountsMap = new Map();
  for (const row of rows) {
    const key = accountKey(row);
    if (accountsMap.has(key)) continue;
    accountsMap.set(key, {
      name: (row['Account Name'] || '').trim() || 'Unknown Account',
      institution: (row['Institution Name'] || '').trim() || null,
      account_number_last4: (row['Account Number'] || '').trim() || null,
      type: guessAccountType(row['Account Type'], row['Account Name'], row['Institution Name'])
    });
  }

  // --- 3. Discover rename rules ---------------------------------------------
  const rulesMap = new Map(); // Name → Custom Name
  for (const row of rows) {
    const customName = (row['Custom Name'] || '').trim();
    const name = (row['Name'] || '').trim();
    if (!customName || !name || customName === name) continue;
    rulesMap.set(name, customName);
  }

  // --- 4. Category lookup ---------------------------------------------------
  const categories = db.prepare('SELECT id, name FROM categories').all();
  const categoryIdByName = new Map(
    categories.map((c) => [c.name.toLowerCase(), c.id])
  );
  const uncategorizedId =
    categoryIdByName.get('uncategorized') ?? categories[0]?.id ?? null;

  // --- 5. Prepared statements ----------------------------------------------
  const findAccount = db.prepare(`
    SELECT id FROM accounts
    WHERE institution IS ? AND account_number_last4 IS ?
  `);
  const insertAccount = db.prepare(`
    INSERT INTO accounts (name, type, institution, account_number_last4, is_manual, current_balance)
    VALUES (?, ?, ?, ?, 1, 0)
  `);

  const findRule = db.prepare('SELECT id FROM rules WHERE name = ?');
  const insertRule = db.prepare(`
    INSERT INTO rules (name, conditions, actions, priority, enabled)
    VALUES (?, ?, ?, 0, 1)
  `);

  // Note: original_merchant holds the raw bank "Name" field. Edited values
  // are populated by reapplying rules after all inserts (see step 7).
  const insertTxn = db.prepare(`
    INSERT OR IGNORE INTO transactions
      (account_id, date, amount, original_merchant, original_description,
       category_id, notes, is_ignored, source, external_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'csv_import', ?)
  `);

  // --- 6. Atomic import -----------------------------------------------------
  const run = db.transaction(() => {
    let accountsCreated = 0;
    let rulesCreated = 0;
    let inserted = 0;
    let skipped = 0;

    const accountIdByKey = new Map();

    for (const [key, acct] of accountsMap) {
      const existing = canMatchExistingAccount(acct)
        ? findAccount.get(acct.institution, acct.account_number_last4)
        : null;
      if (existing) {
        accountIdByKey.set(key, existing.id);
      } else {
        const result = insertAccount.run(
          acct.name,
          acct.type,
          acct.institution,
          acct.account_number_last4
        );
        accountIdByKey.set(key, result.lastInsertRowid);
        accountsCreated++;
      }
    }

    for (const [ruleName, customName] of rulesMap) {
      const friendlyName = `Auto: ${ruleName} → ${customName}`;
      if (findRule.get(friendlyName)) continue;
      insertRule.run(
        friendlyName,
        JSON.stringify([
          { field: 'merchant', operator: 'contains', value: ruleName }
        ]),
        JSON.stringify([{ type: 'rename', value: customName }])
      );
      rulesCreated++;
    }

    for (const row of rows) {
      const key = accountKey(row);
      const accountId = accountIdByKey.get(key);
      if (!accountId) {
        skipped++;
        continue;
      }

      const rmAmount = parseFloat(row['Amount']);
      if (!Number.isFinite(rmAmount)) {
        skipped++;
        continue;
      }
      const amount = -rmAmount;

      const date = (row['Date'] || '').trim();
      if (!date) {
        skipped++;
        continue;
      }

      // v12: original_merchant is the raw "Name" column. Rocket Money's
      // Custom Name is handled via the rule we just inserted, which will
      // populate edited_merchant on reapply below.
      const name = (row['Name'] || '').trim();
      const originalMerchant = name || 'Unknown';

      const originalDesc = (row['Description'] || '').trim();
      const categoryName = (row['Category'] || '').trim().toLowerCase();
      const categoryId = categoryIdByName.get(categoryName) || uncategorizedId;
      const notes = (row['Note'] || '').trim() || null;
      const isIgnored = (row['Ignored From'] || '').trim() ? 1 : 0;

      const amountCents = Math.round(amount * 100);
      const contentHash = computeContentHash({
        date,
        amountCents,
        originalDescription: originalDesc,
        accountKey: key
      });

      const result = insertTxn.run(
        accountId,
        date,
        amount,
        originalMerchant,
        originalDesc,
        categoryId,
        notes,
        isIgnored,
        contentHash
      );

      if (result.changes === 1) inserted++;
      else skipped++;
    }

    return { inserted, skipped, accountsCreated, rulesCreated };
  });

  const summary = run();

  // --- 7. Reapply rules post-import ----------------------------------------
  // Populates edited_* columns for every transaction (new + existing) so the
  // RM Custom Name mappings take effect immediately.
  let reapplyResult = { processed: 0, updated: 0 };
  try {
    reapplyResult = reapplyRulesToAllTransactions(db);
  } catch (err) {
    console.error('Post-import reapply failed (non-fatal):', err);
  }

  return {
    ...summary,
    totalRows: rows.length,
    parseWarnings: parsed.errors.length,
    reapplyUpdated: reapplyResult.updated
  };
}
