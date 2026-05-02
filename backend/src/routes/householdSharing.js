import express from 'express';
import { canWriteHousehold, requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendRouteError
} from '../lib/http.js';
import { readIdParam } from '../lib/routeParams.js';
import {
  createPendingHouseholdShare,
  normalizeAccessLevel,
  normalizeEmail,
  sharingError
} from '../services/householdSharing.js';

const router = express.Router();

function canManageSharing(req) {
  return canWriteHousehold(req) && req.household?.role === 'owner';
}

function canRemoveUsers(req) {
  return canManageSharing(req);
}

function requireShareManager(req) {
  if (!canManageSharing(req)) {
    throw sharingError('Only household owners can manage sharing.', 403);
  }
}

function requireUserRemover(req) {
  if (!canRemoveUsers(req)) {
    throw sharingError('Only household owners can remove family members.', 403);
  }
}

function getHousehold(householdId) {
  return db.prepare('SELECT id, name FROM households WHERE id = ?').get(householdId);
}

function listUsers(householdId) {
  return db
    .prepare(
      `SELECT u.id, u.email, u.display_name, u.username, hm.role, hm.access_level, hm.created_at
         FROM household_memberships hm
         JOIN users u ON u.id = hm.user_id
        WHERE hm.household_id = ?
        ORDER BY CASE hm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END,
                 lower(COALESCE(u.display_name, u.email, u.username, '')) ASC`
    )
    .all(householdId);
}

function listShares(householdId) {
  return db
    .prepare(
      `SELECT hs.id, hs.invited_email, hs.role, hs.access_level, hs.accepted_at, hs.revoked_at,
              hs.created_at, hs.updated_at,
              creator.display_name AS created_by_name,
              accepted.display_name AS accepted_by_name
         FROM household_shares hs
         LEFT JOIN users creator ON creator.id = hs.created_by_user_id
         LEFT JOIN users accepted ON accepted.id = hs.accepted_by_user_id
        WHERE hs.household_id = ?
          AND hs.revoked_at IS NULL
          AND hs.accepted_at IS NULL
        ORDER BY hs.accepted_at IS NOT NULL ASC, hs.created_at DESC`
    )
    .all(householdId);
}

function buildPayload(req) {
  const householdId = requireHouseholdId(req);
  return {
    household: getHousehold(householdId),
    currentUser: {
      id: req.user?.id || null,
      email: req.user?.email || null,
      displayName: req.user?.display_name || req.user?.username || null,
      role: req.household?.role || 'member',
      accessLevel: req.household?.role === 'owner' ? 'write' : req.household?.accessLevel || 'write',
      canManageSharing: canManageSharing(req),
      canRemoveUsers: canRemoveUsers(req)
    },
    users: listUsers(householdId),
    shares: listShares(householdId)
  };
}

router.get('/', requireAuth, (req, res) => {
  try {
    sendOk(res, buildPayload(req));
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/shares', requireAuth, (req, res) => {
  try {
    requireShareManager(req);
    const householdId = requireHouseholdId(req);
    const email = normalizeEmail(req.body?.email);
    if (!email) return sendBadRequest(res, 'Enter a valid email address.');
    if (req.user?.email && email === normalizeEmail(req.user.email)) {
      return sendBadRequest(res, 'You are already part of this household.');
    }

    createPendingHouseholdShare(db, {
      householdId,
      email,
      role: req.body?.role,
      accessLevel: req.body?.accessLevel,
      createdByUserId: req.user?.id || null
    });
    sendOk(res, { success: true, ...buildPayload(req) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.patch('/shares/:id/access', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'share');
  if (id === null) return;

  try {
    requireUserRemover(req);
    const householdId = requireHouseholdId(req);
    const accessLevel = normalizeAccessLevel(req.body?.accessLevel);
    const result = db
      .prepare(
        `UPDATE household_shares
            SET access_level = ?,
                updated_at = datetime('now')
          WHERE id = ?
            AND household_id = ?
            AND revoked_at IS NULL
            AND accepted_at IS NULL`
      )
      .run(accessLevel, id, householdId);
    if (result.changes === 0) return sendNotFound(res, 'Pending invite was not found.');
    sendOk(res, { success: true, ...buildPayload(req) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.patch('/users/:id/access', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'user');
  if (id === null) return;

  try {
    requireUserRemover(req);
    const householdId = requireHouseholdId(req);
    const currentUserId = Number(req.user?.id);
    if (id === currentUserId) {
      throw sharingError('You cannot change your own household permissions.', 400);
    }

    const membership = db
      .prepare(
        `SELECT role
           FROM household_memberships
          WHERE household_id = ?
            AND user_id = ?`
      )
      .get(householdId, id);
    if (!membership) return sendNotFound(res, 'Family member was not found.');
    if (membership.role === 'owner') {
      throw sharingError('Owner accounts always have read and write access.', 400);
    }

    const accessLevel = normalizeAccessLevel(req.body?.accessLevel);
    db.prepare(
      `UPDATE household_memberships
          SET access_level = ?,
              updated_at = datetime('now')
        WHERE household_id = ?
          AND user_id = ?`
    ).run(accessLevel, householdId, id);

    sendOk(res, { success: true, ...buildPayload(req) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.delete('/shares/:id', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'share');
  if (id === null) return;

  try {
    requireShareManager(req);
    const householdId = requireHouseholdId(req);
    const result = db
      .prepare(
        `UPDATE household_shares
            SET revoked_at = datetime('now'),
                updated_at = datetime('now')
          WHERE id = ?
            AND household_id = ?
            AND revoked_at IS NULL
            AND accepted_at IS NULL`
      )
      .run(id, householdId);
    if (result.changes === 0) return sendNotFound(res, 'Pending invite was not found.');
    sendOk(res, { success: true, ...buildPayload(req) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.delete('/users/:id', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'user');
  if (id === null) return;

  try {
    requireUserRemover(req);
    const householdId = requireHouseholdId(req);
    const currentUserId = Number(req.user?.id);
    if (id === currentUserId) {
      throw sharingError('You cannot remove yourself from your own household.', 400);
    }

    const membership = db
      .prepare(
        `SELECT hm.user_id, hm.role, u.email, u.display_name, u.username
           FROM household_memberships hm
           JOIN users u ON u.id = hm.user_id
          WHERE hm.household_id = ?
            AND hm.user_id = ?`
      )
      .get(householdId, id);
    if (!membership) return sendNotFound(res, 'Family member was not found.');

    if (membership.role === 'owner') {
      const ownerCount = db
        .prepare(
          `SELECT COUNT(*) AS count
             FROM household_memberships
            WHERE household_id = ?
              AND role = 'owner'`
        )
        .get(householdId).count;
      if (ownerCount <= 1) {
        throw sharingError('You cannot remove the last owner from a household.', 400);
      }
    }

    const remove = db.transaction(() => {
      db.prepare(
        `DELETE FROM household_memberships
          WHERE household_id = ?
            AND user_id = ?`
      ).run(householdId, id);

      db.prepare(
        `UPDATE household_shares
            SET revoked_at = COALESCE(revoked_at, datetime('now')),
                updated_at = datetime('now')
          WHERE household_id = ?
            AND accepted_by_user_id = ?
            AND revoked_at IS NULL`
      ).run(householdId, id);
    });
    remove();

    sendOk(res, { success: true, ...buildPayload(req) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

export default router;
