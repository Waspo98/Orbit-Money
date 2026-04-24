export function toFiniteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

export function toNonNegativeNumber(value, fallback = 0) {
  return Math.max(0, toFiniteNumber(value, fallback));
}

export function roundMoney(value) {
  return Math.round((toFiniteNumber(value) + Number.EPSILON) * 100) / 100;
}

export function sumMoney(values) {
  return roundMoney(values.reduce((sum, value) => sum + toFiniteNumber(value), 0));
}
