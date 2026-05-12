const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/;
const MONTH_ONLY_RE = /^\d{4}-\d{2}$/;

export function formatLocalDate(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatLocalMonth(date = new Date()) {
  return formatLocalDate(date).slice(0, 7);
}

export function parseDateParts(value) {
  if (typeof value !== 'string' || !DATE_ONLY_RE.test(value)) {
    return null;
  }

  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    return null;
  }
  return { year, month, day };
}

export function parseMonthParts(value) {
  if (typeof value !== 'string' || !MONTH_ONLY_RE.test(value)) {
    return null;
  }
  const [year, month] = value.split('-').map(Number);
  if (!Number.isInteger(year) || month < 1 || month > 12) return null;
  return { year, month };
}

export function isValidDateOnly(value) {
  return parseDateParts(value) !== null;
}

export function isValidMonthOnly(value) {
  return parseMonthParts(value) !== null;
}

export function isValidOptionalDateOnly(value) {
  return !value || isValidDateOnly(value);
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
  const base = parts
    ? new Date(parts.year, parts.month - 1, 1)
    : new Date();
  return {
    start: formatLocalDate(new Date(base.getFullYear(), base.getMonth(), 1)),
    end: formatLocalDate(new Date(base.getFullYear(), base.getMonth() + 1, 0))
  };
}
