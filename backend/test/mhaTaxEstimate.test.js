import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildHouseholdTaxSnapshot,
  estimateMhaTaxSavings,
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
