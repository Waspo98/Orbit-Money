import express from 'express';
import { config } from '../config.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendOk,
  sendServerError,
  sendUnauthorized
} from '../lib/http.js';
import { buildAuthorizationUrl, completeOidcLogin } from '../services/oidc.js';
import { createHouseholdForUser } from '../services/householdDefaults.js';
import { seedDemoDataForHousehold } from '../services/demoSeed.js';

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
      role: req.session.householdRole || 'owner'
    }
  };
}

router.get('/config', (req, res) => {
  sendOk(res, {
    authProvider: authProvider(),
    localEnabled: localEnabled(),
    oidcEnabled: oidcEnabled(),
    oidcLoginUrl: oidcEnabled() ? '/api/auth/oidc/login' : null,
    oidcLoginLabel: config.oidcLoginLabel
  });
});

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

  if (username === config.adminUsername && password === config.adminPassword) {
    const user = db.prepare('SELECT * FROM users WHERE id = 1').get();
    const membership = db
      .prepare(
        `SELECT hm.household_id, hm.role, h.name
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
      householdName: membership?.name || 'Neal Household'
    });
    return sendOk(res, { success: true, ...currentSessionPayload(req) });
  }

  return sendUnauthorized(res, 'Invalid credentials');
});

router.post('/sample', (req, res) => {
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
          `SELECT hm.household_id, hm.role, h.name
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
          .prepare('SELECT id AS household_id, name, ? AS role FROM households WHERE id = ?')
          .get('owner', householdId);
      }

      seedDemoDataForHousehold(db, membership.household_id);
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
      householdName: membership.name || 'Sample Data'
    });
    return sendOk(res, { success: true, sample: true, ...currentSessionPayload(req) });
  } catch (err) {
    console.error('Sample data login failed:', err);
    return sendServerError(res, err);
  }
});

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
    setSessionIdentity(req, identity);
    res.redirect('/');
  } catch (err) {
    console.error('OIDC callback failed:', err);
    res.status(401).send('OIDC login failed. Return to Orbit Money and try again.');
  }
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
        `SELECT role
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
