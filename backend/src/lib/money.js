export function toFiniteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

export function toNonNegativeNumber(value, fallback = 0) {
  return Math.max(0, toFiniteNumber(value, fallback));
}

export function dollarsToCents(value, fallback = 0) {
  const number = toFiniteNumber(value, fallback);
  const sign = Math.sign(number);
  return sign * Math.round((Math.abs(number) + Number.EPSILON) * 100);
}

export function centsToDollars(value, fallback = 0) {
  const cents = toFiniteNumber(value, fallback);
  return cents / 100;
}

export function roundMoney(value) {
  return centsToDollars(dollarsToCents(value));
}

export function sumMoney(values) {
  return roundMoney(values.reduce((sum, value) => sum + toFiniteNumber(value), 0));
}

export function moneyFieldsToDollars(row, fields) {
  if (!row) return row;
  const copy = { ...row };
  for (const field of fields) {
    if (copy[field] !== null && copy[field] !== undefined) {
      copy[field] = centsToDollars(copy[field]);
    }
  }
  return copy;
}
