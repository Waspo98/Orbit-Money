import { useMemo, useState } from 'react';
import PageHero from '../components/PageHero.jsx';
import CurrencyInput, { formatCurrencyInput } from '../components/CurrencyInput.jsx';
import { formatCurrency } from '../lib/formatters.js';

function formatMoney(amount) {
  if (amount === null || amount === undefined) return null;
  return formatCurrency(amount, { maximumFractionDigits: 0 });
}

function parseNumber(value, fallback = 0) {
  if (value === null || value === undefined) return fallback;
  const cleaned = String(value).replace(/[$,%\s,]/g, '');
  if (!cleaned) return fallback;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function hasValue(value) {
  return String(value ?? '').replace(/[$,%\s,]/g, '').trim() !== '';
}

function formatPercentInput(value) {
  const cleaned = String(value || '').replace(/[^0-9.]/g, '');
  if (!cleaned) return '';
  const [whole, decimal = ''] = cleaned.split('.');
  const percent = `${whole || '0'}${cleaned.includes('.') ? `.${decimal.slice(0, 2)}` : ''}`;
  return `${percent}%`;
}

function mortgagePayment(principal, annualRatePercent, years) {
  if (principal <= 0 || years <= 0) return 0;
  const months = years * 12;
  const monthlyRate = annualRatePercent / 100 / 12;
  if (monthlyRate === 0) return principal / months;
  const factor = Math.pow(1 + monthlyRate, months);
  return (principal * monthlyRate * factor) / (factor - 1);
}

const EMPTY_VALUES = {
  selectedMortgageId: '',
  salePrice: '',
  remainingMortgage: '',
  agentCommissionPercent: '',
  sellerClosingPercent: '',
  proratedPropertyTax: '',
  miscRepairs: '',
  purchasePrice: '',
  downPayment: '',
  interestRate: '',
  loanTermYears: '',
  annualPropertyTaxes: '',
  annualHomeInsurance: '',
  monthlyHoa: '',
  buyerClosingPercent: ''
};

const ASSUMPTIONS = {
  agentCommissionPercent: 5,
  sellerClosingPercent: 2,
  proratedPropertyTax: 2000,
  miscRepairs: 1000,
  purchasePrice: 375000,
  interestRate: 6,
  loanTermYears: 30,
  annualPropertyTaxes: 8000,
  annualHomeInsurance: 1500,
  monthlyHoa: 23,
  buyerClosingPercent: 3
};

function calculateSelling(values) {
  const salePrice = hasValue(values.salePrice) ? parseNumber(values.salePrice) : null;
  const remainingMortgage = hasValue(values.remainingMortgage) ? parseNumber(values.remainingMortgage) : null;
  const agentPercent = hasValue(values.agentCommissionPercent) ? parseNumber(values.agentCommissionPercent) : null;
  const sellerClosingPercent = hasValue(values.sellerClosingPercent) ? parseNumber(values.sellerClosingPercent) : null;
  const proratedPropertyTax = hasValue(values.proratedPropertyTax) ? parseNumber(values.proratedPropertyTax) : null;
  const miscRepairs = hasValue(values.miscRepairs) ? parseNumber(values.miscRepairs) : null;

  const agentCommission = salePrice !== null && agentPercent !== null
    ? salePrice * (agentPercent / 100)
    : null;
  const sellerClosing = salePrice !== null && sellerClosingPercent !== null
    ? salePrice * (sellerClosingPercent / 100)
    : null;
  const totalFees =
    agentCommission !== null &&
    sellerClosing !== null &&
    proratedPropertyTax !== null &&
    miscRepairs !== null
      ? agentCommission + sellerClosing + proratedPropertyTax + miscRepairs
      : null;
  const netProceeds =
    salePrice !== null && remainingMortgage !== null && totalFees !== null
      ? salePrice - remainingMortgage - totalFees
      : null;

  return {
    agentCommission,
    sellerClosing,
    totalFees,
    netProceeds
  };
}

function calculateBuying(values) {
  const purchasePrice = hasValue(values.purchasePrice) ? parseNumber(values.purchasePrice) : null;
  const downPayment = hasValue(values.downPayment) ? parseNumber(values.downPayment) : null;
  const interestRate = hasValue(values.interestRate) ? parseNumber(values.interestRate) : null;
  const loanTermYears = hasValue(values.loanTermYears) ? parseNumber(values.loanTermYears) : null;
  const annualPropertyTaxes = hasValue(values.annualPropertyTaxes) ? parseNumber(values.annualPropertyTaxes) : null;
  const annualHomeInsurance = hasValue(values.annualHomeInsurance) ? parseNumber(values.annualHomeInsurance) : null;
  const monthlyHoa = hasValue(values.monthlyHoa) ? parseNumber(values.monthlyHoa) : null;
  const buyerClosingPercent = hasValue(values.buyerClosingPercent) ? parseNumber(values.buyerClosingPercent) : null;

  const buyerClosingCosts = purchasePrice !== null && buyerClosingPercent !== null
    ? purchasePrice * (buyerClosingPercent / 100)
    : null;
  const loanAmount = purchasePrice !== null && downPayment !== null
    ? Math.max(0, purchasePrice - downPayment)
    : null;
  const cashToClose = downPayment !== null && buyerClosingCosts !== null
    ? downPayment + buyerClosingCosts
    : null;
  const principalInterest =
    loanAmount !== null && interestRate !== null && loanTermYears !== null
      ? mortgagePayment(loanAmount, interestRate, loanTermYears)
      : null;
  const monthlyTax = annualPropertyTaxes !== null ? annualPropertyTaxes / 12 : null;
  const monthlyInsurance = annualHomeInsurance !== null ? annualHomeInsurance / 12 : null;
  const totalMonthlyPayment =
    principalInterest !== null &&
    monthlyTax !== null &&
    monthlyInsurance !== null &&
    monthlyHoa !== null
      ? principalInterest + monthlyTax + monthlyInsurance + monthlyHoa
      : null;

  return {
    buyerClosingCosts,
    loanAmount,
    cashToClose,
    principalInterest,
    monthlyTax,
    monthlyInsurance,
    monthlyHoa,
    totalMonthlyPayment
  };
}

export default function HousingCalculator({ accounts = [] }) {
  const [values, setValues] = useState(EMPTY_VALUES);

  const mortgages = useMemo(
    () => accounts.filter((a) => !a.is_archived && a.type === 'mortgage'),
    [accounts]
  );

  const selling = useMemo(() => calculateSelling(values), [values]);
  const buying = useMemo(() => calculateBuying(values), [values]);

  function update(key, value) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function selectMortgage(id) {
    const mortgage = mortgages.find((a) => String(a.id) === String(id));
    if (!mortgage) {
      setValues((prev) => ({ ...prev, selectedMortgageId: '' }));
      return;
    }

    setValues((prev) => {
      const next = {
        ...prev,
        selectedMortgageId: String(id),
        salePrice:
          mortgage.estimated_value == null
            ? prev.salePrice
            : formatCurrencyInput(mortgage.estimated_value),
        remainingMortgage: formatCurrencyInput(Math.abs(Number(mortgage.current_balance) || 0))
      };
      const projected = calculateSelling(next).netProceeds;
      if (projected !== null && projected > 0) {
        next.downPayment = formatCurrencyInput(Math.round(projected));
      }
      return next;
    });
  }

  function useNetProceeds() {
    if (selling.netProceeds !== null) {
      update('downPayment', formatCurrencyInput(Math.max(0, Math.round(selling.netProceeds))));
    }
  }

  function resetCalculator() {
    setValues(EMPTY_VALUES);
  }

  return (
    <div className="housing-view">
      <PageHero
        id="housing-title"
        variant="housing"
        kicker="Home Planning"
        title="Housing Calculator"
        subtitle="Estimate selling proceeds, cash to close, and projected monthly payment."
      />

      <section className="dashboard-card housing-picker">
        <header className="dashboard-card-header">
          <h3>Mortgage</h3>
        </header>
        <div className="housing-picker-row">
          <label className="field">
            <span>Select mortgage</span>
            <select
              value={values.selectedMortgageId}
              onChange={(e) => selectMortgage(e.target.value)}
            >
              <option value="">Manual estimate</option>
              {mortgages.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                  {account.institution ? ` - ${account.institution}` : ''}
                </option>
              ))}
            </select>
          </label>
          <div className="housing-picker-note">
            {mortgages.length === 0 ? (
              <span className="subtle">
                Mark an account as Mortgage and add Estimated Value to prefill this.
              </span>
            ) : (
              <span className="subtle">
                Selection fills sale price from Estimated Value and payoff from balance.
              </span>
            )}
          </div>
        </div>
      </section>

      <div className="housing-grid">
        <section className="dashboard-card housing-panel">
          <header className="dashboard-card-header">
            <h3>Selling</h3>
          </header>
          <div className="housing-form-grid">
            <MoneyField label="Estimated Sale Price" value={values.salePrice} onChange={(v) => update('salePrice', v)} />
            <MoneyField label="Remaining Mortgage Balance" value={values.remainingMortgage} onChange={(v) => update('remainingMortgage', v)} />
            <PercentField label="Agent Commission (%)" value={values.agentCommissionPercent} placeholder="5%" onChange={(v) => update('agentCommissionPercent', v)} />
            <PercentField label="Seller Closing Costs (%)" value={values.sellerClosingPercent} placeholder="2%" onChange={(v) => update('sellerClosingPercent', v)} />
            <MoneyField label="Prorated Property Tax" value={values.proratedPropertyTax} placeholder="$2,000" onChange={(v) => update('proratedPropertyTax', v)} />
            <MoneyField label="Miscellaneous Repairs" value={values.miscRepairs} placeholder="$1,000" onChange={(v) => update('miscRepairs', v)} />
          </div>
          <ResultList
            rows={[
              ['Agent commission', formatMoney(selling.agentCommission)],
              ['Seller closing costs', formatMoney(selling.sellerClosing)],
              ['Total fees', formatMoney(selling.totalFees)],
              ['Net proceeds', formatMoney(selling.netProceeds), true]
            ]}
          />
        </section>

        <section className="dashboard-card housing-panel">
          <header className="dashboard-card-header">
            <h3>Buying</h3>
            <button type="button" className="dashboard-card-link button-link" onClick={useNetProceeds}>
              Use net proceeds
            </button>
          </header>
          <div className="housing-form-grid">
            <MoneyField label="Purchase Price" value={values.purchasePrice} placeholder="$375,000" onChange={(v) => update('purchasePrice', v)} />
            <MoneyField label="Downpayment" value={values.downPayment} onChange={(v) => update('downPayment', v)} />
            <PercentField label="Interest Rate (%)" value={values.interestRate} placeholder="6%" onChange={(v) => update('interestRate', v)} />
            <LoanTermField
              value={values.loanTermYears}
              onChange={(v) => update('loanTermYears', v)}
            />
            <MoneyField label="Property Taxes (Annual)" value={values.annualPropertyTaxes} placeholder="$8,000" onChange={(v) => update('annualPropertyTaxes', v)} />
            <MoneyField label="Home Insurance (Annual)" value={values.annualHomeInsurance} placeholder="$1,500" onChange={(v) => update('annualHomeInsurance', v)} />
            <MoneyField label="HOA Fees (Monthly)" value={values.monthlyHoa} placeholder="$23" onChange={(v) => update('monthlyHoa', v)} />
            <PercentField label="Buyer Closing Costs (%)" value={values.buyerClosingPercent} placeholder="3%" onChange={(v) => update('buyerClosingPercent', v)} />
          </div>
          <ResultList
            rows={[
              [
                <><strong>Loan Amount</strong><span className="housing-result-sub">(Purchase Price - Downpayment)</span></>,
                formatMoney(buying.loanAmount),
                true
              ],
              [
                <><strong>Cash to Close</strong><span className="housing-result-sub">(Downpayment + Buyer Closing Costs)</span></>,
                formatMoney(buying.cashToClose),
                true
              ]
            ]}
          />
        </section>

        <section className="dashboard-card housing-panel housing-monthly">
          <header className="dashboard-card-header">
            <h3>Monthly Payment</h3>
          </header>
          <ResultList
            rows={[
              ['Principal & Interest', formatMoney(buying.principalInterest)],
              ['Monthly Tax', formatMoney(buying.monthlyTax)],
              ['Monthly Insurance', formatMoney(buying.monthlyInsurance)],
              ['HOA Fees', formatMoney(buying.monthlyHoa)],
              ['Projected Monthly Payment', formatMoney(buying.totalMonthlyPayment), true]
            ]}
          />
        </section>
      </div>

      <div className="housing-danger-zone">
        <button type="button" className="btn-danger" onClick={resetCalculator}>
          Reset
        </button>
      </div>
    </div>
  );
}

function MoneyField({ label, value, placeholder = '$0', onChange }) {
  return (
    <label className="field housing-field">
      <span>{label}</span>
      <CurrencyInput
        value={value}
        onChange={onChange}
        placeholder={placeholder}
      />
    </label>
  );
}

function PercentField({ label, value, placeholder = '0%', onChange }) {
  return (
    <label className="field housing-field">
      <span>{label}</span>
      <input
        type="text"
        inputMode="decimal"
        value={value}
        onChange={(e) => onChange(formatPercentInput(e.target.value))}
        placeholder={placeholder}
      />
    </label>
  );
}

function LoanTermField({ value, onChange }) {
  const [customOpen, setCustomOpen] = useState(false);
  const isCustom = hasValue(value) && value !== '30' && value !== '15';
  const showCustom = customOpen || isCustom;

  function choosePreset(nextValue) {
    setCustomOpen(false);
    onChange(nextValue);
  }

  return (
    <div className="field housing-field housing-loan-term">
      <span>Loan Term (Years)</span>
      <div className="housing-segmented" role="group" aria-label="Loan term">
        <button
          type="button"
          className={value === '30' ? 'active' : ''}
          onClick={() => choosePreset('30')}
        >
          30
        </button>
        <button
          type="button"
          className={value === '15' ? 'active' : ''}
          onClick={() => choosePreset('15')}
        >
          15
        </button>
        <button
          type="button"
          className={showCustom ? 'active' : ''}
          onClick={() => {
            setCustomOpen(true);
            if (value === '30' || value === '15') onChange('');
          }}
        >
          Custom
        </button>
      </div>
      <input
        className={`housing-custom-term ${showCustom ? 'visible' : ''}`}
        type="text"
        inputMode="numeric"
        value={showCustom && value !== '30' && value !== '15' ? value : ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Years"
        aria-label="Custom loan term in years"
      />
    </div>
  );
}

function NumberField({ label, value, placeholder = '0', onChange }) {
  return (
    <label className="field housing-field">
      <span>{label}</span>
      <input
        type="text"
        inputMode="numeric"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}

function ResultList({ rows }) {
  return (
    <dl className="housing-results">
      {rows.map(([label, value, strong, tone], index) => (
        <div
          key={typeof label === 'string' ? label : index}
          className={`housing-result-row ${strong ? 'strong' : ''} ${tone || ''}`}
        >
          <dt>{label}</dt>
          <dd>{value === null ? '' : value}</dd>
        </div>
      ))}
    </dl>
  );
}
