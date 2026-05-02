import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { restoreOrbitBackup } from '../src/services/orbitBackupRestore.js';

function makeDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE users (
      id INTEGER PRIMARY KEY,
      username TEXT UNIQUE,
      email TEXT,
      display_name TEXT
    );

    CREATE TABLE households (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      default_currency TEXT NOT NULL DEFAULT 'USD',
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE household_memberships (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      role TEXT NOT NULL,
      access_level TEXT NOT NULL DEFAULT 'write',
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      UNIQUE(household_id, user_id)
    );

    CREATE TABLE household_shares (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL
    );

    CREATE TABLE simplefin_config (
      household_id INTEGER PRIMARY KEY
    );

    CREATE TABLE sync_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL
    );

    CREATE TABLE accounts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      type TEXT NOT NULL,
      current_balance INTEGER NOT NULL DEFAULT 0,
      is_manual INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL
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
      account_id INTEGER NOT NULL,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      original_merchant TEXT NOT NULL,
      category_id INTEGER,
      edited_category_id INTEGER,
      edited_category_id_source TEXT,
      transfer_pair_id INTEGER,
      source TEXT NOT NULL DEFAULT 'manual'
    );

    CREATE TABLE budgets (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      category_id INTEGER NOT NULL,
      amount INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE goals (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      target_amount INTEGER NOT NULL DEFAULT 0,
      current_amount INTEGER NOT NULL DEFAULT 0,
      linked_account_id INTEGER
    );

    CREATE TABLE goal_account_allocations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      goal_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      allocation_type TEXT NOT NULL DEFAULT 'fixed',
      allocation_amount INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE account_balance_records (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      record_date TEXT NOT NULL,
      balance INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE household_members (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL
    );

    CREATE TABLE household_income_records (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      member_id INTEGER NOT NULL,
      effective_date TEXT NOT NULL
    );

    CREATE TABLE household_retirement_accounts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      member_id INTEGER NOT NULL,
      account_id INTEGER NOT NULL,
      account_kind TEXT NOT NULL DEFAULT 'other'
    );

    CREATE TABLE app_settings (
      household_id INTEGER NOT NULL,
      key TEXT NOT NULL,
      value TEXT NOT NULL,
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      PRIMARY KEY (household_id, key)
    );

    CREATE TABLE user_preferences (
      household_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      key TEXT NOT NULL,
      value_json TEXT NOT NULL,
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      PRIMARY KEY (household_id, user_id, key)
    );

    CREATE TABLE upcoming_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      category_id INTEGER,
      account_id INTEGER,
      source_transaction_id INTEGER
    );

    CREATE TABLE upcoming_dismissed_suggestions (
      household_id INTEGER NOT NULL,
      suggestion_key TEXT NOT NULL,
      dismissed_at TEXT NOT NULL DEFAULT (datetime('now')),
      PRIMARY KEY (household_id, suggestion_key)
    );

    CREATE TABLE upcoming_occurrences (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      upcoming_item_id INTEGER NOT NULL,
      expected_date TEXT NOT NULL,
      matched_transaction_id INTEGER
    );

    CREATE TABLE import_batches (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      source TEXT NOT NULL,
      preview_json TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending'
    );

    CREATE TABLE import_batch_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      import_batch_id INTEGER NOT NULL,
      table_name TEXT NOT NULL,
      row_id INTEGER NOT NULL,
      UNIQUE(import_batch_id, table_name, row_id)
    );
  `);

  db.prepare('INSERT INTO users (id, username, email) VALUES (?, ?, ?)').run(
    7,
    'owner',
    'owner@example.com'
  );
  db.prepare('INSERT INTO users (id, username, email) VALUES (?, ?, ?)').run(
    9,
    'stale',
    'stale@example.com'
  );
  db.prepare('INSERT INTO households (id, name) VALUES (1, ?)').run('Before');
  db.prepare('INSERT INTO households (id, name) VALUES (99, ?)').run('Other');
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (1, 9, 'member', 'write')`
  ).run();
  db.prepare('INSERT INTO household_shares (household_id) VALUES (1)').run();
  db.prepare('INSERT INTO sync_log (household_id) VALUES (1)').run();

  db.prepare('INSERT INTO accounts (id, household_id, name, type) VALUES (10, 99, ?, ?)').run(
    'Other Checking',
    'checking'
  );
  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (20, 99, ?)').run('Other');
  db.prepare(
    `INSERT INTO rules (id, household_id, name, conditions, actions)
     VALUES (30, 99, 'Other Rule', '[]', '[]')`
  ).run();
  db.prepare(
    `INSERT INTO transactions (id, household_id, account_id, date, amount, original_merchant)
     VALUES (40, 99, 10, '2026-01-01', -1000, 'Other')`
  ).run();
  db.prepare('INSERT INTO goals (id, household_id, name) VALUES (50, 99, ?)').run('Other Goal');
  db.prepare(
    `INSERT INTO import_batches (id, household_id, source, preview_json)
     VALUES (100, 99, 'rocket_money', '{}')`
  ).run();

  return db;
}

function makeBackup() {
  return {
    type: 'orbit_money_backup',
    version: 1,
    household: {
      name: 'Restored Home',
      default_currency: 'USD'
    },
    memberships: [
      {
        user_id: 2,
        email: 'owner@example.com',
        username: 'owner',
        role: 'owner',
        access_level: 'write'
      }
    ],
    tables: {
      accounts: [
        { id: 10, household_id: 5, name: 'Checking', type: 'checking', current_balance: 12345 }
      ],
      categories: [
        { id: 20, household_id: 5, name: 'Groceries' }
      ],
      rules: [
        {
          id: 30,
          household_id: 5,
          name: 'Groceries Rule',
          conditions: JSON.stringify([
            { field: 'account_id', operator: 'equals', value: 10 },
            { field: 'category_id', operator: 'equals', value: 20 }
          ]),
          actions: JSON.stringify([{ type: 'categorize', value: 20 }]),
          priority: 1,
          enabled: 1
        }
      ],
      transactions: [
        {
          id: 40,
          household_id: 5,
          account_id: 10,
          date: '2026-01-02',
          amount: -2500,
          original_merchant: 'Debit',
          category_id: 20,
          edited_category_id: 20,
          edited_category_id_source: 'rule:30',
          transfer_pair_id: 41,
          source: 'manual'
        },
        {
          id: 41,
          household_id: 5,
          account_id: 10,
          date: '2026-01-02',
          amount: 2500,
          original_merchant: 'Credit',
          category_id: 20,
          transfer_pair_id: 40,
          source: 'manual'
        }
      ],
      budgets: [
        { id: 45, household_id: 5, category_id: 20, amount: 50000 }
      ],
      goals: [
        {
          id: 50,
          household_id: 5,
          name: 'Emergency',
          target_amount: 100000,
          current_amount: 10000,
          linked_account_id: 10
        }
      ],
      goal_account_allocations: [
        {
          id: 60,
          household_id: 5,
          goal_id: 50,
          account_id: 10,
          allocation_type: 'fixed',
          allocation_amount: 10000
        }
      ],
      account_balance_records: [
        {
          id: 70,
          household_id: 5,
          account_id: 10,
          record_date: '2026-01-01',
          balance: 12345
        }
      ],
      household_members: [
        { id: 80, household_id: 5, name: 'Owner' }
      ],
      household_income_records: [
        { id: 81, household_id: 5, member_id: 80, effective_date: '2026-01-01' }
      ],
      household_retirement_accounts: [
        { id: 82, household_id: 5, member_id: 80, account_id: 10, account_kind: '401k' }
      ],
      app_settings: [
        { household_id: 5, key: 'mha_tracker_enabled', value: '1' }
      ],
      user_preferences: [
        {
          household_id: 5,
          user_id: 2,
          key: 'dashboard',
          value_json: '{"layout":"compact"}'
        }
      ],
      upcoming_items: [
        {
          id: 90,
          household_id: 5,
          name: 'Power',
          category_id: 20,
          account_id: 10,
          source_transaction_id: 40
        }
      ],
      upcoming_dismissed_suggestions: [
        { household_id: 5, suggestion_key: 'merchant:old' }
      ],
      upcoming_occurrences: [
        {
          id: 91,
          household_id: 5,
          upcoming_item_id: 90,
          expected_date: '2026-02-01',
          matched_transaction_id: 41
        }
      ],
      import_batches: [
        {
          id: 100,
          household_id: 5,
          source: 'rocket_money',
          status: 'pending',
          preview_json: JSON.stringify({
            accounts: [{ key: 'checking', existingAccountId: 10 }],
            rules: [{ friendlyName: 'Groceries Rule', existingRuleId: 30 }],
            transactions: [{ accountKey: 'checking', categoryId: 20 }]
          })
        }
      ],
      import_batch_items: [
        { id: 101, household_id: 5, import_batch_id: 100, table_name: 'accounts', row_id: 10 },
        { id: 102, household_id: 5, import_batch_id: 100, table_name: 'rules', row_id: 30 },
        { id: 103, household_id: 5, import_batch_id: 100, table_name: 'transactions', row_id: 40 }
      ]
    }
  };
}

function onlyRow(db, table) {
  return db.prepare(`SELECT * FROM ${table} WHERE household_id = 1`).get();
}

test('restoreOrbitBackup allocates fresh ids and remaps dependent references', () => {
  const db = makeDb();

  const result = restoreOrbitBackup(db, makeBackup(), 1, 7);

  assert.equal(result.inserted.accounts, 1);
  assert.equal(result.inserted.transactions, 2);
  assert.equal(result.inserted.user_preferences, 1);

  const account = onlyRow(db, 'accounts');
  const category = onlyRow(db, 'categories');
  const rule = onlyRow(db, 'rules');
  const debit = db
    .prepare("SELECT * FROM transactions WHERE household_id = 1 AND original_merchant = 'Debit'")
    .get();
  const credit = db
    .prepare("SELECT * FROM transactions WHERE household_id = 1 AND original_merchant = 'Credit'")
    .get();

  assert.notEqual(account.id, 10);
  assert.notEqual(category.id, 20);
  assert.notEqual(rule.id, 30);
  assert.notEqual(debit.id, 40);
  assert.equal(debit.account_id, account.id);
  assert.equal(debit.category_id, category.id);
  assert.equal(debit.edited_category_id, category.id);
  assert.equal(debit.edited_category_id_source, `rule:${rule.id}`);
  assert.equal(debit.transfer_pair_id, credit.id);
  assert.equal(credit.transfer_pair_id, debit.id);

  const conditions = JSON.parse(rule.conditions);
  const actions = JSON.parse(rule.actions);
  assert.equal(conditions[0].value, account.id);
  assert.equal(conditions[1].value, category.id);
  assert.equal(actions[0].value, category.id);

  assert.equal(onlyRow(db, 'budgets').category_id, category.id);
  assert.equal(onlyRow(db, 'goals').linked_account_id, account.id);
  assert.equal(onlyRow(db, 'goal_account_allocations').goal_id, onlyRow(db, 'goals').id);
  assert.equal(onlyRow(db, 'goal_account_allocations').account_id, account.id);
  assert.equal(onlyRow(db, 'account_balance_records').account_id, account.id);
  assert.equal(onlyRow(db, 'household_income_records').member_id, onlyRow(db, 'household_members').id);
  assert.equal(onlyRow(db, 'household_retirement_accounts').account_id, account.id);
  assert.equal(onlyRow(db, 'household_retirement_accounts').member_id, onlyRow(db, 'household_members').id);

  const upcomingItem = onlyRow(db, 'upcoming_items');
  assert.equal(upcomingItem.category_id, category.id);
  assert.equal(upcomingItem.account_id, account.id);
  assert.equal(upcomingItem.source_transaction_id, debit.id);
  assert.equal(onlyRow(db, 'upcoming_occurrences').upcoming_item_id, upcomingItem.id);
  assert.equal(onlyRow(db, 'upcoming_occurrences').matched_transaction_id, credit.id);

  const preview = JSON.parse(onlyRow(db, 'import_batches').preview_json);
  assert.equal(preview.accounts[0].existingAccountId, account.id);
  assert.equal(preview.rules[0].existingRuleId, rule.id);
  assert.equal(preview.transactions[0].categoryId, category.id);

  const importItems = db
    .prepare('SELECT table_name, row_id FROM import_batch_items WHERE household_id = 1')
    .all();
  assert.deepEqual(
    Object.fromEntries(importItems.map((item) => [item.table_name, item.row_id])),
    {
      accounts: account.id,
      rules: rule.id,
      transactions: debit.id
    }
  );

  const preference = onlyRow(db, 'user_preferences');
  assert.equal(preference.user_id, 7);
  assert.equal(preference.value_json, '{"layout":"compact"}');
  const membership = onlyRow(db, 'household_memberships');
  assert.equal(membership.user_id, 7);
  assert.equal(membership.role, 'owner');
  assert.equal(membership.access_level, 'write');
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM household_shares WHERE household_id = 1').get().count, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM sync_log WHERE household_id = 1').get().count, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM accounts WHERE household_id = 99 AND id = 10').get().count, 1);
});
