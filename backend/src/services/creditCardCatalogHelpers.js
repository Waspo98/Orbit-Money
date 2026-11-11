// =============================================================================
// creditCardCatalogHelpers.js - Normalization, upstream offer parsing & wire mappers
// =============================================================================

export const UPSTREAM_IMAGE_BASE_URL = 'https://www.offeroptimist.com';
export const RASTER_IMAGE_EXT_RE = /\.(png|jpe?g|webp)$/i;
export const CATALOG_SCHEMA_TAG = '2026-11-v2';

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
  FIRST: 'Cardless',
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
  WELLS_FARGO: 'Wells Fargo Rewards pts',
  BANK_OF_AMERICA: 'Preferred Rewards pts',
  US_BANK: 'Altitude Rewards pts',
  BARCLAYS: 'Bonus pts',
  PENFED: 'PenFed Bonus pts',
  DELTA: 'Delta SkyMiles',
  UNITED: 'United Miles',
  SOUTHWEST: 'Rapid Rewards pts',
  AMERICAN: 'AAdvantage Miles',
  AA: 'AAdvantage Miles',
  JETBLUE: 'TrueBlue pts',
  ALASKA: 'Atmos Rewards pts',
  ATMOS: 'Atmos Rewards pts',
  FLYING_BLUE: 'Flying Blue Miles',
  CATHAY_PACIFIC: 'Cathay Asia Miles',
  ANA: 'ANA Mileage Club Miles',
  KOREAN: 'Korean Air SKYPASS Miles',
  VIRGIN: 'Virgin Points',
  SPIRIT: 'Free Spirit pts',
  LATAM: 'LATAM Pass Miles',
  BREEZE: 'BreezePoints',
  AMTRAK: 'Amtrak Guest Rewards pts',
  CARNIVAL: 'Carnival FunPoints',
  EXPEDIA: 'OneKeyCash',
  MARRIOTT: 'Marriott Bonvoy pts',
  HILTON: 'Hilton Honors pts',
  HYATT: 'World of Hyatt pts',
  IHG: 'IHG One Rewards pts',
  WYNDHAM: 'Wyndham Rewards pts',
  CHOICE: 'Choice Privileges pts',
  BEST_WESTERN: 'Best Western Rewards pts',
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
  EXPEDIA: 100,
  CHASE: 1.5,
  AMERICAN_EXPRESS: 1.5,
  BILT: 1.6,
  HYATT: 1.8,
  AMTRAK: 2.5,
  CAPITAL_ONE: 1.4,
  CITI: 1.4,
  SOUTHWEST: 1.3,
  AMERICAN: 1.3,
  AA: 1.3,
  ALASKA: 1.3,
  ATMOS: 1.3,
  AEROPLAN: 1.3,
  ANA: 1.3,
  KOREAN: 1.3,
  VIRGIN: 1.3,
  UNITED: 1.2,
  DELTA: 1.2,
  JETBLUE: 1.2,
  AVIOS: 1.2,
  FLYING_BLUE: 1.2,
  CATHAY_PACIFIC: 1.2,
  WELLS_FARGO: 1.0,
  BANK_OF_AMERICA: 1.0,
  US_BANK: 1.0,
  PENFED: 0.85,
  WYNDHAM: 0.9,
  MARRIOTT: 0.8,
  CHOICE: 0.6,
  BEST_WESTERN: 0.6,
  IHG: 0.5,
  HILTON: 0.5
};

export function normalizeNetwork(raw) {
  const upper = String(raw || '').toUpperCase();
  if (upper.includes('AMERICAN_EXPRESS') || upper === 'AMEX') return 'Amex';
  if (upper.includes('MASTER')) return 'Mastercard';
  if (upper.includes('DISCOVER')) return 'Discover';
  return 'Visa';
}

export function normalizeIssuer(raw, cardName = '') {
  if (String(cardName).toLowerCase().includes('bilt')) return 'Wells Fargo / Bilt';
  return ISSUER_NAMES[String(raw || '').toUpperCase()] || String(raw || 'Bank').replace(/_/g, ' ');
}

export function slugifyCardId(issuerCode, name) {
  const prefix = String(issuerCode || 'card').toLowerCase().replace(/[^a-z0-9]+/g, '_');
  const slug = String(name || 'card').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
  return `${prefix}_${slug}`.slice(0, 64);
}

export function inferArtKey(issuerName, cardName) {
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

function humanizeCurrencyUnit(currency) {
  if (CURRENCY_LABELS[currency]) return CURRENCY_LABELS[currency];
  const words = String(currency || 'Bonus')
    .split('_')
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase());
  return `${words.join(' ')} pts`;
}

export function parseUpstreamOffer(card) {
  const offers = Array.isArray(card.offers) ? card.offers : [];
  const primaryOffer = offers[0];
  if (!primaryOffer) {
    return { rewardText: null, estimatedValueCents: null, targetSpendCents: null, spendWindowDays: null, isElevated: false };
  }

  const rawAmount = Number(primaryOffer.amount?.[0]?.amount || 0);
  const currency = String(card.currency || 'USD').toUpperCase();
  const offerCredits = Array.isArray(primaryOffer.credits) ? primaryOffer.credits : [];
  const statementCreditDollars = offerCredits
    .filter((c) => Number(c?.value) > 0 && Number(c?.value) <= 1000)
    .reduce((sum, c) => sum + Number(c.value), 0);

  let rewardText = null;
  if (rawAmount > 0) {
    if (currency === 'USD') {
      rewardText = `$${rawAmount.toLocaleString('en-US')} Cash Bonus`;
    } else if (currency === 'EXPEDIA') {
      rewardText = `$${rawAmount.toLocaleString('en-US')} OneKeyCash`;
    } else if (rawAmount <= 10) {
      rewardText = `${rawAmount} Free Night Awards`;
    } else {
      rewardText = `${rawAmount.toLocaleString('en-US')} ${humanizeCurrencyUnit(currency)}`;
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
    if (currency === 'USD' || currency === 'EXPEDIA') {
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
  if (estimatedValueCents !== null && (estimatedValueCents < 1000 || estimatedValueCents > 500000)) {
    estimatedValueCents = null;
    rewardText = null;
  }

  const spendDollars = Number(primaryOffer.spend || 0);
  const targetSpendCents = spendDollars > 0 ? Math.round(spendDollars * 100) : null;
  const days = Number(primaryOffer.days || 90);
  const spendWindowDays = days > 0 && days <= 730 ? days : 90;

  const histAmounts = (Array.isArray(card.historicalOffers) ? card.historicalOffers : [])
    .map((h) => Number(h.amount?.[0]?.amount || 0))
    .filter((n) => n > 0);
  const maxHist = histAmounts.length > 0 ? Math.max(...histAmounts) : 0;
  const minHist = histAmounts.length > 0 ? Math.min(...histAmounts) : 0;
  const isElevated = rawAmount > 0 && histAmounts.length >= 2 && rawAmount >= maxHist && maxHist > minHist;

  return { rewardText, estimatedValueCents, targetSpendCents, spendWindowDays, isElevated };
}

export function normalizeWireRewardCategory(rc) {
  const mult = Number(rc?.rate ?? rc?.multiplier ?? 1.0) || 1.0;
  const rot = Boolean(rc?.is_rotating ?? rc?.isRotating ?? false);
  const capCents = rc?.cap_cents ?? rc?.capCents ?? (rc?.cap != null ? Math.round(Number(rc.cap) * 100) : null);
  return {
    category: rc?.category || rc?.label || 'Everything Else',
    label: rc?.label || rc?.category || 'Every Purchase',
    rate: mult,
    multiplier: mult,
    is_rotating: rot,
    isRotating: rot,
    cap: rc?.cap != null ? Number(rc.cap) : capCents != null ? capCents / 100 : null,
    capCents,
    type: rc?.type || 'multiplier'
  };
}

export function normalizeWireBenefit(ben) {
  const cents =
    ben?.period_value_cents !== undefined
      ? ben.period_value_cents
      : ben?.period_value != null
        ? Math.round(Number(ben.period_value) * 100)
        : null;
  return {
    ...ben,
    name: ben?.name || ben?.title || '',
    title: ben?.title || ben?.name || '',
    period_value: ben?.period_value !== undefined ? ben.period_value : cents != null ? cents / 100 : null,
    period_value_cents: cents
  };
}
