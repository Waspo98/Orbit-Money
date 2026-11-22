import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';
import { runMigrations } from '../src/db/migrations.js';
import { importNormalizedCsvTransactions, undoImportBatch } from '../src/services/csvImport.js';

function createTestDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

test('importNormalizedCsvTransactions creates accounts, imports integer cents, deduplicates, and supports undo', () => {
  const db = createTestDb();
  const payload = {
    fileName: 'ynab_export.csv',
    source: 'ynab',
    accounts: [
      { key: '|Ally Checking|', name: 'Ally Checking', type: 'checking', institution: null, last4: null }
    ],
    transactions: [
      {
        accountKey: '|Ally Checking|',
        date: '2026-10-12',
        amountCents: -6425,
        merchant: 'Trader Joes',
        description: 'TRADER JOES #104',
        externalId: 'ynab_hash_1'
      },
      {
        accountKey: '|Ally Checking|',
        date: '2026-10-15',
        amountCents: 280000,
        merchant: 'Employer Payroll',
        description: 'DIRECT DEPOSIT',
        externalId: 'ynab_hash_2'
      }
    ],
    rules: [
      { originalName: 'TRADER JOES', customName: "Trader Joe's", friendlyRuleName: "Auto: TRADER JOES -> Trader Joe's" }
    ]
  };

  const first = importNormalizedCsvTransactions(db, 1, payload);
  assert.equal(first.source, 'ynab');
  assert.equal(first.accountsCreated, 1);
  assert.equal(first.inserted, 2);
  assert.equal(first.skipped, 0);
  assert.equal(first.rulesCreated, 1);

  const txRows = db.prepare('SELECT amount, original_merchant FROM transactions ORDER BY date ASC').all();
  assert.equal(txRows.length, 2);
  assert.equal(txRows[0].amount, -6425);
  assert.equal(txRows[1].amount, 280000);

  // Re-importing same rows skips duplicates cleanly without creating duplicate accounts
  const second = importNormalizedCsvTransactions(db, 1, payload);
  assert.equal(second.accountsCreated, 0);
  assert.equal(second.inserted, 0);
  assert.equal(second.skipped, 2);

  // Undoing the first batch cleans up transactions, rule, and newly created empty account
  const undone = undoImportBatch(db, first.batchId, 1);
  assert.equal(undone.transactionsDeleted, 2);
  assert.equal(undone.rulesDeleted, 1);
  assert.equal(undone.accountsDeleted, 1);
});

test('importNormalizedCsvTransactions auto-creates Imported Checking when workspace has no accounts and CSV has no account column', () => {
  const db = createTestDb();
  const result = importNormalizedCsvTransactions(db, 1, {
    fileName: 'bank.csv',
    source: 'csv',
    accounts: [],
    transactions: [
      {
        date: '2026-11-01',
        amountCents: -1999,
        merchant: 'Coffee Roasters',
        description: 'COFFEE ROASTERS'
      }
    ]
  });

  assert.equal(result.accountsCreated, 1);
  assert.equal(result.inserted, 1);
  const acct = db.prepare('SELECT name, type FROM accounts WHERE household_id = 1').get();
  assert.equal(acct.name, 'Imported Checking');
  assert.equal(acct.type, 'checking');
});

test('importNormalizedCsvTransactions ignores non-existent or foreign categoryId and falls back safely', () => {
  const db = createTestDb();
  const result = importNormalizedCsvTransactions(db, 1, {
    fileName: 'foreign_cat.csv',
    source: 'csv',
    accounts: [],
    transactions: [
      {
        date: '2026-11-02',
        amountCents: -2500,
        merchant: 'Corner Deli',
        description: 'CORNER DELI',
        categoryId: 999999
      }
    ]
  });

  assert.equal(result.inserted, 1);
  const tx = db.prepare('SELECT category_id FROM transactions WHERE household_id = 1').get();
  assert.notEqual(tx.category_id, 999999);
});

test('importNormalizedCsvTransactions skips blank/null amounts and non-object entries instead of inserting $0.00 rows', () => {
  const db = createTestDb();
  const result = importNormalizedCsvTransactions(db, 1, {
    fileName: 'malformed.csv',
    source: 'csv',
    accounts: [null, 'invalid'],
    rules: [null, 'invalid'],
    transactions: [
      null,
      'invalid',
      { date: '2026-11-05', amount: '', merchant: 'Blank String' },
      { date: '2026-11-05', amount: '   ', merchant: 'Whitespace String' },
      { date: '2026-11-05', amount: null, merchant: 'Null Amount' },
      { date: '2026-11-05', merchant: 'Missing Amount' },
      { date: '2026-11-05', amount: 12.34, merchant: 'Valid Dollar Row' }
    ]
  });

  assert.equal(result.inserted, 1);
  assert.equal(result.skipped, 6);
  const rows = db.prepare('SELECT amount, original_merchant FROM transactions WHERE household_id = 1').all();
  assert.equal(rows.length, 1);
  assert.equal(rows[0].amount, 1234);
  assert.equal(rows[0].original_merchant, 'Valid Dollar Row');
});
