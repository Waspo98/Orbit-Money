import { formatLocalDate, isValidDateOnly } from './localDate.js';

export const FREQUENCY_UNITS = new Set(['days', 'weeks', 'months']);

const FINAL_DAY_VALUE = -1;
const LAST_WEEK_VALUE = -1;

export function isoDate(value) {
  return isValidDateOnly(value) ? value : null;
}

export function parseDateParts(value) {
  const [year, month, day] = value.split('-').map(Number);
  return { year, month, day };
}

export function formatDateParts(year, month, day) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function daysInMonth(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function addDays(value, days) {
  const { year, month, day } = parseDateParts(value);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return formatDateParts(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
}

export function addMonthsClamped(value, monthsToAdd) {
  const { year, month, day } = parseDateParts(value);
  const monthIndex = (year * 12) + (month - 1) + monthsToAdd;
  const nextYear = Math.floor(monthIndex / 12);
  const nextMonth = (monthIndex % 12) + 1;
  const nextDay = Math.min(day, daysInMonth(nextYear, nextMonth));
  return formatDateParts(nextYear, nextMonth, nextDay);
}

export function startOfMonth(value) {
  const { year, month } = parseDateParts(value);
  return formatDateParts(year, month, 1);
}

export function endOfMonth(value) {
  const { year, month } = parseDateParts(value);
  return formatDateParts(year, month, daysInMonth(year, month));
}

export function addInterval(value, item) {
  if (!isoDate(value)) return value;
  const type = item.frequency_type || 'monthly';
  if (type === 'weekly') return addDays(value, 7);
  if (type === 'biweekly') return addDays(value, 14);
  if (type === 'semimonthly') return addDays(value, 15);
  if (type === 'bimonthly') return addMonthsClamped(value, 2);
  if (type === 'yearly') return addMonthsClamped(value, 12);
  if (type === 'custom') {
    const interval = Math.max(1, Number(item.frequency_interval) || 1);
    const unit = FREQUENCY_UNITS.has(item.frequency_unit) ? item.frequency_unit : 'days';
    if (unit === 'days') return addDays(value, interval);
    if (unit === 'weeks') return addDays(value, interval * 7);
    return addMonthsClamped(value, interval);
  }
  return addMonthsClamped(value, 1);
}

export function daysBetween(a, b) {
  const startParts = isoDate(a) ? parseDateParts(a) : null;
  const endParts = isoDate(b) ? parseDateParts(b) : null;
  if (!startParts || !endParts) return null;
  const start = new Date(Date.UTC(startParts.year, startParts.month - 1, startParts.day));
  const end = new Date(Date.UTC(endParts.year, endParts.month - 1, endParts.day));
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
  return Math.round((end.getTime() - start.getTime()) / 86400000);
}

function normalizeIntegerList(values, min, max, limit, extraValues = []) {
  const source = Array.isArray(values) ? values : [values];
  const extras = new Set(extraValues.map((value) => Number(value)));
  const sortValue = (value) => (
    extras.has(value) && value < min
      ? max + Math.abs(value) + 1
      : value
  );
  return Array.from(
    new Set(
      source
        .map((value) => Math.trunc(Number(value)))
        .filter((value) => Number.isFinite(value) && ((value >= min && value <= max) || extras.has(value)))
    )
  )
    .sort((a, b) => sortValue(a) - sortValue(b))
    .slice(0, limit);
}

export function parseRecurrenceRule(value) {
  if (!value) return null;

  let raw = value;
  if (typeof value === 'string') {
    try {
      raw = JSON.parse(value);
    } catch {
      return null;
    }
  }

  if (!raw || typeof raw !== 'object') return null;

  if (raw.type === 'month_days') {
    const days = normalizeIntegerList(raw.days, 1, 31, 6, [FINAL_DAY_VALUE]);
    return days.length ? { type: 'month_days', days } : null;
  }

  if (raw.type === 'month_weekdays') {
    const ordinals = normalizeIntegerList(raw.ordinals, 1, 5, 5, [LAST_WEEK_VALUE]);
    const weekdays = normalizeIntegerList(raw.weekdays, 0, 6, 7);
    return ordinals.length && weekdays.length
      ? { type: 'month_weekdays', ordinals, weekdays }
      : null;
  }

  return null;
}

export function recurrenceRuleToStorage(rule) {
  return rule ? JSON.stringify(rule) : null;
}

function monthlyDatesForRule(rule, year, month) {
  if (!rule) return [];

  const monthDays = daysInMonth(year, month);
  const dates = new Set();

  if (rule.type === 'month_days') {
    for (const day of rule.days || []) {
      const dateDay = day === FINAL_DAY_VALUE ? monthDays : Math.min(day, monthDays);
      if (dateDay >= 1) dates.add(formatDateParts(year, month, dateDay));
    }
  }

  if (rule.type === 'month_weekdays') {
    const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
    const lastWeekday = new Date(Date.UTC(year, month - 1, monthDays)).getUTCDay();
    for (const ordinal of rule.ordinals || []) {
      for (const weekday of rule.weekdays || []) {
        if (ordinal === LAST_WEEK_VALUE) {
          const offsetBack = (lastWeekday - weekday + 7) % 7;
          dates.add(formatDateParts(year, month, monthDays - offsetBack));
          continue;
        }
        const offset = (weekday - firstWeekday + 7) % 7;
        const day = 1 + offset + ((ordinal - 1) * 7);
        if (day <= monthDays) dates.add(formatDateParts(year, month, day));
      }
    }
  }

  return Array.from(dates).sort();
}

export function datesForRuleBetween(rule, startDate, endDate) {
  if (!rule || !isoDate(startDate) || !isoDate(endDate) || endDate < startDate) return [];

  const dates = [];
  let { year, month } = parseDateParts(startDate);
  const endParts = parseDateParts(endDate);

  for (let i = 0; i < 72; i += 1) {
    for (const date of monthlyDatesForRule(rule, year, month)) {
      if (date >= startDate && date <= endDate) dates.push(date);
    }

    if (year > endParts.year || (year === endParts.year && month >= endParts.month)) break;
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }

  return dates.sort();
}

export function findRuleDateOnOrAfter(value, rule) {
  if (!isoDate(value)) return null;
  return datesForRuleBetween(rule, value, addMonthsClamped(value, 24))[0] || null;
}

export function datesForItemBetween(item, startDate, endDate) {
  const rule = parseRecurrenceRule(item?.recurrence_rule);
  if (rule) return datesForRuleBetween(rule, startDate, endDate);

  const dates = [];
  let next = isoDate(item?.next_date) ? item.next_date : startDate;
  for (let i = 0; i < 240 && next < startDate; i += 1) {
    const advanced = addInterval(next, item);
    if (advanced === next) break;
    next = advanced;
  }

  for (let i = 0; i < 240 && next <= endDate; i += 1) {
    if (next >= startDate) dates.push(next);
    const advanced = addInterval(next, item);
    if (advanced === next) break;
    next = advanced;
  }

  return dates;
}

export function advanceToUpcoming(value, item) {
  const rule = parseRecurrenceRule(item?.recurrence_rule);
  if (rule) return findRuleDateOnOrAfter(value, rule) || value;

  let next = value;
  const today = formatLocalDate();
  for (let i = 0; i < 240 && next < today; i += 1) {
    const advanced = addInterval(next, item);
    if (advanced === next) break;
    next = advanced;
  }
  return next;
}
