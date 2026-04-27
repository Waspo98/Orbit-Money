import { config } from './config.js';

function parseHouseholdHeader(req) {
  const raw = req.header('X-Household-ID');
  const id = raw ? Number.parseInt(raw, 10) : 1;
  return Number.isInteger(id) && id > 0 ? id : 1;
}

/**
 * Auth middleware. API-key requests default to household 1 unless they provide
 * X-Household-ID. Browser requests use the household stored in the session.
 */
export function requireAuth(req, res, next) {
  const apiKey = req.header('X-API-Key');
  if (apiKey && config.apiKey && apiKey === config.apiKey) {
    req.user = { id: null, username: 'api', display_name: 'API' };
    req.household = { id: parseHouseholdHeader(req), role: 'owner' };
    return next();
  }

  if (req.session && req.session.authenticated) {
    req.user = {
      id: req.session.userId || 1,
      username: req.session.username || null,
      email: req.session.email || null,
      display_name: req.session.displayName || req.session.username || null
    };
    req.household = {
      id: req.session.householdId || 1,
      role: req.session.householdRole || 'owner'
    };
    return next();
  }

  return res.status(401).json({ error: 'Unauthorized' });
}

export function requireHouseholdId(req) {
  const id = Number(req.household?.id);
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Authenticated request is missing household context.');
  }
  return id;
}
