export function formatCurrency(amount, options = {}) {
  return Number(amount || 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    ...options
  });
}

export function formatCurrencyOr(amount, fallback = '\u2014', options = {}) {
  if (amount === null || amount === undefined || amount === '') return fallback;
  return formatCurrency(amount, options);
}

export function formatSignedCurrency(amount, options = {}) {
  const value = Number(amount || 0);
  if (value === 0) return formatCurrency(0, options);
  return `${value < 0 ? '-' : '+'}${formatCurrency(Math.abs(value), options)}`;
}

export function formatCompactCurrency(amount) {
  const value = Number(amount || 0);
  const abs = Math.abs(value);
  if (abs >= 1_000_000) {
    return `${value < 0 ? '-' : ''}$${(abs / 1_000_000).toFixed(1)}M`;
  }
  if (abs >= 10_000) {
    return `${value < 0 ? '-' : ''}$${(abs / 1_000).toFixed(1)}k`;
  }
  return formatCurrency(value, { maximumFractionDigits: 0 });
}

export function formatPercent(value, { maximumFractionDigits = 1 } = {}) {
  return `${Number(value || 0).toLocaleString(undefined, {
    maximumFractionDigits
  })}%`;
}

export function parsePercentInput(value) {
  const parsed = Number(String(value ?? '').replace(/[^0-9.]/g, ''));
  return Number.isFinite(parsed) ? parsed : 0;
}

export function formatPercentInput(value) {
  const cleaned = String(value ?? '').replace(/[^0-9.]/g, '');
  if (!cleaned) return '';
  return `${cleaned}%`;
}
