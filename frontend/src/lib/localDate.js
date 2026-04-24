const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

export function parseDateOnly(value) {
  if (!value || typeof value !== 'string') return null;
  const [year, month, day = '01'] = value.split('-').map(Number);
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return null;
  }
  const date = new Date(year, month - 1, day);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatLocalDate(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatLocalMonth(date = new Date()) {
  return formatLocalDate(date).slice(0, 7);
}

export function addMonthsToLocalDate(value, amount) {
  const base = value instanceof Date ? new Date(value) : parseDateOnly(value) || new Date();
  base.setMonth(base.getMonth() + amount);
  return formatLocalDate(base);
}

export function formatMonthKeyLabel(key) {
  const date = parseDateOnly(`${key}-01`);
  if (!date) return key;
  return `${MONTH_NAMES[date.getMonth()]} ${date.getFullYear()}`;
}

export function formatMonthDay(value) {
  const date = parseDateOnly(value);
  if (!date) return '';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function formatFullDate(value) {
  const date = parseDateOnly(value);
  if (!date) return '';
  return date.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  });
}
