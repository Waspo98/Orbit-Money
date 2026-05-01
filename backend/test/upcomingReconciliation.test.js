import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { reconcileUpcomingTransactions } from '../src/services/upcomingReconciliation.js';

function makeDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE households (
      id INTEGER PRIMARY KEY
    );

    CREATE TABLE upcoming_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      merchant TEXT,
      amount INTEGER NOT NULL DEFAULT 0,
      direction TEXT NOT NULL,
      frequency_type TEXT NOT NULL DEFAULT 'monthly',
      frequency_interval INTEGER NOT NULL DEFAULT 1,
      frequency_unit TEXT NOT NULL DEFAULT 'months',
      recurrence_rule TEXT,
      next_date TEXT NOT NULL,
      category_id INTEGER,
      account_id INTEGER,
      status TEXT NOT NULL DEFAULT 'active'
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      account_id INTEGER,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      original_merchant TEXT NOT NULL,
      edited_merchant TEXT,
      category_id INTEGER,
      edited_category_id INTEGER,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      edited_is_transfer INTEGER,
      is_ignored INTEGER NOT NULL DEFAULT 0,
      edited_is_ignored INTEGER
    );

    CREATE TABLE upcoming_occurrences (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      upcoming_item_id INTEGER NOT NULL,
      expected_date TEXT NOT NULL,
      grace_until TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending',
      matched_transaction_id INTEGER,
      matched_at TEXT,
      missed_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      UNIQUE(household_id, upcoming_item_id, expected_date)
    );
  `);
  db.prepare('INSERT INTO households (id) VALUES (1)').run();
  return db;
}

function addUpcomingItem(db, overrides = {}) {
  return db.prepare(`
    INSERT INTO upcoming_items (
      household_id, name, merchant, amount, direction, frequency_type,
      frequency_interval, frequency_unit, next_date, category_id, account_id, status
    ) VALUES (
      @household_id, @name, @merchant, @amount, @direction, @frequency_type,
      @frequency_interval, @frequency_unit, @next_date, @category_id, @account_id, @status
    )
  `).run({
    household_id: 1,
    name: 'PepsiCo Payroll',
    merchant: 'PepsiCo Payroll',
    amount: 285259,
    direction: 'income',
    frequency_type: 'monthly',
    frequency_interval: 1,
    frequency_unit: 'months',
    next_date: '2026-05-15',
    category_id: 7,
    account_id: 3,
    status: 'active',
    ...overrides
  }).lastInsertRowid;
}

function addTransaction(db, overrides = {}) {
  return db.prepare(`
    INSERT INTO transactions (
      household_id, account_id, date, amount, original_merchant,
      category_id, is_transfer, is_ignored
    ) VALUES (
      @household_id, @account_id, @date, @amount, @original_merchant,
      @category_id, 0, 0
    )
  `).run({
    household_id: 1,
    account_id: 3,
    date: '2026-05-15',
    amount: 285259,
    original_merchant: 'PepsiCo Payroll',
    category_id: 7,
    ...overrides
  }).lastInsertRowid;
}

test('reconcileUpcomingTransactions creates and matches expected occurrences', () => {
  const db = makeDb();
  const itemId = addUpcomingItem(db);
  const transactionId = addTransaction(db);

  const result = reconcileUpcomingTransactions(db, {
    householdId: 1,
    today: '2026-05-17',
    lookbackDays: 15,
    lookaheadDays: 15
  });

  assert.equal(result.occurrences_created, 1);
  assert.equal(result.matched, 1);

  const occurrence = db.prepare('SELECT * FROM upcoming_occurrences WHERE upcoming_item_id = ?').get(itemId);
  assert.equal(occurrence.expected_date, '2026-05-15');
  assert.equal(occurrence.grace_until, '2026-05-19');
  assert.equal(occurrence.status, 'matched');
  assert.equal(occurrence.matched_transaction_id, transactionId);
});

test('reconcileUpcomingTransactions waits through the 4-day grace period before marking missed', () => {
  const db = makeDb();
  const itemId = addUpcomingItem(db, { next_date: '2026-05-01' });

  reconcileUpcomingTransactions(db, {
    householdId: 1,
    today: '2026-05-05',
    lookbackDays: 10,
    lookaheadDays: 0
  });
  assert.equal(
    db.prepare('SELECT status FROM upcoming_occurrences WHERE upcoming_item_id = ? AND expected_date = ?').get(itemId, '2026-05-01').status,
    'pending'
  );

  const result = reconcileUpcomingTransactions(db, {
    householdId: 1,
    today: '2026-05-06',
    lookbackDays: 10,
    lookaheadDays: 0
  });
  assert.equal(result.missed, 1);
  assert.equal(
    db.prepare('SELECT status FROM upcoming_occurrences WHERE upcoming_item_id = ? AND expected_date = ?').get(itemId, '2026-05-01').status,
    'missed'
  );
});

test('reconcileUpcomingTransactions can match a transaction after an occurrence was marked missed', () => {
  const db = makeDb();
  const itemId = addUpcomingItem(db, { next_date: '2026-05-01' });

  reconcileUpcomingTransactions(db, {
    householdId: 1,
    today: '2026-05-06',
    lookbackDays: 10,
    lookaheadDays: 0
  });
  const transactionId = addTransaction(db, { date: '2026-05-01' });

  const result = reconcileUpcomingTransactions(db, {
    householdId: 1,
    today: '2026-05-07',
    lookbackDays: 10,
    lookaheadDays: 0
  });

  assert.equal(result.matched, 1);
  const occurrence = db.prepare('SELECT * FROM upcoming_occurrences WHERE upcoming_item_id = ? AND expected_date = ?').get(itemId, '2026-05-01');
  assert.equal(occurrence.status, 'matched');
  assert.equal(occurrence.matched_transaction_id, transactionId);
  assert.equal(occurrence.missed_at, null);
});
