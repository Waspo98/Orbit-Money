export function seedHouseholdDefaults(db, householdId) {
  const settings = db.prepare('SELECT key, value FROM app_default_settings').all();
  const insertSetting = db.prepare(
    `INSERT OR IGNORE INTO app_settings (household_id, key, value)
     VALUES (?, ?, ?)`
  );

  for (const setting of settings) {
    insertSetting.run(householdId, setting.key, setting.value);
  }

  const existingCategories = db
    .prepare('SELECT COUNT(*) AS count FROM categories WHERE household_id = ?')
    .get(householdId).count;
  if (existingCategories > 0) return;

  const categories = db
    .prepare(
      `SELECT name, color, icon, is_transfer, is_income, sort_order,
              mha_default_eligible, mha_default_ignored
         FROM app_default_categories
        ORDER BY sort_order ASC, name ASC`
    )
    .all();
  const insertCategory = db.prepare(
    `INSERT INTO categories (
       household_id, name, color, icon, is_transfer, is_income, sort_order,
       mha_default_eligible, mha_default_ignored
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
  );

  for (const category of categories) {
    insertCategory.run(
      householdId,
      category.name,
      category.color,
      category.icon,
      category.is_transfer,
      category.is_income,
      category.sort_order,
      category.mha_default_eligible,
      category.mha_default_ignored
    );
  }
}

export function createHouseholdForUser(db, userId, displayName) {
  const name = `${displayName || 'New'} Household`;
  const run = db.transaction(() => {
    const householdId = db
      .prepare('INSERT INTO households (name) VALUES (?)')
      .run(name).lastInsertRowid;
    db.prepare(
      `INSERT INTO household_memberships (household_id, user_id, role)
       VALUES (?, ?, 'owner')`
    ).run(householdId, userId);
    seedHouseholdDefaults(db, householdId);
    return householdId;
  });
  return run();
}
