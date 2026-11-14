// Category edits need the source column because NULL is a valid user-selected
// category, not only the absence of an edit.
export function effectiveCategoryIdSql(alias = '') {
  const prefix = alias ? `${alias}.` : '';
  return `CASE
    WHEN ${prefix}edited_category_id_source IS NOT NULL THEN ${prefix}edited_category_id
    ELSE ${prefix}category_id
  END`;
}

export function isUncategorizedCategoryName(name) {
  return String(name || '').trim().toLowerCase() === 'uncategorized';
}

export function uncategorizedTransactionWhereSql(alias = '') {
  const prefix = alias ? `${alias}.` : '';
  const effCatSql = effectiveCategoryIdSql(alias);
  return `(COALESCE(${prefix}edited_is_transfer, ${prefix}is_transfer) = 0 AND (${effCatSql} IS NULL OR ${effCatSql} IN (SELECT id FROM categories WHERE household_id = ? AND lower(trim(name)) = 'uncategorized')))`;
}
