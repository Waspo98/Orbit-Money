export function sharingError(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

export function normalizeEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  if (!email || email.length > 254) return null;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return null;
  return email;
}

export function normalizeAccessLevel(value) {
  return value === 'read' ? 'read' : 'write';
}

function normalizeShareRole(value) {
  return value === 'admin' ? 'admin' : 'member';
}

function findUserByEmail(db, email) {
  return db
    .prepare('SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1')
    .get(email);
}

function findMembership(db, householdId, userId) {
  return db
    .prepare(
      `SELECT user_id, role, access_level
         FROM household_memberships
        WHERE household_id = ?
          AND user_id = ?`
    )
    .get(householdId, userId);
}

export function createPendingHouseholdShare(db, {
  householdId,
  email,
  role = 'member',
  accessLevel = 'write',
  createdByUserId = null
}) {
  const invitedEmail = normalizeEmail(email);
  if (!invitedEmail) {
    throw sharingError('Enter a valid email address.', 400);
  }

  const existingUser = findUserByEmail(db, invitedEmail);
  if (existingUser && findMembership(db, householdId, existingUser.id)) {
    throw sharingError('That person is already part of this household.', 400);
  }

  const normalizedRole = normalizeShareRole(role);
  const normalizedAccess = normalizeAccessLevel(accessLevel);

  db.prepare(
    `INSERT INTO household_shares (
       household_id, invited_email, role, access_level, created_by_user_id
     ) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(household_id, lower(invited_email)) WHERE revoked_at IS NULL
     DO UPDATE SET
       role = excluded.role,
       access_level = excluded.access_level,
       created_by_user_id = excluded.created_by_user_id,
       updated_at = datetime('now')
     WHERE household_shares.accepted_at IS NULL`
  ).run(householdId, invitedEmail, normalizedRole, normalizedAccess, createdByUserId);

  const share = db
    .prepare(
      `SELECT id, invited_email, role, access_level, accepted_at
         FROM household_shares
        WHERE household_id = ?
          AND lower(invited_email) = lower(?)
          AND revoked_at IS NULL`
    )
    .get(householdId, invitedEmail);

  if (!share || share.accepted_at) {
    throw sharingError('That invite has already been accepted. Remove the member before inviting them again.', 400);
  }

  return share;
}

export function acceptPendingHouseholdShares(db, userId, email) {
  const invitedEmail = normalizeEmail(email);
  if (!invitedEmail) return null;

  const shares = db
    .prepare(
      `SELECT id, household_id, role, access_level
         FROM household_shares
        WHERE lower(invited_email) = lower(?)
          AND revoked_at IS NULL
          AND accepted_at IS NULL
        ORDER BY created_at ASC, id ASC`
    )
    .all(invitedEmail);
  if (shares.length === 0) return null;

  const insertMembership = db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(household_id, user_id) DO NOTHING`
  );
  const markAccepted = db.prepare(
    `UPDATE household_shares
        SET accepted_by_user_id = ?,
            accepted_at = COALESCE(accepted_at, datetime('now')),
            revoked_at = COALESCE(revoked_at, datetime('now')),
            updated_at = datetime('now')
      WHERE id = ?
        AND revoked_at IS NULL
        AND accepted_at IS NULL`
  );

  const run = db.transaction(() => {
    let preferredHouseholdId = null;
    for (const share of shares) {
      const accessLevel = share.role === 'owner'
        ? 'write'
        : normalizeAccessLevel(share.access_level);
      const role = normalizeShareRole(share.role);
      const existing = findMembership(db, share.household_id, userId);
      if (!existing) {
        insertMembership.run(share.household_id, userId, role, accessLevel);
      }
      markAccepted.run(userId, share.id);
      preferredHouseholdId = preferredHouseholdId || share.household_id;
    }
    return preferredHouseholdId;
  });

  return run();
}
