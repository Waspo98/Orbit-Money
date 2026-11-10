import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';
import express from 'express';

import { runMigrations } from '../src/db/migrations.js';
import { applyBenefitUsage } from '../src/services/creditCardBenefitUsage.js';
import accountsRouter from '../src/routes/accounts.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, householdId = 1, name = 'Test Credit Card') {
  return db
    .prepare(
      `INSERT INTO accounts (household_id, name, type, current_balance, is_manual, sort_order)
       VALUES (?, ?, 'credit', 0, 1, 0)`
    )
    .run(householdId, name).lastInsertRowid;
}

function insertProfile(db, accountId, householdId = 1, benefits = []) {
  db.prepare(
    `INSERT INTO credit_card_profiles (
       household_id, account_id, card_name, issuer_name, annual_fee,
       authorized_users_json, reward_categories_json, benefits_json, strategy_json
     )
     VALUES (?, ?, 'Test Card', 'Test Bank', 0, '[]', '[]', ?, '{}')`
  ).run(householdId, accountId, JSON.stringify(benefits));
}

test('applyBenefitUsage handles malformed JSON in benefits_json gracefully', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  db.prepare(
    `INSERT INTO credit_card_profiles (
       household_id, account_id, card_name, issuer_name, annual_fee,
       authorized_users_json, reward_categories_json, benefits_json, strategy_json
     )
     VALUES (1, ?, 'Card', 'Bank', 0, '[]', '[]', '{malformed json}', '{}')`
  ).run(accountId);

  const res = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_any',
    periodKey: '2026-11',
    amount: 10
  });
  assert.equal(res, null);
});

test('applyBenefitUsage handles non-array usage gracefully', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, 1, [
    { id: 'b_corrupt_usage', title: 'Perk', usage: 'not-an-array' }
  ]);

  const updated = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_corrupt_usage',
    periodKey: '2026-11',
    amount: 25.5
  });
  assert.ok(updated);
  const benefits = JSON.parse(updated.benefits_json);
  const benefit = benefits.find((b) => b.id === 'b_corrupt_usage');
  assert.equal(benefit.usage.length, 1);
  assert.equal(benefit.usage[0].amount, 25.5);
});

test('applyBenefitUsage enforces household isolation', () => {
  const db = makeDb();
  db.prepare("INSERT INTO households (name) VALUES ('H2')").run();
  // Insert account for household 2
  const accountId = insertAccount(db, 2, 'Household 2 Card');
  insertProfile(db, accountId, 2, [
    { id: 'b_h2', title: 'H2 Perk', usage: [] }
  ]);

  // Household 1 tries to mutate Household 2's card
  const crossResult = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_h2',
    periodKey: '2026-11',
    amount: 50
  });
  assert.equal(crossResult, null);

  // Household 2 succeeds
  const validResult = applyBenefitUsage(db, {
    householdId: 2,
    accountId,
    benefitId: 'b_h2',
    periodKey: '2026-11',
    amount: 50
  });
  assert.ok(validResult);
});

test('applyBenefitUsage rounds amount to integer cents', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, 1, [
    { id: 'b_cents', title: 'Cents perk', usage: [] }
  ]);

  const updated = applyBenefitUsage(db, {
    householdId: 1,
    accountId,
    benefitId: 'b_cents',
    periodKey: '2026-11',
    amount: 12.3456
  });
  assert.ok(updated);
  const benefits = JSON.parse(updated.benefits_json);
  assert.equal(benefits[0].usage[0].amount, 12.35);
});

test('applyBenefitUsage stress test: 200 mutations retain exactly last 120', () => {
  const db = makeDb();
  const accountId = insertAccount(db);
  insertProfile(db, accountId, 1, [
    { id: 'b_stress', title: 'Stress Perk', usage: [] }
  ]);

  for (let i = 1; i <= 200; i++) {
    applyBenefitUsage(db, {
      householdId: 1,
      accountId,
      benefitId: 'b_stress',
      periodKey: `p_${i}`,
      amount: i
    });
  }

  const row = db
    .prepare('SELECT benefits_json FROM credit_card_profiles WHERE account_id = ?')
    .get(accountId);
  const benefits = JSON.parse(row.benefits_json);
  assert.equal(benefits[0].usage.length, 120);
  assert.equal(benefits[0].usage[0].period_key, 'p_81');
  assert.equal(benefits[0].usage[119].period_key, 'p_200');
});

// -----------------------------------------------------------------------------
// Route Boundary Validation Tests
// -----------------------------------------------------------------------------

function setupTestServer(householdId = 1) {
  const app = express();
  app.use(express.json());

  // Mock requireAuth and requireHouseholdId
  app.use((req, res, next) => {
    req.session = { userId: 1, householdId };
    req.user = { id: 1, householdId };
    next();
  });

  app.use('/api/accounts', accountsRouter);
  return app;
}

import { config } from '../src/config.js';

test('Route PUT /:id/credit-card-profile/benefits/:benefitId/usage boundary validation', async (t) => {
  config.apiKey = 'test-api-key';
  // First, we must ensure our shared db instance has test data
  const { db, runMigrations: runSharedMigrations } = await import('../src/db/index.js');
  runSharedMigrations();
  const accountId = insertAccount(db, 1, 'Route Test Card');
  insertProfile(db, accountId, 1, [
    { id: 'b_route_test', title: 'Route Test Perk', usage: [] }
  ]);

  const app = express();
  app.use(express.json());
  app.use('/api/accounts', accountsRouter);

  const server = app.listen(0);
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}/api/accounts/${accountId}/credit-card-profile/benefits/b_route_test/usage`;
  const defaultHeaders = {
    'Content-Type': 'application/json',
    'X-API-Key': 'test-api-key',
    'X-Household-ID': '1'
  };

  t.after(() => server.close());

  // Test valid period_keys
  const validPeriodKeys = [
    '2026',
    '2026-01',
    '2026-12',
    '2026-Q1',
    '2026-Q2',
    '2026-Q3',
    '2026-Q4',
    '2026-H1',
    '2026-H2',
    'CM-2026-04-18'
  ];

  for (const periodKey of validPeriodKeys) {
    const res = await fetch(baseUrl, {
      method: 'PUT',
      headers: defaultHeaders,
      body: JSON.stringify({ period_key: periodKey, amount: 15.0 })
    });
    assert.equal(res.status, 200, `Expected 200 for valid periodKey: ${periodKey}`);
    const data = await res.json();
    assert.equal(data.success, true);
  }

  // Test invalid period_keys -> must return 400
  const invalidPeriodKeys = [
    '',
    '   ',
    '202',
    '20265',
    '2026-Q0',
    '2026-Q5',
    '2026-H0',
    '2026-H3',
    'CM-2026',
    'CM-2026-04',
    'CM-2026-4-18',
    'CM-invalid',
    "' OR '1'='1",
    '2026-11; DROP TABLE accounts;'
  ];

  for (const periodKey of invalidPeriodKeys) {
    const res = await fetch(baseUrl, {
      method: 'PUT',
      headers: defaultHeaders,
      body: JSON.stringify({ period_key: periodKey, amount: 15.0 })
    });
    assert.equal(res.status, 400, `Expected 400 for invalid periodKey: '${periodKey}'`);
  }

  // Test invalid amounts -> must return 400
  const invalidAmounts = [
    -1,
    -0.01,
    'not-a-number',
    'Infinity',
    '-Infinity'
  ];

  for (const amt of invalidAmounts) {
    const res = await fetch(baseUrl, {
      method: 'PUT',
      headers: defaultHeaders,
      body: JSON.stringify({ period_key: '2026-11', amount: amt })
    });
    assert.equal(res.status, 400, `Expected 400 for invalid amount: ${amt}`);
  }

  // Test valid amounts (null and 0 delete usage)
  const clearRes1 = await fetch(baseUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-11', amount: null })
  });
  assert.equal(clearRes1.status, 200);

  const clearRes2 = await fetch(baseUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-Q1', amount: 0 })
  });
  assert.equal(clearRes2.status, 200);

  // Test used_on validation
  const validDates = ['2026-11-09', null, ''];
  for (const d of validDates) {
    const res = await fetch(baseUrl, {
      method: 'PUT',
      headers: defaultHeaders,
      body: JSON.stringify({ period_key: '2026-11', amount: 10, used_on: d })
    });
    assert.equal(res.status, 200, `Expected 200 for used_on: ${d}`);
  }

  const invalidDates = ['not-a-date', '2026-02-31', '2026-13-01', '2026-11-32'];
  for (const d of invalidDates) {
    const res = await fetch(baseUrl, {
      method: 'PUT',
      headers: defaultHeaders,
      body: JSON.stringify({ period_key: '2026-11', amount: 10, used_on: d })
    });
    assert.equal(res.status, 400, `Expected 400 for invalid used_on: ${d}`);
  }

  // Test benefitId validation
  const invalidBenefitIdUrl = `http://127.0.0.1:${port}/api/accounts/${accountId}/credit-card-profile/benefits/${'a'.repeat(65)}/usage`;
  const longIdRes = await fetch(invalidBenefitIdUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-11', amount: 10 })
  });
  assert.equal(longIdRes.status, 400);

  // Test non-existent benefitId -> 404
  const unknownBenefitUrl = `http://127.0.0.1:${port}/api/accounts/${accountId}/credit-card-profile/benefits/b_nonexistent/usage`;
  const notFoundBenefitRes = await fetch(unknownBenefitUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-11', amount: 10 })
  });
  assert.equal(notFoundBenefitRes.status, 404);

  // Test non-existent account -> 404
  const unknownAccountUrl = `http://127.0.0.1:${port}/api/accounts/9999999/credit-card-profile/benefits/b_route_test/usage`;
  const notFoundAccountRes = await fetch(unknownAccountUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-11', amount: 10 })
  });
  assert.equal(notFoundAccountRes.status, 404);

  // Test invalid account id format -> 400
  const invalidAccountUrl = `http://127.0.0.1:${port}/api/accounts/invalid_id/credit-card-profile/benefits/b_route_test/usage`;
  const badAccountRes = await fetch(invalidAccountUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify({ period_key: '2026-11', amount: 10 })
  });
  assert.equal(badAccountRes.status, 400);
});

test('Route PUT /:id/credit-card-profile sanitizes benefits and assigns stable IDs', async (t) => {
  const { db } = await import('../src/db/index.js');
  const accountId = insertAccount(db, 1, 'Profile Normalization Card');

  const app = express();
  app.use(express.json());
  app.use('/api/accounts', accountsRouter);

  const server = app.listen(0);
  const port = server.address().port;
  t.after(() => server.close());

  const profileUrl = `http://127.0.0.1:${port}/api/accounts/${accountId}/credit-card-profile`;
  const defaultHeaders = {
    'Content-Type': 'application/json',
    'X-API-Key': 'test-api-key',
    'X-Household-ID': '1'
  };

  const payload = {
    account_name: 'Chase Sapphire Reserve',
    card_name: 'Sapphire Reserve',
    issuer_name: 'Chase',
    benefits: [
      // 1. String-only legacy benefit
      'Simple String Perk',
      // 2. Invalid benefit ID with bad characters and negative period_value
      {
        id: 'bad id with spaces / paths',
        title: 'Malformed ID Perk',
        period_value: -50,
        anchor: 'invalid_anchor',
        usage: [
          { period_key: '2026-11', amount: 10.555 },
          { period_key: 'invalid_period', amount: 50 },
          { period_key: '2026-12', amount: -20 }
        ]
      },
      // 3. Valid v2 benefit
      {
        id: 'csr-doordash',
        title: '$5 DoorDash Credit',
        period_value: 5.0,
        anchor: 'calendar',
        cadence: 'monthly',
        usage: [
          { period_key: '2026-11', amount: 5.0, used_on: '2026-11-01' }
        ]
      }
    ]
  };

  const res = await fetch(profileUrl, {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify(payload)
  });
  assert.equal(res.status, 200);

  const data = await res.json();
  assert.equal(data.success, true);
  const benefits = data.profile.benefits;
  assert.equal(benefits.length, 3);

  // 1. String perk got auto-assigned id
  assert.ok(benefits[0].id.startsWith('b_'));
  assert.equal(benefits[0].title, 'Simple String Perk');
  assert.equal(benefits[0].anchor, 'calendar');

  // 2. Bad ID got replaced with valid b_<uuid12>
  assert.ok(benefits[1].id.startsWith('b_'));
  assert.notEqual(benefits[1].id, 'bad id with spaces / paths');
  assert.equal(benefits[1].period_value, null); // negative converted to null
  assert.equal(benefits[1].anchor, 'calendar'); // invalid converted to calendar
  assert.equal(benefits[1].usage.length, 1); // invalid period & negative amount filtered
  assert.equal(benefits[1].usage[0].period_key, '2026-11');
  assert.equal(benefits[1].usage[0].amount, 10.56); // rounded to cents

  // 3. Valid v2 preserved
  assert.equal(benefits[2].id, 'csr-doordash');
  assert.equal(benefits[2].period_value, 5.0);
  assert.equal(benefits[2].anchor, 'calendar');
  assert.equal(benefits[2].usage.length, 1);
  assert.equal(benefits[2].usage[0].amount, 5.0);
});

