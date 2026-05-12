const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];
const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/;
const MONTH_ONLY_RE = /^\d{4}-\d{2}$/;

export function parseDateParts(value) {
  if (!value || typeof value !== 'string' || !DATE_ONLY_RE.test(value)) {
    return null;
  }
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  if (
    Number.isNaN(date.getTime()) ||
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    return null;
  }
  return { year, month, day };
}

export function parseMonthParts(value) {
  if (!value || typeof value !== 'string' || !MONTH_ONLY_RE.test(value)) {
    return null;
  }
  const [year, month] = value.split('-').map(Number);
  if (!Number.isInteger(year) || month < 1 || month > 12) return null;
  return { year, month };
}

export function parseDateOnly(value) {
  const parts = parseDateParts(value);
  if (!parts) return null;
  return new Date(parts.year, parts.month - 1, parts.day);
}

export function isValidDateOnly(value) {
  return parseDateParts(value) !== null;
}

export function isValidMonthOnly(value) {
  return parseMonthParts(value) !== null;
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

export function todayLocalDate() {
  return formatLocalDate();
}

export function addMonthsToLocalDate(value, amount) {
  const base = value instanceof Date ? new Date(value) : parseDateOnly(value) || new Date();
  const day = base.getDate();
  base.setDate(1);
  base.setMonth(base.getMonth() + amount);
  const lastDay = new Date(base.getFullYear(), base.getMonth() + 1, 0).getDate();
  base.setDate(Math.min(day, lastDay));
  return formatLocalDate(base);
}

export function addMonthsToLocalMonth(value, amount) {
  const parts = parseMonthParts(value);
  const base = parts
    ? new Date(parts.year, parts.month - 1, 1)
    : new Date();
  base.setMonth(base.getMonth() + amount);
  return formatLocalMonth(base);
}

export function getLocalMonthBounds(value) {
  const parts = parseMonthParts(value);
  const startDate = parts
    ? new Date(parts.year, parts.month - 1, 1)
    : new Date();
  const year = startDate.getFullYear();
  const monthIndex = startDate.getMonth();
  return {
    start: formatLocalDate(new Date(year, monthIndex, 1)),
    end: formatLocalDate(new Date(year, monthIndex + 1, 0))
  };
}

export function daysLeftInLocalMonth(value, today = new Date()) {
  const parts = parseMonthParts(value);
  if (!parts) return null;
  const target = new Date(parts.year, parts.month - 1, 1);
  const currentMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  if (target.getTime() !== currentMonthStart.getTime()) return null;
  const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
  return lastDay - today.getDate();
}

export function formatMonthKeyLabel(key) {
  const parts = parseMonthParts(key);
  if (!parts) return key;
  return `${MONTH_NAMES[parts.month - 1]} ${parts.year}`;
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

export function formatMediumDate(value) {
  const date = parseDateOnly(value);
  if (!date) return '';
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  });
}

export function ageFromDate(value, today = new Date()) {
  const birth = parseDateOnly(value);
  if (!birth) return null;
  let age = today.getFullYear() - birth.getFullYear();
  const monthDelta = today.getMonth() - birth.getMonth();
  if (monthDelta < 0 || (monthDelta === 0 && today.getDate() < birth.getDate())) {
    age -= 1;
  }
  return age >= 0 ? age : null;
}
