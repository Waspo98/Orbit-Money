import { config } from './config.js';
import { db } from './db/index.js';
import { parseId } from './lib/routeParams.js';
import { touchSampleUser } from './services/sampleHouseholds.js';

function parseHouseholdHeader(req) {
  return parseId(req.header('X-Household-ID')) || 1;
}

/**
 * Auth middleware. API-key requests default to household 1 unless they provide
 * X-Household-ID. Browser requests use the household stored in the session.
 */
export function requireAuth(req, res, next) {
  const apiKey = req.header('X-API-Key');
  if (apiKey && config.apiKey && apiKey === config.apiKey) {
    req.user = { id: null, username: 'api', display_name: 'API' };
    req.household = { id: parseHouseholdHeader(req), role: 'owner', accessLevel: 'write' };
    return next();
  }

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
      return res.status(401).json({ error: 'Unauthorized' });
    }

    if (String(req.session.username || '').startsWith('sample:')) {
      touchSampleUser(db, req.session.userId || 1);
    }
    req.user = {
      id: req.session.userId || 1,
      username: req.session.username || null,
      email: req.session.email || null,
      display_name: req.session.displayName || req.session.username || null
    };
    req.household = {
      id: householdId,
      role: membership.role || req.session.householdRole || 'member',
      accessLevel: membership.role === 'owner'
        ? 'write'
        : membership.access_level || req.session.householdAccessLevel || 'write'
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

export function canWriteHousehold(req) {
  if (req.household?.role === 'owner') return true;
  return req.household?.accessLevel !== 'read';
}

export function requireHouseholdWrite(req, res, next) {
  if (canWriteHousehold(req)) return next();
  return res.status(403).json({
    error: 'This household is read-only for your account.'
  });
}

export function requireWriteForUnsafeMethods(req, res, next) {
  if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) return next();
  return requireHouseholdWrite(req, res, next);
}
