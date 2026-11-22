import crypto from 'crypto';

export function computeContentHash({ date, amountCents, originalDescription, accountKey }) {
  const input = `${date}|${amountCents}|${originalDescription}|${accountKey}`;
  return crypto.createHash('sha256').update(input).digest('hex').slice(0, 16);
}

export function readCategories(db, householdId) {
  const categories = db
    .prepare('SELECT id, name FROM categories WHERE household_id = ?')
    .all(householdId);
  const categoryIdByName = new Map(categories.map((c) => [c.name.toLowerCase(), c.id]));
  const validCategoryIds = new Set(categories.map((c) => c.id));
  const uncategorizedId = categoryIdByName.get('uncategorized') ?? categories[0]?.id ?? null;
  return { categoryIdByName, validCategoryIds, uncategorizedId };
}

export function recordBatchItem(db, householdId, importBatchId, tableName, rowId) {
  db.prepare(
    `INSERT OR IGNORE INTO import_batch_items (household_id, import_batch_id, table_name, row_id)
     VALUES (?, ?, ?, ?)`
  ).run(householdId, importBatchId, tableName, rowId);
}
