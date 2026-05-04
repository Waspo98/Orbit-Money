// Category edits need the source column because NULL is a valid user-selected
// category, not only the absence of an edit.
export function effectiveCategoryIdSql(alias = '') {
  const prefix = alias ? `${alias}.` : '';
  return `CASE
    WHEN ${prefix}edited_category_id_source IS NOT NULL THEN ${prefix}edited_category_id
    ELSE ${prefix}category_id
  END`;
}
