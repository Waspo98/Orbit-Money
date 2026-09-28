import crypto from 'crypto';
import express from 'express';
import { config } from '../config.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError,
  sendUnauthorized
} from '../lib/http.js';
import { buildAuthorizationUrl, completeOidcLogin } from '../services/oidc.js';
import { createHouseholdForUser } from '../services/householdDefaults.js';
import { seedDemoDataForHousehold } from '../services/demoSeed.js';
import { touchSampleUser } from '../services/sampleHouseholds.js';

const router = express.Router();

function authProvider() {
  return config.authProvider;
}

function oidcEnabled() {
  return config.authProvider === 'oidc' || config.authProvider === 'both';
}

function localEnabled() {
  return config.authProvider === 'local' || config.authProvider === 'both';
}

function setSessionIdentity(req, identity) {
  req.session.authenticated = true;
  req.session.userId = identity.userId;
  req.session.username = identity.username;
  req.session.email = identity.email || null;
  req.session.displayName = identity.displayName || identity.username;
  req.session.householdId = identity.householdId;
  req.session.householdRole = identity.role || 'owner';
  req.session.householdAccessLevel = identity.role === 'owner'
    ? 'write'
    : identity.accessLevel || 'write';
  req.session.householdName = identity.householdName || null;
}

function currentSessionPayload(req) {
  return {
    authenticated: true,
    username: req.session.username,
    email: req.session.email || null,
    displayName: req.session.displayName || req.session.username,
    authProvider: authProvider(),
    household: {
      id: req.session.householdId || 1,
      name: req.session.householdName || null,
      role: req.session.householdRole || 'owner',
      accessLevel: req.session.householdRole === 'owner'
        ? 'write'
        : req.session.householdAccessLevel || 'write'
    }
  };
}

router.get('/config', (req, res) => {
  sendOk(res, {
    authProvider: authProvider(),
    localEnabled: localEnabled(),
    oidcEnabled: oidcEnabled(),
    oidcLoginUrl: oidcEnabled() ? '/api/auth/oidc/login' : null,
    oidcLoginLabel: config.oidcLoginLabel,
    sampleDataEnabled: config.sampleDataEnabled
  });
});

function timingSafeCompare(str1, str2) {
  if (typeof str1 !== 'string' || typeof str2 !== 'string') {
    return false;
  }
  const h1 = crypto.createHash('sha256').update(str1).digest();
  const h2 = crypto.createHash('sha256').update(str2).digest();
  return crypto.timingSafeEqual(h1, h2);
}

/**
 * POST /api/auth/login
 * Local login. OIDC-only deployments should use /api/auth/oidc/login.
 */
router.post('/login', (req, res) => {
  if (!localEnabled()) {
    return sendBadRequest(res, 'Password login is disabled for this deployment.');
  }

  const { username, password } = req.body || {};

  if (!username || !password) {
    return sendBadRequest(res, 'Username and password required');
  }

  const isUsernameMatch = timingSafeCompare(username, config.adminUsername);
  const isPasswordMatch = timingSafeCompare(password, config.adminPassword);

  if (isUsernameMatch && isPasswordMatch) {
    const user = db.prepare('SELECT * FROM users WHERE id = 1').get();
    const membership = db
      .prepare(
        `SELECT hm.household_id, hm.role, hm.access_level, h.name
           FROM household_memberships hm
           JOIN households h ON h.id = hm.household_id
          WHERE hm.user_id = 1
          ORDER BY hm.id ASC
          LIMIT 1`
      )
      .get();

    setSessionIdentity(req, {
      userId: 1,
      username,
      email: user?.email || null,
      displayName: user?.display_name || username,
      householdId: membership?.household_id || 1,
      role: membership?.role || 'owner',
      accessLevel: membership?.access_level || 'write',
      householdName: membership?.name || 'My Household'
    });
    return sendOk(res, { success: true, ...currentSessionPayload(req) });
  }

  return sendUnauthorized(res, 'Invalid credentials');
});

router.post('/sample', (req, res) => {
  if (!config.sampleDataEnabled) {
    return sendNotFound(res, 'Sample data is disabled for this deployment.');
  }

  const rawDeviceId = String(req.body?.deviceId || '').trim();
  if (!/^[a-zA-Z0-9_-]{16,80}$/.test(rawDeviceId)) {
    return sendBadRequest(res, 'Sample data device id is invalid.');
  }

  try {
    const username = `sample:${rawDeviceId}`;
    const displayName = 'Sample Data';
    const run = db.transaction(() => {
      let user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
      if (!user) {
        const result = db
          .prepare(
            `INSERT INTO users (username, display_name, is_local_admin)
             VALUES (?, ?, 0)`
          )
          .run(username, displayName);
        user = db.prepare('SELECT * FROM users WHERE id = ?').get(result.lastInsertRowid);
      }

      let membership = db
        .prepare(
          `SELECT hm.household_id, hm.role, hm.access_level, h.name
             FROM household_memberships hm
             JOIN households h ON h.id = hm.household_id
            WHERE hm.user_id = ?
            ORDER BY hm.id ASC
            LIMIT 1`
        )
        .get(user.id);

      if (!membership) {
        const householdId = createHouseholdForUser(db, user.id, 'Sample Data');
        db.prepare('UPDATE households SET name = ? WHERE id = ?').run('Sample Data', householdId);
        membership = db
          .prepare("SELECT id AS household_id, name, ? AS role, 'write' AS access_level FROM households WHERE id = ?")
          .get('owner', householdId);
      }

      seedDemoDataForHousehold(db, membership.household_id);
      touchSampleUser(db, user.id);
      return { user, membership };
    });

    const { user, membership } = run();
    setSessionIdentity(req, {
      userId: user.id,
      username,
      email: null,
      displayName,
      householdId: membership.household_id,
      role: membership.role || 'owner',
      accessLevel: membership.access_level || 'write',
      householdName: membership.name || 'Sample Data'
    });
    return sendOk(res, { success: true, sample: true, ...currentSessionPayload(req) });
  } catch (err) {
    console.error('Sample data login failed:', err);
    return sendServerError(res, err);
  }
});

const pendingNativeExchanges = new Map();

function cleanExpiredExchanges() {
  const now = Date.now();
  for (const [code, item] of pendingNativeExchanges.entries()) {
    if (item.expiresAt <= now) {
      pendingNativeExchanges.delete(code);
    }
  }
}

function createNativeExchange(code, identity) {
  cleanExpiredExchanges();
  pendingNativeExchanges.set(code, {
    identity,
    expiresAt: Date.now() + 60000
  });
}

function consumeNativeExchange(code) {
  cleanExpiredExchanges();
  const item = pendingNativeExchanges.get(code);
  if (!item) return null;
  pendingNativeExchanges.delete(code);
  if (item.expiresAt <= Date.now()) return null;
  return item;
}

router.get('/oidc/login', async (req, res) => {
  if (!oidcEnabled()) {
    return res.redirect('/');
  }

  try {
    const url = await buildAuthorizationUrl(req);
    res.redirect(url);
  } catch (err) {
    console.error('OIDC login start failed:', err);
    sendServerError(res, err);
  }
});

router.get('/oidc/callback', async (req, res) => {
  if (!oidcEnabled()) {
    return res.redirect('/');
  }

  try {
    const identity = await completeOidcLogin(req, req.query.code, req.query.state);

    if (identity.isAppReturn) {
      const exchangeCode = crypto.randomBytes(32).toString('hex');
      createNativeExchange(exchangeCode, identity);
      const appScheme = identity.appScheme || 'orbitmoney';
      const appCallbackUrl = `${appScheme}://auth/callback?code=${encodeURIComponent(exchangeCode)}`;

      return res.send(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Orbit Money - Authenticated</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #fafaf7; color: #171717; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 24px; box-sizing: border-box; text-align: center; }
    .card { background: #ffffff; border: 1px solid #e5e5e5; border-radius: 16px; padding: 32px 24px; max-width: 400px; width: 100%; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
    h2 { margin: 0 0 12px; font-size: 1.25rem; font-weight: 600; color: #047857; }
    p { margin: 0 0 24px; font-size: 0.95rem; color: #666666; line-height: 1.4; }
    .btn { display: inline-block; background: #047857; color: #ffffff; padding: 12px 24px; border-radius: 9999px; text-decoration: none; font-weight: 500; font-size: 0.95rem; }
  </style>
  <script>
    window.location.href = ${JSON.stringify(appCallbackUrl)};
  </script>
</head>
<body>
  <div class="card">
    <h2>Login Successful</h2>
    <p>Returning you to Orbit Money...</p>
    <a href="${appCallbackUrl}" class="btn">Open Orbit Money</a>
  </div>
</body>
</html>`);
    }

    setSessionIdentity(req, identity);
    res.redirect('/');
  } catch (err) {
    console.error('OIDC callback failed:', err);
    res.status(401).send('OIDC login failed. Return to Orbit Money and try again.');
  }
});

router.post('/oidc/native-exchange', (req, res) => {
  const { code } = req.body || {};
  if (!code || typeof code !== 'string') {
    return sendBadRequest(res, 'Exchange code required');
  }

  const exchange = consumeNativeExchange(code);
  if (!exchange) {
    return sendUnauthorized(res, 'Invalid or expired exchange code');
  }

  setSessionIdentity(req, exchange.identity);
  return sendOk(res, { success: true, ...currentSessionPayload(req) });
});

router.post('/logout', (req, res) => {
  req.session.destroy((err) => {
    if (err) {
      return sendServerError(res, new Error('Logout failed'));
    }
    res.clearCookie(config.sessionName);
    sendOk(res, { success: true });
  });
});

router.get('/me', (req, res) => {
  if (req.session && req.session.authenticated) {
    const householdId = req.session.householdId || 1;
    const membership = db
      .prepare(
        `SELECT role, access_level
           FROM household_memberships
          WHERE user_id = ?
            AND household_id = ?`
      )
      .get(req.session.userId || 1, householdId);
    if (!membership) {
      req.session.authenticated = false;
      return sendOk(res, {
        authenticated: false,
        authProvider: authProvider(),
        localEnabled: localEnabled(),
        oidcEnabled: oidcEnabled()
      });
    }
    req.session.householdRole = membership.role || req.session.householdRole || 'member';
    req.session.householdAccessLevel = membership.role === 'owner'
      ? 'write'
      : membership.access_level || req.session.householdAccessLevel || 'write';
    if (String(req.session.username || '').startsWith('sample:')) {
      touchSampleUser(db, req.session.userId || 1);
    }
    return sendOk(res, currentSessionPayload(req));
  }
  sendOk(res, {
    authenticated: false,
    authProvider: authProvider(),
    localEnabled: localEnabled(),
    oidcEnabled: oidcEnabled()
  });
});

export default router;
