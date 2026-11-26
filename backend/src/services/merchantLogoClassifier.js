const PREFIXED_DOMAIN_RE = /\b(?:https?:\/\/(?:www\.)?|www\.)([a-z0-9][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)*\.[a-z]{2,})\b/i;
const BARE_DOMAIN_RE = /\b([a-z0-9][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)*\.(?:com|org|net|io|co|us|edu|gov|bank|ai|app|dev|me|tv|biz|info|ca|uk|de|eu|cc|gg|so|fm|ly|to|shop|store|online|xyz|money|finance|financial|coop))\b/i;
const PAYMENT_PREFIX_RE = /^(sq|tst|sp|paypal|pp|stripe|sumup|toast|clover)[\s*.-]+/i;
const SAFE_SHORT_NAMES = new Set(['aldi', 'amex', 'apple', 'at&t', 'aws', 'cvs', 'etsy', 'hulu', 'ikea', 'lyft', 'uber', 'ups', 'usps']);

const GENERIC_MERCHANTS = new Set([
  'unknown',
  'transfer',
  'payment',
  'online payment',
  'automatic payment',
  'deposit',
  'withdrawal',
  'check',
  'check paid',
  'atm',
  'atm withdrawal',
  'atm deposit',
  'cash withdrawal',
  'branch deposit',
  'mobile deposit',
  'check deposit',
  'remote deposit',
  'direct deposit',
  'ach deposit',
  'ach withdrawal',
  'ach transfer',
  'wire transfer',
  'electronic transfer',
  'online transfer',
  'internal transfer',
  'external transfer',
  'account transfer',
  'balance transfer',
  'autopay',
  'bill pay',
  'minimum payment',
  'credit card payment',
  'card payment',
  'loan payment',
  'mortgage payment',
  'principal payment',
  'escrow',
  'escrow payment',
  'interest',
  'interest payment',
  'interest credit',
  'interest earned',
  'interest income',
  'interest paid',
  'interest charge',
  'finance charge',
  'dividend',
  'dividends',
  'dividend payment',
  'dividend reinvestment',
  'capital gain',
  'capital gains',
  'fee',
  'annual fee',
  'monthly fee',
  'maintenance fee',
  'service fee',
  'overdraft fee',
  'nsf fee',
  'late fee',
  'wire fee',
  'atm fee',
  'foreign transaction fee',
  'account fee',
  'membership fee',
  'cash back',
  'cashback',
  'reward',
  'rewards',
  'reward credit',
  'statement credit',
  'credit',
  'refund',
  'fee refund',
  'fee waiver',
  'reimbursement',
  'adjustment',
  'balance adjustment',
  'opening balance',
  'starting balance'
]);

const GENERIC_EVENT_PATTERNS = [
  /^(savings|checking|hysa|apy|monthly|quarterly|bank|account)\s+interest(\s+(payment|credit|earned|income|paid|charge))?$/i,
  /^(interest|dividend)\s+(payments?|credits?|earned|income|paid|reinvestment|charges?)$/i,
  /^(?:(?:bank|account|checking|savings|card|credit\s+card|monthly|quarterly|annual|minimum\s+balance)\s+)*(?:annual|monthly|maintenance|service|overdraft|nsf|wire|atm|late|foreign\s+transaction|returned\s+item|account|membership)\s+fees?$/i,
  /^(atm|cash|check|mobile|remote|branch|ach|wire|direct)\s+(deposits?|withdrawals?|transfers?|payments?)$/i
];

const COUNTERPARTY_TRANSFER_MERCHANTS = new Set([
  'transfer',
  'online transfer',
  'internal transfer',
  'external transfer',
  'account transfer',
  'balance transfer',
  'ach transfer',
  'wire transfer',
  'electronic transfer',
  'payment',
  'online payment',
  'automatic payment',
  'autopay',
  'bill pay',
  'minimum payment',
  'credit card payment',
  'card payment',
  'loan payment',
  'mortgage payment',
  'principal payment',
  'deposit',
  'direct deposit',
  'ach deposit',
  'ach withdrawal',
  'withdrawal',
  'check',
  'check paid'
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

export const VALID_LOGO_SCOPES = new Set(['global', 'institution', 'transaction']);

export function cleanMerchantName(value) {
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

export function extractDomain(value) {
  const lower = String(value || '').toLowerCase();
  const match = PREFIXED_DOMAIN_RE.exec(lower) || BARE_DOMAIN_RE.exec(lower);
  if (!match) return null;
  const domain = match[1].replace(/^www\./, '');
  if (domain.endsWith('.local') || domain.endsWith('.invalid')) return null;
  return domain;
}

function normalizeGenericPhrase(value) {
  return cleanMerchantName(value)
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

export function isInstitutionScopedMerchant(value) {
  const normalized = normalizeGenericPhrase(value);
  if (!normalized) return false;
  if (GENERIC_MERCHANTS.has(normalized)) return true;
  return GENERIC_EVENT_PATTERNS.some((pattern) => pattern.test(normalized));
}

export function isCounterpartyTransferMerchant(value) {
  const normalized = normalizeGenericPhrase(value);
  if (!normalized) return false;
  if (COUNTERPARTY_TRANSFER_MERCHANTS.has(normalized)) return true;
  return /^(ach|wire|direct|online|external)\s+(deposits?|withdrawals?|transfers?|payments?)$/i.test(normalized);
}

export function isStrictNameCandidate(query, rawText) {
  const lowerQuery = query.toLowerCase();
  const lowerRaw = String(rawText || '').toLowerCase();
  if (isInstitutionScopedMerchant(lowerQuery)) return false;
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

export function isCuratedMerchantName(row, query) {
  if (row?.edited_merchant_source) return true;

  const original = cleanMerchantName(row?.original_merchant || row?.merchant);
  const displayed = cleanMerchantName(row?.merchant);
  return (
    merchantLogoKey(original) === merchantLogoKey(query) &&
    merchantLogoKey(displayed) === merchantLogoKey(query) &&
    String(row?.original_merchant || row?.merchant || '').trim() === query
  );
}

export function resolveTransactionScopeKeys({
  row,
  accountInstKey = null,
  transferPairInstKey = null,
  counterpartyInstKey = null
}) {
  const effectiveDisplay = cleanMerchantName(row?.merchant || row?.original_merchant);
  const slug = merchantLogoKey(effectiveDisplay);
  const isGeneric = isInstitutionScopedMerchant(effectiveDisplay);
  const supportsCounterparty = isGeneric && isCounterpartyTransferMerchant(effectiveDisplay);

  const resolvedInstKey = supportsCounterparty
    ? (transferPairInstKey || counterpartyInstKey || accountInstKey || null)
    : (accountInstKey || null);

  const txnKey = row?.id ? `txn:${row.id}` : null;
  const scopedKey = slug && resolvedInstKey ? `${slug}@${resolvedInstKey}` : null;
  const explicitGlobalGenericKey = isGeneric && slug ? `global:${slug}` : null;

  let domain = null;
  if (!isGeneric) {
    domain = extractDomain(row?.merchant);
    if (!domain && !row?.edited_merchant_source) {
      const rawText = [row?.merchant, row?.original_merchant, row?.original_description]
        .filter(Boolean)
        .join(' ');
      domain = extractDomain(rawText);
    }
  }

  const globalDomainKey = !isGeneric && domain ? `domain-${domain}` : null;
  const globalSlugKey = !isGeneric && slug ? slug : null;

  const autoCandidateQuery = !isGeneric
    ? (domain || (effectiveDisplay && isStrictNameCandidate(effectiveDisplay, [row?.merchant, row?.original_merchant, row?.original_description].filter(Boolean).join(' ')) && isCuratedMerchantName(row, effectiveDisplay)
      ? effectiveDisplay
      : null))
    : null;
  const autoCandidateKey = !isGeneric && autoCandidateQuery
    ? (domain ? `domain-${domain}` : slug)
    : null;

  return {
    merchantName: effectiveDisplay || row?.merchant || row?.original_merchant || '',
    slug,
    isGeneric,
    defaultScope: isGeneric && resolvedInstKey ? 'institution' : 'global',
    txnKey,
    scopedKey,
    globalSlugKey,
    globalDomainKey,
    explicitGlobalGenericKey,
    fallbackInstKey: isGeneric ? resolvedInstKey : null,
    domain,
    autoCandidateQuery,
    autoCandidateLookup: domain ? 'domain' : 'name',
    autoCandidateKey
  };
}
