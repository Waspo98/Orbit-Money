export function effectiveCategoryIdSql(alias = '') {
  const prefix = alias ? `${alias}.` : '';
  return `CASE
    WHEN ${prefix}edited_category_id_source IS NOT NULL THEN ${prefix}edited_category_id
    ELSE ${prefix}category_id
  END`;
}
