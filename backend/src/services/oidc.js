import crypto from 'crypto';
import { config } from '../config.js';
import { db } from '../db/index.js';
import { createHouseholdForUser } from './householdDefaults.js';

let discoveryCache = null;
let jwksCache = null;

function base64Url(input) {
  return Buffer.from(input)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function decodeBase64Url(input) {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(input.length / 4) * 4, '=');
  return Buffer.from(padded, 'base64');
}

function randomUrlSafe(bytes = 32) {
  return base64Url(crypto.randomBytes(bytes));
}

function sha256UrlSafe(value) {
  return base64Url(crypto.createHash('sha256').update(value).digest());
}

async function fetchJson(url, options = {}) {
  const res = await fetch(url, options);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error_description || data.error || `OIDC request failed: ${res.status}`);
  }
  return data;
}

export async function getDiscovery() {
  if (discoveryCache) return discoveryCache;
  const issuer = config.oidcIssuerUrl.replace(/\/+$/, '');
  discoveryCache = await fetchJson(`${issuer}/.well-known/openid-configuration`);
  return discoveryCache;
}

async function getJwks() {
  if (jwksCache) return jwksCache;
  const discovery = await getDiscovery();
  jwksCache = await fetchJson(discovery.jwks_uri);
  return jwksCache;
}

export async function buildAuthorizationUrl(req) {
  const discovery = await getDiscovery();
  const state = randomUrlSafe();
  const nonce = randomUrlSafe();
  const codeVerifier = randomUrlSafe(64);

  req.session.oidcState = state;
  req.session.oidcNonce = nonce;
  req.session.oidcCodeVerifier = codeVerifier;

  const params = new URLSearchParams({
    client_id: config.oidcClientId,
    redirect_uri: config.oidcRedirectUri,
    response_type: 'code',
    scope: config.oidcScopes,
    state,
    nonce,
    code_challenge: sha256UrlSafe(codeVerifier),
    code_challenge_method: 'S256'
  });

  return `${discovery.authorization_endpoint}?${params.toString()}`;
}

async function exchangeCodeForTokens(code, codeVerifier) {
  const discovery = await getDiscovery();
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: config.oidcRedirectUri,
    client_id: config.oidcClientId,
    client_secret: config.oidcClientSecret,
    code_verifier: codeVerifier
  });

  return fetchJson(discovery.token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });
}

function decodeJwt(token) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Invalid ID token.');
  return {
    header: JSON.parse(decodeBase64Url(parts[0]).toString('utf8')),
    payload: JSON.parse(decodeBase64Url(parts[1]).toString('utf8')),
    signingInput: `${parts[0]}.${parts[1]}`,
    signature: decodeBase64Url(parts[2])
  };
}

async function verifyIdToken(idToken, expectedNonce) {
  const decoded = decodeJwt(idToken);
  if (decoded.header.alg !== 'RS256') {
    throw new Error(`Unsupported ID token algorithm: ${decoded.header.alg || 'unknown'}`);
  }

  const jwks = await getJwks();
  const jwk = jwks.keys?.find((key) => key.kid === decoded.header.kid);
  if (!jwk) throw new Error('Could not find a matching OIDC signing key.');

  const key = crypto.createPublicKey({ key: jwk, format: 'jwk' });
  const verifier = crypto.createVerify('RSA-SHA256');
  verifier.update(decoded.signingInput);
  verifier.end();
  if (!verifier.verify(key, decoded.signature)) {
    throw new Error('ID token signature is invalid.');
  }

  const now = Math.floor(Date.now() / 1000);
  const issuer = config.oidcIssuerUrl.replace(/\/+$/, '');
  if (decoded.payload.iss?.replace(/\/+$/, '') !== issuer) throw new Error('ID token issuer mismatch.');
  const audiences = Array.isArray(decoded.payload.aud) ? decoded.payload.aud : [decoded.payload.aud];
  if (!audiences.includes(config.oidcClientId)) {
    throw new Error('ID token audience mismatch.');
  }
  if (Number(decoded.payload.exp) <= now) throw new Error('ID token is expired.');
  if (decoded.payload.nonce !== expectedNonce) throw new Error('ID token nonce mismatch.');
  if (!decoded.payload.sub) throw new Error('ID token is missing subject.');

  return decoded.payload;
}

function profileName(profile) {
  return profile.name || profile.preferred_username || profile.email || 'OIDC User';
}

function applyPendingHouseholdShares(userId, email) {
  if (!email) return null;
  const shares = db
    .prepare(
      `SELECT id, household_id, role
         FROM household_shares
        WHERE lower(invited_email) = lower(?)
          AND revoked_at IS NULL`
    )
    .all(email);

  const insertMembership = db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role)
     VALUES (?, ?, ?)
     ON CONFLICT(household_id, user_id) DO UPDATE SET
       role = CASE
         WHEN role = 'owner' THEN role
         WHEN excluded.role = 'admin' THEN 'admin'
         ELSE role
       END,
       updated_at = datetime('now')`
  );
  const markAccepted = db.prepare(
    `UPDATE household_shares
        SET accepted_by_user_id = ?,
            accepted_at = COALESCE(accepted_at, datetime('now')),
            updated_at = datetime('now')
      WHERE id = ?`
  );

  let preferredHouseholdId = null;
  for (const share of shares) {
    insertMembership.run(share.household_id, userId, share.role || 'member');
    markAccepted.run(userId, share.id);
    preferredHouseholdId = preferredHouseholdId || share.household_id;
  }
  return preferredHouseholdId;
}

function resolveUserAndHousehold(profile) {
  const existing = db
    .prepare('SELECT * FROM users WHERE oidc_sub = ?')
    .get(profile.sub);
  if (existing) {
    db.prepare(
      `UPDATE users
          SET email = ?, display_name = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).run(profile.email || null, profileName(profile), existing.id);
    return existing.id;
  }

  return db
    .prepare(
      `INSERT INTO users (oidc_sub, email, display_name)
       VALUES (?, ?, ?)`
    )
    .run(profile.sub, profile.email || null, profileName(profile)).lastInsertRowid;
}

function defaultMembershipForUser(userId, displayName, preferredHouseholdId = null) {
  if (preferredHouseholdId) {
    const preferred = db
      .prepare(
        `SELECT hm.household_id, hm.role, h.name
           FROM household_memberships hm
           JOIN households h ON h.id = hm.household_id
          WHERE hm.user_id = ?
            AND hm.household_id = ?
          LIMIT 1`
      )
      .get(userId, preferredHouseholdId);
    if (preferred) return preferred;
  }

  const membership = db
    .prepare(
      `SELECT hm.household_id, hm.role, h.name
         FROM household_memberships hm
         JOIN households h ON h.id = hm.household_id
        WHERE hm.user_id = ?
        ORDER BY CASE hm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, hm.id ASC
        LIMIT 1`
    )
    .get(userId);

  if (membership) return membership;
  const householdId = createHouseholdForUser(db, userId, displayName);
  return db
    .prepare('SELECT id AS household_id, name, ? AS role FROM households WHERE id = ?')
    .get('owner', householdId);
}

export async function completeOidcLogin(req, code, state) {
  if (!state || state !== req.session.oidcState) {
    throw new Error('OIDC state mismatch.');
  }
  if (!code) throw new Error('OIDC callback did not include a code.');

  const tokens = await exchangeCodeForTokens(code, req.session.oidcCodeVerifier);
  const profile = await verifyIdToken(tokens.id_token, req.session.oidcNonce);
  const displayName = profileName(profile);
  const userId = resolveUserAndHousehold(profile);
  const preferredHouseholdId = applyPendingHouseholdShares(userId, profile.email || null);
  const membership = defaultMembershipForUser(userId, displayName, preferredHouseholdId);

  delete req.session.oidcState;
  delete req.session.oidcNonce;
  delete req.session.oidcCodeVerifier;

  return {
    userId,
    householdId: membership.household_id,
    role: membership.role,
    username: profile.preferred_username || profile.email || profile.sub,
    email: profile.email || null,
    displayName,
    householdName: membership.name
  };
}
