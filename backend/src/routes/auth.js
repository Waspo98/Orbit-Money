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
