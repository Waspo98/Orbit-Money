export function parseCurrencyInput(value, fallback = 0) {
  if (value === null || value === undefined) return fallback;
  const cleaned = String(value).replace(/[$,\s]/g, '').trim();
  if (!cleaned || cleaned === '-' || cleaned === '.') return fallback;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function hasCurrencyInputValue(value) {
  return String(value ?? '').replace(/[$,\s]/g, '').trim() !== '';
}

export function formatCurrencyInput(value, { allowNegative = false } = {}) {
  if (value === null || value === undefined || value === '') return '';

  const raw = String(value);
  const negative = allowNegative && raw.trim().startsWith('-');
  const cleaned = raw.replace(/[^0-9.]/g, '');
  if (!cleaned) return negative ? '-' : '';

  const [whole, ...decimalParts] = cleaned.split('.');
  const wholeNumber = Number(whole || 0);
  const dollars = wholeNumber.toLocaleString();
  const hasDecimal = cleaned.includes('.');
  const cents = decimalParts.join('').slice(0, 2);

  return `${negative ? '-' : ''}$${dollars}${hasDecimal ? `.${cents}` : ''}`;
}

export default function CurrencyInput({
  value,
  onChange,
  allowNegative = false,
  ...props
}) {
  return (
    <input
      {...props}
      type="text"
      inputMode="decimal"
      value={value}
      onChange={(event) => {
        onChange(formatCurrencyInput(event.target.value, { allowNegative }));
      }}
    />
  );
}
