import { config } from './config.js';

/**
 * Auth middleware — allows the request if either:
 *   1. X-API-Key header matches config.apiKey (programmatic access), OR
 *   2. req.session.authenticated is true (logged in via browser)
 * Otherwise responds 401.
 */
export function requireAuth(req, res, next) {
  const apiKey = req.header('X-API-Key');
  if (apiKey && config.apiKey && apiKey === config.apiKey) {
    return next();
  }

  if (req.session && req.session.authenticated) {
    return next();
  }

  return res.status(401).json({ error: 'Unauthorized' });
}
