import express from 'express';
import { config } from '../config.js';
import {
  sendBadRequest,
  sendOk,
  sendServerError,
  sendUnauthorized
} from '../lib/http.js';

const router = express.Router();

/**
 * POST /api/auth/login
 * Body: { username, password }
 * Sets req.session.authenticated on success.
 */
router.post('/login', (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return sendBadRequest(res, 'Username and password required');
  }

  if (username === config.adminUsername && password === config.adminPassword) {
    req.session.authenticated = true;
    req.session.username = username;
    return sendOk(res, { success: true, username });
  }

  // Same error for bad user or bad password — don't leak which one was wrong.
  return sendUnauthorized(res, 'Invalid credentials');
});

/**
 * POST /api/auth/logout
 * Destroys the session.
 */
router.post('/logout', (req, res) => {
  req.session.destroy((err) => {
    if (err) {
      return sendServerError(res, new Error('Logout failed'));
    }
    res.clearCookie('connect.sid');
    sendOk(res, { success: true });
  });
});

/**
 * GET /api/auth/me
 * Returns auth status. Useful for the frontend to check session on load.
 */
router.get('/me', (req, res) => {
  if (req.session && req.session.authenticated) {
    return sendOk(res, { authenticated: true, username: req.session.username });
  }
  sendOk(res, { authenticated: false });
});

export default router;
