import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

process.env.SESSION_SECRET ??= 'test-session-secret-1234567890';
process.env.ADMIN_PASSWORD ??= 'test-admin-password-1234567890';
process.env.LOGO_DEV_PUBLISHABLE_KEY ??= 'pk_test_orbit';

const { runMigrations } = await import('../src/db/migrations.js');
const { attachInstitutionLogos } = await import('../src/services/institutionLogos.js');
const {
  attachMerchantLogos,
  overrideMerchantLogo,
  reportMerchantLogoStatus
} = await import('../src/services/merchantLogos.js');
const {
  isInstitutionScopedMerchant
} = await import('../src/services/merchantLogoClassifier.js');

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

function seedAccounts(db) {
  const chase = Number(
    db.prepare(
      `INSERT INTO accounts (household_id, name, type, institution, current_balance)
       VALUES (1, 'Chase Checking', 'checking', 'Chase Bank', 250000)`
    ).run().lastInsertRowid
  );
  const betterment = Number(
    db.prepare(
      `INSERT INTO accounts (household_id, name, type, institution, current_balance)
       VALUES (1, 'Betterment Cash', 'savings', 'Betterment', 850000)`
    ).run().lastInsertRowid
  );
  const amex = Number(
    db.prepare(
      `INSERT INTO accounts (household_id, name, type, institution, current_balance)
       VALUES (1, 'Amex Gold', 'credit', 'American Express', -42000)`
    ).run().lastInsertRowid
  );
  return { chase, betterment, amex };
}

function insertTxn(db, {
  accountId,
  date = '2026-11-01',
  amount = 1500,
  originalMerchant,
  originalDescription = null,
  editedMerchant = null,
  editedMerchantSource = null,
  isTransfer = 0,
  transferPairId = null
}) {
  return Number(
    db.prepare(
      `INSERT INTO transactions
        (household_id, account_id, date, amount, original_merchant, original_description,
         edited_merchant, edited_merchant_source, is_transfer, transfer_pair_id, source)
       VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'simplefin')`
    ).run(
      accountId,
      date,
      amount,
      originalMerchant,
      originalDescription,
      editedMerchant,
      editedMerchantSource,
      isTransfer,
      transferPairId
    ).lastInsertRowid
  );
}

test('isInstitutionScopedMerchant identifies bank-native terms without false positives on real brands', () => {
  assert.equal(isInstitutionScopedMerchant('Interest Payment'), true);
  assert.equal(isInstitutionScopedMerchant('Savings Interest Earned'), true);
  assert.equal(isInstitutionScopedMerchant('Dividend'), true);
  assert.equal(isInstitutionScopedMerchant('Annual Fee'), true);
  assert.equal(isInstitutionScopedMerchant('ATM Withdrawal'), true);
  assert.equal(isInstitutionScopedMerchant('Credit Card Payment'), true);
  assert.equal(isInstitutionScopedMerchant('Check #1042'), true);

  assert.equal(isInstitutionScopedMerchant('Target'), false);
  assert.equal(isInstitutionScopedMerchant('Credit Karma'), false);
  assert.equal(isInstitutionScopedMerchant('TransferWise'), false);
  assert.equal(isInstitutionScopedMerchant('Pinterest'), false);
});

test('interest payments on Chase and Betterment resolve to distinct institution logos even after renaming', () => {
  const db = makeDb();
  const { chase, betterment } = seedAccounts(db);

  const chaseTxnId = insertTxn(db, {
    accountId: chase,
    originalMerchant: 'JPMORGAN CHASE INT',
    editedMerchant: 'Interest Payment',
    editedMerchantSource: 'rule:1'
  });
  const bettermentTxnId = insertTxn(db, {
    accountId: betterment,
    originalMerchant: 'BETTERMENT INTEREST',
    editedMerchant: 'Interest Payment',
    editedMerchantSource: 'rule:1'
  });

  // Legacy accidental global cache entry from older flat-key engine should not leak Chase onto Betterment
  db.prepare(
    `INSERT INTO merchant_logo_cache (merchant_key, merchant_name, provider, provider_query, logo_url, status)
     VALUES ('interest-payment', 'Interest Payment', 'logo_dev', 'Interest Payment', 'https://img.logo.dev/chase.com?format=png', 'manual')`
  ).run();

  const rows = db.prepare(`
    SELECT id, account_id, COALESCE(edited_merchant, original_merchant) AS merchant,
           original_merchant, original_description, edited_merchant_source, transfer_pair_id
      FROM transactions
     WHERE id IN (?, ?)
     ORDER BY id ASC
  `).all(chaseTxnId, bettermentTxnId);

  const enriched = attachMerchantLogos(db, rows);
  assert.equal(enriched[0].merchant_logo.merchant_key, 'interest-payment@inst:chase.com');
  assert.equal(enriched[0].merchant_logo.scope, 'institution');
  assert.ok(enriched[0].merchant_logo.url.includes('chase.com'));

  assert.equal(enriched[1].merchant_logo.merchant_key, 'interest-payment@inst:betterment.com');
  assert.equal(enriched[1].merchant_logo.scope, 'institution');
  assert.ok(enriched[1].merchant_logo.url.includes('betterment.com'));
});

test('institution-scoped override or hide on Chase Interest does not affect Betterment Interest or Chase account logo', () => {
  const db = makeDb();
  const { chase, betterment } = seedAccounts(db);

  const chaseTxnId = insertTxn(db, { accountId: chase, originalMerchant: 'Interest Payment' });
  const bettermentTxnId = insertTxn(db, { accountId: betterment, originalMerchant: 'Interest Payment' });

  const customUrl = 'https://example.com/chase-interest-custom.png';
  const overrideRes = overrideMerchantLogo(db, {
    transactionId: chaseTxnId,
    scope: 'institution',
    logoUrl: customUrl
  });
  assert.equal(overrideRes.updated, true);
  assert.equal(overrideRes.merchant_logo.merchant_key, 'interest-payment@inst:chase.com');
  assert.equal(overrideRes.merchant_logo.scope, 'institution');

  let rows = db.prepare(`
    SELECT id, account_id, COALESCE(edited_merchant, original_merchant) AS merchant,
           original_merchant, original_description, edited_merchant_source, transfer_pair_id
      FROM transactions WHERE id IN (?, ?) ORDER BY id ASC
  `).all(chaseTxnId, bettermentTxnId);
  let enriched = attachMerchantLogos(db, rows);

  assert.equal(enriched[0].merchant_logo.url, customUrl);
  assert.equal(enriched[0].merchant_logo.status, 'manual');
  assert.ok(enriched[1].merchant_logo.url.includes('betterment.com'));
  assert.equal(enriched[1].merchant_logo.status, 'candidate');

  // Hide Chase interest payment (Use Category Icon) -> Betterment and Chase account logo remain intact
  overrideMerchantLogo(db, {
    transactionId: chaseTxnId,
    scope: 'institution',
    hide: true
  });

  enriched = attachMerchantLogos(db, rows);
  assert.equal(enriched[0].merchant_logo.status, 'hidden');
  assert.equal(enriched[0].merchant_logo.url, null);
  assert.ok(enriched[1].merchant_logo.url.includes('betterment.com'));

  const [chaseAccount] = attachInstitutionLogos(db, [
    { id: chase, name: 'Chase Checking', institution: 'Chase Bank' }
  ]);
  assert.equal(chaseAccount.institution_logo.status, 'candidate');
  assert.ok(chaseAccount.institution_logo.url.includes('chase.com'));
});

test('precedence cascade honors txn > institution > explicit global > institution fallback and clears shadowing on broaden', () => {
  const db = makeDb();
  const { chase, betterment } = seedAccounts(db);

  const chaseTxn1 = insertTxn(db, { accountId: chase, originalMerchant: 'Interest Payment' });
  const chaseTxn2 = insertTxn(db, { accountId: chase, originalMerchant: 'Interest Payment' });
  const bettermentTxn = insertTxn(db, { accountId: betterment, originalMerchant: 'Interest Payment' });

  // 1. Explicit global override applies across institutions
  overrideMerchantLogo(db, {
    transactionId: chaseTxn1,
    scope: 'global',
    logoUrl: 'https://example.com/global-interest.png'
  });

  // 2. Institution override on Betterment beats global
  overrideMerchantLogo(db, {
    transactionId: bettermentTxn,
    scope: 'institution',
    logoUrl: 'https://example.com/betterment-interest.png'
  });

  // 3. Single transaction override on chaseTxn1 beats institution and global
  overrideMerchantLogo(db, {
    transactionId: chaseTxn1,
    scope: 'transaction',
    logoUrl: 'https://example.com/one-off-interest.png'
  });

  const selectRows = () => db.prepare(`
    SELECT id, account_id, COALESCE(edited_merchant, original_merchant) AS merchant,
           original_merchant, original_description, edited_merchant_source, transfer_pair_id
      FROM transactions WHERE id IN (?, ?, ?) ORDER BY id ASC
  `).all(chaseTxn1, chaseTxn2, bettermentTxn);

  let enriched = attachMerchantLogos(db, selectRows());
  assert.equal(enriched[0].merchant_logo.url, 'https://example.com/one-off-interest.png');
  assert.equal(enriched[0].merchant_logo.scope, 'transaction');
  assert.equal(enriched[1].merchant_logo.url, 'https://example.com/global-interest.png');
  assert.equal(enriched[1].merchant_logo.scope, 'global');
  assert.equal(enriched[2].merchant_logo.url, 'https://example.com/betterment-interest.png');
  assert.equal(enriched[2].merchant_logo.scope, 'institution');

  // 4. Broadening chaseTxn1 to global clears its txn: shadowing row so it immediately reflects global
  overrideMerchantLogo(db, {
    transactionId: chaseTxn1,
    scope: 'global',
    logoUrl: 'https://example.com/new-global.png'
  });
  enriched = attachMerchantLogos(db, selectRows());
  assert.equal(enriched[0].merchant_logo.url, 'https://example.com/new-global.png');
  assert.equal(enriched[0].merchant_logo.scope, 'global');
  assert.equal(enriched[1].merchant_logo.url, 'https://example.com/new-global.png');
  assert.equal(enriched[2].merchant_logo.url, 'https://example.com/betterment-interest.png');
});

test('generic credit card payments and transfers resolve counterparty institution from raw text or transfer pair', () => {
  const db = makeDb();
  const { chase, amex } = seedAccounts(db);

  const chaseToAmex = insertTxn(db, {
    accountId: chase,
    amount: -50000,
    originalMerchant: 'AMERICAN EXPRESS ACH PMT',
    originalDescription: 'ONLINE PAYMENT TO AMEX GOLD',
    editedMerchant: 'Credit Card Payment',
    editedMerchantSource: 'user'
  });
  const chaseToDiscover = insertTxn(db, {
    accountId: chase,
    amount: -25000,
    originalMerchant: 'DISCOVER E-PAYMENT',
    editedMerchant: 'Credit Card Payment',
    editedMerchantSource: 'user'
  });
  const pairedAmex = insertTxn(db, {
    accountId: amex,
    amount: 50000,
    originalMerchant: 'PAYMENT RECEIVED',
    editedMerchant: 'Transfer',
    editedMerchantSource: 'rule:2',
    isTransfer: 1,
    transferPairId: chaseToAmex
  });

  const rows = db.prepare(`
    SELECT id, account_id, COALESCE(edited_merchant, original_merchant) AS merchant,
           original_merchant, original_description, edited_merchant_source, transfer_pair_id
      FROM transactions WHERE id IN (?, ?, ?) ORDER BY id ASC
  `).all(chaseToAmex, chaseToDiscover, pairedAmex);

  const enriched = attachMerchantLogos(db, rows);
  assert.equal(enriched[0].merchant_logo.merchant_key, 'credit-card-payment@inst:americanexpress.com');
  assert.ok(enriched[0].merchant_logo.url.includes('americanexpress.com'));

  assert.equal(enriched[1].merchant_logo.merchant_key, 'credit-card-payment@inst:discover.com');
  assert.ok(enriched[1].merchant_logo.url.includes('discover.com'));

  assert.equal(enriched[2].merchant_logo.merchant_key, 'transfer@inst:chase.com');
  assert.ok(enriched[2].merchant_logo.url.includes('chase.com'));
});

test('reportMerchantLogoStatus on scoped fallback updates underlying inst: candidate without blocking manual overrides', () => {
  const db = makeDb();
  const { chase } = seedAccounts(db);
  const txnId = insertTxn(db, { accountId: chase, originalMerchant: 'Interest Payment' });

  const [row] = attachMerchantLogos(db, [
    db.prepare(`
      SELECT id, account_id, COALESCE(edited_merchant, original_merchant) AS merchant,
             original_merchant, original_description, edited_merchant_source, transfer_pair_id
        FROM transactions WHERE id = ?
    `).get(txnId)
  ]);

  assert.equal(row.merchant_logo.merchant_key, 'interest-payment@inst:chase.com');
  assert.equal(row.merchant_logo.status, 'candidate');

  const rep = reportMerchantLogoStatus(db, {
    merchantKey: row.merchant_logo.merchant_key,
    status: 'loaded'
  });
  assert.equal(rep.updated, true);

  const instRow = db.prepare('SELECT status FROM merchant_logo_cache WHERE merchant_key = ?').get('inst:chase.com');
  assert.equal(instRow.status, 'loaded');
});
