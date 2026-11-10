// =============================================================================
// creditCardCatalogSync.js - Catalog seeding, daily upstream SUB sync & artwork cache
// =============================================================================

import { CURATED_CARD_CATALOG } from './creditCardCatalogSeed.js';

const UPSTREAM_DATA_URL =
  'https://raw.githubusercontent.com/andenacitelli/credit-card-bonuses-api/main/exports/data.json';
const UPSTREAM_IMAGE_BASE_URL = 'https://www.offeroptimist.com';
const RASTER_IMAGE_EXT_RE = /\.(png|jpe?g|webp)$/i;

const ISSUER_NAMES = {
  AMERICAN_EXPRESS: 'American Express',
  BANK_OF_AMERICA: 'Bank of America',
  BARCLAYS: 'Barclays',
  BREX: 'Brex',
  CAPITAL_ONE: 'Capital One',
  CHASE: 'Chase',
  CITI: 'Citi',
  COMENITY: 'AAA / Comenity',
  DISCOVER: 'Discover',
  FIRST: 'Cardless / First',
  FNBO: 'FNBO',
  PENFED: 'PenFed',
  PNC: 'PNC',
  SYNCHRONY: 'Synchrony',
  US_BANK: 'U.S. Bank',
  WEB_BANK: 'WebBank',
  WELLS_FARGO: 'Wells Fargo'
};

const ISSUER_PALETTES = {
  'American Express': 'hex://#1E3A8A:#3B82F6:#FFFFFF',
  'Bank of America': 'hex://#7F1D1D:#DC2626:#FFFFFF',
  Barclays: 'hex://#0C4A6E:#0284C7:#FFFFFF',
  Brex: 'hex://#18181B:#3F3F46:#FFFFFF',
  'Capital One': 'hex://#0F2B5B:#2563EB:#FFFFFF',
  Chase: 'preset://sapphire-blue',
  Citi: 'hex://#0F172A:#0D9488:#FFFFFF',
  Discover: 'preset://discover-plum',
  'U.S. Bank': 'hex://#1E3A8A:#B91C1C:#FFFFFF',
  'Wells Fargo': 'hex://#7F1D1D:#B91C1C:#FFFFFF'
};

const CURRENCY_LABELS = {
  AMERICAN_EXPRESS: 'Membership Rewards pts',
  CHASE: 'Ultimate Rewards pts',
  CAPITAL_ONE: 'Capital One Miles',
  CITI: 'ThankYou Points',
  BILT: 'Bilt Points',
  DELTA: 'Delta SkyMiles',
  UNITED: 'United Miles',
  SOUTHWEST: 'Rapid Rewards pts',
  AMERICAN: 'AAdvantage Miles',
  AA: 'AAdvantage Miles',
  JETBLUE: 'TrueBlue pts',
  ALASKA: 'Atmos Rewards pts',
  ATMOS: 'Atmos Rewards pts',
  MARRIOTT: 'Marriott Bonvoy pts',
  HILTON: 'Hilton Honors pts',
  HYATT: 'World of Hyatt pts',
  IHG: 'IHG One Rewards pts',
  WYNDHAM: 'Wyndham Rewards pts',
  CHOICE: 'Choice Privileges pts',
  AVIANCA: 'LifeMiles',
  AEROPLAN: 'Aeroplan pts',
  AVIOS: 'Avios',
  EMIRATES: 'Skywards Miles',
  FRONTIER: 'Frontier Miles',
  HAWAIIAN: 'HawaiianMiles',
  LUFTHANSA: 'Miles & More'
};

const CURRENCY_CPP = {
  USD: 100,
  CHASE: 1.5,
  AMERICAN_EXPRESS: 1.5,
  BILT: 1.6,
  HYATT: 1.8,
  CAPITAL_ONE: 1.4,
  CITI: 1.4,
  UNITED: 1.2,
  DELTA: 1.2,
  SOUTHWEST: 1.3,
  AMERICAN: 1.3,
  AA: 1.3,
  ALASKA: 1.3,
  ATMOS: 1.3,
  JETBLUE: 1.2,
  AEROPLAN: 1.3,
  AVIOS: 1.2,
  MARRIOTT: 0.8,
  IHG: 0.5,
  HILTON: 0.5,
  WYNDHAM: 0.9,
  CHOICE: 0.6
};

function normalizeNetwork(raw) {
  const upper = String(raw || '').toUpperCase();
  if (upper.includes('AMERICAN_EXPRESS') || upper === 'AMEX') return 'Amex';
  if (upper.includes('MASTER')) return 'Mastercard';
  if (upper.includes('DISCOVER')) return 'Discover';
  return 'Visa';
}

function normalizeIssuer(raw, cardName = '') {
  if (String(cardName).toLowerCase().includes('bilt')) return 'Wells Fargo / Bilt';
  return ISSUER_NAMES[String(raw || '').toUpperCase()] || String(raw || 'Bank').replace(/_/g, ' ');
}

function slugifyCardId(issuerCode, name) {
  const prefix = String(issuerCode || 'card').toLowerCase().replace(/[^a-z0-9]+/g, '_');
  const slug = String(name || 'card').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
  return `${prefix}_${slug}`.slice(0, 64);
}

function inferArtKey(issuerName, cardName) {
  const lower = String(cardName || '').toLowerCase();
  if (lower.includes('gold') && !lower.includes('marriott')) return 'preset://amex-gold';
  if (lower.includes('platinum') || lower.includes('venture x') || lower.includes('reserve')) {
    return 'preset://venture-silver';
  }
  if (lower.includes('bilt') || lower.includes('premier') || lower.includes('club') || lower.includes('elite')) {
    return 'hex://#09090B:#27272A:#FFFFFF';
  }
  if (lower.includes('marriott') || lower.includes('bonvoy')) return 'hex://#4A1525:#7F1D1D:#FFFFFF';
  if (lower.includes('green') || lower.includes('cash')) return 'hex://#065F46:#059669:#FFFFFF';
  return ISSUER_PALETTES[issuerName] || 'preset://sapphire-blue';
}

function parseUpstreamOffer(card) {
  const offers = Array.isArray(card.offers) ? card.offers : [];
  const primaryOffer = offers[0];
  if (!primaryOffer) {
    return {
      rewardText: null,
      estimatedValueCents: null,
      targetSpendCents: null,
      spendWindowDays: null,
      isElevated: false
    };
  }

  const rawAmount = Number(primaryOffer.amount?.[0]?.amount || 0);
  const currency = String(card.currency || 'USD').toUpperCase();
  const offerCredits = Array.isArray(primaryOffer.credits) ? primaryOffer.credits : [];
  const statementCreditDollars = offerCredits.reduce((sum, c) => sum + (Number(c.value) || 0), 0);

  let rewardText = null;
  if (rawAmount > 0) {
    if (currency === 'USD') {
      rewardText = `$${rawAmount.toLocaleString('en-US')} Cash Bonus`;
    } else if (rawAmount <= 10) {
      rewardText = `${rawAmount} Free Night Awards`;
    } else {
      const unit = CURRENCY_LABELS[currency] || `${currency.charAt(0) + currency.slice(1).toLowerCase()} pts`;
      rewardText = `${rawAmount.toLocaleString('en-US')} ${unit}`;
    }
    if (statementCreditDollars > 0) {
      rewardText += ` + $${statementCreditDollars} Credit`;
    }
  } else if (statementCreditDollars > 0) {
    rewardText = `$${statementCreditDollars} Statement Credit`;
  }

  const cpp = CURRENCY_CPP[currency] ?? 1.0;
  let estimatedValueCents = null;
  if (rawAmount > 0) {
    if (currency === 'USD') {
      estimatedValueCents = Math.round(rawAmount * 100);
    } else if (rawAmount <= 10) {
      estimatedValueCents = Math.round(rawAmount * 20000);
    } else {
      estimatedValueCents = Math.round(rawAmount * cpp);
    }
  }
  if (statementCreditDollars > 0) {
    estimatedValueCents = (estimatedValueCents || 0) + Math.round(statementCreditDollars * 100);
  }

  const spendDollars = Number(primaryOffer.spend || 0);
  const targetSpendCents = spendDollars > 0 ? Math.round(spendDollars * 100) : null;
  const days = Number(primaryOffer.days || 90);
  const spendWindowDays = days > 0 && days <= 730 ? days : 90;

  const historical = Array.isArray(card.historicalOffers) ? card.historicalOffers : [];
  const histAmounts = historical
    .map((h) => Number(h.amount?.[0]?.amount || 0))
    .filter((n) => n > 0);
  const maxHist = histAmounts.length > 0 ? Math.max(...histAmounts) : 0;
  const minHist = histAmounts.length > 0 ? Math.min(...histAmounts) : 0;
  const isElevated = rawAmount > 0 && histAmounts.length >= 2 && rawAmount >= maxHist && maxHist > minHist;

  return {
    rewardText,
    estimatedValueCents,
    targetSpendCents,
    spendWindowDays,
    isElevated
  };
}

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
      0, @rewardCategoriesJson, @benefitsJson,
      @sortRank, 1, '2026-11', datetime('now')
    )
    ON CONFLICT(id) DO UPDATE SET
      bonus_api_card_id = excluded.bonus_api_card_id,
      short_label = excluded.short_label,
      card_name = excluded.card_name,
      issuer_name = excluded.issuer_name,
      network = excluded.network,
      art_key = excluded.art_key,
      upstream_image_path = COALESCE(excluded.upstream_image_path, credit_card_catalog.upstream_image_path),
      is_business_card = excluded.is_business_card,
      counts_toward_524_override = excluded.counts_toward_524_override,
      sub_cooldown_months = excluded.sub_cooldown_months,
      reward_categories_json = excluded.reward_categories_json,
      benefits_json = excluded.benefits_json,
      sort_rank = excluded.sort_rank,
      is_curated = 1,
      updated_at = datetime('now')
  `);

  const trx = db.transaction(() => {
    for (const item of CURATED_CARD_CATALOG) {
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
        rewardCategoriesJson: JSON.stringify(item.rewardCategories || []),
        benefitsJson: JSON.stringify(item.benefits || []),
        sortRank: item.sortRank ?? 100
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
    if (!res.ok) {
      throw new Error(`Upstream credit-card-bonuses-api HTTP ${res.status}`);
    }
    rawCards = await res.json();
  } finally {
    clearTimeout(timeout);
  }

  if (!Array.isArray(rawCards)) {
    throw new Error('Invalid upstream credit-card-bonuses-api payload');
  }

  const updateCuratedStmt = db.prepare(`
    UPDATE credit_card_catalog
       SET annual_fee_cents = @annualFeeCents,
           is_annual_fee_waived = @isAnnualFeeWaived,
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
      @rewardText, @estimatedValueCents,
      @targetSpendCents, @spendWindowDays,
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
      as_of = excluded.as_of,
      last_synced_at = datetime('now'),
      updated_at = datetime('now')
    WHERE credit_card_catalog.is_curated = 0
  `);

  const now = new Date();
  const asOf = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  let updatedCurated = 0;
  let upsertedLongTail = 0;

  const trx = db.transaction(() => {
    for (const card of rawCards) {
      if (!card || card.discontinued || !card.cardId || !card.name) continue;
      const offer = parseUpstreamOffer(card);
      const imagePath =
        typeof card.imageUrl === 'string' && RASTER_IMAGE_EXT_RE.test(card.imageUrl)
          ? card.imageUrl
          : null;
      const annualFeeCents = Math.max(0, Math.round((Number(card.annualFee) || 0) * 100));
      const isAnnualFeeWaived = card.isAnnualFeeWaived ? 1 : 0;

      const res = updateCuratedStmt.run({
        bonusApiCardId: card.cardId,
        annualFeeCents,
        isAnnualFeeWaived,
        upstreamImagePath: imagePath,
        rewardText: offer.rewardText,
        estimatedValueCents: offer.estimatedValueCents,
        targetSpendCents: offer.targetSpendCents,
        spendWindowDays: offer.spendWindowDays,
        isElevated: offer.isElevated ? 1 : 0,
        asOf
      });

      if (res.changes > 0) {
        updatedCurated += res.changes;
        continue;
      }

      const issuerName = normalizeIssuer(card.issuer, card.name);
      const network = normalizeNetwork(card.network);
      const id = slugifyCardId(card.issuer, card.name);
      const isBusiness = Boolean(card.isBusiness);
      const isCapOneSpark =
        isBusiness && issuerName === 'Capital One' && !String(card.name).toLowerCase().includes('venture x');
      const isDiscoverBiz = isBusiness && issuerName === 'Discover';
      const countsToward524Override = isCapOneSpark || isDiscoverBiz ? 1 : null;
      const subCooldownMonths =
        issuerName === 'American Express' ? 999 : issuerName === 'Chase' ? 24 : 48;

      const baseRate = Number(card.universalCashbackPercent) || 1.0;
      const rewardCategories = [
        { category: 'Everything Else', label: 'Everything Else (Base Rate)', multiplier: baseRate }
      ];

      const rawCredits = Array.isArray(card.credits) ? card.credits : [];
      const benefits = rawCredits
        .filter((c) => c && c.description && Number(c.value) > 0)
        .map((c, idx) => {
          const valCents = Math.round(Number(c.value) * 100);
          return {
            id: `${id}-credit-${idx + 1}`,
            title: `$${Math.round(Number(c.value))} ${c.description}`,
            period_value: Number(c.value),
            period_value_cents: valCents,
            cadence: 'ANNUAL',
            anchor: 'CARDMEMBER_YEAR',
            description: String(c.description)
          };
        });

      upsertUpstreamCardStmt.run({
        id,
        bonusApiCardId: card.cardId,
        shortLabel: String(card.name).slice(0, 36),
        cardName: `${issuerName.startsWith(card.name.split(' ')[0]) ? '' : ''}${card.name}`.trim(),
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

  const row = db
    .prepare('SELECT upstream_image_path FROM credit_card_catalog WHERE id = ?')
    .get(cardId);
  const imagePath = row?.upstream_image_path;
  if (!imagePath || !RASTER_IMAGE_EXT_RE.test(imagePath)) {
    return null;
  }

  const url = `${UPSTREAM_IMAGE_BASE_URL}${imagePath.startsWith('/') ? '' : '/'}${imagePath}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetchImpl(url, { signal: controller.signal });
    if (!res.ok) return null;
    const contentType = String(res.headers.get('content-type') || 'image/png').split(';')[0].trim();
    if (!contentType.startsWith('image/') || contentType.includes('svg') || contentType.includes('avif')) {
      return null;
    }
    const arrayBuf = await res.arrayBuffer();
    if (!arrayBuf || arrayBuf.byteLength === 0 || arrayBuf.byteLength > 2 * 1024 * 1024) {
      return null;
    }
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
    annual_fee_cents: Number(row.annual_fee_cents) || 0,
    is_annual_fee_waived: Boolean(row.is_annual_fee_waived),
    is_business_card: Boolean(row.is_business_card),
    counts_toward_524_override:
      row.counts_toward_524_override === null || row.counts_toward_524_override === undefined
        ? null
        : Boolean(row.counts_toward_524_override),
    sub_cooldown_months:
      row.sub_cooldown_months === null || row.sub_cooldown_months === undefined
        ? null
        : Number(row.sub_cooldown_months),
    default_sub_reward_text: row.default_sub_reward_text || null,
    default_sub_estimated_value_cents:
      row.default_sub_estimated_value_cents === null || row.default_sub_estimated_value_cents === undefined
        ? null
        : Number(row.default_sub_estimated_value_cents),
    default_sub_target_spend_cents:
      row.default_sub_target_spend_cents === null || row.default_sub_target_spend_cents === undefined
        ? null
        : Number(row.default_sub_target_spend_cents),
    sub_spend_window_days:
      row.sub_spend_window_days === null || row.sub_spend_window_days === undefined
        ? null
        : Number(row.sub_spend_window_days),
    is_elevated_sub_offer: Boolean(row.is_elevated_sub_offer),
    reward_categories: safeJsonParse(row.reward_categories_json, []),
    benefits: safeJsonParse(row.benefits_json, []),
    is_curated: Boolean(row.is_curated),
    as_of: row.as_of || '2026-11',
    last_synced_at: row.last_synced_at || null
  };
}

export function listCatalogTemplates(db) {
  const count = db.prepare('SELECT COUNT(*) AS cnt FROM credit_card_catalog').get()?.cnt || 0;
  if (count === 0) {
    ensureCreditCardCatalogSeeded(db);
  }
  return db
    .prepare(
      `SELECT *
         FROM credit_card_catalog
        ORDER BY sort_rank ASC, issuer_name ASC, card_name ASC`
    )
    .all()
    .map(serializeCatalogRow);
}

