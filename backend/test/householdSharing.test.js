import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import {
  acceptPendingHouseholdShares,
  createPendingHouseholdShare
} from '../src/services/householdSharing.js';

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
      name TEXT NOT NULL
    );

    CREATE TABLE household_memberships (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      role TEXT NOT NULL DEFAULT 'member',
      access_level TEXT NOT NULL DEFAULT 'write',
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      UNIQUE(household_id, user_id)
    );

    CREATE TABLE household_shares (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      household_id INTEGER NOT NULL,
      invited_email TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'member',
      access_level TEXT NOT NULL DEFAULT 'write',
      created_by_user_id INTEGER,
      accepted_by_user_id INTEGER,
      accepted_at TEXT,
      revoked_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE UNIQUE INDEX idx_household_shares_active_email
      ON household_shares(household_id, lower(invited_email))
      WHERE revoked_at IS NULL;
  `);

  db.prepare('INSERT INTO households (id, name) VALUES (1, ?)').run('Home');
  db.prepare('INSERT INTO users (id, username, email, display_name) VALUES (?, ?, ?, ?)').run(
    1,
    'owner',
    'owner@example.com',
    'Owner'
  );
  db.prepare('INSERT INTO users (id, username, email, display_name) VALUES (?, ?, ?, ?)').run(
    2,
    'partner',
    'partner@example.com',
    'Partner'
  );
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (1, 1, 'owner', 'write')`
  ).run();
  return db;
}

function membership(db, userId) {
  return db
    .prepare(
      `SELECT role, access_level
         FROM household_memberships
        WHERE household_id = 1
          AND user_id = ?`
    )
    .get(userId);
}

test('accepted shares are not re-applied on later OIDC logins', () => {
  const db = makeDb();
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (1, 2, 'member', 'read')`
  ).run();
  db.prepare(
    `INSERT INTO household_shares (
       household_id, invited_email, role, access_level, accepted_by_user_id, accepted_at
     ) VALUES (1, 'partner@example.com', 'member', 'write', 2, datetime('now'))`
  ).run();

  const preferred = acceptPendingHouseholdShares(db, 2, 'partner@example.com');

  assert.equal(preferred, null);
  assert.deepEqual(membership(db, 2), { role: 'member', access_level: 'read' });
});

test('pending shares are accepted once and then retired', () => {
  const db = makeDb();
  createPendingHouseholdShare(db, {
    householdId: 1,
    email: 'partner@example.com',
    accessLevel: 'write',
    createdByUserId: 1
  });

  const preferred = acceptPendingHouseholdShares(db, 2, 'PARTNER@example.com');
  assert.equal(preferred, 1);
  assert.deepEqual(membership(db, 2), { role: 'member', access_level: 'write' });

  const share = db
    .prepare('SELECT accepted_at, revoked_at FROM household_shares WHERE household_id = 1')
    .get();
  assert.ok(share.accepted_at);
  assert.ok(share.revoked_at);

  db.prepare(
    `UPDATE household_memberships
        SET access_level = 'read'
      WHERE household_id = 1
        AND user_id = 2`
  ).run();

  assert.equal(acceptPendingHouseholdShares(db, 2, 'partner@example.com'), null);
  assert.deepEqual(membership(db, 2), { role: 'member', access_level: 'read' });
});

test('inviting an existing member does not mutate their access', () => {
  const db = makeDb();
  db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (1, 2, 'member', 'read')`
  ).run();

  assert.throws(
    () => createPendingHouseholdShare(db, {
      householdId: 1,
      email: 'partner@example.com',
      accessLevel: 'write',
      createdByUserId: 1
    }),
    /already part of this household/
  );
  assert.deepEqual(membership(db, 2), { role: 'member', access_level: 'read' });
});
