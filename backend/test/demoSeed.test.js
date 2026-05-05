import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import { buildDemoTransactions, seedDemoDataForHousehold } from '../src/services/demoSeed.js';
import { MHA_TAX_PROFILE_KEY } from '../src/services/mhaTaxEstimate.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

test('buildDemoTransactions creates a rolling complete 12-month sample year', () => {
  const transactions = buildDemoTransactions(new Date('2026-05-05T12:00:00Z'));
  const months = new Set(transactions.map((txn) => txn.date.slice(0, 7)));

  assert.equal(months.size, 12);
  assert.equal([...months].sort()[0], '2025-05');
  assert.equal([...months].sort().at(-1), '2026-04');
  assert.ok(transactions.every((txn) => txn.date >= '2025-05-01' && txn.date <= '2026-04-30'));
});

test('seedDemoDataForHousehold creates household members, MHA settings, and 12 months of transactions', () => {
  const db = makeDb();
  try {
    const result = seedDemoDataForHousehold(db, 1, {
      now: new Date('2026-05-05T12:00:00Z')
    });

    const transactionStats = db
      .prepare(
        `SELECT COUNT(*) AS count,
                COUNT(DISTINCT substr(date, 1, 7)) AS months,
                MIN(date) AS minDate,
                MAX(date) AS maxDate
           FROM transactions
          WHERE household_id = 1
            AND external_id LIKE 'demo-txn-%'`
      )
      .get();
    const memberCount = db
      .prepare('SELECT COUNT(*) AS count FROM household_members WHERE household_id = 1')
      .get().count;
    const mhaEnabled = db
      .prepare("SELECT value FROM app_settings WHERE household_id = 1 AND key = 'mha_tracker_enabled'")
      .get().value;
    const taxProfile = JSON.parse(
      db
        .prepare('SELECT value FROM app_settings WHERE household_id = 1 AND key = ?')
        .get(MHA_TAX_PROFILE_KEY).value
    );
    const mhaCategoryCount = db
      .prepare('SELECT COUNT(*) AS count FROM categories WHERE household_id = 1 AND mha_default_eligible = 1')
      .get().count;

    assert.equal(result.householdMembers, 2);
    assert.equal(memberCount, 2);
    assert.equal(transactionStats.months, 12);
    assert.equal(transactionStats.minDate, '2025-05-01');
    assert.equal(transactionStats.maxDate, '2026-04-27');
    assert.equal(transactionStats.count, result.transactions);
    assert.equal(mhaEnabled, '1');
    assert.equal(taxProfile.mode, 'household');
    assert.ok(mhaCategoryCount >= 4);
  } finally {
    db.close();
  }
});
