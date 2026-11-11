// =============================================================================
// creditCardCatalogSync.js - Catalog seeding, daily upstream SUB sync & artwork cache
// =============================================================================

import { EXTENDED_CARD_CATALOG } from './creditCardCatalogExtendedSeed.js';
import {
  CATALOG_SCHEMA_TAG,
  inferArtKey,
  normalizeIssuer,
  normalizeNetwork,
  normalizeWireBenefit,
  normalizeWireRewardCategory,
  parseUpstreamOffer,
  RASTER_IMAGE_EXT_RE,
  slugifyCardId,
  UPSTREAM_IMAGE_BASE_URL
} from './creditCardCatalogHelpers.js';
import { CURATED_CARD_CATALOG } from './creditCardCatalogSeed.js';

const UPSTREAM_DATA_URL =
  'https://raw.githubusercontent.com/andenacitelli/credit-card-bonuses-api/main/exports/data.json';

export function ensureCreditCardCatalogSeeded(db) {
  const upsertStmt = db.prepare(`
    INSERT INTO credit_card_catalog (
      id, bonus_api_card_id, short_label, card_name, issuer_name, network,
      art_key, upstream_image_path, annual_fee_cents, is_annual_fee_waived,
      is_business_card, counts_toward_524_override, sub_cooldown_months,
      default_sub_reward_text, default_sub_estimated_value_cents,
      default_sub_target_spend_cents, sub_spend_window_days,
      is_elevated_sub_offer, reward_categories_json, benefits_json,
      sort_rank, is_curated, as_of, updated_at
    ) VALUES (
      @id, @bonusApiCardId, @shortLabel, @cardName, @issuerName, @network,
      @artKey, @upstreamImagePath, @annualFeeCents, @isAnnualFeeWaived,
      @isBusinessCard, @countsToward524Override, @subCooldownMonths,
      @defaultSubRewardText, @defaultSubEstimatedValueCents,
      @defaultSubTargetSpendCents, @subSpendWindowDays,
      @isElevatedSubOffer, @rewardCategoriesJson, @benefitsJson,
      @sortRank, @isCurated, @asOf, datetime('now')
    )
    ON CONFLICT(id) DO UPDATE SET
      bonus_api_card_id = excluded.bonus_api_card_id,
      short_label = excluded.short_label,
      card_name = excluded.card_name,
      issuer_name = excluded.issuer_name,
      network = excluded.network,
      art_key = excluded.art_key,
      upstream_image_path = COALESCE(excluded.upstream_image_path, credit_card_catalog.upstream_image_path),
      annual_fee_cents = excluded.annual_fee_cents,
      is_annual_fee_waived = excluded.is_annual_fee_waived,
      is_business_card = excluded.is_business_card,
      counts_toward_524_override = excluded.counts_toward_524_override,
      sub_cooldown_months = excluded.sub_cooldown_months,
      default_sub_reward_text = CASE
        WHEN credit_card_catalog.last_synced_at IS NULL
          OR credit_card_catalog.default_sub_reward_text LIKE '%\\_%' ESCAPE '\\'
          OR credit_card_catalog.default_sub_estimated_value_cents > 500000
          OR credit_card_catalog.default_sub_estimated_value_cents < 1000
        THEN excluded.default_sub_reward_text
        ELSE COALESCE(credit_card_catalog.default_sub_reward_text, excluded.default_sub_reward_text)
      END,
      default_sub_estimated_value_cents = CASE
        WHEN credit_card_catalog.last_synced_at IS NULL
          OR credit_card_catalog.default_sub_reward_text LIKE '%\\_%' ESCAPE '\\'
          OR credit_card_catalog.default_sub_estimated_value_cents > 500000
          OR credit_card_catalog.default_sub_estimated_value_cents < 1000
        THEN excluded.default_sub_estimated_value_cents
        ELSE COALESCE(credit_card_catalog.default_sub_estimated_value_cents, excluded.default_sub_estimated_value_cents)
      END,
      default_sub_target_spend_cents = CASE
        WHEN credit_card_catalog.last_synced_at IS NULL
          OR credit_card_catalog.default_sub_reward_text LIKE '%\\_%' ESCAPE '\\'
          OR credit_card_catalog.default_sub_estimated_value_cents > 500000
          OR credit_card_catalog.default_sub_estimated_value_cents < 1000
        THEN excluded.default_sub_target_spend_cents
        ELSE COALESCE(credit_card_catalog.default_sub_target_spend_cents, excluded.default_sub_target_spend_cents)
      END,
      sub_spend_window_days = CASE
        WHEN credit_card_catalog.last_synced_at IS NULL
          OR credit_card_catalog.default_sub_reward_text LIKE '%\\_%' ESCAPE '\\'
          OR credit_card_catalog.default_sub_estimated_value_cents > 500000
          OR credit_card_catalog.default_sub_estimated_value_cents < 1000
        THEN excluded.sub_spend_window_days
        ELSE COALESCE(credit_card_catalog.sub_spend_window_days, excluded.sub_spend_window_days)
      END,
      is_elevated_sub_offer = CASE
        WHEN credit_card_catalog.last_synced_at IS NULL
          OR credit_card_catalog.default_sub_reward_text LIKE '%\\_%' ESCAPE '\\'
          OR credit_card_catalog.default_sub_estimated_value_cents > 500000
          OR credit_card_catalog.default_sub_estimated_value_cents < 1000
        THEN excluded.is_elevated_sub_offer
        ELSE credit_card_catalog.is_elevated_sub_offer
      END,
      reward_categories_json = excluded.reward_categories_json,
      benefits_json = excluded.benefits_json,
      sort_rank = excluded.sort_rank,
      is_curated = excluded.is_curated,
      as_of = CASE WHEN credit_card_catalog.as_of LIKE '%-v2' THEN credit_card_catalog.as_of ELSE excluded.as_of END,
      updated_at = datetime('now')
  `);

  const trx = db.transaction(() => {
    const seedList = [
      ...CURATED_CARD_CATALOG.map((c) => ({ ...c, isCurated: 1 })),
      ...EXTENDED_CARD_CATALOG.map((c) => ({ ...c, isCurated: 0 }))
    ];
    for (const item of seedList) {
      upsertStmt.run({
        id: item.id,
        bonusApiCardId: item.bonusApiCardId || null,
        shortLabel: item.shortLabel,
        cardName: item.cardName,
        issuerName: item.issuerName,
        network: item.network,
        artKey: item.artKey,
        upstreamImagePath: item.upstreamImagePath || null,
        annualFeeCents: item.annualFeeCents || 0,
        isAnnualFeeWaived: item.isAnnualFeeWaived ? 1 : 0,
        isBusinessCard: item.isBusinessCard ? 1 : 0,
        countsToward524Override:
          item.countsToward524Override === undefined || item.countsToward524Override === null
            ? null
            : item.countsToward524Override ? 1 : 0,
        subCooldownMonths: item.subCooldownMonths ?? null,
        defaultSubRewardText: item.defaultSubRewardText || null,
        defaultSubEstimatedValueCents: item.defaultSubEstimatedValueCents ?? null,
        defaultSubTargetSpendCents: item.defaultSubTargetSpendCents ?? null,
        subSpendWindowDays: item.subSpendWindowDays ?? null,
        isElevatedSubOffer: item.isElevatedSubOffer ? 1 : 0,
        rewardCategoriesJson: JSON.stringify(item.rewardCategories || []),
        benefitsJson: JSON.stringify(item.benefits || []),
        sortRank: item.sortRank ?? 100,
        isCurated: item.isCurated,
        asOf: CATALOG_SCHEMA_TAG
      });
    }
  });
  trx();
}

export async function syncCreditCardCatalogFromUpstream(db, { fetchImpl = globalThis.fetch } = {}) {
  ensureCreditCardCatalogSeeded(db);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  let rawCards;
  try {
    const res = await fetchImpl(UPSTREAM_DATA_URL, { signal: controller.signal });
    if (!res.ok) throw new Error(`Upstream credit-card-bonuses-api HTTP ${res.status}`);
    rawCards = await res.json();
  } finally {
    clearTimeout(timeout);
  }
  if (!Array.isArray(rawCards)) throw new Error('Invalid upstream credit-card-bonuses-api payload');

  const updateCuratedStmt = db.prepare(`
    UPDATE credit_card_catalog
       SET is_annual_fee_waived = @isAnnualFeeWaived,
           upstream_image_path = COALESCE(@upstreamImagePath, upstream_image_path),
           default_sub_reward_text = COALESCE(@rewardText, default_sub_reward_text),
           default_sub_estimated_value_cents = COALESCE(@estimatedValueCents, default_sub_estimated_value_cents),
           default_sub_target_spend_cents = COALESCE(@targetSpendCents, default_sub_target_spend_cents),
           sub_spend_window_days = COALESCE(@spendWindowDays, sub_spend_window_days),
           is_elevated_sub_offer = @isElevated,
           as_of = @asOf,
           last_synced_at = datetime('now'),
           updated_at = datetime('now')
     WHERE bonus_api_card_id = @bonusApiCardId
       AND is_curated = 1
  `);

  const updateSeededExtendedStmt = db.prepare(`
    UPDATE credit_card_catalog
       SET is_annual_fee_waived = @isAnnualFeeWaived,
           upstream_image_path = COALESCE(@upstreamImagePath, upstream_image_path),
           default_sub_reward_text = COALESCE(@rewardText, default_sub_reward_text),
           default_sub_estimated_value_cents = COALESCE(@estimatedValueCents, default_sub_estimated_value_cents),
           default_sub_target_spend_cents = COALESCE(@targetSpendCents, default_sub_target_spend_cents),
           sub_spend_window_days = COALESCE(@spendWindowDays, sub_spend_window_days),
           is_elevated_sub_offer = @isElevated,
           as_of = @asOf,
           last_synced_at = datetime('now'),
           updated_at = datetime('now')
     WHERE bonus_api_card_id = @bonusApiCardId
       AND is_curated = 0
  `);

  const upsertUpstreamCardStmt = db.prepare(`
    INSERT INTO credit_card_catalog (
      id, bonus_api_card_id, short_label, card_name, issuer_name, network,
      art_key, upstream_image_path, annual_fee_cents, is_annual_fee_waived,
      is_business_card, counts_toward_524_override, sub_cooldown_months,
      default_sub_reward_text, default_sub_estimated_value_cents,
      default_sub_target_spend_cents, sub_spend_window_days,
      is_elevated_sub_offer, reward_categories_json, benefits_json,
      sort_rank, is_curated, as_of, last_synced_at, updated_at
    ) VALUES (
      @id, @bonusApiCardId, @shortLabel, @cardName, @issuerName, @network,
      @artKey, @upstreamImagePath, @annualFeeCents, @isAnnualFeeWaived,
      @isBusinessCard, @countsToward524Override, @subCooldownMonths,
      @rewardText, @estimatedValueCents, @targetSpendCents, @spendWindowDays,
      @isElevated, @rewardCategoriesJson, @benefitsJson,
      @sortRank, 0, @asOf, datetime('now'), datetime('now')
    )
    ON CONFLICT(id) DO UPDATE SET
      annual_fee_cents = excluded.annual_fee_cents,
      is_annual_fee_waived = excluded.is_annual_fee_waived,
      upstream_image_path = COALESCE(excluded.upstream_image_path, credit_card_catalog.upstream_image_path),
      default_sub_reward_text = COALESCE(excluded.default_sub_reward_text, credit_card_catalog.default_sub_reward_text),
      default_sub_estimated_value_cents = COALESCE(excluded.default_sub_estimated_value_cents, credit_card_catalog.default_sub_estimated_value_cents),
      default_sub_target_spend_cents = COALESCE(excluded.default_sub_target_spend_cents, credit_card_catalog.default_sub_target_spend_cents),
      sub_spend_window_days = COALESCE(excluded.sub_spend_window_days, credit_card_catalog.sub_spend_window_days),
      is_elevated_sub_offer = excluded.is_elevated_sub_offer,
      reward_categories_json = excluded.reward_categories_json,
      benefits_json = excluded.benefits_json,
      as_of = excluded.as_of,
      last_synced_at = datetime('now'),
      updated_at = datetime('now')
    WHERE credit_card_catalog.is_curated = 0
  `);

  const now = new Date();
  const asOf = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-v2`;
  let updatedCurated = 0;
  let upsertedLongTail = 0;

  const trx = db.transaction(() => {
    for (const card of rawCards) {
      if (!card || card.discontinued || !card.cardId || !card.name) continue;
      const offer = parseUpstreamOffer(card);
      const imagePath =
        typeof card.imageUrl === 'string' && RASTER_IMAGE_EXT_RE.test(card.imageUrl) ? card.imageUrl : null;
      const annualFeeCents = Math.max(0, Math.round((Number(card.annualFee) || 0) * 100));
      const isAnnualFeeWaived = card.isAnnualFeeWaived ? 1 : 0;
      const syncParams = {
        bonusApiCardId: card.cardId,
        isAnnualFeeWaived,
        upstreamImagePath: imagePath,
        rewardText: offer.rewardText,
        estimatedValueCents: offer.estimatedValueCents,
        targetSpendCents: offer.targetSpendCents,
        spendWindowDays: offer.spendWindowDays,
        isElevated: offer.isElevated ? 1 : 0,
        asOf
      };

      const curatedRes = updateCuratedStmt.run(syncParams);
      if (curatedRes.changes > 0) {
        updatedCurated += curatedRes.changes;
        continue;
      }

      const extRes = updateSeededExtendedStmt.run(syncParams);
      if (extRes.changes > 0) {
        upsertedLongTail += extRes.changes;
        continue;
      }

      const issuerName = normalizeIssuer(card.issuer, card.name);
      const network = normalizeNetwork(card.network);
      const id = slugifyCardId(card.issuer, card.name);
      const isBusiness = Boolean(card.isBusiness);
      const lowerName = String(card.name).toLowerCase();
      const isCapOneSpark =
        isBusiness && issuerName === 'Capital One' && !lowerName.includes('venture x') && !lowerName.includes('plus');
      const isDiscoverBiz = isBusiness && issuerName === 'Discover';
      const countsToward524Override = isCapOneSpark || isDiscoverBiz ? 1 : isBusiness ? 0 : null;
      const subCooldownMonths = issuerName === 'American Express' ? 999 : issuerName === 'Chase' ? 24 : 48;

      const baseRate = Math.max(1.0, Number(card.universalCashbackPercent) || 1.0);
      const rewardCategories = [
        {
          category: 'Everything Else',
          label: 'Every Purchase',
          rate: baseRate,
          multiplier: baseRate,
          is_rotating: false,
          isRotating: false,
          cap: null,
          capCents: null,
          type: 'multiplier'
        }
      ];

      const rawCredits = Array.isArray(card.credits) ? card.credits : [];
      const benefits = rawCredits
        .filter((c) => {
          const val = Number(c?.value);
          const desc = String(c?.description || '').trim();
          return desc && val > 0 && val <= 1000 && !/\b(lounge|status|checked bag|pts|points|miles)\b/i.test(desc);
        })
        .map((c, idx) => {
          const valDollars = Number(c.value);
          const valCents = Math.round(valDollars * 100);
          const desc = String(c.description).trim();
          const title = desc.startsWith('$') ? desc : `$${Math.round(valDollars)} ${desc}`;
          return {
            id: `${id}-credit-${idx + 1}`,
            name: title,
            title,
            value: valDollars,
            period_value: valDollars,
            period_value_cents: valCents,
            cadence: 'ANNUAL',
            anchor: 'CARDMEMBER_YEAR',
            description: desc
          };
        });

      upsertUpstreamCardStmt.run({
        id,
        bonusApiCardId: card.cardId,
        shortLabel: String(card.name).slice(0, 36),
        cardName: String(card.name).trim(),
        issuerName,
        network,
        artKey: inferArtKey(issuerName, card.name),
        upstreamImagePath: imagePath,
        annualFeeCents,
        isAnnualFeeWaived,
        isBusinessCard: isBusiness ? 1 : 0,
        countsToward524Override,
        subCooldownMonths,
        rewardText: offer.rewardText,
        estimatedValueCents: offer.estimatedValueCents,
        targetSpendCents: offer.targetSpendCents,
        spendWindowDays: offer.spendWindowDays,
        isElevated: offer.isElevated ? 1 : 0,
        rewardCategoriesJson: JSON.stringify(rewardCategories),
        benefitsJson: JSON.stringify(benefits),
        sortRank: isBusiness ? 350 : 250,
        asOf
      });
      upsertedLongTail++;
    }
  });
  trx();

  return { updatedCurated, upsertedLongTail, totalProcessed: updatedCurated + upsertedLongTail };
}

export async function fetchAndCacheCardArtwork(db, cardId, { fetchImpl = globalThis.fetch } = {}) {
  const cached = db
    .prepare('SELECT content_type, image_blob FROM credit_card_artwork_cache WHERE card_id = ?')
    .get(cardId);
  if (cached && cached.image_blob) {
    return { contentType: cached.content_type, buffer: cached.image_blob };
  }

  const row = db.prepare('SELECT upstream_image_path FROM credit_card_catalog WHERE id = ?').get(cardId);
  const imagePath = row?.upstream_image_path;
  if (!imagePath || !RASTER_IMAGE_EXT_RE.test(imagePath)) return null;

  const url = `${UPSTREAM_IMAGE_BASE_URL}${imagePath.startsWith('/') ? '' : '/'}${imagePath}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetchImpl(url, { signal: controller.signal });
    if (!res.ok) return null;
    const contentType = String(res.headers.get('content-type') || 'image/png').split(';')[0].trim();
    if (!contentType.startsWith('image/') || contentType.includes('svg') || contentType.includes('avif')) return null;
    const arrayBuf = await res.arrayBuffer();
    if (!arrayBuf || arrayBuf.byteLength === 0 || arrayBuf.byteLength > 2 * 1024 * 1024) return null;
    const buffer = Buffer.from(arrayBuf);
    db.prepare(`
      INSERT INTO credit_card_artwork_cache (card_id, content_type, image_blob, fetched_at)
      VALUES (?, ?, ?, datetime('now'))
      ON CONFLICT(card_id) DO UPDATE SET
        content_type = excluded.content_type,
        image_blob = excluded.image_blob,
        fetched_at = datetime('now')
    `).run(cardId, contentType, buffer);
    return { contentType, buffer };
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

function safeJsonParse(raw, fallback) {
  if (typeof raw !== 'string') return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

export function serializeCatalogRow(row) {
  const hasRasterArtwork =
    typeof row.upstream_image_path === 'string' && RASTER_IMAGE_EXT_RE.test(row.upstream_image_path);

  return {
    id: row.id,
    short_label: row.short_label,
    card_name: row.card_name,
    issuer_name: row.issuer_name,
    network: row.network,
    image_url: row.art_key,
    artwork_url: hasRasterArtwork ? `/api/card-catalog/${encodeURIComponent(row.id)}/artwork` : null,
    direct_artwork_url: hasRasterArtwork
      ? `${UPSTREAM_IMAGE_BASE_URL}${row.upstream_image_path.startsWith('/') ? '' : '/'}${row.upstream_image_path}`
      : null,
    annual_fee_cents: Number(row.annual_fee_cents) || 0,
    is_annual_fee_waived: Boolean(row.is_annual_fee_waived),
    is_business_card: Boolean(row.is_business_card),
    counts_toward_524_override:
      row.counts_toward_524_override === null || row.counts_toward_524_override === undefined
        ? null
        : Boolean(row.counts_toward_524_override),
    sub_cooldown_months:
      row.sub_cooldown_months === null || row.sub_cooldown_months === undefined ? null : Number(row.sub_cooldown_months),
    default_sub_reward_text: row.default_sub_reward_text || null,
    default_sub_estimated_value_cents:
      row.default_sub_estimated_value_cents == null ? null : Number(row.default_sub_estimated_value_cents),
    default_sub_target_spend_cents:
      row.default_sub_target_spend_cents == null ? null : Number(row.default_sub_target_spend_cents),
    sub_spend_window_days: row.sub_spend_window_days == null ? null : Number(row.sub_spend_window_days),
    is_elevated_sub_offer: Boolean(row.is_elevated_sub_offer),
    reward_categories: safeJsonParse(row.reward_categories_json, []).map(normalizeWireRewardCategory),
    benefits: safeJsonParse(row.benefits_json, []).map(normalizeWireBenefit),
    is_curated: Boolean(row.is_curated),
    as_of: row.as_of || CATALOG_SCHEMA_TAG,
    last_synced_at: row.last_synced_at || null
  };
}

export function listCatalogTemplates(db) {
  const count = db.prepare('SELECT COUNT(*) AS cnt FROM credit_card_catalog').get()?.cnt || 0;
  if (count === 0) {
    ensureCreditCardCatalogSeeded(db);
  }
  return db
    .prepare('SELECT * FROM credit_card_catalog ORDER BY sort_rank ASC, issuer_name ASC, card_name ASC')
    .all()
    .map(serializeCatalogRow);
}
