import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import {
  markMissingSimpleFinAccounts,
  parseRealEstateAccountFields,
  resolveAccountClosure
} from '../src/services/accountRealEstateService.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function insertAccount(db, values) {
  const res = db
    .prepare(
      `INSERT INTO accounts (
         household_id, name, type, institution, current_balance, estimated_value,
         is_property, property_name, linked_property_account_id,
         simplefin_account_id, is_manual, sort_order
       )
       VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    )
    .run(
      values.name,
      values.type || 'checking',
      values.institution ?? null,
      values.current_balance || 0,
      values.estimated_value ?? null,
      values.is_property ?? 0,
      values.property_name ?? null,
      values.linked_property_account_id ?? null,
      values.simplefin_account_id ?? null,
      values.is_manual ?? 1,
      values.sort_order || 0
    );
  return Number(res.lastInsertRowid);
}

test('markMissingSimpleFinAccounts flags disappeared account when institution synced cleanly, skips errored orgs', () => {
  const db = makeDb();
  try {
    const chaseCheckingId = insertAccount(db, {
      name: 'Chase Checking',
      type: 'checking',
      institution: 'Chase Bank',
      simplefin_account_id: 'sf-chase-chk',
      is_manual: 0
    });
    const parkRidgeId = insertAccount(db, {
      name: '2008 Park Ridge (8143)',
      type: 'mortgage',
      institution: 'Chase Bank',
      current_balance: -7457157,
      estimated_value: 30000000,
      simplefin_account_id: 'sf-chase-mtg',
      is_manual: 0
    });
    const amexCardId = insertAccount(db, {
      name: 'Amex Gold',
      type: 'credit',
      institution: 'American Express',
      simplefin_account_id: 'sf-amex-1',
      is_manual: 0
    });

    // Partial sync: Chase Checking succeeded, Chase Mortgage vanished, Amex had an org error
    const flagged = markMissingSimpleFinAccounts(db, {
      householdId: 1,
      seenAccountIds: new Set([chaseCheckingId]),
      syncedInstitutions: new Set(['Chase Bank']),
      sfErrors: ['Connection to American Express requires re-authentication']
    });

    assert.equal(flagged, 1);

    const parkRidge = db.prepare('SELECT * FROM accounts WHERE id = ?').get(parkRidgeId);
    const amex = db.prepare('SELECT * FROM accounts WHERE id = ?').get(amexCardId);
    assert.ok(parkRidge.simplefin_missing_since, 'Closed Chase mortgage should be flagged missing');
    assert.equal(amex.simplefin_missing_since, null, 'Errored Amex institution must not false-trigger closure');
  } finally {
    db.close();
  }
});

test('resolveAccountClosure sold_property archives property anchor and linked child loans while preserving transactions', () => {
  const db = makeDb();
  try {
    const parkRidgeId = insertAccount(db, {
      name: '2008 Park Ridge (8143)',
      type: 'mortgage',
      institution: 'Chase Bank',
      current_balance: -7457157,
      estimated_value: 30000000,
      simplefin_account_id: 'sf-chase-mtg',
      is_manual: 0
    });
    const helocId = insertAccount(db, {
      name: 'Park Ridge HELOC',
      type: 'loan',
      institution: 'Chase Bank',
      current_balance: -500000,
      linked_property_account_id: parkRidgeId
    });
    db.prepare(
      `INSERT INTO transactions (household_id, account_id, date, amount, original_merchant, source)
       VALUES (1, ?, '2026-10-20', 7425542, 'Sale of Park Ridge', 'simplefin')`
    ).run(parkRidgeId);

    const { updatedAccount, createdProperty } = resolveAccountClosure(db, {
      householdId: 1,
      accountId: parkRidgeId,
      resolution: 'sold_property'
    });

    assert.equal(createdProperty, null);
    assert.equal(updatedAccount.is_archived, 1);

    const heloc = db.prepare('SELECT * FROM accounts WHERE id = ?').get(helocId);
    assert.equal(heloc.is_archived, 1);

    const txCount = db
      .prepare('SELECT COUNT(*) AS c FROM transactions WHERE account_id = ?')
      .get(parkRidgeId).c;
    assert.equal(txCount, 1);
  } finally {
    db.close();
  }
});

test('resolveAccountClosure paid_off_keep_property splits standalone property and archives paid-off mortgage', () => {
  const db = makeDb();
  try {
    const doubletreeId = insertAccount(db, {
      name: '2104 Doubletree Lane',
      type: 'mortgage',
      institution: 'New American Funding',
      current_balance: -39677450,
      estimated_value: 45000000,
      simplefin_account_id: 'sf-naf-1',
      is_manual: 0,
      sort_order: 40
    });
    const helocId = insertAccount(db, {
      name: 'Doubletree HELOC',
      type: 'loan',
      institution: 'Chase Bank',
      current_balance: -1000000,
      linked_property_account_id: doubletreeId
    });

    const { updatedAccount, createdProperty } = resolveAccountClosure(db, {
      householdId: 1,
      accountId: doubletreeId,
      resolution: 'paid_off_keep_property'
    });

    assert.ok(createdProperty, 'Standalone property row should be created');
    assert.equal(createdProperty.name, '2104 Doubletree Lane');
    assert.equal(createdProperty.type, 'other');
    assert.equal(createdProperty.is_property, 1);
    assert.equal(createdProperty.estimated_value, 45000000);
    assert.equal(createdProperty.is_archived, 0);

    assert.equal(updatedAccount.is_archived, 1);
    assert.equal(updatedAccount.estimated_value, null);
    assert.equal(updatedAccount.linked_property_account_id, createdProperty.id);

    const heloc = db.prepare('SELECT * FROM accounts WHERE id = ?').get(helocId);
    assert.equal(heloc.linked_property_account_id, createdProperty.id);
    assert.equal(heloc.is_archived, 0);
  } finally {
    db.close();
  }
});

test('parseRealEstateAccountFields prevents self-links and non-loan links', () => {
  const db = makeDb();
  try {
    const propId = insertAccount(db, {
      name: '2104 Doubletree Lane',
      type: 'other',
      is_property: 1,
      estimated_value: 45000000
    });

    assert.throws(
      () =>
        parseRealEstateAccountFields(db, {
          householdId: 1,
          accountId: propId,
          effectiveType: 'loan',
          body: { linked_property_account_id: propId }
        }),
      /cannot be linked to itself/i
    );

    assert.throws(
      () =>
        parseRealEstateAccountFields(db, {
          householdId: 1,
          accountId: null,
          effectiveType: 'checking',
          body: { linked_property_account_id: propId }
        }),
      /Only loan or mortgage/i
    );

    const valid = parseRealEstateAccountFields(db, {
      householdId: 1,
      accountId: null,
      effectiveType: 'mortgage',
      body: { linked_property_account_id: propId, estimated_value: 450000 }
    });
    assert.equal(valid.linked_property_account_id, propId);
    assert.equal(valid.estimated_value, 45000000);
  } finally {
    db.close();
  }
});
