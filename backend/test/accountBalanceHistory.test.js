import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import {
  captureAllHouseholdsDailyCheckpoints,
  deleteAccountBalanceRecord,
  listAccountBalanceRecords,
  listHouseholdBalanceRecords,
  recordManualAccountBalance,
  upsertAutoAccountBalance
} from '../src/services/accountBalanceRecordService.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, values) {
  return Number(
    db
      .prepare(
        `INSERT INTO accounts (
           household_id, name, type, current_balance, estimated_value,
           is_property, simplefin_account_id, is_manual, is_archived, sort_order
         )
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(
        values.household_id || 1,
        values.name,
        values.type || 'checking',
        values.current_balance || 0,
        values.estimated_value ?? null,
        values.is_property ?? 0,
        values.simplefin_account_id ?? null,
        values.is_manual ?? 1,
        values.is_archived ?? 0,
        values.sort_order || 0
      ).lastInsertRowid
  );
}

test('upsertAutoAccountBalance deduplicates unchanged same-month snapshots and writes on-change + monthly anchors', () => {
  const db = makeDb();
  try {
    const checkingId = insertAccount(db, {
      name: 'Main Checking',
      type: 'checking',
      current_balance: 250000
    });
    const zeroManualId = insertAccount(db, {
      name: 'Empty CSV Placeholder',
      type: 'checking',
      current_balance: 0
    });
    const zeroSfId = insertAccount(db, {
      name: 'Zero Credit Card',
      type: 'credit',
      current_balance: 0,
      simplefin_account_id: 'sf-zero-card',
      is_manual: 0
    });

    // Zero manual placeholder with no prior records is skipped
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: zeroManualId,
        recordDate: '2026-04-01',
        balanceCents: 0,
        source: 'manual'
      }).recorded,
      false
    );

    // Zero SimpleFIN account DOES record initial baseline
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: zeroSfId,
        recordDate: '2026-04-01',
        balanceCents: 0,
        source: 'simplefin'
      }).recorded,
      true
    );

    // Initial baseline recorded for non-zero checking
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: checkingId,
        recordDate: '2026-04-01',
        balanceCents: 250000,
        source: 'manual'
      }).recorded,
      true
    );

    // Unchanged balance later in same month -> skipped (0 duplicate rows)
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: checkingId,
        recordDate: '2026-04-15',
        balanceCents: 250000,
        source: 'manual'
      }).recorded,
      false
    );

    // Changed balance in same month -> recorded
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: checkingId,
        recordDate: '2026-04-20',
        balanceCents: 310000,
        source: 'manual'
      }).recorded,
      true
    );

    // Same-day balance change updates existing row in place rather than adding a row
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: checkingId,
        recordDate: '2026-04-20',
        balanceCents: 325000,
        source: 'manual'
      }).recorded,
      true
    );

    // Unchanged balance in a NEW calendar month -> monthly anchor recorded
    assert.equal(
      upsertAutoAccountBalance(db, {
        householdId: 1,
        accountId: checkingId,
        recordDate: '2026-05-01',
        balanceCents: 325000,
        source: 'manual'
      }).recorded,
      true
    );

    const checkingRecords = listAccountBalanceRecords(db, { householdId: 1, accountId: checkingId });
    assert.deepEqual(
      checkingRecords.map((r) => [r.record_date, r.balance, r.source]),
      [
        ['2026-05-01', 3250, 'manual'],
        ['2026-04-20', 3250, 'manual'],
        ['2026-04-01', 2500, 'manual']
      ]
    );
  } finally {
    db.close();
  }
});

test('manual balance records take precedence over same-day SimpleFIN snapshots and list/delete helpers work', () => {
  const db = makeDb();
  try {
    const acctId = insertAccount(db, {
      name: 'Brokerage',
      type: 'investment',
      current_balance: 500000,
      simplefin_account_id: 'sf-brok',
      is_manual: 0
    });

    recordManualAccountBalance(db, {
      householdId: 1,
      accountId: acctId,
      recordDate: '2026-06-10',
      balanceCents: 525000
    });

    // SimpleFIN sync on the same date must not overwrite manual entry
    const sfAttempt = upsertAutoAccountBalance(db, {
      householdId: 1,
      accountId: acctId,
      recordDate: '2026-06-10',
      balanceCents: 510000,
      source: 'simplefin'
    });
    assert.equal(sfAttempt.recorded, false);

    const all = listHouseholdBalanceRecords(db, 1);
    assert.equal(all.length, 1);
    assert.equal(all[0].balance, 5250);
    assert.equal(all[0].source, 'manual');

    assert.equal(deleteAccountBalanceRecord(db, { householdId: 1, accountId: acctId, recordId: all[0].id }), true);
    assert.equal(listHouseholdBalanceRecords(db, 1).length, 0);
  } finally {
    db.close();
  }
});

test('captureAllHouseholdsDailyCheckpoints skips archived, standalone properties, and sample households', () => {
  const db = makeDb();
  try {
    insertAccount(db, { name: 'Active Savings', type: 'savings', current_balance: 100000 });
    insertAccount(db, { name: 'Archived Savings', type: 'savings', current_balance: 90000, is_archived: 1 });
    insertAccount(db, { name: 'Primary Home', type: 'other', current_balance: 0, estimated_value: 45000000, is_property: 1 });

    // Create a sample household (#2) owned exclusively by sample:guest
    const sampleHhId = Number(db.prepare("INSERT INTO households (name) VALUES ('Sample')").run().lastInsertRowid);
    const sampleUserId = Number(db.prepare("INSERT INTO users (username, display_name) VALUES ('sample:demo1', 'Demo')").run().lastInsertRowid);
    db.prepare("INSERT INTO household_memberships (household_id, user_id, role) VALUES (?, ?, 'owner')").run(sampleHhId, sampleUserId);
    insertAccount(db, { household_id: sampleHhId, name: 'Sample Checking', type: 'checking', current_balance: 88800 });

    const firstPass = captureAllHouseholdsDailyCheckpoints(db, '2026-07-01');
    assert.equal(firstPass.checked, 1);
    assert.equal(firstPass.recorded, 1);

    // Second pass on same day is a pure no-op
    const secondPass = captureAllHouseholdsDailyCheckpoints(db, '2026-07-01');
    assert.equal(secondPass.recorded, 0);

    assert.equal(listHouseholdBalanceRecords(db, 1).length, 1);
    assert.equal(listHouseholdBalanceRecords(db, sampleHhId).length, 0);
  } finally {
    db.close();
  }
});
