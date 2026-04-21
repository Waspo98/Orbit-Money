import { config } from '../config.js';

const PROVIDER = 'logo_dev';
const DOMAIN_RE = /\b(?:https?:\/\/)?(?:www\.)?([a-z0-9][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)+\.[a-z]{2,})\b/i;
const GENERIC_MERCHANTS = new Set([
  'unknown',
  'transfer',
  'payment',
  'online payment',
  'automatic payment',
  'deposit',
  'withdrawal',
  'check',
  'atm',
  'interest',
  'dividend'
]);
const AMBIGUOUS_MERCHANT_WORDS = [
  'ach',
  'autopay',
  'bill pay',
  'card',
  'checkcard',
  'credit',
  'debit',
  'direct debit',
  'external transfer',
  'mobile payment',
  'online transfer',
  'payment',
  'pos',
  'purchase',
  'recurring',
  'transfer',
  'withdrawal'
];
const PAYMENT_PREFIX_RE = /^(sq|tst|sp|paypal|pp|stripe|sumup|toast|clover)[\s*.-]+/i;
const SAFE_SHORT_NAMES = new Set(['aldi', 'amex', 'apple', 'at&t', 'aws', 'cvs', 'etsy', 'hulu', 'ikea', 'lyft', 'uber', 'ups', 'usps']);

function cleanMerchantName(value) {
  return String(value || '')
    .trim()
    .replace(/\s+/g, ' ')
    .replace(PAYMENT_PREFIX_RE, '')
    .replace(/\s+#?\d{3,}\b.*$/i, '')
    .replace(/\b(store|location|terminal|auth|pending)\s*#?\d*\b/gi, ' ')
    .replace(/\b\d{2}\/\d{2}(\/\d{2,4})?\b/g, ' ')
    .replace(/\b\d{4,}\b/g, ' ')
    .replace(/\s+(inc|llc|ltd|co|corp|corporation)\.?$/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function merchantLogoKey(value) {
  const cleaned = cleanMerchantName(value).toLowerCase();
  return cleaned
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, '-');
}

function extractDomain(value) {
  const match = DOMAIN_RE.exec(String(value || '').toLowerCase());
  if (!match) return null;
  const domain = match[1].replace(/^www\./, '');
  if (domain.endsWith('.local') || domain.endsWith('.invalid')) return null;
  return domain;
}

function isStrictNameCandidate(query, rawText) {
  const lowerQuery = query.toLowerCase();
  const lowerRaw = String(rawText || '').toLowerCase();
  if (GENERIC_MERCHANTS.has(lowerQuery)) return false;
  if (AMBIGUOUS_MERCHANT_WORDS.some((word) => lowerQuery.includes(word))) return false;
  if (PAYMENT_PREFIX_RE.test(String(rawText || ''))) return false;
  if (/[#*_/@\\]/.test(query)) return false;
  if (/\d/.test(query)) return false;
  if (/\b\d{3,}\b/.test(lowerRaw)) return false;

  const words = lowerQuery.split(/\s+/).filter(Boolean);
  if (words.length === 0 || words.length > 3) return false;
  if (query.length < 3 || query.length > 32) return false;
  if (words.length === 1 && words[0].length < 4 && !SAFE_SHORT_NAMES.has(words[0])) return false;
  if (words.some((word) => word.length < 3 && !['&', 'and'].includes(word))) return false;

  return true;
}

function isCuratedMerchantName(row, query) {
  if (row?.edited_merchant_source) return true;

  const original = cleanMerchantName(row?.original_merchant || row?.merchant);
  const displayed = cleanMerchantName(row?.merchant);
  return (
    merchantLogoKey(original) === merchantLogoKey(query) &&
    merchantLogoKey(displayed) === merchantLogoKey(query) &&
    String(row?.original_merchant || row?.merchant || '').trim() === query
  );
}

function logoDevUrl(query, includeToken = false, lookup = 'name') {
  const params = new URLSearchParams({
    size: '96',
    retina: 'true',
    format: 'png',
    fallback: '404'
  });
  if (includeToken) {
    params.set('token', config.logoDevPublishableKey);
  }
  const path = lookup === 'domain' ? encodeURIComponent(query) : `name/${encodeURIComponent(query)}`;
  return `https://img.logo.dev/${path}?${params.toString()}`;
}

function candidateForTransaction(row) {
  if (!config.logoDevPublishableKey) return null;

  const rawText = [
    row?.merchant,
    row?.original_merchant,
    row?.original_description
  ].filter(Boolean).join(' ');
  const domain = extractDomain(rawText);
  const providerQuery = domain || cleanMerchantName(row?.merchant);
  const lookup = domain ? 'domain' : 'name';
  const key = domain ? `domain-${domain}` : merchantLogoKey(providerQuery);
  if (!providerQuery || providerQuery.length < 2 || !key) return null;
  if (!domain && !isStrictNameCandidate(providerQuery, rawText)) return null;
  if (!domain && !isCuratedMerchantName(row, providerQuery)) return null;

  return {
    merchant_key: key,
    merchant_name: providerQuery,
    provider: PROVIDER,
    provider_query: providerQuery,
    logo_url: logoDevUrl(providerQuery, false, lookup)
  };
}

function ensureCandidates(db, candidates) {
  if (candidates.length === 0) return;

  const insert = db.prepare(`
    INSERT INTO merchant_logo_cache
      (merchant_key, merchant_name, provider, provider_query, logo_url, status)
    VALUES
      (@merchant_key, @merchant_name, @provider, @provider_query, @logo_url, 'candidate')
    ON CONFLICT(merchant_key) DO UPDATE SET
      merchant_name = excluded.merchant_name,
      provider_query = excluded.provider_query,
      logo_url = CASE
        WHEN merchant_logo_cache.status IN ('manual','hidden') THEN merchant_logo_cache.logo_url
        ELSE excluded.logo_url
      END,
      updated_at = datetime('now')
    WHERE merchant_logo_cache.status = 'candidate'
  `);

  const trx = db.transaction((items) => {
    for (const item of items) insert.run(item);
  });
  trx(candidates);
}

export function attachMerchantLogos(db, rows) {
  if (!Array.isArray(rows) || rows.length === 0) return rows;

  const candidateByKey = new Map();
  const rowKeys = new Map();

  for (const row of rows) {
    const candidate = candidateForTransaction(row);
    if (!candidate) {
      rowKeys.set(row?.id, null);
      continue;
    }
    candidateByKey.set(candidate.merchant_key, candidate);
    rowKeys.set(row.id, candidate.merchant_key);
  }

  const candidates = [...candidateByKey.values()];
  ensureCandidates(db, candidates);

  if (candidateByKey.size === 0) {
    return rows.map((row) => ({ ...row, merchant_logo: null }));
  }

  const keys = [...candidateByKey.keys()];
  const cachedRows = db
    .prepare(`
      SELECT merchant_key, merchant_name, provider, provider_query, logo_url, status
      FROM merchant_logo_cache
      WHERE merchant_key IN (${keys.map(() => '?').join(',')})
    `)
    .all(...keys);
  const cacheByKey = new Map(cachedRows.map((row) => [row.merchant_key, row]));

  return rows.map((row) => {
    const key = rowKeys.get(row.id);
    const cached = key ? cacheByKey.get(key) : null;
    const hasLogo =
      cached &&
      cached.logo_url &&
      cached.status !== 'failed' &&
      cached.status !== 'hidden';

    return {
      ...row,
      merchant_logo: hasLogo
        ? {
            merchant_key: cached.merchant_key,
            merchant_name: cached.merchant_name,
            provider: cached.provider,
            provider_query: cached.provider_query,
            url:
              cached.status === 'manual'
                ? cached.logo_url
                : logoDevUrl(
                    cached.provider_query,
                    true,
                    extractDomain(cached.provider_query) ? 'domain' : 'name'
                  ),
            status: cached.status
          }
        : null
    };
  });
}

export function reportMerchantLogoStatus(db, { merchantKey, status }) {
  if (!merchantKey || !['loaded', 'failed'].includes(status)) {
    return { updated: false };
  }

  const existing = db
    .prepare('SELECT status FROM merchant_logo_cache WHERE merchant_key = ?')
    .get(merchantKey);
  if (!existing || existing.status === 'manual' || existing.status === 'hidden') {
    return { updated: false };
  }

  const result = db
    .prepare(`
      UPDATE merchant_logo_cache
      SET status = CASE
            WHEN ? = 'failed' AND failure_count + 1 < 2 THEN status
            ELSE ?
          END,
          failure_count = CASE WHEN ? = 'failed' THEN failure_count + 1 ELSE 0 END,
          last_checked_at = datetime('now'),
          updated_at = datetime('now')
      WHERE merchant_key = ?
        AND status NOT IN ('manual','hidden')
    `)
    .run(status, status, status, merchantKey);

  return { updated: result.changes > 0 };
}
