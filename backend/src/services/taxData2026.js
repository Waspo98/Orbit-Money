export const TAX_YEAR = 2026;

export const TAX_DATA_META = {
  taxYear: TAX_YEAR,
  sourceDate: '2026-02-11',
  reviewedAt: '2026-05-05',
  reviewAfter: '2027-01-15',
  federalSource: 'IRS Revenue Procedure 2025-32 and 2026 Publication 505 tax rate schedules',
  stateSource: 'Tax Foundation 2026 State Individual Income Tax Rates and Brackets',
  coverage:
    'Federal brackets and standard deductions are bundled. State auto-estimates cover no-tax and single-rate ordinary income states; graduated states need a manual state-rate override.'
};

export const FILING_STATUSES = {
  single: {
    label: 'Single',
    standardDeduction: 16100
  },
  married_joint: {
    label: 'Married Filing Jointly',
    standardDeduction: 32200
  },
  married_separate: {
    label: 'Married Filing Separately',
    standardDeduction: 16100
  },
  head_of_household: {
    label: 'Head Of Household',
    standardDeduction: 24150
  }
};

export const FEDERAL_BRACKETS = {
  single: [
    { threshold: 0, rate: 0.10 },
    { threshold: 12400, rate: 0.12 },
    { threshold: 50400, rate: 0.22 },
    { threshold: 105700, rate: 0.24 },
    { threshold: 201775, rate: 0.32 },
    { threshold: 256225, rate: 0.35 },
    { threshold: 640600, rate: 0.37 }
  ],
  married_joint: [
    { threshold: 0, rate: 0.10 },
    { threshold: 24800, rate: 0.12 },
    { threshold: 100800, rate: 0.22 },
    { threshold: 211400, rate: 0.24 },
    { threshold: 403550, rate: 0.32 },
    { threshold: 512450, rate: 0.35 },
    { threshold: 768700, rate: 0.37 }
  ],
  married_separate: [
    { threshold: 0, rate: 0.10 },
    { threshold: 12400, rate: 0.12 },
    { threshold: 50400, rate: 0.22 },
    { threshold: 105700, rate: 0.24 },
    { threshold: 201775, rate: 0.32 },
    { threshold: 256225, rate: 0.35 },
    { threshold: 384350, rate: 0.37 }
  ],
  head_of_household: [
    { threshold: 0, rate: 0.10 },
    { threshold: 17700, rate: 0.12 },
    { threshold: 67450, rate: 0.22 },
    { threshold: 105700, rate: 0.24 },
    { threshold: 201750, rate: 0.32 },
    { threshold: 256200, rate: 0.35 },
    { threshold: 640600, rate: 0.37 }
  ]
};

const graduated = { structure: 'graduated' };
const noTax = { structure: 'none', rate: 0 };

export const STATE_TAX_RATES = {
  AL: { name: 'Alabama', ...graduated },
  AK: { name: 'Alaska', ...noTax },
  AZ: { name: 'Arizona', structure: 'single_rate', rate: 0.025 },
  AR: { name: 'Arkansas', ...graduated },
  CA: { name: 'California', ...graduated },
  CO: { name: 'Colorado', structure: 'single_rate', rate: 0.044 },
  CT: { name: 'Connecticut', ...graduated },
  DE: { name: 'Delaware', ...graduated },
  DC: { name: 'District Of Columbia', ...graduated },
  FL: { name: 'Florida', ...noTax },
  GA: { name: 'Georgia', structure: 'single_rate', rate: 0.0519 },
  HI: { name: 'Hawaii', ...graduated },
  ID: { name: 'Idaho', structure: 'single_rate', rate: 0.053, threshold: { single: 4811, married_joint: 9622 } },
  IL: { name: 'Illinois', structure: 'single_rate', rate: 0.0495 },
  IN: { name: 'Indiana', structure: 'single_rate', rate: 0.0295 },
  IA: { name: 'Iowa', structure: 'single_rate', rate: 0.038 },
  KS: { name: 'Kansas', ...graduated },
  KY: { name: 'Kentucky', structure: 'single_rate', rate: 0.035 },
  LA: { name: 'Louisiana', structure: 'single_rate', rate: 0.03 },
  ME: { name: 'Maine', ...graduated },
  MD: { name: 'Maryland', ...graduated },
  MA: { name: 'Massachusetts', ...graduated },
  MI: { name: 'Michigan', structure: 'single_rate', rate: 0.0425 },
  MN: { name: 'Minnesota', ...graduated },
  MS: { name: 'Mississippi', structure: 'single_rate', rate: 0.04, threshold: { default: 10000 } },
  MO: { name: 'Missouri', ...graduated },
  MT: { name: 'Montana', ...graduated },
  NE: { name: 'Nebraska', ...graduated },
  NV: { name: 'Nevada', ...noTax },
  NH: { name: 'New Hampshire', ...noTax },
  NJ: { name: 'New Jersey', ...graduated },
  NM: { name: 'New Mexico', ...graduated },
  NY: { name: 'New York', ...graduated },
  NC: { name: 'North Carolina', structure: 'single_rate', rate: 0.0399 },
  ND: { name: 'North Dakota', ...graduated },
  OH: { name: 'Ohio', structure: 'single_rate', rate: 0.0275, threshold: { default: 26050 } },
  OK: { name: 'Oklahoma', ...graduated },
  OR: { name: 'Oregon', ...graduated },
  PA: { name: 'Pennsylvania', structure: 'single_rate', rate: 0.0307 },
  RI: { name: 'Rhode Island', ...graduated },
  SC: { name: 'South Carolina', ...graduated },
  SD: { name: 'South Dakota', ...noTax },
  TN: { name: 'Tennessee', ...noTax },
  TX: { name: 'Texas', ...noTax },
  UT: { name: 'Utah', structure: 'single_rate', rate: 0.045 },
  VT: { name: 'Vermont', ...graduated },
  VA: { name: 'Virginia', ...graduated },
  WA: {
    name: 'Washington',
    structure: 'capital_gains_only',
    rate: 0,
    note: 'Washington taxes high capital gains, not ordinary wage income.'
  },
  WV: { name: 'West Virginia', ...graduated },
  WI: { name: 'Wisconsin', ...graduated },
  WY: { name: 'Wyoming', ...noTax }
};

export const STATE_OPTIONS = Object.entries(STATE_TAX_RATES)
  .map(([code, state]) => ({ code, name: state.name }))
  .sort((a, b) => a.name.localeCompare(b.name));
