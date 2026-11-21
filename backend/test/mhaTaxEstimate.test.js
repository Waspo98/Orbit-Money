import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildHouseholdTaxSnapshot,
  estimateMhaTaxSavings,
  normalizeMhaTaxProfile,
  taxDataMeta
} from '../src/services/mhaTaxEstimate.js';

test('simple mode uses only the manual federal and state rates', () => {
  const estimate = estimateMhaTaxSavings({
    transactionTotal: 10000,
    profile: {
      mode: 'simple',
      simpleFederalRate: 22,
      simpleStateRate: 4.95
    }
  });

  assert.equal(estimate.savings, 2695);
  assert.equal(estimate.federalSavings, 2200);
  assert.equal(estimate.stateSavings, 495);
  assert.equal(estimate.stateRateSource, 'manual');
});

test('household mode calculates federal savings across tax bracket boundaries', () => {
  const estimate = estimateMhaTaxSavings({
    transactionTotal: 20000,
    profile: {
      mode: 'household',
      filingStatus: 'married_joint',
      state: 'IL',
      deductionMode: 'standard',
      taxableIncomeOverride: 110800
    }
  });

  assert.equal(estimate.federalSavings, 3400);
  assert.equal(estimate.stateSavings, 990);
  assert.equal(estimate.savings, 4390);
  assert.equal(estimate.stateRateSource, 'bundled');
});

test('graduated state income taxes require a manual state-rate override', () => {
  const estimate = estimateMhaTaxSavings({
    transactionTotal: 10000,
    profile: {
      mode: 'household',
      filingStatus: 'single',
      state: 'CA',
      deductionMode: 'standard',
      taxableIncomeOverride: 100000
    }
  });

  assert.equal(estimate.stateRate, 0);
  assert.equal(estimate.stateSavings, 0);
  assert.equal(estimate.stateRateSource, 'manual_needed');
  assert.equal(estimate.stateRateConfidence, 'needs_review');
});

test('household taxable income assumes stored paycheck contributions are pre-tax', () => {
  const snapshot = buildHouseholdTaxSnapshot(
    [
      {
        gross_income_annual: 11000000,
        employee_contribution_percent: 10,
        health_premium_per_month: 50000
      }
    ],
    {
      filingStatus: 'married_joint',
      deductionMode: 'standard',
      itemizedDeduction: 0,
      taxableIncomeOverride: null
    }
  );

  assert.equal(snapshot.grossIncome, 110000);
  assert.equal(snapshot.pretaxContributions, 17000);
  assert.equal(snapshot.deduction, 32200);
  assert.equal(snapshot.taxableIncome, 60800);
});

test('tax data metadata marks tables stale after the review date', () => {
  assert.equal(taxDataMeta(new Date('2026-06-01T00:00:00Z')).stale, false);
  assert.equal(taxDataMeta(new Date('2027-01-15T00:00:00Z')).stale, true);
});

test('normalizeMhaTaxProfile preserves caps and yearlyRecords, and estimateMhaTaxSavings honors historical year records', () => {
  const normalized = normalizeMhaTaxProfile({
    mode: 'simple',
    simpleFederalRate: 22,
    simpleStateRate: 4.95,
    designatedCap: 50000,
    fairMarketRentalValue: 55000,
    yearlyRecords: {
      2025: {
        year: 2025,
        effectiveRatePercent: 18.5,
        stateRatePercent: 4.95,
        designatedCap: 25000,
        capsConfigured: true,
        isLocked: true
      }
    }
  });

  assert.equal(normalized.designatedCap, 50000);
  assert.equal(normalized.fairMarketRentalValue, 55000);
  assert.equal(normalized.yearlyRecords['2025'].effectiveRatePercent, 18.5);

  const est2025 = estimateMhaTaxSavings({
    transactionTotal: 30000,
    profile: normalized,
    year: 2025,
    currentYear: 2026
  });
  assert.equal(est2025.excludableAllowance, 25000);
  assert.equal(est2025.savings, 4625);
  assert.equal(est2025.stateRateSource, 'stored_year');

  const est2026 = estimateMhaTaxSavings({
    transactionTotal: 30000,
    profile: normalized,
    year: 2026,
    currentYear: 2026
  });
  assert.equal(est2026.excludableAllowance, 30000);
  assert.equal(est2026.savings, 8085);
});

test('historical year record with useLiveProfile=true uses live rates while preserving per-year caps', () => {
  const normalized = normalizeMhaTaxProfile({
    mode: 'simple',
    simpleFederalRate: 22,
    simpleStateRate: 4.95,
    designatedCap: 50000,
    yearlyRecords: {
      2025: {
        year: 2025,
        effectiveRatePercent: 18.0,
        designatedCap: 20000,
        capsConfigured: true,
        isLocked: false,
        useLiveProfile: true
      }
    }
  });

  assert.equal(normalized.yearlyRecords['2025'].useLiveProfile, true);

  const est2025 = estimateMhaTaxSavings({
    transactionTotal: 30000,
    profile: normalized,
    year: 2025,
    currentYear: 2026
  });
  assert.equal(est2025.excludableAllowance, 20000);
  assert.equal(est2025.savings, 5390);
  assert.equal(est2025.stateRateSource, 'manual');
});

test('trackingMode defaults to mha for legacy profiles and uncapped general mode ignores clergy caps', () => {
  const legacy = normalizeMhaTaxProfile({ mode: 'simple' });
  assert.equal(legacy.trackingMode, 'mha');

  const general = normalizeMhaTaxProfile({
    trackingMode: 'general',
    mode: 'simple',
    simpleFederalRate: 22,
    simpleStateRate: 5,
    designatedCap: 10000,
    fairMarketRentalValue: 12000
  });
  assert.equal(general.trackingMode, 'general');

  const est = estimateMhaTaxSavings({
    transactionTotal: 25000,
    profile: general,
    year: 2026,
    currentYear: 2026
  });
  assert.equal(est.excludableAllowance, 25000);
  assert.equal(est.savings, 6750);
});
