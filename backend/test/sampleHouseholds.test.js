import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';
import { cleanupExpiredSampleHouseholds } from '../src/services/sampleHouseholds.js';

const HOUSEHOLD_TABLES = [
  'upcoming_dismissed_suggestions',
  'upcoming_items',
  'sync_log',
  'simplefin_config',
  'app_settings',
  'household_shares',
  'household_retirement_accounts',
  'household_income_records',
  'household_members',
  'account_balance_records',
  'goal_account_allocations',
  'goals',
  'budgets',
  'rules',
  'transactions',
  'categories',
  'accounts'
];

function makeDb() {
  const db = new Database(':memory:');
  db.exec(`
    CREATE TABLE users (
      id INTEGER PRIMARY KEY,
      username TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
    CREATE TABLE households (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL
    );
    CREATE TABLE household_memberships (
      id INTEGER PRIMARY KEY,
      household_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      role TEXT NOT NULL
    );
  `);

  for (const table of HOUSEHOLD_TABLES) {
    db.exec(`CREATE TABLE ${table} (id INTEGER PRIMARY KEY, household_id INTEGER NOT NULL);`);
  }

  return db;
}

function addUserHousehold(db, { userId, householdId, username, updatedAt }) {
  db.prepare('INSERT INTO users (id, username, updated_at) VALUES (?, ?, ?)').run(
    userId,
    username,
    updatedAt
  );
  db.prepare('INSERT INTO households (id, name) VALUES (?, ?)').run(householdId, 'Sample Data');
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role)
     VALUES (?, ?, 'owner')`
  ).run(householdId, userId);

  for (const table of HOUSEHOLD_TABLES) {
    db.prepare(`INSERT INTO ${table} (household_id) VALUES (?)`).run(householdId);
  }
}

test('cleanupExpiredSampleHouseholds removes expired sample households and users', () => {
  const db = makeDb();
  addUserHousehold(db, {
    userId: 10,
    householdId: 20,
    username: 'sample:expired-device',
    updatedAt: db.prepare("SELECT datetime('now', '-49 hours') AS value").get().value
  });

  const result = cleanupExpiredSampleHouseholds(db);

  assert.equal(result.householdsDeleted, 1);
  assert.equal(result.usersDeleted, 1);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM users').get().count, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM households').get().count, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM accounts').get().count, 0);
});

test('cleanupExpiredSampleHouseholds keeps recently used sample households', () => {
  const db = makeDb();
  addUserHousehold(db, {
    userId: 11,
    householdId: 21,
    username: 'sample:active-device',
    updatedAt: db.prepare("SELECT datetime('now') AS value").get().value
  });

  const result = cleanupExpiredSampleHouseholds(db);

  assert.equal(result.householdsDeleted, 0);
  assert.equal(result.usersDeleted, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM households').get().count, 1);
});

test('cleanupExpiredSampleHouseholds keeps households that include a real user', () => {
  const db = makeDb();
  addUserHousehold(db, {
    userId: 12,
    householdId: 22,
    username: 'sample:old-shared-device',
    updatedAt: db.prepare("SELECT datetime('now', '-49 hours') AS value").get().value
  });
  db.prepare('INSERT INTO users (id, username, updated_at) VALUES (?, ?, ?)').run(
    13,
    'owner',
    db.prepare("SELECT datetime('now', '-49 hours') AS value").get().value
  );
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role)
     VALUES (?, ?, 'owner')`
  ).run(22, 13);

  const result = cleanupExpiredSampleHouseholds(db);

  assert.equal(result.householdsDeleted, 0);
  assert.equal(result.usersDeleted, 0);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM households').get().count, 1);
  assert.equal(db.prepare('SELECT COUNT(*) AS count FROM users').get().count, 2);
});
