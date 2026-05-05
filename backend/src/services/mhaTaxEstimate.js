import { roundMoney, toFiniteNumber } from '../lib/money.js';
import {
  FEDERAL_BRACKETS,
  FILING_STATUSES,
  STATE_OPTIONS,
  STATE_TAX_RATES,
  TAX_DATA_META,
  TAX_YEAR
} from './taxData2026.js';

export const MHA_TAX_PROFILE_KEY = 'mha_tax_profile';
const SIMPLE_DEFAULT_FEDERAL_RATE = 22;
const SIMPLE_DEFAULT_STATE_RATE = 4.95;

export function defaultMhaTaxProfile() {
  return {
    mode: 'simple',
    simpleFederalRate: SIMPLE_DEFAULT_FEDERAL_RATE,
    simpleStateRate: SIMPLE_DEFAULT_STATE_RATE,
    filingStatus: 'married_joint',
    state: 'IL',
    deductionMode: 'standard',
    itemizedDeduction: 0,
    taxableIncomeOverride: null,
    stateRateOverride: null
  };
}

function clampPercent(value, fallback = 0) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(0, Math.min(100, number));
}

function cleanMoneyValue(value, fallback = 0) {
  if (value === null || value === undefined || value === '') return fallback;
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(0, number);
}

function cleanOptionalMoney(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  if (!Number.isFinite(number)) return null;
  return Math.max(0, number);
}

export function normalizeMhaTaxProfile(value) {
  const defaults = defaultMhaTaxProfile();
  const source = value && typeof value === 'object' ? value : {};
  const filingStatus = FILING_STATUSES[source.filingStatus] ? source.filingStatus : defaults.filingStatus;
  const state = STATE_TAX_RATES[source.state] ? source.state : defaults.state;
  const deductionMode = source.deductionMode === 'itemized' ? 'itemized' : 'standard';
  const mode = source.mode === 'household' ? 'household' : 'simple';

  return {
    mode,
    simpleFederalRate: clampPercent(source.simpleFederalRate, defaults.simpleFederalRate),
    simpleStateRate: clampPercent(source.simpleStateRate, defaults.simpleStateRate),
    filingStatus,
    state,
    deductionMode,
    itemizedDeduction: cleanMoneyValue(source.itemizedDeduction, defaults.itemizedDeduction),
    taxableIncomeOverride: cleanOptionalMoney(source.taxableIncomeOverride),
    stateRateOverride: cleanOptionalMoney(source.stateRateOverride)
  };
}

export function parseMhaTaxProfile(value) {
  if (!value) return defaultMhaTaxProfile();
  try {
    return normalizeMhaTaxProfile(JSON.parse(value));
  } catch {
    return defaultMhaTaxProfile();
  }
}

export function taxDataMeta(now = new Date()) {
  const reviewAfter = new Date(`${TAX_DATA_META.reviewAfter}T00:00:00Z`);
  return {
    ...TAX_DATA_META,
    stale: Number.isFinite(reviewAfter.getTime()) ? now >= reviewAfter : false
  };
}

export function filingStatusOptions() {
  return Object.entries(FILING_STATUSES).map(([value, item]) => ({
    value,
    label: item.label,
    standardDeduction: item.standardDeduction
  }));
}

export function taxReferencePayload() {
  return {
    taxYear: TAX_YEAR,
    meta: taxDataMeta(),
    filingStatuses: filingStatusOptions(),
    states: STATE_OPTIONS
  };
}

export function federalTaxForTaxableIncome(taxableIncome, filingStatus) {
  const brackets = FEDERAL_BRACKETS[filingStatus] || FEDERAL_BRACKETS.married_joint;
  const income = Math.max(0, toFiniteNumber(taxableIncome));
  let tax = 0;

  for (let index = 0; index < brackets.length; index += 1) {
    const current = brackets[index];
    const next = brackets[index + 1];
    if (income <= current.threshold) break;
    const upper = next ? Math.min(income, next.threshold) : income;
    tax += Math.max(0, upper - current.threshold) * current.rate;
  }

  return roundMoney(tax);
}

export function federalMarginalRate(taxableIncome, filingStatus) {
  const brackets = FEDERAL_BRACKETS[filingStatus] || FEDERAL_BRACKETS.married_joint;
  const income = Math.max(0, toFiniteNumber(taxableIncome));
  return brackets.reduce((rate, bracket) => (
    income >= bracket.threshold ? bracket.rate : rate
  ), brackets[0]?.rate || 0);
}

function centsToDollars(value) {
  return toFiniteNumber(value) / 100;
}

function memberPretaxContributions(member) {
  const gross = centsToDollars(member.gross_income_annual);
  const explicitRetirement = centsToDollars(member.employee_contribution_annual);
  const retirement =
    explicitRetirement > 0
      ? explicitRetirement
      : gross * ((toFiniteNumber(member.employee_contribution_percent) || 0) / 100);

  return (
    retirement +
    centsToDollars(member.hsa_contribution_annual) +
    centsToDollars(member.dependent_care_fsa_annual) +
    centsToDollars(member.other_benefits_annual) +
    centsToDollars(member.health_premium_per_month) * 12
  );
}

export function buildHouseholdTaxSnapshot(members, profile) {
  const grossIncome = roundMoney(
    (members || []).reduce((sum, member) => sum + centsToDollars(member.gross_income_annual), 0)
  );
  const pretaxContributions = roundMoney(
    (members || []).reduce((sum, member) => sum + memberPretaxContributions(member), 0)
  );
  const filing = FILING_STATUSES[profile.filingStatus] || FILING_STATUSES.married_joint;
  const deduction =
    profile.deductionMode === 'itemized'
      ? cleanMoneyValue(profile.itemizedDeduction)
      : filing.standardDeduction;
  const estimatedTaxableIncome = roundMoney(Math.max(0, grossIncome - pretaxContributions - deduction));
  const taxableIncome =
    profile.taxableIncomeOverride !== null
      ? cleanMoneyValue(profile.taxableIncomeOverride)
      : estimatedTaxableIncome;

  return {
    grossIncome,
    pretaxContributions,
    deduction,
    standardDeduction: filing.standardDeduction,
    estimatedTaxableIncome,
    taxableIncome,
    taxableIncomeOverridden: profile.taxableIncomeOverride !== null
  };
}

function thresholdForState(stateConfig, filingStatus) {
  if (!stateConfig?.threshold) return 0;
  if (typeof stateConfig.threshold === 'number') return stateConfig.threshold;
  return stateConfig.threshold[filingStatus] ?? stateConfig.threshold.default ?? 0;
}

export function stateRateForProfile(profile, taxableIncome) {
  if (profile.stateRateOverride !== null) {
    return {
      rate: clampPercent(profile.stateRateOverride) / 100,
      source: 'manual',
      confidence: 'manual',
      note: 'Manual State Rate Override'
    };
  }

  const state = STATE_TAX_RATES[profile.state] || STATE_TAX_RATES.IL;
  if (state.structure === 'none') {
    return {
      rate: 0,
      source: 'bundled',
      confidence: 'high',
      note: 'No State Income Tax'
    };
  }

  if (state.structure === 'capital_gains_only') {
    return {
      rate: 0,
      source: 'bundled',
      confidence: 'medium',
      note: state.note || 'Capital Gains Only'
    };
  }

  if (state.structure === 'single_rate') {
    const threshold = thresholdForState(state, profile.filingStatus);
    return {
      rate: taxableIncome > threshold ? state.rate : 0,
      source: 'bundled',
      confidence: 'medium',
      note: threshold > 0 ? `Single Rate Above ${threshold}` : 'Single Rate State'
    };
  }

  return {
    rate: 0,
    source: 'manual_needed',
    confidence: 'needs_review',
    note: 'Graduated State: Enter Manual State Rate'
  };
}

export function estimateMhaTaxSavings({
  transactionTotal = 0,
  profile = defaultMhaTaxProfile(),
  householdMembers = []
} = {}) {
  const normalized = normalizeMhaTaxProfile(profile);
  const eligibleTotal = roundMoney(Math.max(0, toFiniteNumber(transactionTotal)));

  if (normalized.mode === 'simple') {
    const federalRate = clampPercent(normalized.simpleFederalRate) / 100;
    const stateRate = clampPercent(normalized.simpleStateRate) / 100;
    const effectiveRate = federalRate + stateRate;
    return {
      mode: normalized.mode,
      taxYear: TAX_YEAR,
      eligibleTotal,
      savings: roundMoney(eligibleTotal * effectiveRate),
      effectiveRate,
      federalRate,
      federalSavings: roundMoney(eligibleTotal * federalRate),
      stateRate,
      stateSavings: roundMoney(eligibleTotal * stateRate),
      stateRateSource: 'manual',
      household: null,
      assumptions: ['Simple mode uses the rates you enter.']
    };
  }

  const household = buildHouseholdTaxSnapshot(householdMembers, normalized);
  const taxableBefore = household.taxableIncome;
  const taxableAfter = Math.max(0, taxableBefore - eligibleTotal);
  const federalBefore = federalTaxForTaxableIncome(taxableBefore, normalized.filingStatus);
  const federalAfter = federalTaxForTaxableIncome(taxableAfter, normalized.filingStatus);
  const federalSavings = roundMoney(Math.max(0, federalBefore - federalAfter));
  const federalRate = federalMarginalRate(taxableBefore, normalized.filingStatus);
  const stateRate = stateRateForProfile(normalized, taxableBefore);
  const stateSavings = roundMoney(eligibleTotal * stateRate.rate);
  const savings = roundMoney(federalSavings + stateSavings);
  const effectiveRate = eligibleTotal > 0 ? savings / eligibleTotal : federalRate + stateRate.rate;

  return {
    mode: normalized.mode,
    taxYear: TAX_YEAR,
    eligibleTotal,
    savings,
    effectiveRate,
    federalRate,
    federalSavings,
    stateRate: stateRate.rate,
    stateSavings,
    stateRateSource: stateRate.source,
    stateRateConfidence: stateRate.confidence,
    stateRateNote: stateRate.note,
    household,
    assumptions: [
      'Household mode assumes listed paycheck contributions are pre-tax.',
      'Federal savings are calculated as tax before MHA minus tax after MHA.'
    ]
  };
}
