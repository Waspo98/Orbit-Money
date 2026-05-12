import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import { mergeAccounts } from '../src/services/accountMerge.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, values) {
  return db
    .prepare(
      `INSERT INTO accounts (
         household_id, name, type, current_balance, estimated_value,
         simplefin_account_id, is_manual, sort_order
       )
       VALUES (1, ?, ?, ?, ?, ?, ?, ?)`
    )
    .run(
      values.name,
      values.type || 'checking',
      values.current_balance || 0,
      values.estimated_value ?? null,
      values.simplefin_account_id ?? null,
      values.is_manual ?? 1,
      values.sort_order || 0
    ).lastInsertRowid;
}

test('mergeAccounts preserves account-owned records before deleting the source account', () => {
  const db = makeDb();
  try {
    const targetId = insertAccount(db, {
      name: 'Target Card',
      type: 'credit',
      current_balance: 9000,
      sort_order: 10
    });
    const sourceId = insertAccount(db, {
      name: 'Source Card',
      type: 'credit',
      current_balance: 12000,
      estimated_value: 80000,
      simplefin_account_id: 'sf-source',
      sort_order: 20
    });

    db.prepare(
      `INSERT INTO transactions (household_id, account_id, date, amount, original_merchant, source)
       VALUES (1, ?, '2026-05-01', -1200, 'Coffee', 'manual')`
    ).run(sourceId);
    db.prepare(
      `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
       VALUES (1, ?, ?, ?, ?)`
    ).run(targetId, '2026-04-30', 9000, 'simplefin');
    db.prepare(
      `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
       VALUES (1, ?, ?, ?, ?)`
    ).run(sourceId, '2026-04-30', 9500, 'manual');
    db.prepare(
      `INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
       VALUES (1, ?, ?, ?, ?)`
    ).run(sourceId, '2026-05-31', 12000, 'manual');

    const linkedGoalId = db
      .prepare(
        `INSERT INTO goals (household_id, name, target_amount, current_amount, linked_account_id)
         VALUES (1, 'Linked', 100000, 0, ?)`
      )
      .run(sourceId).lastInsertRowid;
    db.prepare(
      `INSERT INTO goal_account_allocations (
         household_id, goal_id, account_id, allocation_type,
         allocation_percent, allocation_amount, reserve_amount
       )
       VALUES (1, ?, ?, 'fixed', 0, 2000, 0)`
    ).run(linkedGoalId, sourceId);

    const sharedGoalId = db
      .prepare(
        `INSERT INTO goals (household_id, name, target_amount, current_amount)
         VALUES (1, 'Shared', 100000, 0)`
      )
      .run().lastInsertRowid;
    db.prepare(
      `INSERT INTO goal_account_allocations (
         household_id, goal_id, account_id, allocation_type,
         allocation_percent, allocation_amount, reserve_amount
       )
       VALUES (1, ?, ?, 'fixed', 0, 1000, 0)`
    ).run(sharedGoalId, targetId);
    db.prepare(
      `INSERT INTO goal_account_allocations (
         household_id, goal_id, account_id, allocation_type,
         allocation_percent, allocation_amount, reserve_amount
       )
       VALUES (1, ?, ?, 'fixed', 0, 1500, 0)`
    ).run(sharedGoalId, sourceId);

    const memberA = db
      .prepare("INSERT INTO household_members (household_id, name) VALUES (1, 'Alex')")
      .run().lastInsertRowid;
    const memberB = db
      .prepare("INSERT INTO household_members (household_id, name) VALUES (1, 'Sam')")
      .run().lastInsertRowid;
    db.prepare(
      `INSERT INTO household_retirement_accounts (household_id, member_id, account_id, account_kind)
       VALUES (1, ?, ?, '401k')`
    ).run(memberA, targetId);
    db.prepare(
      `INSERT INTO household_retirement_accounts (household_id, member_id, account_id, account_kind)
       VALUES (1, ?, ?, '401k')`
    ).run(memberA, sourceId);
    db.prepare(
      `INSERT INTO household_retirement_accounts (household_id, member_id, account_id, account_kind)
       VALUES (1, ?, ?, 'ira')`
    ).run(memberB, sourceId);

    db.prepare(
      `INSERT INTO upcoming_items (
         household_id, name, kind, amount, direction, frequency_type, next_date, account_id
       )
       VALUES (1, 'Annual Fee', 'bill', 9500, 'expense', 'yearly', '2026-12-01', ?)`
    ).run(sourceId);

    db.prepare(
      `INSERT INTO credit_card_profiles (
         household_id, account_id, issuer_name, annual_fee,
         authorized_users_json, reward_categories_json, benefits_json
       )
       VALUES (1, ?, 'Target Bank', 0, '["Alex"]', '[]', '[{"name":"Warranty"}]')`
    ).run(targetId);
    db.prepare(
      `INSERT INTO credit_card_profiles (
         household_id, account_id, card_name, issuer_name, annual_fee,
         authorized_users_json, reward_categories_json, benefits_json, notes
       )
       VALUES (
         1, ?, 'Orbit Rewards', 'Source Bank', 9500,
         '["Alex","Sam"]',
         '[{"label":"Groceries","rate":3}]',
         '[{"name":"Warranty"},{"name":"Travel"}]',
         'source note'
       )`
    ).run(sourceId);

    const result = mergeAccounts(db, { householdId: 1, sourceId, targetId });

    assert.equal(result.transactionsMoved, 1);
    assert.equal(result.balanceRecordsMoved, 1);
    assert.equal(result.balanceRecordConflicts, 1);
    assert.equal(result.balanceRecordsReplaced, 1);
    assert.equal(result.goalAllocationsMoved, 1);
    assert.equal(result.goalAllocationConflicts, 1);
    assert.equal(result.retirementLinksMoved, 1);
    assert.equal(result.retirementLinkDuplicates, 1);
    assert.equal(result.creditCardProfileMerged, true);

    assert.equal(
      db.prepare('SELECT COUNT(*) AS count FROM accounts WHERE id = ?').get(sourceId).count,
      0
    );
    assert.equal(
      db.prepare('SELECT account_id FROM transactions WHERE original_merchant = ?').get('Coffee').account_id,
      targetId
    );
    assert.equal(
      db.prepare('SELECT linked_account_id FROM goals WHERE id = ?').get(linkedGoalId).linked_account_id,
      targetId
    );
    assert.equal(
      db.prepare('SELECT account_id FROM upcoming_items WHERE name = ?').get('Annual Fee').account_id,
      targetId
    );

    const target = db.prepare('SELECT * FROM accounts WHERE id = ?').get(targetId);
    assert.equal(target.current_balance, 12000);
    assert.equal(target.estimated_value, 80000);
    assert.equal(target.simplefin_account_id, 'sf-source');
    assert.equal(target.is_manual, 1);

    const replacedBalance = db
      .prepare(
        `SELECT balance, source
           FROM account_balance_records
          WHERE account_id = ?
            AND record_date = '2026-04-30'`
      )
      .get(targetId);
    assert.deepEqual(replacedBalance, { balance: 9500, source: 'manual' });
    assert.equal(
      db
        .prepare(
          `SELECT balance
             FROM account_balance_records
            WHERE account_id = ?
              AND record_date = '2026-05-31'`
        )
        .get(targetId).balance,
      12000
    );

    assert.equal(
      db
        .prepare(
          `SELECT account_id
             FROM goal_account_allocations
            WHERE goal_id = ?
              AND allocation_amount = 2000`
        )
        .get(linkedGoalId).account_id,
      targetId
    );
    assert.equal(
      db
        .prepare(
          `SELECT allocation_type, allocation_amount
             FROM goal_account_allocations
            WHERE goal_id = ?
              AND account_id = ?`
        )
        .get(sharedGoalId, targetId).allocation_amount,
      2500
    );
    assert.equal(
      db
        .prepare(
          `SELECT COUNT(*) AS count
             FROM household_retirement_accounts
            WHERE account_id = ?`
        )
        .get(targetId).count,
      2
    );

    const profile = db
      .prepare('SELECT * FROM credit_card_profiles WHERE account_id = ?')
      .get(targetId);
    assert.equal(profile.card_name, 'Orbit Rewards');
    assert.equal(profile.issuer_name, 'Target Bank');
    assert.equal(profile.annual_fee, 0);
    assert.deepEqual(JSON.parse(profile.authorized_users_json), ['Alex', 'Sam']);
    assert.deepEqual(JSON.parse(profile.reward_categories_json), [{ label: 'Groceries', rate: 3 }]);
    assert.deepEqual(JSON.parse(profile.benefits_json), [
      { name: 'Warranty' },
      { name: 'Travel' }
    ]);
  } finally {
    db.close();
  }
});
