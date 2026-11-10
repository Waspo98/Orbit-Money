import assert from 'node:assert/strict';
import test from 'node:test';
import Database from 'better-sqlite3';

import { runMigrations } from '../src/db/migrations.js';
import {
  ensureCreditCardCatalogSeeded,
  fetchAndCacheCardArtwork,
  listCatalogTemplates,
  syncCreditCardCatalogFromUpstream
} from '../src/services/creditCardCatalogSync.js';

function makeDb() {
  const db = new Database(':memory:');
  db.pragma('foreign_keys = ON');
  runMigrations(db);
  return db;
}

test('ensureCreditCardCatalogSeeded seeds curated cards with structured perks and 5/24 rules', () => {
  const db = makeDb();
  ensureCreditCardCatalogSeeded(db);

  const items = listCatalogTemplates(db);
  assert.ok(items.length >= 30, `Expected at least 30 curated cards, got ${items.length}`);

  const amexGold = items.find((c) => c.id === 'amex_gold');
  assert.ok(amexGold);
  assert.equal(amexGold.issuer_name, 'American Express');
  assert.equal(amexGold.annual_fee_cents, 32500);
  assert.equal(amexGold.artwork_url, '/api/card-catalog/amex_gold/artwork');
  assert.equal(amexGold.benefits.length, 4);
  assert.equal(amexGold.reward_categories.length, 3);

  const spark = items.find((c) => c.id === 'spark_cash_select');
  assert.ok(spark);
  assert.equal(spark.is_business_card, true);
  assert.equal(spark.counts_toward_524_override, true);
});

test('syncCreditCardCatalogFromUpstream updates curated SUBs, detects elevated offers, and ingests long-tail cards', async () => {
  const db = makeDb();
  const mockPayload = [
    {
      cardId: '57f8a819c3bb4c6aa494016703281f35',
      name: 'Sapphire Preferred',
      issuer: 'CHASE',
      network: 'VISA',
      currency: 'CHASE',
      isBusiness: false,
      annualFee: 95,
      isAnnualFeeWaived: false,
      universalCashbackPercent: 1,
      imageUrl: '/images/chase/sapphire-preferred.png',
      credits: [],
      offers: [
        {
          spend: 4000,
          amount: [{ amount: 75000 }],
          days: 90,
          credits: [{ description: 'Chase Travel Credit', value: 300 }]
        }
      ],
      historicalOffers: [
        { spend: 4000, amount: [{ amount: 60000 }], days: 90 },
        { spend: 4000, amount: [{ amount: 75000 }], days: 90 }
      ],
      discontinued: false
    },
    {
      cardId: 'new-long-tail-card-id',
      name: 'JetBlue Plus',
      issuer: 'BARCLAYS',
      network: 'MASTERCARD',
      currency: 'JETBLUE',
      isBusiness: false,
      annualFee: 99,
      isAnnualFeeWaived: false,
      universalCashbackPercent: 1,
      imageUrl: '/images/barclays/jetblue-plus.png',
      credits: [{ description: 'Anniversary Bonus', value: 50 }],
      offers: [
        {
          spend: 1000,
          amount: [{ amount: 80000 }],
          days: 90,
          credits: []
        }
      ],
      historicalOffers: [
        { spend: 1000, amount: [{ amount: 50000 }], days: 90 },
        { spend: 1000, amount: [{ amount: 80000 }], days: 90 }
      ],
      discontinued: false
    }
  ];

  const summary = await syncCreditCardCatalogFromUpstream(db, {
    fetchImpl: async () => ({
      ok: true,
      json: async () => mockPayload
    })
  });

  assert.equal(summary.updatedCurated, 1);
  assert.equal(summary.upsertedLongTail, 1);

  const items = listCatalogTemplates(db);
  const csp = items.find((c) => c.id === 'csp');
  assert.equal(csp.default_sub_reward_text, '75,000 Ultimate Rewards pts + $300 Credit');
  assert.equal(csp.default_sub_target_spend_cents, 400000);
  assert.equal(csp.sub_spend_window_days, 90);
  assert.equal(csp.default_sub_estimated_value_cents, 142500);
  assert.equal(csp.is_elevated_sub_offer, true);
  // Curated perks remain preserved
  assert.equal(csp.benefits.length, 2);

  const jetblue = items.find((c) => c.id === 'barclays_jetblue_plus');
  assert.ok(jetblue);
  assert.equal(jetblue.issuer_name, 'Barclays');
  assert.equal(jetblue.default_sub_reward_text, '80,000 TrueBlue pts');
  assert.equal(jetblue.is_elevated_sub_offer, true);
  assert.equal(jetblue.benefits.length, 1);
});

test('fetchAndCacheCardArtwork caches raster image bytes in SQLite and serves from cache on repeat', async () => {
  const db = makeDb();
  ensureCreditCardCatalogSeeded(db);

  let fetchCalls = 0;
  const fakePng = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);

  const first = await fetchAndCacheCardArtwork(db, 'csr', {
    fetchImpl: async () => {
      fetchCalls++;
      return {
        ok: true,
        headers: { get: () => 'image/png' },
        arrayBuffer: async () => fakePng.buffer
      };
    }
  });
  assert.ok(first);
  assert.equal(first.contentType, 'image/png');
  assert.equal(fetchCalls, 1);

  const second = await fetchAndCacheCardArtwork(db, 'csr', {
    fetchImpl: async () => {
      fetchCalls++;
      throw new Error('Should not fetch from network when cached');
    }
  });
  assert.ok(second);
  assert.equal(second.buffer.length, 8);
  assert.equal(fetchCalls, 1);
});
