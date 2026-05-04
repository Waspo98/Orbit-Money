export function appendUserEditAssignment({
  sets,
  values,
  editColumn,
  sourceColumn,
  value,
  originalValue,
  existingSource
}) {
  if (Object.is(value, originalValue) && existingSource == null) {
    sets.push(`${editColumn} = NULL`);
    sets.push(`${sourceColumn} = NULL`);
    return 'clear';
  }

  sets.push(`${editColumn} = ?`);
  values.push(value);
  sets.push(`${sourceColumn} = 'user'`);
  return 'user';
}
