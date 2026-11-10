import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import { applyBenefitUsage } from '../src/services/creditCardBenefitUsage.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, name = 'Test Credit Card') {
  return db
    .prepare(
      `INSERT INTO accounts (household_id, name, type, current_balance, is_manual, sort_order)
       VALUES (1, ?, 'credit', 0, 1, 0)`
    )
    .run(name).lastInsertRowid;
}

function insertProfile(db, accountId, benefits = []) {
  db.prepare(
    `INSERT INTO credit_card_profiles (
       household_id, account_id, card_name, issuer_name, annual_fee,
       authorized_users_json, reward_categories_json, benefits_json, strategy_json
     )
     VALUES (1, ?, 'Test Card', 'Test Bank', 0, '[]', '[]', ?, '{}')`
  ).run(accountId, JSON.stringify(benefits));
}

test('applyBenefitUsage inserts new usage entry', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, [
    { id: 'b_doordash', title: '$5 DoorDash', usage: [] }
  ]);

  const updated = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_doordash',
    periodKey: '2026-11',
    amount: 5.0,
    usedOn: '2026-11-09'
  });

  assert.ok(updated);
  const benefits = JSON.parse(updated.benefits_json);
  const benefit = benefits.find((b) => b.id === 'b_doordash');
  assert.equal(benefit.usage.length, 1);
  assert.deepEqual(benefit.usage[0], {
    period_key: '2026-11',
    amount: 5.0,
    used_on: '2026-11-09'
  });
});

test('applyBenefitUsage replaces existing usage for the same periodKey', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, [
    {
      id: 'b_dining',
      title: '$10 Dining',
      usage: [{ period_key: '2026-11', amount: 5.0, used_on: '2026-11-01' }]
    }
  ]);

  const updated = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_dining',
    periodKey: '2026-11',
    amount: 10.0,
    usedOn: '2026-11-09'
  });

  assert.ok(updated);
  const benefits = JSON.parse(updated.benefits_json);
  const benefit = benefits.find((b) => b.id === 'b_dining');
  assert.equal(benefit.usage.length, 1);
  assert.deepEqual(benefit.usage[0], {
    period_key: '2026-11',
    amount: 10.0,
    used_on: '2026-11-09'
  });
});

test('applyBenefitUsage removes usage when amount is null or 0', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, [
    {
      id: 'b_travel',
      title: '$300 Travel',
      usage: [
        { period_key: '2026', amount: 300.0, used_on: '2026-04-15' },
        { period_key: '2025', amount: 300.0, used_on: '2025-04-15' }
      ]
    }
  ]);

  // Remove via amount = null
  const afterNull = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_travel',
    periodKey: '2026',
    amount: null,
    usedOn: null
  });
  assert.ok(afterNull);
  let benefits = JSON.parse(afterNull.benefits_json);
  let benefit = benefits.find((b) => b.id === 'b_travel');
  assert.equal(benefit.usage.length, 1);
  assert.equal(benefit.usage[0].period_key, '2025');

  // Remove remaining via amount = 0
  const afterZero = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_travel',
    periodKey: '2025',
    amount: 0,
    usedOn: null
  });
  assert.ok(afterZero);
  benefits = JSON.parse(afterZero.benefits_json);
  benefit = benefits.find((b) => b.id === 'b_travel');
  assert.equal(benefit.usage.length, 0);
});

test('applyBenefitUsage caps usage array at 120 items keeping newest', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  const initialUsage = [];
  for (let i = 1; i <= 120; i++) {
    initialUsage.push({ period_key: `p_${i}`, amount: 1.0, used_on: null });
  }
  insertProfile(db, accountId, [
    { id: 'b_capped', title: 'Capped Perk', usage: initialUsage }
  ]);

  // Push 5 more items
  for (let i = 121; i <= 125; i++) {
    applyBenefitUsage(db, {
      householdId: 1,
      accountId,
      benefitId: 'b_capped',
      periodKey: `p_${i}`,
      amount: 1.0,
      usedOn: null
    });
  }

  const row = db
    .prepare('SELECT benefits_json FROM credit_card_profiles WHERE account_id = ?')
    .get(accountId);
  const benefits = JSON.parse(row.benefits_json);
  const benefit = benefits.find((b) => b.id === 'b_capped');
  assert.equal(benefit.usage.length, 120);
  assert.equal(benefit.usage[0].period_key, 'p_6');
  assert.equal(benefit.usage[119].period_key, 'p_125');
});

test('applyBenefitUsage returns null for unknown benefit or account', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, [
    { id: 'b_real', title: 'Real Perk', usage: [] }
  ]);

  // Unknown benefit
  const missingBenefit = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_nonexistent',
    periodKey: '2026-11',
    amount: 10.0
  });
  assert.equal(missingBenefit, null);

  // Unknown account
  const missingAccount = applyBenefitUsage(db, {
    householdId: 1,
    accountId: 99999,
    benefitId: 'b_real',
    periodKey: '2026-11',
    amount: 10.0
  });
  assert.equal(missingAccount, null);
});
