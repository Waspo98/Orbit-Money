// =============================================================================
// simplefinClient.js — thin HTTP client for SimpleFIN Bridge
// =============================================================================
// Two entry points:
//   - claimAccessUrl(setupToken) : exchanges a one-shot setup token for the
//                                  persistent access URL
//   - fetchAccounts(accessUrl, { startDate, endDate }) : pulls accounts and
//                                  transactions from the bridge
//
// SimpleFIN Bridge docs: https://www.simplefin.org/bridge.html
//
// IMPORTANT: SimpleFIN access URLs embed credentials as `https://user:pass@host`.
// Node's fetch (undici) rejects these per WHATWG spec, throwing:
//   "Request cannot be constructed from a URL that includes credentials"
// We split the credentials off the URL and send them as a Basic Auth header.
// =============================================================================

/**
 * Split an `https://user:pass@host/path` URL into a clean URL and a Basic
 * Authorization header value. If there are no embedded credentials, returns
 * { url: input, authHeader: null }.
 */
function splitCredentialsFromUrl(rawUrl) {
  const u = new URL(rawUrl);
  if (!u.username && !u.password) {
    return { url: rawUrl, authHeader: null };
  }

  // URL decodes username/password automatically (handles %-encoded chars).
  const user = decodeURIComponent(u.username);
  const pass = decodeURIComponent(u.password);
  const authHeader = 'Basic ' + Buffer.from(`${user}:${pass}`).toString('base64');

  // Rebuild without credentials.
  u.username = '';
  u.password = '';
  return { url: u.toString(), authHeader };
}

/**
 * Exchange a one-time setup token for a long-lived access URL.
 *
 * The setup token is base64-encoded. Decode it to get a claim URL, POST to
 * that URL with no body, and the response body is the access URL as plain text.
 *
 * NOTE: Setup tokens are single-use. If this call fails, the user needs a
 * fresh setup token from simplefin.org.
 */
export async function claimAccessUrl(setupToken) {
  const trimmed = (setupToken || '').trim();
  if (!trimmed) {
    throw new Error('Setup token is empty.');
  }

  let claimUrlRaw;
  try {
    claimUrlRaw = Buffer.from(trimmed, 'base64').toString('utf8');
  } catch {
    throw new Error('Setup token is not valid base64.');
  }

  if (!/^https:\/\//i.test(claimUrlRaw)) {
    throw new Error('Setup token did not decode to a valid https URL.');
  }

  // Setup/claim URLs typically don't have embedded credentials, but be safe.
  const { url, authHeader } = splitCredentialsFromUrl(claimUrlRaw);

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeader ? { Authorization: authHeader } : {}
  });
  if (!res.ok) {
    throw new Error(
      `SimpleFIN rejected the setup token (HTTP ${res.status}). ` +
        'If you just used this token, it may already be consumed — generate a new one.'
    );
  }

  const accessUrl = (await res.text()).trim();
  if (!/^https:\/\/.+@.+$/i.test(accessUrl)) {
    throw new Error(
      'SimpleFIN returned an unexpected response. The access URL should ' +
        'contain embedded credentials.'
    );
  }

  return accessUrl;
}

/**
 * Fetch accounts + transactions from the bridge.
 *
 * The access URL contains basic-auth credentials inline (https://user:pass@host).
 * We strip them off and send as an Authorization header (undici rejects inline
 * credentials).
 *
 * Params:
 *   - accessUrl: the persistent URL from claimAccessUrl()
 *   - startDate, endDate: Date objects. Converted to Unix seconds for the API.
 *
 * Returns the parsed JSON body with shape:
 *   { accounts: [{ id, name, currency, balance, balance-date, org: { name, domain }, transactions: [...] }],
 *     errors: [...] }
 *
 * We surface `errors` to the caller — SimpleFIN reports per-institution
 * errors there (e.g., "bank needs reauth") but still returns whatever data
 * it could fetch.
 */
export async function fetchAccounts(accessUrl, { startDate, endDate }) {
  const start = Math.floor(startDate.getTime() / 1000);
  const end = Math.floor(endDate.getTime() / 1000);

  const { url: baseUrlClean, authHeader } = splitCredentialsFromUrl(accessUrl);
  if (!authHeader) {
    throw new Error('Access URL is missing credentials — reconnect SimpleFIN.');
  }

  // Strip trailing slash to avoid //accounts.
  const base = baseUrlClean.replace(/\/$/, '');
  const url = `${base}/accounts?start-date=${start}&end-date=${end}&pending=1`;

  const res = await fetch(url, {
    headers: {
      Authorization: authHeader,
      Accept: 'application/json'
    }
  });

  if (!res.ok) {
    throw new Error(
      `SimpleFIN fetch failed (HTTP ${res.status}). ` +
        (res.status === 401
          ? 'Your access URL may be invalid — try reconnecting.'
          : 'Try again later.')
    );
  }

  const body = await res.json();
  if (!body || !Array.isArray(body.accounts)) {
    throw new Error('SimpleFIN returned an unexpected response shape.');
  }

  return body;
}
