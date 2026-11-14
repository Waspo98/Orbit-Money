import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import {
  isUncategorizedCategoryName,
  uncategorizedTransactionWhereSql
} from '../src/lib/effectiveSql.js';

function makeDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE categories (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      name TEXT NOT NULL
    );

    CREATE TABLE transactions (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      date TEXT NOT NULL,
      amount INTEGER NOT NULL,
      category_id INTEGER,
      is_transfer INTEGER NOT NULL DEFAULT 0,
      edited_category_id INTEGER,
      edited_category_id_source TEXT,
      edited_is_transfer INTEGER
    );
  `);
  return db;
}

test('isUncategorizedCategoryName matches case-insensitive trimmed names only', () => {
  assert.equal(isUncategorizedCategoryName('Uncategorized'), true);
  assert.equal(isUncategorizedCategoryName('  uncategorized '), true);
  assert.equal(isUncategorizedCategoryName('Groceries'), false);
  assert.equal(isUncategorizedCategoryName(null), false);
});

test('uncategorizedTransactionWhereSql matches null and seeded Uncategorized while excluding transfers and categorized overrides', () => {
  const db = makeDb();
  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (?, ?, ?)').run(1, 1, 'Groceries');
  db.prepare('INSERT INTO categories (id, household_id, name) VALUES (?, ?, ?)').run(99, 1, 'Uncategorized');

  const insert = db.prepare(`
    INSERT INTO transactions (
      id, household_id, date, amount, category_id, is_transfer,
      edited_category_id, edited_category_id_source, edited_is_transfer
    ) VALUES (?, 1, '2026-11-10', -1500, ?, ?, ?, ?, ?)
  `);

  // Included:
  insert.run(1, null, 0, null, null, null);      // NULL original
  insert.run(2, 99, 0, null, null, null);        // DB 'Uncategorized' row
  insert.run(3, 1, 0, null, 'user', null);       // User cleared to NULL
  insert.run(4, 1, 0, 99, 'user', null);         // User set to DB 'Uncategorized'

  // Excluded:
  insert.run(5, 1, 0, null, null, null);         // Groceries
  insert.run(6, 99, 0, 1, 'user', null);         // Re-categorized to Groceries
  insert.run(7, null, 1, null, null, null);      // Transfer with NULL category
  insert.run(8, 99, 0, null, null, 1);           // Transfer override on DB 'Uncategorized'

  const rows = db
    .prepare(
      `SELECT id FROM transactions WHERE household_id = ? AND ${uncategorizedTransactionWhereSql()} ORDER BY id ASC`
    )
    .all(1, 1);

  assert.deepEqual(rows.map((r) => r.id), [1, 2, 3, 4]);
});
