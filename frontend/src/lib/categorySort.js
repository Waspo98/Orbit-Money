export function sortCategoriesByName(categories = []) {
  return [...categories].sort((a, b) => (
    String(a?.name || '').localeCompare(String(b?.name || ''), undefined, {
      sensitivity: 'base'
    })
  ));
}
