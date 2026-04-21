import { config } from '../config.js';

const PROVIDER = 'logo_dev';
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

function cleanMerchantName(value) {
  return String(value || '')
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/^(sq|tst|sp|paypal|pp|zelle|venmo|cash app|stripe)[\s*.-]+/i, '')
    .replace(/\s+#?\d{3,}\b.*$/i, '')
    .replace(/\b\d{2}\/\d{2}(\/\d{2,4})?\b/g, ' ')
    .replace(/\b\d{4,}\b/g, ' ')
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

function logoDevUrl(query, includeToken = false) {
  const params = new URLSearchParams({
    size: '96',
    retina: 'true',
    format: 'png',
    fallback: '404'
  });
  if (includeToken) {
    params.set('token', config.logoDevPublishableKey);
  }
  return `https://img.logo.dev/name/${encodeURIComponent(query)}?${params.toString()}`;
}

function candidateForMerchant(merchant) {
  if (!config.logoDevPublishableKey) return null;

  const providerQuery = cleanMerchantName(merchant);
  const key = merchantLogoKey(providerQuery);
  if (!providerQuery || providerQuery.length < 2 || !key) return null;
  if (GENERIC_MERCHANTS.has(providerQuery.toLowerCase())) return null;

  return {
    merchant_key: key,
    merchant_name: providerQuery,
    provider: PROVIDER,
    provider_query: providerQuery,
    logo_url: logoDevUrl(providerQuery)
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
    const candidate = candidateForMerchant(row?.merchant);
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
                : logoDevUrl(cached.provider_query, true),
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
