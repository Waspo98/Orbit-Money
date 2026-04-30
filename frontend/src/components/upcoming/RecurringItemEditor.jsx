import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import AnimatedModal from '../AnimatedModal.jsx';
import AppSelect from '../AppSelect.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../CurrencyInput.jsx';
import SelectableListItem from '../SelectableListItem.jsx';
import { addMonthsToLocalDate, formatLocalDate } from '../../lib/localDate.js';

export const KIND_OPTIONS = [
  { value: 'income', label: 'Income' },
  { value: 'giving', label: 'Giving' },
  { value: 'subscription', label: 'Subscription' },
  { value: 'bill', label: 'Bill' }
];

const FREQUENCY_OPTIONS = [
  { value: 'weekly', label: 'Weekly' },
  { value: 'biweekly', label: 'Every Other Week' },
  { value: 'semimonthly', label: 'Semi-Monthly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'bimonthly', label: 'Every Other Month' },
  { value: 'yearly', label: 'Yearly' },
  { value: 'custom', label: 'Custom' }
];

const FREQUENCY_UNIT_OPTIONS = [
  { value: 'days', label: 'Days' },
  { value: 'weeks', label: 'Weeks' },
  { value: 'months', label: 'Months' }
];

const AMOUNT_STRATEGY_OPTIONS = [
  { value: 'fixed', label: 'Fixed Amount' },
  { value: 'history_average', label: 'Estimate From History' }
];

const SCHEDULE_MODE_OPTIONS = [
  { value: 'interval', label: 'Regular Interval' },
  { value: 'month_days', label: 'Day Of Month' },
  { value: 'month_weekdays', label: 'Weekday Pattern' }
];

const FINAL_DAY_VALUE = -1;
const LAST_WEEK_VALUE = -1;

const ORDINAL_OPTIONS = [
  { value: '1', label: '1st' },
  { value: '2', label: '2nd' },
  { value: '3', label: '3rd' },
  { value: '4', label: '4th' },
  { value: '5', label: '5th' },
  { value: String(LAST_WEEK_VALUE), label: 'Last' }
];

const MONTH_DAY_OPTIONS = [
  ...Array.from({ length: 31 }, (_, index) => {
    const day = index + 1;
    return { value: String(day), label: monthDayLabel(day) };
  }),
  { value: String(FINAL_DAY_VALUE), label: 'Final Day' }
];

const WEEKDAY_OPTIONS = [
  { value: '0', label: 'Sunday' },
  { value: '1', label: 'Monday' },
  { value: '2', label: 'Tuesday' },
  { value: '3', label: 'Wednesday' },
  { value: '4', label: 'Thursday' },
  { value: '5', label: 'Friday' },
  { value: '6', label: 'Saturday' }
];

const WEEKDAY_NAMES = WEEKDAY_OPTIONS.map((option) => option.label);
const BILL_HINTS = ['bill', 'utilit', 'insurance', 'loan', 'mortgage', 'rent'];
const GIVING_HINTS = ['charitable', 'charity', 'church', 'donation', 'donations', 'giving', 'nonprofit', 'non-profit', 'tithe'];
const INCOME_HINTS = ['income', 'paycheck', 'salary', 'payroll'];
const SCHEDULE_KEYS = new Set([
  'frequency_type',
  'schedule_mode',
  'month_day_one',
  'month_day_two',
  'ordinal_one',
  'ordinal_two',
  'weekday'
]);

function todayIso() {
  return formatLocalDate();
}

function defaultNextDate() {
  return addMonthsToLocalDate(formatLocalDate(), 1);
}

function parseDateParts(value) {
  const [year, month, day] = String(value || '').split('-').map(Number);
  return { year, month, day };
}

function formatDateParts(year, month, day) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate();
}

function addMonthsClamped(value, amount) {
  return addMonthsToLocalDate(value, amount);
}

function normalizeNumberList(values, min, max, limit, extraValues = []) {
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

function monthlyDatesForRule(rule, year, month) {
  const monthDays = daysInMonth(year, month);
  const dates = new Set();

  if (rule?.type === 'month_days') {
    for (const day of rule.days || []) {
      const dateDay = day === FINAL_DAY_VALUE ? monthDays : Math.min(day, monthDays);
      if (dateDay >= 1) dates.add(formatDateParts(year, month, dateDay));
    }
  }

  if (rule?.type === 'month_weekdays') {
    const firstWeekday = new Date(year, month - 1, 1).getDay();
    const lastWeekday = new Date(year, month - 1, monthDays).getDay();
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

function nextRuleDate(rule, startDate = todayIso()) {
  if (!rule) return '';
  let { year, month } = parseDateParts(startDate);
  for (let i = 0; i < 36; i += 1) {
    const match = monthlyDatesForRule(rule, year, month).find((date) => date >= startDate);
    if (match) return match;
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return '';
}

function ordinalLabel(value) {
  return ORDINAL_OPTIONS.find((option) => String(option.value) === String(value))?.label || `${value}`;
}

function monthDayLabel(value) {
  const day = Number(value);
  if (day === FINAL_DAY_VALUE) return 'Final Day';
  const suffix = day % 10 === 1 && day % 100 !== 11
    ? 'st'
    : day % 10 === 2 && day % 100 !== 12
      ? 'nd'
      : day % 10 === 3 && day % 100 !== 13
        ? 'rd'
        : 'th';
  return `${day}${suffix}`;
}

function joinLabels(values) {
  if (values.length <= 1) return values[0] || '';
  return `${values.slice(0, -1).join(', ')} and ${values[values.length - 1]}`;
}

function weekdayLabel(value, plural = false) {
  const label = WEEKDAY_NAMES[Number(value)] || 'Friday';
  return plural ? `${label}s` : label;
}

function buildRuleFromForm(form) {
  if (!['monthly', 'semimonthly'].includes(form.frequency_type)) return null;

  if (form.schedule_mode === 'month_days') {
    const dayValues = form.frequency_type === 'semimonthly'
      ? [form.month_day_one, form.month_day_two]
      : [form.month_day_one];
    const days = normalizeNumberList(dayValues, 1, 31, 6, [FINAL_DAY_VALUE]);
    return days.length ? { type: 'month_days', days } : null;
  }

  if (form.schedule_mode === 'month_weekdays') {
    const ordinalValues = form.frequency_type === 'semimonthly'
      ? [form.ordinal_one, form.ordinal_two]
      : [form.ordinal_one];
    const ordinals = normalizeNumberList(ordinalValues, 1, 5, 5, [LAST_WEEK_VALUE]);
    const weekdays = normalizeNumberList([form.weekday], 0, 6, 7);
    return ordinals.length && weekdays.length ? { type: 'month_weekdays', ordinals, weekdays } : null;
  }

  return null;
}

function scheduleModeFromRule(rule) {
  if (rule?.type === 'month_days') return 'month_days';
  if (rule?.type === 'month_weekdays') return 'month_weekdays';
  return 'interval';
}

function isGivingText(value) {
  const text = String(value || '').toLowerCase();
  return GIVING_HINTS.some((hint) => text.includes(hint));
}

function isGivingCategory(category) {
  return isGivingText(category?.name);
}

function isGivingRecurringItem(item) {
  if (item?.kind === 'giving') return true;
  if (item?.kind === 'income' || item?.direction === 'income') return false;
  return [
    item?.category_name,
    item?.name,
    item?.merchant,
    item?.notes
  ].some(isGivingText);
}

function defaultGivingCategoryId(categories) {
  const exact = categories.find((category) => String(category.name || '').toLowerCase() === 'charitable donations');
  const fallback = exact || categories.find(isGivingCategory);
  return fallback?.id ? String(fallback.id) : '';
}

function applyGivingDefaults(form, categories) {
  if (form.kind !== 'giving' || form.category_id) return form;
  const categoryId = defaultGivingCategoryId(categories);
  return categoryId ? { ...form, category_id: categoryId } : form;
}

function storageKind(kind) {
  return kind === 'giving' ? 'bill' : kind;
}

export function kindLabel(kind) {
  return KIND_OPTIONS.find((option) => option.value === kind)?.label || 'Item';
}

export function recurringFrequencyLabel(item) {
  const rule = item?.recurrence_rule;
  if (rule?.type === 'month_days') {
    const days = (rule.days || []).map((day) => monthDayLabel(day));
    return `${item?.frequency_type === 'semimonthly' ? 'Semi-monthly' : 'Monthly'} on ${joinLabels(days)}`;
  }

  if (rule?.type === 'month_weekdays') {
    const ordinals = (rule.ordinals || []).map((ordinal) => ordinalLabel(ordinal));
    const weekday = weekdayLabel((rule.weekdays || [5])[0], ordinals.length > 1);
    return `${joinLabels(ordinals)} ${weekday}`;
  }

  const type = item?.frequency_type || 'monthly';
  if (type === 'weekly') return 'Weekly';
  if (type === 'biweekly') return 'Every other week';
  if (type === 'semimonthly') return 'Semi-monthly';
  if (type === 'bimonthly') return 'Every other month';
  if (type === 'yearly') return 'Yearly';
  if (type === 'custom') {
    const interval = Number(item.frequency_interval) || 1;
    const unit = String(item.frequency_unit || 'days').replace(/s$/, '');
    return `Every ${interval} ${unit}${interval === 1 ? '' : 's'}`;
  }
  return 'Monthly';
}

export function inferRecurringKind(direction, category) {
  const categoryName = String(category?.name || '').toLowerCase();
  if (direction === 'income' || category?.is_income || INCOME_HINTS.some((hint) => categoryName.includes(hint))) {
    return 'income';
  }
  if (isGivingCategory(category)) return 'giving';
  if (BILL_HINTS.some((hint) => categoryName.includes(hint))) return 'bill';
  return 'subscription';
}

export function newRecurringForm(overrides = {}) {
  const kind = overrides.kind || 'bill';
  return {
    name: '',
    merchant: '',
    kind,
    amount: '',
    direction: kind === 'income' ? 'income' : 'expense',
    frequency_type: 'monthly',
    frequency_interval: '1',
    frequency_unit: 'months',
    schedule_mode: 'interval',
    month_day_one: '1',
    month_day_two: '15',
    ordinal_one: '1',
    ordinal_two: '3',
    weekday: '5',
    amount_strategy: 'fixed',
    amount_lookback_months: '6',
    projected_amount: '',
    next_date: defaultNextDate(),
    category_id: '',
    account_id: '',
    source: 'manual',
    source_transaction_id: '',
    notes: '',
    ...overrides
  };
}

export function formFromRecurringItem(item = {}) {
  const rule = item.recurrence_rule || null;
  const scheduleMode = scheduleModeFromRule(rule);
  const monthDays = rule?.type === 'month_days' ? rule.days || [] : [];
  const ordinals = rule?.type === 'month_weekdays' ? rule.ordinals || [] : [];
  const weekdays = rule?.type === 'month_weekdays' ? rule.weekdays || [] : [];
  const amountForEditor = item.amount_strategy === 'history_average' && item.projected_amount != null
    ? item.projected_amount
    : item.amount;
  const kind = isGivingRecurringItem(item) ? 'giving' : item.kind || 'bill';
  return newRecurringForm({
    name: item.name || item.merchant || '',
    merchant: item.merchant || item.name || '',
    kind,
    amount: formatCurrencyInput(amountForEditor || 0),
    direction: item.direction || (kind === 'income' ? 'income' : 'expense'),
    frequency_type: item.frequency_type || 'monthly',
    frequency_interval: String(item.frequency_interval || 1),
    frequency_unit: item.frequency_unit || 'months',
    schedule_mode: scheduleMode,
    month_day_one: String(monthDays[0] || 1),
    month_day_two: String(monthDays[1] || ''),
    ordinal_one: String(ordinals[0] || 1),
    ordinal_two: String(ordinals[1] || ''),
    weekday: String(weekdays[0] ?? 5),
    amount_strategy: item.amount_strategy || 'fixed',
    amount_lookback_months: String(item.amount_lookback_months || 6),
    projected_amount: item.projected_amount ?? '',
    next_date: item.next_date || defaultNextDate(),
    category_id: item.category_id ? String(item.category_id) : '',
    account_id: item.account_id ? String(item.account_id) : '',
    source: item.source || 'manual',
    source_transaction_id: item.source_transaction_id ? String(item.source_transaction_id) : '',
    notes: item.notes || ''
  });
}

export function formFromTransaction(txn = {}, category) {
  const direction = Number(txn.amount || 0) >= 0 ? 'income' : 'expense';
  const kind = inferRecurringKind(direction, category);
  return newRecurringForm({
    name: txn.merchant || txn.original_merchant || '',
    merchant: txn.merchant || txn.original_merchant || '',
    kind,
    amount: formatCurrencyInput(Math.abs(Number(txn.amount || 0))),
    direction: kind === 'income' ? 'income' : direction,
    next_date: addMonthsClamped(txn.date || todayIso(), 1),
    category_id: txn.category_id ? String(txn.category_id) : '',
    account_id: txn.account_id ? String(txn.account_id) : '',
    source: 'transaction',
    source_transaction_id: txn.id ? String(txn.id) : ''
  });
}

export function formFromSuggestion(suggestion = {}) {
  const direction = suggestion.direction || (suggestion.kind === 'income' ? 'income' : 'expense');
  const kind = inferRecurringKind(direction, { name: suggestion.category_name });
  return newRecurringForm({
    name: suggestion.name || suggestion.merchant || '',
    merchant: suggestion.merchant || suggestion.name || '',
    kind,
    amount: formatCurrencyInput(suggestion.amount || 0),
    direction: kind === 'income' ? 'income' : direction,
    frequency_type: suggestion.frequency_type || 'monthly',
    frequency_interval: String(suggestion.frequency_interval || 1),
    frequency_unit: suggestion.frequency_unit || 'months',
    next_date: suggestion.next_date || defaultNextDate(),
    category_id: suggestion.category_id ? String(suggestion.category_id) : '',
    account_id: suggestion.account_id ? String(suggestion.account_id) : '',
    source: 'suggestion',
    source_transaction_id: suggestion.source_transaction_id ? String(suggestion.source_transaction_id) : ''
  });
}

export function payloadFromRecurringForm(form) {
  const kind = form.kind;
  const storedKind = storageKind(kind);
  const recurrenceRule = buildRuleFromForm(form);
  return {
    name: String(form.name || '').trim(),
    merchant: String(form.merchant || form.name || '').trim(),
    kind: storedKind,
    amount: parseCurrencyInput(form.amount, 0),
    direction: kind === 'income' ? 'income' : 'expense',
    frequency_type: form.frequency_type,
    frequency_interval: Number(form.frequency_interval) || 1,
    frequency_unit: form.frequency_unit,
    recurrence_rule: recurrenceRule,
    amount_strategy: form.amount_strategy,
    amount_lookback_months: Number(form.amount_lookback_months) || 6,
    next_date: form.next_date,
    category_id: form.category_id ? Number(form.category_id) : null,
    account_id: form.account_id ? Number(form.account_id) : null,
    source: form.source || 'manual',
    source_transaction_id: form.source_transaction_id ? Number(form.source_transaction_id) : null,
    notes: String(form.notes || '').trim() || null
  };
}

export default function RecurringItemEditor({
  item,
  accounts = [],
  categories = [],
  title,
  subtitle = 'Adjust the amount, schedule, and type before saving.',
  saveLabel = 'Save',
  onSave,
  onClose
}) {
  const [form, setForm] = useState(() => applyGivingDefaults(item?.form || item || newRecurringForm(), categories));
  const [saving, setSaving] = useState(false);
  const [estimatingAmount, setEstimatingAmount] = useState(false);
  const [error, setError] = useState('');
  const isMonthBased = ['monthly', 'semimonthly'].includes(form.frequency_type);
  const activeRule = buildRuleFromForm(form);
  const projectedNextDate = activeRule ? nextRuleDate(activeRule, todayIso()) : '';

  useEffect(() => {
    setForm((prev) => applyGivingDefaults(prev, categories));
  }, [categories]);

  useEffect(() => {
    if (form.amount_strategy !== 'history_average') {
      setEstimatingAmount(false);
      return undefined;
    }

    const payload = payloadFromRecurringForm(form);
    if (!payload.name || !payload.next_date) {
      setEstimatingAmount(false);
      return undefined;
    }

    let active = true;
    const timer = setTimeout(async () => {
      setEstimatingAmount(true);
      try {
        const result = await api.post('/api/upcoming/estimate', payload);
        if (!active) return;
        const amount = Number(result.amount || 0);
        setForm((prev) => (
          prev.amount_strategy === 'history_average'
            ? {
                ...prev,
                amount: formatCurrencyInput(amount),
                projected_amount: amount
              }
            : prev
        ));
      } catch {
        // Keep the last visible amount if the estimate cannot be refreshed yet.
      } finally {
        if (active) setEstimatingAmount(false);
      }
    }, 250);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [
    form.account_id,
    form.amount_lookback_months,
    form.amount_strategy,
    form.category_id,
    form.direction,
    form.kind,
    form.merchant,
    form.name,
    form.next_date
  ]);

  function patch(patchValue) {
    setForm((prev) => {
      const next = { ...prev, ...patchValue };
      if (patchValue.kind === 'income') next.direction = 'income';
      if (patchValue.kind && patchValue.kind !== 'income') next.direction = 'expense';
      if (patchValue.kind === 'giving' && !next.category_id) {
        next.category_id = defaultGivingCategoryId(categories);
      }
      if (patchValue.frequency_type && !['monthly', 'semimonthly'].includes(patchValue.frequency_type)) {
        next.schedule_mode = 'interval';
      }
      if (patchValue.frequency_type === 'semimonthly' && prev.schedule_mode === 'interval') {
        next.schedule_mode = 'month_days';
      }
      if (patchValue.frequency_type === 'semimonthly' && next.schedule_mode === 'month_days' && !next.month_day_two) {
        next.month_day_two = '15';
      }
      if (patchValue.frequency_type === 'semimonthly' && next.schedule_mode === 'month_weekdays' && !next.ordinal_two) {
        next.ordinal_two = '3';
      }
      if (patchValue.schedule_mode === 'month_weekdays' && next.frequency_type === 'semimonthly' && !next.ordinal_two) {
        next.ordinal_two = '3';
      }
      if (
        (patchValue.frequency_type === 'monthly' && prev.frequency_type === 'semimonthly') ||
        (patchValue.schedule_mode === 'month_days' && next.frequency_type === 'monthly' && prev.schedule_mode !== 'month_days')
      ) {
        next.month_day_two = '';
      }
      if (
        (patchValue.frequency_type && patchValue.frequency_type !== 'semimonthly') ||
        (patchValue.schedule_mode === 'month_weekdays' && next.frequency_type === 'monthly' && prev.schedule_mode !== 'month_weekdays')
      ) {
        next.ordinal_two = '';
      }
      if (patchValue.amount_strategy === 'history_average' && next.projected_amount !== '' && next.projected_amount != null) {
        next.amount = formatCurrencyInput(next.projected_amount);
      }

      const scheduleChanged = Object.keys(patchValue).some((key) => SCHEDULE_KEYS.has(key));
      const nextRule = buildRuleFromForm(next);
      if (scheduleChanged && nextRule) {
        next.next_date = nextRuleDate(nextRule, todayIso()) || next.next_date;
      }
      return next;
    });
  }

  async function handleSave(close) {
    const payload = payloadFromRecurringForm(form);
    if (!payload.name) {
      setError('Name is required.');
      return;
    }
    if (!payload.next_date) {
      setError('Next date is required.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSave(payload, form);
      close();
    } catch (err) {
      setError(err.message || 'Could not save recurring item.');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>{title || (form.id ? 'Edit Recurring' : 'Add Recurring')}</h3>
          {subtitle && <p className="modal-copy">{subtitle}</p>}

          <div className="upcoming-kind-picker recurring-kind-picker">
            {KIND_OPTIONS.map((option) => (
              <SelectableListItem
                key={option.value}
                active={form.kind === option.value}
                title={option.label}
                subtitle={
                  option.value === 'income'
                    ? 'Money coming in'
                    : option.value === 'giving'
                      ? 'Donation or support'
                      : option.value === 'bill'
                        ? 'Required payment'
                        : 'Subscription or membership'
                }
                onClick={() => patch({ kind: option.value })}
              />
            ))}
          </div>

          <div className="upcoming-editor-grid">
            <label className="field">
              <span>Name</span>
              <input
                value={form.name}
                onChange={(event) => patch({ name: event.target.value })}
                placeholder="Mortgage, paycheck, Netflix"
              />
            </label>

            <label className="field">
              <span>Amount Mode</span>
              <AppSelect
                value={form.amount_strategy}
                options={AMOUNT_STRATEGY_OPTIONS}
                onChange={(value) => patch({ amount_strategy: value })}
                ariaLabel="Amount mode"
              />
            </label>

            <label className="field">
              <span>Amount</span>
              <CurrencyInput
                value={form.amount}
                onChange={(value) => patch({ amount: value })}
                placeholder={estimatingAmount ? 'Estimating...' : '$0.00'}
                disabled={form.amount_strategy === 'history_average'}
                aria-busy={estimatingAmount}
              />
            </label>

            {form.amount_strategy === 'history_average' && (
              <label className="field">
                <span>Lookback</span>
                <AppSelect
                  value={form.amount_lookback_months}
                  options={[3, 6, 9, 12, 18, 24].map((value) => ({
                    value: String(value),
                    label: `${value} Months`
                  }))}
                  onChange={(value) => patch({ amount_lookback_months: value })}
                  ariaLabel="Projection lookback"
                />
              </label>
            )}

            <label className="field">
              <span>Frequency</span>
              <AppSelect
                value={form.frequency_type}
                options={FREQUENCY_OPTIONS}
                onChange={(value) => patch({ frequency_type: value })}
                ariaLabel="Recurring frequency"
              />
            </label>

            {isMonthBased && (
              <label className="field">
                <span>Schedule</span>
                <AppSelect
                  value={form.schedule_mode}
                  options={SCHEDULE_MODE_OPTIONS}
                  onChange={(value) => patch({ schedule_mode: value })}
                  ariaLabel="Recurring schedule"
                />
              </label>
            )}

            {form.frequency_type === 'custom' && (
              <div className="upcoming-custom-frequency">
                <label className="field">
                  <span>Every</span>
                  <input
                    type="number"
                    min="1"
                    value={form.frequency_interval}
                    onChange={(event) => patch({ frequency_interval: event.target.value })}
                  />
                </label>
                <label className="field">
                  <span>Unit</span>
                  <AppSelect
                    value={form.frequency_unit}
                    options={FREQUENCY_UNIT_OPTIONS}
                    onChange={(value) => patch({ frequency_unit: value })}
                    ariaLabel="Frequency unit"
                  />
                </label>
              </div>
            )}

            {isMonthBased && form.schedule_mode === 'month_days' && (
              <div className={`upcoming-custom-frequency ${form.frequency_type === 'semimonthly' ? '' : 'single'}`}>
                <label className="field">
                  <span>Day</span>
                  <AppSelect
                    value={form.month_day_one}
                    options={MONTH_DAY_OPTIONS}
                    onChange={(value) => patch({ month_day_one: value })}
                    ariaLabel="Month day"
                  />
                </label>
                {form.frequency_type === 'semimonthly' && (
                  <label className="field">
                    <span>Second Day</span>
                    <AppSelect
                      value={form.month_day_two}
                      options={[{ value: '', label: 'None' }, ...MONTH_DAY_OPTIONS]}
                      onChange={(value) => patch({ month_day_two: value })}
                      ariaLabel="Second month day"
                    />
                  </label>
                )}
              </div>
            )}

            {isMonthBased && form.schedule_mode === 'month_weekdays' && (
              <div className="upcoming-weekday-grid">
                <label className="field">
                  <span>Week</span>
                  <AppSelect
                    value={form.ordinal_one}
                    options={ORDINAL_OPTIONS}
                    onChange={(value) => patch({ ordinal_one: value })}
                    ariaLabel="First week"
                  />
                </label>
                {form.frequency_type === 'semimonthly' && (
                  <label className="field">
                    <span>Second Week</span>
                    <AppSelect
                      value={form.ordinal_two}
                      options={[{ value: '', label: 'None' }, ...ORDINAL_OPTIONS]}
                      onChange={(value) => patch({ ordinal_two: value })}
                      ariaLabel="Second week"
                    />
                  </label>
                )}
                <label className="field">
                  <span>Weekday</span>
                  <AppSelect
                    value={form.weekday}
                    options={WEEKDAY_OPTIONS}
                    onChange={(value) => patch({ weekday: value })}
                    ariaLabel="Weekday"
                  />
                </label>
              </div>
            )}

            <label className="field">
              <span>Next Date</span>
              <input
                type="date"
                value={form.next_date}
                onChange={(event) => patch({ next_date: event.target.value })}
              />
            </label>

            <label className="field">
              <span>Category</span>
              <AppSelect
                value={form.category_id}
                onChange={(value) => patch({ category_id: value })}
                ariaLabel="Recurring category"
                options={[
                  { value: '', label: 'Uncategorized' },
                  ...categories.map((category) => ({ value: String(category.id), label: category.name }))
                ]}
              />
            </label>

            <label className="field">
              <span>Account</span>
              <AppSelect
                value={form.account_id}
                onChange={(value) => patch({ account_id: value })}
                ariaLabel="Recurring account"
                options={[
                  { value: '', label: 'Any Account' },
                  ...accounts
                    .filter((account) => !account.is_archived)
                    .map((account) => ({ value: String(account.id), label: account.name }))
                ]}
              />
            </label>

            <label className="field upcoming-notes-field">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => patch({ notes: event.target.value })}
                placeholder="Optional note"
              />
            </label>
          </div>

          {projectedNextDate && (
            <p className="recurring-editor-footnote">
              Next matching date: {projectedNextDate}
            </p>
          )}
          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close} disabled={saving}>
              Cancel
            </button>
            <button type="button" className="btn-primary" onClick={() => handleSave(close)} disabled={saving}>
              {saving ? 'Saving...' : saveLabel}
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}
