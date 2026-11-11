import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

process.env.SESSION_SECRET ??= 'test-session-secret-1234567890';
process.env.ADMIN_PASSWORD ??= 'test-admin-password-1234567890';
process.env.LOGO_DEV_PUBLISHABLE_KEY ??= 'pk_test_orbit';

const { runMigrations } = await import('../src/db/migrations.js');
const {
  attachInstitutionLogos,
  ensureInstitutionLogoEntryForAccount,
  resolveInstitutionDomain
} = await import('../src/services/institutionLogos.js');
const {
  attachMerchantLogos,
  overrideMerchantLogo
} = await import('../src/services/merchantLogos.js');

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

test('resolveInstitutionDomain maps common household banks and brokerages to canonical domains', () => {
  assert.equal(resolveInstitutionDomain('Chase Bank'), 'chase.com');
  assert.equal(resolveInstitutionDomain('Betterment'), 'betterment.com');
  assert.equal(resolveInstitutionDomain('Charles Schwab'), 'schwab.com');
  assert.equal(resolveInstitutionDomain('Fidelity Investments'), 'fidelity.com');
  assert.equal(resolveInstitutionDomain('Navy Federal Credit Union'), 'navyfederal.org');
  assert.equal(resolveInstitutionDomain('https://www.localcu.org/login'), 'localcu.org');
});

test('attachInstitutionLogos populates inst:-prefixed logos isolated from transaction category-icon overrides', () => {
  const db = makeDb();

  const chaseAcct = db.prepare(
    `INSERT INTO accounts (household_id, name, type, institution, current_balance)
     VALUES (1, 'Shared Checking', 'checking', 'Chase Bank', 370786)`
  ).run();
  const bettermentAcct = db.prepare(
    `INSERT INTO accounts (household_id, name, type, institution, current_balance)
     VALUES (1, 'High Yield Cash', 'savings', 'Betterment', 5040377)`
  ).run();

  // Hide 'chase' in transaction logo cache (e.g., user set Chase payment transfer to Category Icon)
  attachMerchantLogos(db, [
    { id: 1, merchant: 'Chase', original_merchant: 'Chase', edited_merchant_source: 'user' }
  ]);
  overrideMerchantLogo(db, { merchantKey: 'chase', hide: true });

  const enriched = attachInstitutionLogos(db, [
    { id: Number(chaseAcct.lastInsertRowid), name: 'Shared Checking', institution: 'Chase Bank' },
    { id: Number(bettermentAcct.lastInsertRowid), name: 'High Yield Cash', institution: 'Betterment' }
  ]);

  assert.equal(enriched[0].institution_logo.merchant_key, 'inst:chase.com');
  assert.equal(enriched[0].institution_logo.status, 'candidate');
  assert.ok(enriched[0].institution_logo.url.includes('chase.com'));

  assert.equal(enriched[1].institution_logo.merchant_key, 'inst:betterment.com');
  assert.ok(enriched[1].institution_logo.url.includes('betterment.com'));
});

test('ensureInstitutionLogoEntryForAccount supports manual override and hide reset even when institution is blank', () => {
  const db = makeDb();

  const cashAcct = db.prepare(
    `INSERT INTO accounts (household_id, name, type, institution, current_balance)
     VALUES (1, 'Emergency Cash Envelope', 'cash', NULL, 100000)`
  ).run();
  const accountId = Number(cashAcct.lastInsertRowid);

  const ensured = ensureInstitutionLogoEntryForAccount(db, accountId, 1);
  assert.equal(ensured.found, true);
  assert.equal(ensured.merchant_logo.merchant_key, `inst:acct-${accountId}`);
  assert.equal(ensured.merchant_logo.status, 'hidden');
  assert.equal(ensured.merchant_logo.url, null);

  const manual = overrideMerchantLogo(db, {
    merchantKey: ensured.merchant_logo.merchant_key,
    logoUrl: 'https://example.com/custom-vault.png',
    hide: false
  });
  assert.equal(manual.updated, true);
  assert.equal(manual.merchant_logo.url, 'https://example.com/custom-vault.png');

  const [reattached] = attachInstitutionLogos(db, [
    { id: accountId, name: 'Emergency Cash Envelope', institution: null }
  ]);
  assert.equal(reattached.institution_logo.status, 'manual');
  assert.equal(reattached.institution_logo.url, 'https://example.com/custom-vault.png');

  overrideMerchantLogo(db, {
    merchantKey: ensured.merchant_logo.merchant_key,
    hide: true
  });
  const [afterReset] = attachInstitutionLogos(db, [
    { id: accountId, name: 'Emergency Cash Envelope', institution: null }
  ]);
  assert.equal(afterReset.institution_logo.status, 'hidden');
  assert.equal(afterReset.institution_logo.url, null);
});
