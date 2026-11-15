import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import {
  buildRetirementHistory,
  parseAccountIdsQuery
} from '../src/services/retirementHistoryService.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, { householdId = 1, name, type = 'investment', balanceCents }) {
  return Number(
    db
      .prepare(
        `INSERT INTO accounts (household_id, name, type, current_balance, is_manual, is_archived, sort_order)
         VALUES (?, ?, ?, ?, 1, 0, 0)`
      )
      .run(householdId, name, type, balanceCents).lastInsertRowid
  );
}

test('parseAccountIdsQuery handles undefined, empty, valid CSV, and invalid inputs', () => {
  assert.equal(parseAccountIdsQuery(undefined), null);
  assert.equal(parseAccountIdsQuery(null), null);
  assert.deepEqual(parseAccountIdsQuery(''), []);
  assert.deepEqual(parseAccountIdsQuery('  '), []);
  assert.deepEqual(parseAccountIdsQuery('12, 10, 12'), [12, 10]);
  assert.throws(() => parseAccountIdsQuery('10,abc'), { status: 400 });
  assert.throws(() => parseAccountIdsQuery('0'), { status: 400 });
  assert.throws(() => parseAccountIdsQuery('-5'), { status: 400 });
});

test('buildRetirementHistory falls back to investment accounts when unlinked and supports custom accountIds', () => {
  const db = makeDb();
  try {
    const brokerId = insertAccount(db, { name: 'Vanguard Taxable', type: 'investment', balanceCents: 25_000_00 });
    const hsaId = insertAccount(db, { name: 'HealthEquity HSA', type: 'savings', balanceCents: 10_000_00 });

    db.prepare(
      `INSERT INTO account_balance_records (household_id, account_id, record_date, balance)
       VALUES (1, ?, '2026-09-15', 2200000), (1, ?, '2026-09-15', 900000)`
    ).run(brokerId, hsaId);

    // Auto default without household_retirement_accounts falls back to active investment accounts
    const autoDefault = buildRetirementHistory(db, 1, 24, null);
    assert.deepEqual(
      autoDefault.accounts.map((a) => a.id),
      [brokerId]
    );
    const septAuto = autoDefault.history.find((h) => h.month === '2026-09');
    assert.equal(septAuto.balance, 22000);

    // Custom account selection includes non-investment HSA savings + brokerage history
    const customBoth = buildRetirementHistory(db, 1, 24, [brokerId, hsaId]);
    assert.deepEqual(
      customBoth.accounts.map((a) => a.id).sort((a, b) => a - b),
      [brokerId, hsaId]
    );
    const septBoth = customBoth.history.find((h) => h.month === '2026-09');
    assert.equal(septBoth.balance, 31000);

    // Custom selection limited to HSA returns only HSA history
    const hsaOnly = buildRetirementHistory(db, 1, 24, [hsaId]);
    assert.deepEqual(
      hsaOnly.accounts.map((a) => a.id),
      [hsaId]
    );
    const septHsa = hsaOnly.history.find((h) => h.month === '2026-09');
    assert.equal(septHsa.balance, 9000);

    // Explicit empty list returns empty accounts and history
    const emptyRes = buildRetirementHistory(db, 1, 24, []);
    assert.deepEqual(emptyRes, { accounts: [], history: [] });
  } finally {
    db.close();
  }
});
