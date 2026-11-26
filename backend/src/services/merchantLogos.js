import { config } from '../config.js';
import {
  attachInstitutionLogos,
  fallbackFaviconUrl,
  formatInstitutionCachedLogo,
  resolveCounterpartyInstitutionDomain,
  resolveInstitutionDomain
} from './institutionLogos.js';
import {
  LOGO_PROVIDER,
  ensureCandidates,
  logoDevUrl,
  publicLogoDevUrl
} from './logoCachePrimitives.js';
import {
  VALID_LOGO_SCOPES,
  cleanMerchantName,
  extractDomain,
  isCuratedMerchantName,
  isStrictNameCandidate,
  merchantLogoKey,
  resolveTransactionScopeKeys
} from './merchantLogoClassifier.js';

export {
  ensureCandidates,
  extractDomain,
  logoDevUrl,
  merchantLogoKey,
  publicLogoDevUrl
};

const TRANSACTION_LOGO_SELECT = `
  id,
  account_id,
  COALESCE(edited_merchant, original_merchant) AS merchant,
  original_merchant,
  original_description,
  edited_merchant_source,
  transfer_pair_id
`;

function buildAccountContextMaps(db, rows) {
  const accountIds = [...new Set(rows.map((r) => Number(r?.account_id)).filter((id) => Number.isInteger(id) && id > 0))];
  const pairIds = [...new Set(rows.map((r) => Number(r?.transfer_pair_id)).filter((id) => Number.isInteger(id) && id > 0))];

  const accountById = new Map();
  if (accountIds.length > 0) {
    const accountRows = db
      .prepare(`SELECT id, name, institution FROM accounts WHERE id IN (${accountIds.map(() => '?').join(',')})`)
      .all(...accountIds);
    const enriched = attachInstitutionLogos(db, accountRows);
    for (const acct of enriched) {
      accountById.set(acct.id, acct);
    }
  }

  const pairInstKeyByTxnId = new Map();
  if (pairIds.length > 0) {
    const pairRows = db
      .prepare(
        `SELECT t.id AS pair_id, a.id AS account_id, a.name AS account_name, a.institution AS institution
           FROM transactions t
           JOIN accounts a ON a.id = t.account_id
          WHERE t.id IN (${pairIds.map(() => '?').join(',')})`
      )
      .all(...pairIds);
    const pairAccts = pairRows.map((p) => ({ id: p.account_id, name: p.account_name, institution: p.institution }));
    const enrichedPairAccts = attachInstitutionLogos(db, pairAccts);
    const pairAcctById = new Map(enrichedPairAccts.map((a) => [a.id, a]));
    for (const pair of pairRows) {
      const acct = pairAcctById.get(pair.account_id);
      if (acct?.institution_logo?.merchant_key) {
        pairInstKeyByTxnId.set(pair.pair_id, acct.institution_logo.merchant_key);
      }
    }
  }

  return { accountById, pairInstKeyByTxnId };
}

function buildRowResolution(row, { accountById, pairInstKeyByTxnId }) {
  const acct = row?.account_id ? accountById.get(Number(row.account_id)) : null;
  const accountInstKey = acct?.institution_logo?.merchant_key || null;
  const owningDomain = acct?.institution ? resolveInstitutionDomain(acct.institution) : null;

  const pairInstKey = row?.transfer_pair_id ? pairInstKeyByTxnId.get(Number(row.transfer_pair_id)) || null : null;
  const transferPairInstKey = pairInstKey && pairInstKey !== accountInstKey ? pairInstKey : null;

  const rawText = [row?.original_merchant, row?.original_description].filter(Boolean).join(' ');
  const counterpartyDomain = rawText ? resolveCounterpartyInstitutionDomain(rawText, owningDomain) : null;
  const counterpartyInstKey = counterpartyDomain ? `inst:${counterpartyDomain}` : null;

  const keys = resolveTransactionScopeKeys({
    row,
    accountInstKey,
    transferPairInstKey,
    counterpartyInstKey
  });

  return { keys, counterpartyDomain };
}

export function formatCachedLogo(cached, scope = null) {
  if (!cached) return null;
  const hasLogo =
    cached.logo_url &&
    cached.status !== 'failed' &&
    cached.status !== 'hidden';

  return {
    merchant_key: cached.merchant_key,
    merchant_name: cached.merchant_name,
    provider: cached.provider,
    provider_query: cached.provider_query,
    url: hasLogo
      ? cached.status === 'manual'
          ? cached.logo_url
          : publicLogoDevUrl(
              cached.provider_query,
              extractDomain(cached.provider_query) ? 'domain' : 'name'
            )
      : null,
    status: cached.status,
    ...(scope ? { scope } : {})
  };
}

function pickFromCascade(keys, cacheByKey) {
  const isOverrideStatus = (entry) => entry && (entry.status === 'manual' || entry.status === 'hidden');

  if (keys.txnKey) {
    const txnCached = cacheByKey.get(keys.txnKey);
    if (isOverrideStatus(txnCached)) {
      return formatCachedLogo(txnCached, 'transaction');
    }
  }

  if (keys.scopedKey) {
    const scopedCached = cacheByKey.get(keys.scopedKey);
    if (isOverrideStatus(scopedCached)) {
      return formatCachedLogo(scopedCached, 'institution');
    }
  }

  if (keys.isGeneric) {
    if (keys.explicitGlobalGenericKey) {
      const globalGeneric = cacheByKey.get(keys.explicitGlobalGenericKey);
      if (isOverrideStatus(globalGeneric)) {
        return formatCachedLogo(globalGeneric, 'global');
      }
    }

    if (keys.fallbackInstKey) {
      const instCached = cacheByKey.get(keys.fallbackInstKey);
      if (instCached) {
        const formattedInst = formatInstitutionCachedLogo(instCached);
        return {
          ...formattedInst,
          merchant_key: keys.scopedKey || instCached.merchant_key,
          merchant_name: keys.merchantName || instCached.merchant_name,
          scope: 'institution'
        };
      }
    }
    return null;
  }

  // Non-generic brand: honor explicit manual/hidden global overrides (slug first, then domain),
  // then fall back to auto-generated candidate/loaded entries.
  const globalSlugCached = keys.globalSlugKey ? cacheByKey.get(keys.globalSlugKey) : null;
  const globalDomainCached = keys.globalDomainKey ? cacheByKey.get(keys.globalDomainKey) : null;

  if (isOverrideStatus(globalSlugCached)) {
    return formatCachedLogo(globalSlugCached, 'global');
  }
  if (isOverrideStatus(globalDomainCached)) {
    return formatCachedLogo(globalDomainCached, 'global');
  }
  const fallbackGlobal = globalDomainCached || globalSlugCached;
  return fallbackGlobal ? formatCachedLogo(fallbackGlobal, 'global') : null;
}

export function attachMerchantLogos(db, rows) {
  if (!Array.isArray(rows) || rows.length === 0) return rows;

  const contextMaps = buildAccountContextMaps(db, rows);
  const candidateByKey = new Map();
  const counterpartyInstCandidates = new Map();
  const resolutionByIndex = [];
  const allLookupKeys = new Set();

  for (const row of rows) {
    const { keys, counterpartyDomain } = buildRowResolution(row, contextMaps);
    resolutionByIndex.push(keys);

    if (keys.autoCandidateKey && config.logoDevPublishableKey) {
      candidateByKey.set(keys.autoCandidateKey, {
        merchant_key: keys.autoCandidateKey,
        merchant_name: keys.autoCandidateQuery,
        provider: LOGO_PROVIDER,
        provider_query: keys.autoCandidateQuery,
        logo_url: logoDevUrl(keys.autoCandidateQuery, false, keys.autoCandidateLookup)
      });
    }

    if (keys.isGeneric && counterpartyDomain && keys.fallbackInstKey === `inst:${counterpartyDomain}`) {
      counterpartyInstCandidates.set(keys.fallbackInstKey, {
        merchant_key: keys.fallbackInstKey,
        merchant_name: counterpartyDomain,
        provider: LOGO_PROVIDER,
        provider_query: counterpartyDomain,
        logo_url: config.logoDevPublishableKey
          ? logoDevUrl(counterpartyDomain, false, 'domain')
          : fallbackFaviconUrl(counterpartyDomain)
      });
    }

    for (const k of [
      keys.txnKey,
      keys.scopedKey,
      keys.explicitGlobalGenericKey,
      keys.globalSlugKey,
      keys.globalDomainKey,
      keys.fallbackInstKey
    ]) {
      if (k) allLookupKeys.add(k);
    }
  }

  ensureCandidates(db, [...candidateByKey.values(), ...counterpartyInstCandidates.values()]);

  if (allLookupKeys.size === 0) {
    return rows.map((row) => ({ ...row, merchant_logo: null }));
  }

  const keyList = [...allLookupKeys];
  const cachedRows = db
    .prepare(`
      SELECT merchant_key, merchant_name, provider, provider_query, logo_url, status
      FROM merchant_logo_cache
      WHERE merchant_key IN (${keyList.map(() => '?').join(',')})
    `)
    .all(...keyList);
  const cacheByKey = new Map(cachedRows.map((row) => [row.merchant_key, row]));

  return rows.map((row, index) => ({
    ...row,
    merchant_logo: pickFromCascade(resolutionByIndex[index], cacheByKey)
  }));
}

export function ensureMerchantLogoEntryForTransaction(db, transactionId, householdId = 1) {
  const row = db
    .prepare(`SELECT ${TRANSACTION_LOGO_SELECT} FROM transactions WHERE id = ? AND household_id = ?`)
    .get(transactionId, householdId);

  if (!row) return { found: false };

  const [enriched] = attachMerchantLogos(db, [row]);
  if (enriched?.merchant_logo) {
    return { found: true, merchant_logo: enriched.merchant_logo };
  }

  const contextMaps = buildAccountContextMaps(db, [row]);
  const { keys } = buildRowResolution(row, contextMaps);
  const fallbackKey = keys.defaultScope === 'institution'
    ? (keys.scopedKey || keys.txnKey)
    : (keys.globalDomainKey || keys.globalSlugKey || keys.txnKey);
  if (!fallbackKey) return { found: true, merchant_logo: null };

  return {
    found: true,
    merchant_logo: {
      merchant_key: fallbackKey,
      merchant_name: keys.merchantName,
      provider: LOGO_PROVIDER,
      provider_query: keys.merchantName,
      url: null,
      status: 'candidate',
      scope: keys.defaultScope
    }
  };
}

export async function searchMerchantLogoBrands(db, { transactionId, query, householdId = 1 }) {
  const ensured = ensureMerchantLogoEntryForTransaction(db, transactionId, householdId);
  if (!ensured.found) return { found: false };

  const cleanedQuery = cleanMerchantName(query).slice(0, 80);
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

function resolveOverrideTarget(db, { transactionId, scope, merchantKey, merchantName, householdId }) {
  if (transactionId && Number(transactionId) > 0) {
    const row = db
      .prepare(`SELECT ${TRANSACTION_LOGO_SELECT} FROM transactions WHERE id = ? AND household_id = ?`)
      .get(transactionId, householdId);
    if (!row) return { notFound: true };

    const contextMaps = buildAccountContextMaps(db, [row]);
    const { keys } = buildRowResolution(row, contextMaps);
    const resolvedScope = VALID_LOGO_SCOPES.has(scope) ? scope : keys.defaultScope;
    const displayName = merchantName || keys.merchantName || `Transaction ${transactionId}`;

    let targetKey = null;
    if (resolvedScope === 'transaction') {
      targetKey = keys.txnKey;
    } else if (resolvedScope === 'institution') {
      targetKey = keys.scopedKey || keys.txnKey;
    } else {
      targetKey = keys.isGeneric
        ? (keys.explicitGlobalGenericKey || keys.txnKey)
        : (keys.globalSlugKey || keys.globalDomainKey || keys.txnKey);
    }

    if (!targetKey) return { notFound: true };
    return {
      notFound: false,
      targetKey,
      displayName,
      resolvedScope,
      keys
    };
  }

  if (!merchantKey) return { notFound: true };
  return {
    notFound: false,
    targetKey: merchantKey,
    displayName: merchantName || null,
    resolvedScope: VALID_LOGO_SCOPES.has(scope) ? scope : null,
    keys: null
  };
}

export function overrideMerchantLogo(
  db,
  {
    merchantKey = null,
    transactionId = null,
    scope = null,
    merchantName = null,
    logoUrl = null,
    hide = false,
    householdId = 1
  }
) {
  const status = hide ? 'hidden' : 'manual';
  const nextUrl = hide ? null : logoUrl;
  if (status === 'manual' && !nextUrl) return { updated: false };

  const target = resolveOverrideTarget(db, { transactionId, scope, merchantKey, merchantName, householdId });
  if (target.notFound || !target.targetKey) return { updated: false };

  const { targetKey, displayName, resolvedScope, keys } = target;
  let existing = db
    .prepare('SELECT merchant_key, merchant_name, provider, provider_query FROM merchant_logo_cache WHERE merchant_key = ?')
    .get(targetKey);

  const canAutoUpsert = Boolean(
    transactionId ||
    displayName ||
    targetKey.startsWith('inst:') ||
    targetKey.startsWith('txn:') ||
    targetKey.startsWith('global:') ||
    targetKey.includes('@inst:')
  );

  if (!existing && !canAutoUpsert) {
    return { updated: false };
  }

  const resolvedName = String(displayName || existing?.merchant_name || targetKey.replace(/^(inst:|txn:|global:)/, '')).trim() || targetKey;
  const providerQuery = existing?.provider_query || resolvedName;

  const applyTx = db.transaction(() => {
    if (keys) {
      if (resolvedScope === 'institution' && keys.txnKey && keys.txnKey !== targetKey) {
        db.prepare('DELETE FROM merchant_logo_cache WHERE merchant_key = ?').run(keys.txnKey);
      } else if (resolvedScope === 'global') {
        for (const narrower of [keys.txnKey, keys.scopedKey]) {
          if (narrower && narrower !== targetKey) {
            db.prepare('DELETE FROM merchant_logo_cache WHERE merchant_key = ?').run(narrower);
          }
        }
      }
    }

    db.prepare(`
      INSERT INTO merchant_logo_cache
        (merchant_key, merchant_name, provider, provider_query, logo_url, status, failure_count, last_checked_at)
      VALUES (?, ?, ?, ?, ?, ?, 0, datetime('now'))
      ON CONFLICT(merchant_key) DO UPDATE SET
        merchant_name = excluded.merchant_name,
        logo_url = excluded.logo_url,
        status = excluded.status,
        failure_count = 0,
        last_checked_at = datetime('now'),
        updated_at = datetime('now')
    `).run(targetKey, resolvedName, LOGO_PROVIDER, providerQuery, nextUrl, status);
  });

  applyTx();

  existing = db
    .prepare('SELECT merchant_key, merchant_name, provider, provider_query FROM merchant_logo_cache WHERE merchant_key = ?')
    .get(targetKey);
  if (!existing) return { updated: false };

  return {
    updated: true,
    merchant_logo: {
      merchant_key: existing.merchant_key,
      merchant_name: existing.merchant_name,
      provider: existing.provider,
      provider_query: existing.provider_query,
      url: status === 'manual' ? nextUrl : null,
      status,
      ...(resolvedScope ? { scope: resolvedScope } : {})
    }
  };
}

export function reportMerchantLogoStatus(db, { merchantKey, status }) {
  if (!merchantKey || !['loaded', 'failed'].includes(status)) {
    return { updated: false };
  }

  let targetKey = merchantKey;
  let existing = db
    .prepare('SELECT merchant_key, status FROM merchant_logo_cache WHERE merchant_key = ?')
    .get(targetKey);

  if (!existing && merchantKey.includes('@inst:')) {
    const instSuffix = merchantKey.slice(merchantKey.indexOf('@inst:') + 1);
    if (instSuffix.startsWith('inst:')) {
      targetKey = instSuffix;
      existing = db
        .prepare('SELECT merchant_key, status FROM merchant_logo_cache WHERE merchant_key = ?')
        .get(targetKey);
    }
  }

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
    .run(status, status, status, targetKey);

  return { updated: result.changes > 0 };
}
