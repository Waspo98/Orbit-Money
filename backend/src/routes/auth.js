import express from 'express';
import { config } from '../config.js';

const router = express.Router();

/**
 * POST /api/auth/login
 * Body: { username, password }
 * Sets req.session.authenticated on success.
 */
router.post('/login', (req, res) => {
  const { username, password } = req.body || {};

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password required' });
  }

  if (username === config.adminUsername && password === config.adminPassword) {
    req.session.authenticated = true;
    req.session.username = username;
    return res.json({ success: true, username });
  }

  // Same error for bad user or bad password — don't leak which one was wrong.
  return res.status(401).json({ error: 'Invalid credentials' });
});

/**
 * POST /api/auth/logout
 * Destroys the session.
 */
router.post('/logout', (req, res) => {
  req.session.destroy((err) => {
    if (err) {
      return res.status(500).json({ error: 'Logout failed' });
    }
    res.clearCookie('connect.sid');
    res.json({ success: true });
  });
});

/**
 * GET /api/auth/me
 * Returns auth status. Useful for the frontend to check session on load.
 */
router.get('/me', (req, res) => {
  if (req.session && req.session.authenticated) {
    return res.json({ authenticated: true, username: req.session.username });
  }
  res.json({ authenticated: false });
});

export default router;
