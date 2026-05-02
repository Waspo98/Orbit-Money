export const SAMPLE_HOUSEHOLD_TTL_HOURS = 48;

const HOUSEHOLD_DELETE_TABLES = [
  'upcoming_occurrences',
  'upcoming_dismissed_suggestions',
  'upcoming_items',
  'import_batch_items',
  'import_batches',
  'sync_log',
  'simplefin_config',
  'app_settings',
  'user_preferences',
  'household_shares',
  'household_retirement_accounts',
  'household_income_records',
  'household_members',
  'account_balance_records',
  'goal_account_allocations',
  'goals',
  'budgets',
  'rules',
  'transactions',
  'categories',
  'accounts'
];

function placeholders(values) {
  return values.map(() => '?').join(', ');
}

function tableExists(db, table) {
  return Boolean(
    db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").get(table)
  );
}

export function touchSampleUser(db, userId) {
  db.prepare(
    `UPDATE users
        SET updated_at = datetime('now')
      WHERE id = ?
        AND username LIKE 'sample:%'`
  ).run(userId);
}

export function cleanupExpiredSampleHouseholds(db) {
  const householdRows = db
    .prepare(
      `SELECT h.id
         FROM households h
         JOIN household_memberships hm ON hm.household_id = h.id
         JOIN users u ON u.id = hm.user_id
        GROUP BY h.id
       HAVING COUNT(*) > 0
          AND SUM(CASE WHEN u.username LIKE 'sample:%' THEN 0 ELSE 1 END) = 0
          AND MAX(datetime(u.updated_at)) < datetime('now', ?)`
    )
    .all(`-${SAMPLE_HOUSEHOLD_TTL_HOURS} hours`);

  const householdIds = householdRows.map((row) => row.id);
  if (householdIds.length === 0) {
    return { householdsDeleted: 0, usersDeleted: 0 };
  }

  const run = db.transaction(() => {
    const userIds = db
      .prepare(
        `SELECT DISTINCT u.id
           FROM users u
           JOIN household_memberships hm ON hm.user_id = u.id
          WHERE u.username LIKE 'sample:%'
            AND hm.household_id IN (${placeholders(householdIds)})`
      )
      .all(...householdIds)
      .map((row) => row.id);

    for (const table of HOUSEHOLD_DELETE_TABLES) {
      if (!tableExists(db, table)) continue;
      const deleteRows = db.prepare(`DELETE FROM ${table} WHERE household_id = ?`);
      for (const householdId of householdIds) {
        deleteRows.run(householdId);
      }
    }

    const householdMembershipsDeleted = db
      .prepare(
        `DELETE FROM household_memberships
          WHERE household_id IN (${placeholders(householdIds)})`
      )
      .run(...householdIds);

    const householdsDeleted = db
      .prepare(`DELETE FROM households WHERE id IN (${placeholders(householdIds)})`)
      .run(...householdIds);

    let usersDeleted = 0;
    if (userIds.length > 0) {
      usersDeleted = db
        .prepare(
          `DELETE FROM users
            WHERE id IN (${placeholders(userIds)})
              AND username LIKE 'sample:%'
              AND NOT EXISTS (
                SELECT 1
                  FROM household_memberships hm
                 WHERE hm.user_id = users.id
              )`
        )
        .run(...userIds).changes;
    }

    return {
      householdsDeleted: householdsDeleted.changes,
      usersDeleted,
      membershipsDeleted: householdMembershipsDeleted.changes
    };
  });

  return run();
}
