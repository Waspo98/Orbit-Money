import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { previewRuleImpact } from '../src/services/ruleMatcher.js';

function makeDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      name TEXT
    );

    CREATE TABLE rules (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      conditions TEXT NOT NULL,
      actions TEXT NOT NULL,
      priority INTEGER NOT NULL DEFAULT 0,
      enabled INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      account_id INTEGER,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      original_merchant TEXT NOT NULL,
      original_description TEXT,
      category_id INTEGER,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      is_ignored INTEGER NOT NULL DEFAULT 0,
      edited_merchant TEXT,
      edited_merchant_source TEXT,
      edited_category_id INTEGER,
      edited_category_id_source TEXT,
      edited_is_transfer INTEGER,
      edited_is_transfer_source TEXT,
      edited_is_ignored INTEGER,
      edited_is_ignored_source TEXT,
      notes TEXT,
      transfer_pair_id INTEGER
    );
  `);

  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (?, 1, ?)')
    .run(10, 'Bills');
  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (?, 1, ?)')
    .run(20, 'Entertainment');
  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (?, 1, ?)')
    .run(99, 'Manual');

  return db;
}

function addTransaction(db, overrides = {}) {
  return db.prepare(`
    INSERT INTO transactions (
      household_id, account_id, date, amount, original_merchant,
      original_description, category_id, is_transfer, is_ignored,
      edited_merchant, edited_merchant_source, edited_category_id,
      edited_category_id_source, edited_is_transfer, edited_is_transfer_source,
      edited_is_ignored, edited_is_ignored_source, notes, transfer_pair_id
    ) VALUES (
      @household_id, @account_id, @date, @amount, @original_merchant,
      @original_description, @category_id, @is_transfer, @is_ignored,
      @edited_merchant, @edited_merchant_source, @edited_category_id,
      @edited_category_id_source, @edited_is_transfer, @edited_is_transfer_source,
      @edited_is_ignored, @edited_is_ignored_source, @notes, @transfer_pair_id
    )
  `).run({
    household_id: 1,
    account_id: 1,
    date: '2026-04-15',
    amount: -1499,
    original_merchant: 'Streamly',
    original_description: 'Streamly monthly',
    category_id: null,
    is_transfer: 0,
    is_ignored: 0,
    edited_merchant: null,
    edited_merchant_source: null,
    edited_category_id: null,
    edited_category_id_source: null,
    edited_is_transfer: null,
    edited_is_transfer_source: null,
    edited_is_ignored: null,
    edited_is_ignored_source: null,
    notes: null,
    transfer_pair_id: null,
    ...overrides
  }).lastInsertRowid;
}

function addRule(db, overrides = {}) {
  return db.prepare(`
    INSERT INTO rules (household_id, name, conditions, actions, priority, enabled)
    VALUES (@household_id, @name, @conditions, @actions, @priority, @enabled)
  `).run({
    household_id: 1,
    name: 'Existing rule',
    conditions: JSON.stringify([{ field: 'merchant', operator: 'contains', value: 'Streamly' }]),
    actions: JSON.stringify([{ type: 'categorize', value: 10 }]),
    priority: 0,
    enabled: 1,
    ...overrides
  }).lastInsertRowid;
}

test('previewRuleImpact returns visible changes for a draft rule', () => {
  const db = makeDb();
  addTransaction(db, {
    original_merchant: 'Walmart',
    original_description: 'Walmart store',
    amount: -8642
  });

  const preview = previewRuleImpact(db, {
    conditions: [{ field: 'merchant', operator: 'contains', value: 'Walmart' }],
    actions: [{ type: 'rename', value: 'Walmart Supercenter' }]
  });

  assert.equal(preview.count, 1);
  assert.equal(preview.willChangeCount, 1);
  assert.equal(preview.conflictCount, 0);
  assert.equal(preview.willChange[0].fields[0].label, 'Merchant');
  assert.equal(preview.willChange[0].fields[0].from, 'Walmart');
  assert.equal(preview.willChange[0].fields[0].to, 'Walmart Supercenter');
});

test('previewRuleImpact shows rule conflicts but omits protected and no-op matches', () => {
  const db = makeDb();
  addRule(db, { name: 'Streamly to Bills' });
  addTransaction(db, { original_merchant: 'Streamly' });
  addTransaction(db, {
    original_merchant: 'Streamly manual',
    edited_category_id: 99,
    edited_category_id_source: 'user'
  });
  addTransaction(db, {
    original_merchant: 'Streamly already entertainment',
    category_id: 20
  });

  const preview = previewRuleImpact(db, {
    conditions: [{ field: 'merchant', operator: 'contains', value: 'Streamly' }],
    actions: [{ type: 'categorize', value: 20 }]
  });

  assert.equal(preview.count, 3);
  assert.equal(preview.willChangeCount, 0);
  assert.equal(preview.conflictCount, 1);
  assert.equal(preview.conflicts[0].fields[0].label, 'Category');
  assert.equal(preview.conflicts[0].rules[0].name, 'Streamly to Bills');
});

test('previewRuleImpact includes currently applied rows when editing a rule', () => {
  const db = makeDb();
  const ruleId = addRule(db, {
    name: 'Streamly to Entertainment',
    actions: JSON.stringify([{ type: 'categorize', value: 20 }])
  });
  addTransaction(db, {
    original_merchant: 'Streamly',
    edited_category_id: 20,
    edited_category_id_source: `rule:${ruleId}`
  });

  const preview = previewRuleImpact(db, {
    ruleId,
    conditions: [{ field: 'merchant', operator: 'contains', value: 'Streamly' }],
    actions: [{ type: 'categorize', value: 20 }]
  });

  assert.equal(preview.count, 1);
  assert.equal(preview.willChangeCount, 1);
  assert.equal(preview.conflictCount, 0);
  assert.equal(preview.willChange[0].fields[0].label, 'Category');
  assert.equal(preview.willChange[0].fields[0].applied, true);
});

test('previewRuleImpact shows rows that would change after editing a rule condition', () => {
  const db = makeDb();
  const ruleId = addRule(db, {
    name: 'Streamly to Entertainment',
    actions: JSON.stringify([{ type: 'categorize', value: 20 }])
  });
  addTransaction(db, {
    original_merchant: 'Streamly',
    edited_category_id: 20,
    edited_category_id_source: `rule:${ruleId}`
  });

  const preview = previewRuleImpact(db, {
    ruleId,
    conditions: [{ field: 'merchant', operator: 'contains', value: 'Other Merchant' }],
    actions: [{ type: 'categorize', value: 20 }]
  });

  assert.equal(preview.count, 0);
  assert.equal(preview.willChangeCount, 1);
  assert.equal(preview.willChange[0].fields[0].from, 20);
  assert.equal(preview.willChange[0].fields[0].to, null);
});
