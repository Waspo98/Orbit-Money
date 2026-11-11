import { config } from '../config.js';
import {
  ensureCandidates,
  extractDomain,
  logoDevUrl,
  merchantLogoKey,
  publicLogoDevUrl
} from './merchantLogos.js';

const PROVIDER = 'logo_dev';

const INSTITUTION_DOMAIN_MAP = new Map([
  ['chase', 'chase.com'],
  ['chase bank', 'chase.com'],
  ['jpmorgan', 'chase.com'],
  ['jpmorgan chase', 'chase.com'],
  ['jp morgan', 'chase.com'],
  ['betterment', 'betterment.com'],
  ['fidelity', 'fidelity.com'],
  ['fidelity investments', 'fidelity.com'],
  ['fidelity netbenefits', 'fidelity.com'],
  ['vanguard', 'vanguard.com'],
  ['schwab', 'schwab.com'],
  ['charles schwab', 'schwab.com'],
  ['ally', 'ally.com'],
  ['ally bank', 'ally.com'],
  ['sofi', 'sofi.com'],
  ['capital one', 'capitalone.com'],
  ['american express', 'americanexpress.com'],
  ['amex', 'americanexpress.com'],
  ['bank of america', 'bankofamerica.com'],
  ['bofa', 'bankofamerica.com'],
  ['wells fargo', 'wellsfargo.com'],
  ['citi', 'citi.com'],
  ['citibank', 'citi.com'],
  ['discover', 'discover.com'],
  ['discover bank', 'discover.com'],
  ['wealthfront', 'wealthfront.com'],
  ['marcus', 'marcus.com'],
  ['goldman sachs', 'marcus.com'],
  ['usaa', 'usaa.com'],
  ['navy federal', 'navyfederal.org'],
  ['navy federal credit union', 'navyfederal.org'],
  ['us bank', 'usbank.com'],
  ['u s bank', 'usbank.com'],
  ['pnc', 'pnc.com'],
  ['pnc bank', 'pnc.com'],
  ['td bank', 'td.com'],
  ['truist', 'truist.com'],
  ['fifth third', '53.com'],
  ['fifth third bank', '53.com'],
  ['huntington', 'huntington.com'],
  ['huntington bank', 'huntington.com'],
  ['citizens', 'citizensbank.com'],
  ['citizens bank', 'citizensbank.com'],
  ['bmo', 'bmo.com'],
  ['bmo harris', 'bmo.com'],
  ['m and t bank', 'mtb.com'],
  ['mt bank', 'mtb.com'],
  ['synchrony', 'synchrony.com'],
  ['synchrony bank', 'synchrony.com'],
  ['barclays', 'barclaysus.com'],
  ['robinhood', 'robinhood.com'],
  ['coinbase', 'coinbase.com'],
  ['m1', 'm1.com'],
  ['m1 finance', 'm1.com'],
  ['etrade', 'etrade.com'],
  ['e trade', 'etrade.com'],
  ['morgan stanley', 'morganstanley.com'],
  ['merrill', 'ml.com'],
  ['merrill lynch', 'ml.com'],
  ['merrill edge', 'ml.com'],
  ['empower', 'empower.com'],
  ['personal capital', 'empower.com'],
  ['principal', 'principal.com'],
  ['tiaa', 'tiaa.org'],
  ['paypal', 'paypal.com'],
  ['venmo', 'venmo.com'],
  ['apple', 'apple.com'],
  ['apple cash', 'apple.com'],
  ['apple card', 'apple.com'],
  ['rocket mortgage', 'rocketmortgage.com'],
  ['mr cooper', 'mrcooper.com'],
  ['pennymac', 'pennymac.com'],
  ['alliant', 'alliantcreditunion.org'],
  ['alliant credit union', 'alliantcreditunion.org'],
  ['becu', 'becu.org'],
  ['penfed', 'penfed.org'],
  ['chime', 'chime.com'],
  ['wise', 'wise.com'],
  ['revolut', 'revolut.com']
]);

function normalizeInstitutionLookup(raw) {
  return String(raw || '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+(n a|na|inc|llc|corp|corporation|co|ltd)$/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function resolveInstitutionDomain(raw) {
  const explicit = extractDomain(raw);
  if (explicit) return explicit;
  const norm = normalizeInstitutionLookup(raw);
  if (!norm) return null;
  if (INSTITUTION_DOMAIN_MAP.has(norm)) return INSTITUTION_DOMAIN_MAP.get(norm);
  const withoutBank = norm.replace(/\s+(bank|financial|investments|savings|mortgage)$/i, '').trim();
  if (withoutBank && INSTITUTION_DOMAIN_MAP.has(withoutBank)) {
    return INSTITUTION_DOMAIN_MAP.get(withoutBank);
  }
  return null;
}

function cleanInstitutionDisplayName(raw) {
  return String(raw || '').replace(/\s+/g, ' ').trim();
}

function fallbackFaviconUrl(domain) {
  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=128`;
}

function formatInstitutionCachedLogo(cached) {
  if (!cached) return null;
  const hasLogo = Boolean(cached.logo_url) && cached.status !== 'failed' && cached.status !== 'hidden';
  let url = null;
  if (hasLogo) {
    if (cached.status === 'manual') {
      url = cached.logo_url;
    } else {
      const domain = extractDomain(cached.provider_query);
      url = config.logoDevPublishableKey
        ? publicLogoDevUrl(cached.provider_query, domain ? 'domain' : 'name')
        : (domain ? fallbackFaviconUrl(domain) : null);
    }
  }
  return {
    merchant_key: cached.merchant_key,
    merchant_name: cached.merchant_name,
    provider: cached.provider,
    provider_query: cached.provider_query,
    url,
    status: cached.status
  };
}

function candidateForAccount(row) {
  const display = cleanInstitutionDisplayName(row?.institution);
  if (!display || display.length < 2) return null;

  const domain = resolveInstitutionDomain(display);
  if (!domain && !config.logoDevPublishableKey) return null;

  const slug = merchantLogoKey(display);
  if (!domain && (!slug || display.length > 48)) return null;

  const providerQuery = domain || display;
  const lookup = domain ? 'domain' : 'name';
  const key = domain ? `inst:${domain}` : `inst:${slug}`;
  const storedUrl = config.logoDevPublishableKey
    ? logoDevUrl(providerQuery, false, lookup)
    : fallbackFaviconUrl(domain);

  return {
    merchant_key: key,
    merchant_name: display,
    provider: PROVIDER,
    provider_query: providerQuery,
    logo_url: storedUrl
  };
}

function identityForAccount(row) {
  const display = cleanInstitutionDisplayName(row?.institution);
  if (display && display.length >= 2) {
    const domain = resolveInstitutionDomain(display);
    const slug = merchantLogoKey(display);
    if (domain || slug) {
      return {
        merchant_key: domain ? `inst:${domain}` : `inst:${slug}`,
        merchant_name: display,
        provider: PROVIDER,
        provider_query: domain || display
      };
    }
  }
  const fallbackName = cleanInstitutionDisplayName(row?.name) || `Account ${row?.id || ''}`.trim();
  return {
    merchant_key: `inst:acct-${row?.id}`,
    merchant_name: fallbackName,
    provider: PROVIDER,
    provider_query: fallbackName
  };
}

export function attachInstitutionLogos(db, rows) {
  if (!Array.isArray(rows) || rows.length === 0) return rows;

  const candidateByKey = new Map();
  const primaryKeys = new Map();
  const acctFallbackKeys = new Map();

  for (const row of rows) {
    const acctKey = row?.id ? `inst:acct-${row.id}` : null;
    if (acctKey) acctFallbackKeys.set(row.id, acctKey);

    const candidate = candidateForAccount(row);
    if (candidate) {
      candidateByKey.set(candidate.merchant_key, candidate);
      primaryKeys.set(row.id, candidate.merchant_key);
    } else if (row?.institution) {
      const id = identityForAccount(row);
      primaryKeys.set(row.id, id.merchant_key);
    }
  }

  ensureCandidates(db, [...candidateByKey.values()]);

  const keys = [
    ...new Set([
      ...[...primaryKeys.values()].filter(Boolean),
      ...[...acctFallbackKeys.values()].filter(Boolean)
    ])
  ];
  if (keys.length === 0) {
    return rows.map((row) => ({ ...row, institution_logo: null }));
  }

  const cachedRows = db
    .prepare(
      `SELECT merchant_key, merchant_name, provider, provider_query, logo_url, status
         FROM merchant_logo_cache
        WHERE merchant_key IN (${keys.map(() => '?').join(',')})`
    )
    .all(...keys);
  const cacheByKey = new Map(cachedRows.map((r) => [r.merchant_key, r]));

  return rows.map((row) => {
    const primary = primaryKeys.get(row.id);
    const fallback = acctFallbackKeys.get(row.id);
    const cached = (primary && cacheByKey.get(primary)) || (fallback && cacheByKey.get(fallback)) || null;
    return {
      ...row,
      institution_logo: formatInstitutionCachedLogo(cached)
    };
  });
}

export function attachInstitutionLogo(db, row) {
  if (!row) return row;
  return attachInstitutionLogos(db, [row])[0];
}

export function ensureInstitutionLogoEntryForAccount(db, accountId, householdId = 1) {
  const row = db
    .prepare('SELECT id, name, institution FROM accounts WHERE id = ? AND household_id = ?')
    .get(accountId, householdId);
  if (!row) return { found: false };

  const candidate = candidateForAccount(row);
  const identity = candidate || identityForAccount(row);

  db.prepare(
    `INSERT INTO merchant_logo_cache
       (merchant_key, merchant_name, provider, provider_query, logo_url, status)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(merchant_key) DO NOTHING`
  ).run(
    identity.merchant_key,
    identity.merchant_name,
    identity.provider,
    identity.provider_query,
    candidate?.logo_url || null,
    candidate ? 'candidate' : 'hidden'
  );

  const cached = db
    .prepare(
      `SELECT merchant_key, merchant_name, provider, provider_query, logo_url, status
         FROM merchant_logo_cache
        WHERE merchant_key = ?`
    )
    .get(identity.merchant_key);

  return { found: true, merchant_logo: formatInstitutionCachedLogo(cached) };
}

export async function searchInstitutionLogoBrands(db, { accountId, query, householdId = 1 }) {
  const ensured = ensureInstitutionLogoEntryForAccount(db, accountId, householdId);
  if (!ensured.found) return { found: false };

  const cleanedQuery = cleanInstitutionDisplayName(query).slice(0, 80);
  if (!cleanedQuery || cleanedQuery.length < 2) {
    return {
      found: true,
      configured: Boolean(config.logoDevSecretKey && config.logoDevPublishableKey),
      merchant_logo: ensured.merchant_logo,
      candidates: []
    };
  }

  if (!config.logoDevSecretKey || !config.logoDevPublishableKey) {
    const mappedDomain = resolveInstitutionDomain(cleanedQuery);
    const fallbackCandidates = mappedDomain
      ? [{ name: cleanedQuery, domain: mappedDomain, logo_url: fallbackFaviconUrl(mappedDomain) }]
      : [];
    return {
      found: true,
      configured: Boolean(mappedDomain),
      merchant_logo: ensured.merchant_logo,
      candidates: fallbackCandidates
    };
  }

  const params = new URLSearchParams({ q: cleanedQuery, strategy: 'match' });
  const response = await fetch(`https://api.logo.dev/search?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${config.logoDevSecretKey}`,
      Accept: 'application/json'
    }
  });
  if (!response.ok) {
    throw new Error(`Logo search failed with ${response.status}`);
  }
  const results = await response.json();
  const candidates = Array.isArray(results)
    ? results
        .filter((item) => item?.domain)
        .slice(0, 10)
        .map((item) => ({
          name: String(item.name || item.domain).trim(),
          domain: String(item.domain).trim().toLowerCase(),
          logo_url: publicLogoDevUrl(String(item.domain).trim().toLowerCase(), 'domain')
        }))
    : [];

  return {
    found: true,
    configured: true,
    merchant_logo: ensured.merchant_logo,
    candidates
  };
}
