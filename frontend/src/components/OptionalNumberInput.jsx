import { useEffect, useState } from 'react';

function finiteNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clamp(value, min, max) {
  let next = value;
  if (Number.isFinite(min)) next = Math.max(min, next);
  if (Number.isFinite(max)) next = Math.min(max, next);
  return next;
}

function cleanDraft(value, integer, allowNegative) {
  const raw = String(value ?? '');
  if (integer) {
    const sign = allowNegative && raw.trim().startsWith('-') ? '-' : '';
    return `${sign}${raw.replace(/\D/g, '')}`;
  }

  const sign = allowNegative && raw.trim().startsWith('-') ? '-' : '';
  const cleaned = raw.replace(/[^0-9.]/g, '');
  const [whole, ...decimalParts] = cleaned.split('.');
  return `${sign}${whole}${decimalParts.length ? `.${decimalParts.join('')}` : ''}`;
}

function displayNumber(value, integer) {
  if (value === '' || value === null || value === undefined) return '';
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return '';
  return String(integer ? Math.trunc(parsed) : parsed);
}

export default function OptionalNumberInput({
  value,
  onChange,
  fallback,
  min,
  max,
  integer = true,
  inputMode,
  onFocus,
  onBlur,
  ...props
}) {
  const minValue = finiteNumber(min, NaN);
  const maxValue = finiteNumber(max, NaN);
  const fallbackValue = clamp(
    finiteNumber(fallback, Number.isFinite(minValue) ? minValue : 0),
    minValue,
    maxValue
  );
  const allowNegative = Number.isFinite(minValue) ? minValue < 0 : true;
  const [focused, setFocused] = useState(false);
  const [draft, setDraft] = useState(() => displayNumber(value, integer));

  useEffect(() => {
    if (!focused) setDraft(displayNumber(value, integer));
  }, [focused, integer, value]);

  function commit(rawDraft) {
    const cleaned = cleanDraft(rawDraft, integer, allowNegative);
    const parsed = finiteNumber(cleaned, fallbackValue);
    const normalized = clamp(integer ? Math.trunc(parsed) : parsed, minValue, maxValue);
    setDraft(displayNumber(normalized, integer));
    onChange?.(normalized);
  }

  return (
    <input
      {...props}
      type="text"
      inputMode={inputMode || (integer ? 'numeric' : 'decimal')}
      value={draft}
      onFocus={(event) => {
        setFocused(true);
        onFocus?.(event);
      }}
      onChange={(event) => {
        const nextDraft = cleanDraft(event.target.value, integer, allowNegative);
        setDraft(nextDraft);
        if (nextDraft !== '' && nextDraft !== '-' && nextDraft !== '.') {
          const parsed = Number(nextDraft);
          if (Number.isFinite(parsed)) onChange?.(integer ? Math.trunc(parsed) : parsed);
        }
      }}
      onBlur={(event) => {
        setFocused(false);
        commit(draft);
        onBlur?.(event);
      }}
    />
  );
}
