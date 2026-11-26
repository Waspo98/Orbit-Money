import { config } from '../config.js';

export const LOGO_PROVIDER = 'logo_dev';

export function logoDevUrl(query, includeToken = false, lookup = 'name') {
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

export function publicLogoDevUrl(query, lookup = 'name') {
  if (!config.logoDevPublishableKey) return null;
  return logoDevUrl(query, true, lookup);
}

export function ensureCandidates(db, candidates) {
  if (!Array.isArray(candidates) || candidates.length === 0) return;

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
