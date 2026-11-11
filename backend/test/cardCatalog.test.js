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

test('ensureCreditCardCatalogSeeded seeds all 178 audited cards with wire-compatible multipliers, perks, and 5/24 rules', () => {
  const db = makeDb();
  ensureCreditCardCatalogSeeded(db);

  const items = listCatalogTemplates(db);
  assert.equal(items.length, 178, `Expected 178 cards in catalog seed, got ${items.length}`);

  const amexGold = items.find((c) => c.id === 'amex_gold');
  assert.ok(amexGold);
  assert.equal(amexGold.issuer_name, 'American Express');
  assert.equal(amexGold.annual_fee_cents, 32500);
  assert.equal(amexGold.artwork_url, '/api/card-catalog/amex_gold/artwork');
  assert.equal(amexGold.direct_artwork_url, 'https://www.offeroptimist.com/images/amex/gold.webp');
  assert.equal(amexGold.benefits.length, 4);
  assert.ok(amexGold.reward_categories.length >= 4);
  assert.equal(amexGold.reward_categories[0].rate, 4);
  assert.equal(amexGold.reward_categories[0].multiplier, 4);

  const spark = items.find((c) => c.id === 'spark_cash_select');
  assert.ok(spark);
  assert.equal(spark.network, 'Mastercard');
  assert.equal(spark.is_business_card, true);
  assert.equal(spark.counts_toward_524_override, true);

  const savor = items.find((c) => c.id === 'capital_one_savor');
  assert.equal(savor.network, 'Mastercard');
  assert.equal(items.find((c) => c.id === 'chase_freedom_classic').network, 'Visa');
  assert.equal(items.find((c) => c.id === 'cff').network, 'Mastercard');
  assert.equal(items.find((c) => c.id === 'citi_costco_visa').network, 'Visa');

  // Verify zero placeholder multipliers, snake_case SUBs, or corrupted perks across all 178 cards
  for (const item of items) {
    assert.ok(['Visa', 'Mastercard', 'Amex', 'Discover'].includes(item.network), `Bad network on ${item.id}`);
    assert.ok(item.reward_categories.length >= 1, `Empty reward_categories on ${item.id}`);
    for (const rc of item.reward_categories) {
      assert.equal(typeof rc.rate, 'number', `Missing wire rate on ${item.id}`);
      assert.ok(!rc.label.includes('Base Rate'), `Placeholder Base Rate on ${item.id}`);
    }
    if (item.default_sub_reward_text) {
      assert.ok(!item.default_sub_reward_text.includes('_'), `Snake_case SUB on ${item.id}: ${item.default_sub_reward_text}`);
    }
    for (const ben of item.benefits) {
      assert.ok(!/^\$[0-9]{4,}\b/.test(ben.title), `Points-as-dollars title on ${item.id}: ${ben.title}`);
    }
  }
});

test('syncCreditCardCatalogFromUpstream updates curated SUBs, preserves extended multipliers/perks, and ingests long-tail cards', async () => {
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

test('syncCreditCardCatalogFromUpstream preserves seeded extended multipliers and blocks bogus upstream credits', async () => {
  const db = makeDb();
  ensureCreditCardCatalogSeeded(db);
  const marriottRow = db
    .prepare('SELECT bonus_api_card_id FROM credit_card_catalog WHERE id = ?')
    .get('american_express_marriott_bonvoy_business');
  assert.ok(marriottRow?.bonus_api_card_id);

  await syncCreditCardCatalogFromUpstream(db, {
    fetchImpl: async () => ({
      ok: true,
      json: async () => [
        {
          cardId: marriottRow.bonus_api_card_id,
          name: 'Marriott Bonvoy Business',
          issuer: 'AMERICAN_EXPRESS',
          network: 'AMERICAN_EXPRESS',
          currency: 'MARRIOTT',
          isBusiness: true,
          annualFee: 125,
          isAnnualFeeWaived: false,
          universalCashbackPercent: 1,
          imageUrl: '/images/amex/marriott-bonvoy-business.webp',
          credits: [],
          offers: [
            {
              spend: 8000,
              amount: [{ amount: 0 }],
              days: 180,
              credits: [{ description: '3x 50k FNAs', value: 150000 }]
            }
          ],
          historicalOffers: [],
          discontinued: false
        }
      ]
    })
  });

  const items = listCatalogTemplates(db);
  const marriottBiz = items.find((c) => c.id === 'american_express_marriott_bonvoy_business');
  assert.ok(marriottBiz);
  assert.equal(marriottBiz.default_sub_reward_text, '3 Free Night Awards (50k pts each)');
  assert.equal(marriottBiz.default_sub_estimated_value_cents, 60000);
  assert.ok(marriottBiz.reward_categories.some((rc) => rc.rate === 6));
  assert.equal(marriottBiz.benefits.length, 1);
});
