export const FIELD_OPTIONS = [
  { value: 'merchant', label: 'Merchant name' },
  { value: 'original_description', label: 'Original description' },
  { value: 'amount', label: 'Amount' },
  { value: 'account_id', label: 'Account' },
  { value: 'category_id', label: 'Category' }
];

export const OPERATORS_BY_FIELD_TYPE = {
  text: ['contains', 'equals', 'starts_with', 'regex'],
  number: ['equals', 'greater_than', 'less_than', 'between'],
  id: ['is', 'is_not']
};

export const OPERATOR_LABELS = {
  contains: 'contains',
  equals: 'equals',
  starts_with: 'starts with',
  regex: 'matches regex',
  greater_than: 'is greater than',
  less_than: 'is less than',
  between: 'is between',
  is: 'is',
  is_not: 'is not'
};

export const ACTION_TYPES = [
  { value: 'rename', label: 'Rename merchant to' },
  { value: 'categorize', label: 'Set category to' },
  { value: 'mark_transfer', label: 'Mark as transfer' },
  { value: 'mark_ignored', label: 'Ignore from budget' }
];

export function fieldType(field) {
  if (field === 'amount') return 'number';
  if (field === 'account_id' || field === 'category_id') return 'id';
  return 'text';
}

export function summarizeCondition(condition, accounts, categories) {
  const fieldLabel = FIELD_OPTIONS.find((field) => field.value === condition.field)?.label || condition.field;
  const operatorLabel = OPERATOR_LABELS[condition.operator] || condition.operator;

  let value = condition.value;
  if (condition.field === 'account_id') {
    const account = accounts.find((item) => item.id === Number(condition.value));
    value = account ? account.name : `account #${condition.value}`;
  } else if (condition.field === 'category_id') {
    const category = categories.find((item) => item.id === Number(condition.value));
    value = category ? category.name : `category #${condition.value}`;
  } else if (Array.isArray(condition.value)) {
    value = condition.value.join(' and ');
  }

  return `${fieldLabel} ${operatorLabel} "${value}"`;
}

export function summarizeAction(action, categories) {
  switch (action.type) {
    case 'rename':
      return `Rename to "${action.value}"`;
    case 'categorize': {
      const category = categories.find((item) => item.id === Number(action.value));
      return `Set category to ${category ? category.name : `#${action.value}`}`;
    }
    case 'mark_transfer':
      return 'Mark as transfer';
    case 'mark_ignored':
      return 'Ignore from budget';
    default:
      return action.type;
  }
}
