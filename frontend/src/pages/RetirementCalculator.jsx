import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppRangeSlider from '../components/AppRangeSlider.jsx';
import CurrencyInput, { formatCurrencyInput, parseCurrencyInput } from '../components/CurrencyInput.jsx';
import PageHero from '../components/PageHero.jsx';
import PercentInput from '../components/PercentInput.jsx';
import { formatCurrency, formatPercent, parsePercentInput } from '../lib/formatters.js';

const ACCOUNT_KIND_LABELS = {
  '401k': '401(k)',
  '403b': '403(b)',
  '457b': '457(b)',
  ira: 'IRA',
  roth_ira: 'Roth IRA',
  sep_ira: 'SEP IRA',
  simple_ira: 'SIMPLE IRA',
  hsa: 'HSA',
  pension: 'Pension',
  other: 'Other'
};

const PRESETS = {
  conservative: {
    label: 'Conservative',
    annualReturn: '5%',
    inflation: '3%',
    withdrawalRate: '3.5%'
  },
  balanced: {
    label: 'Balanced',
    annualReturn: '7%',
    inflation: '2.5%',
    withdrawalRate: '4%'
  },
  growth: {
    label: 'Growth',
    annualReturn: '8.5%',
    inflation: '2.25%',
    withdrawalRate: '4.25%'
  }
};

const MONTHLY_SAVINGS_MAX = 10000;

function formatMoney(amount, digits = 0) {
  return formatCurrency(amount, { maximumFractionDigits: digits });
}

function ageFromBirthDate(date) {
  if (!date) return null;
  const birth = new Date(`${date}T00:00:00`);
  if (Number.isNaN(birth.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const hadBirthday =
    now.getMonth() > birth.getMonth() ||
    (now.getMonth() === birth.getMonth() && now.getDate() >= birth.getDate());
  if (!hadBirthday) age -= 1;
  return age >= 0 ? age : null;
}

function effectiveMonthlyRate(annualReturn) {
  const rate = Number(annualReturn) || 0;
  if (rate <= -1) return -1;
  return (1 + rate) ** (1 / 12) - 1;
}

function futureValue(current, monthly, annualReturn, months) {
  const principal = Number(current) || 0;
  const contribution = Number(monthly) || 0;
  const monthCount = Math.max(0, Number(months) || 0);
  const monthlyReturn = effectiveMonthlyRate(annualReturn);
  if (monthCount <= 0) return principal;
  if (Math.abs(monthlyReturn) < 0.000001) {
    return principal + contribution * monthCount;
  }
  return principal * ((1 + monthlyReturn) ** monthCount) +
    contribution * ((((1 + monthlyReturn) ** monthCount) - 1) / monthlyReturn);
}

function requiredMonthlyContribution(current, target, annualReturn, months) {
  const principal = Number(current) || 0;
  const goal = Number(target) || 0;
  const monthCount = Math.max(0, Number(months) || 0);
  const monthlyReturn = effectiveMonthlyRate(annualReturn);
  if (monthCount <= 0) return Math.max(0, goal - principal);
  if (Math.abs(monthlyReturn) < 0.000001) {
    return Math.max(0, (goal - principal) / monthCount);
  }
  const grownPrincipal = principal * ((1 + monthlyReturn) ** monthCount);
  const factor = (((1 + monthlyReturn) ** monthCount) - 1) / monthlyReturn;
  return Math.max(0, (goal - grownPrincipal) / factor);
}

function classifyAccount(account) {
  const explicit = ACCOUNT_KIND_LABELS[account?.account_kind];
  if (explicit) return explicit;
  const text = String(account?.account_name || account?.name || '').toLowerCase();
  if (text.includes('hsa')) return 'HSA';
  if (text.includes('roth')) return 'Roth IRA';
  if (text.includes('403')) return '403(b)';
  if (text.includes('401')) return '401(k)';
  if (text.includes('ira')) return 'IRA';
  return 'Other';
}

function retirementGoalFrom(goals) {
  return (goals || []).find((goal) => String(goal?.name || '').trim().toLowerCase() === 'retirement') || null;
}

function buildAccountSources(household, retirementGoal) {
  const members = household?.members || [];
  const linkedAccounts = members.flatMap((member) => (
    (member.retirement_accounts || []).map((account) => ({
      id: `member-${member.id}-${account.account_id || account.account_name}`,
      name: account.account_name,
      owner: member.name,
      label: ACCOUNT_KIND_LABELS[account.account_kind] || classifyAccount(account),
      kind: account.account_kind || classifyAccount(account).toLowerCase(),
      balance: Number(account.balance ?? account.estimated_value ?? account.current_balance) || 0
    }))
  ));
  if (linkedAccounts.length > 0) return linkedAccounts;

  return (retirementGoal?.allocations || []).map((allocation) => ({
    id: `goal-${allocation.id}`,
    name: allocation.account_name,
    owner: null,
    label: classifyAccount(allocation),
    kind: classifyAccount(allocation).toLowerCase(),
    balance: Number(allocation.current_amount) || 0
  }));
}

function chartPath(points, width, height, min, max) {
  if (!points.length) return '';
  const safeMax = max > min ? max : min + 1;
  return points.map((point, index) => {
    const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width;
    const y = height - (((point.amount - min) / (safeMax - min)) * height);
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${Math.max(0, Math.min(height, y)).toFixed(2)}`;
  }).join(' ');
}

function areaPath(line, width, height) {
  if (!line) return '';
  return `${line} L ${width} ${height} L 0 ${height} Z`;
}

function makeProjectionPoints({ currentAge, endAge, currentBalance, monthlySavings, realReturn }) {
  const startAge = Math.round(currentAge);
  const lastAge = Math.max(startAge + 1, Math.round(endAge));
  const points = [];
  for (let age = startAge; age <= lastAge; age += 1) {
    points.push({
      age,
      amount: futureValue(currentBalance, monthlySavings, realReturn, (age - currentAge) * 12)
    });
  }
  return points;
}

function mixForAccounts(accounts) {
  const totals = accounts.reduce((items, account) => {
    const key = account.label || 'Other';
    items[key] = (items[key] || 0) + account.balance;
    return items;
  }, {});
  return Object.entries(totals)
    .map(([label, amount]) => ({ label, amount }))
    .sort((a, b) => b.amount - a.amount);
}

export default function RetirementCalculator() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [household, setHousehold] = useState(null);
  const [goals, setGoals] = useState([]);
  const [mode, setMode] = useState('have');
  const [editingAssumptions, setEditingAssumptions] = useState(false);
  const [openSections, setOpenSections] = useState({
    hsa: false,
    household: false,
    accounts: false
  });
  const [values, setValues] = useState({
    currentAge: '',
    retirementAge: 67,
    monthlySavings: '',
    annualSpending: '',
    annualReturn: PRESETS.balanced.annualReturn,
    inflation: PRESETS.balanced.inflation,
    withdrawalRate: PRESETS.balanced.withdrawalRate
  });

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const [householdNext, goalsNext] = await Promise.all([
          api.get('/api/household').catch(() => null),
          api.get('/api/goals?months=24').catch(() => ({ goals: [] }))
        ]);
        if (cancelled) return;
        setHousehold(householdNext);
        setGoals(goalsNext?.goals || []);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Failed to load retirement data');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const model = useMemo(() => {
    const members = household?.members || [];
    const summary = household?.summary || {};
    const retirementGoal = retirementGoalFrom(goals);
    const accounts = buildAccountSources(household, retirementGoal);
    const currentBalance = accounts.reduce((sum, account) => sum + account.balance, 0);
    const hsaBalance = accounts
      .filter((account) => account.kind === 'hsa' || account.label === 'HSA')
      .reduce((sum, account) => sum + account.balance, 0);
    const hsaAnnual = members.reduce(
      (sum, member) => sum + (Number(member.hsa_contribution_annual) || 0),
      0
    );
    const inferredAge =
      members.map((member) => ageFromBirthDate(member.birth_date)).filter((age) => age !== null).sort((a, b) => b - a)[0] ?? 35;
    const employeeAnnual = Number(summary.employee_retirement_annual) || 0;
    const employerAnnual = Number(summary.employer_retirement_annual) || 0;
    const retirementMonthly = (employeeAnnual + employerAnnual) / 12;
    const hsaMonthly = hsaAnnual / 12;
    const plannedMonthly = retirementMonthly + hsaMonthly;
    const grossIncome = Number(summary.gross_income_annual) || 0;
    const netIncome = Number(summary.net_pay_annual) || 0;
    const baselineSpending = Math.max(0, Math.round((netIncome || grossIncome) * 0.8));
    const currentAge = Math.max(0, Number(values.currentAge) || inferredAge);
    const retirementAge = Math.max(currentAge, Number(values.retirementAge) || 67);
    const months = Math.max(0, Math.round((retirementAge - currentAge) * 12));
    const monthlySavings = String(values.monthlySavings).trim() === ''
      ? plannedMonthly
      : parseCurrencyInput(values.monthlySavings);
    const annualSpending = String(values.annualSpending).trim() === ''
      ? baselineSpending
      : parseCurrencyInput(values.annualSpending);
    const annualReturn = parsePercentInput(values.annualReturn) / 100;
    const inflation = parsePercentInput(values.inflation) / 100;
    const realReturn = ((1 + annualReturn) / (1 + inflation)) - 1;
    const withdrawalRate = Math.max(0.001, parsePercentInput(values.withdrawalRate) / 100);
    const targetNestEgg = annualSpending / withdrawalRate;
    const hsaMonthlyForProjection = Math.min(hsaMonthly, monthlySavings);
    const nonHsaBalance = Math.max(0, currentBalance - hsaBalance);
    const nonHsaMonthly = Math.max(0, monthlySavings - hsaMonthlyForProjection);
    const projectedHsaAtRetirement = futureValue(hsaBalance, hsaMonthlyForProjection, realReturn, months);
    const projectedNonHsa = futureValue(nonHsaBalance, nonHsaMonthly, realReturn, months);
    const projectedBalance = projectedNonHsa + projectedHsaAtRetirement;
    const projectedAnnualIncome = projectedBalance * withdrawalRate;
    const gap = projectedBalance - targetNestEgg;
    const readiness = targetNestEgg > 0 ? Math.min(150, (projectedBalance / targetNestEgg) * 100) : 0;
    const requiredMonthly = requiredMonthlyContribution(currentBalance, targetNestEgg, realReturn, months);
    const savingsGap = Math.max(0, requiredMonthly - monthlySavings);
    const savingsRate = grossIncome > 0 ? ((monthlySavings * 12) / grossIncome) * 100 : 0;
    const endAge = Math.max(95, retirementAge + 10);
    const points = makeProjectionPoints({ currentAge, endAge, currentBalance, monthlySavings, realReturn });
    const targetPoints = points.map((point) => ({ age: point.age, amount: targetNestEgg }));
    const chartCeilingSavings = Math.min(
      MONTHLY_SAVINGS_MAX,
      Math.max(3000, plannedMonthly * 3, requiredMonthly * 1.5)
    );
    const chartCeilingProjection = futureValue(
      currentBalance,
      chartCeilingSavings,
      realReturn,
      Math.max(1, (endAge - currentAge) * 12)
    );
    const chartMax = Math.max(targetNestEgg * 1.2, chartCeilingProjection, 1) * 1.08;
    const earliest = points.find((point) => point.age >= currentAge && point.amount >= targetNestEgg)?.age || null;
    const hsaAccessAge = Math.max(currentAge, 65);
    const nonHsaAtRetirement = projectedNonHsa;
    const bridgeYears = Math.max(0, hsaAccessAge - retirementAge);
    const bridgeNeed = bridgeYears * annualSpending;
    const bridgeGap = bridgeNeed > 0 ? nonHsaAtRetirement - bridgeNeed : nonHsaAtRetirement;
    const needsHsaBridge = retirementAge < hsaAccessAge;
    const hsaBridgeStatus = !needsHsaBridge
      ? 'Not Needed'
      : bridgeGap >= 0
        ? 'Covered'
        : `${formatMoney(Math.abs(bridgeGap))} Short`;
    const hsaBridgeDetail = !needsHsaBridge
      ? 'Retirement age is 65 or later'
      : `${formatMoney(bridgeNeed)} estimated spending before 65`;

    return {
      accounts,
      annualReturn,
      annualSpending,
      baselineSpending,
      bridgeGap,
      bridgeNeed,
      bridgeYears,
      chartMax,
      currentAge,
      currentBalance,
      employeeAnnual,
      employerAnnual,
      endAge,
      earliest,
      gap,
      grossIncome,
      hsaAccessAge,
      hsaAnnual,
      hsaBalance,
      hsaBridgeDetail,
      hsaBridgeStatus,
      hsaMonthly,
      hsaMonthlyForProjection,
      inflation,
      members,
      monthlySavings,
      nonHsaAtRetirement,
      plannedMonthly,
      points,
      projectedAnnualIncome,
      projectedBalance,
      projectedHsaAtRetirement,
      projectedNonHsa,
      readiness,
      realReturn,
      requiredMonthly,
      retirementMonthly,
      savingsGap,
      savingsRate,
      targetNestEgg,
      targetPoints,
      retirementAge,
      withdrawalRate
    };
  }, [goals, household, values]);

  function update(key, value) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function applyPreset(presetKey) {
    const preset = PRESETS[presetKey];
    if (!preset) return;
    setValues((prev) => ({
      ...prev,
      annualReturn: preset.annualReturn,
      inflation: preset.inflation,
      withdrawalRate: preset.withdrawalRate
    }));
  }

  function toggleSection(section) {
    setOpenSections((prev) => ({ ...prev, [section]: !prev[section] }));
  }

  const heroStats = [
    { label: 'Projected', value: formatMoney(model.projectedBalance), tone: model.gap >= 0 ? 'income' : 'expense' },
    { label: 'Target', value: formatMoney(model.targetNestEgg) },
    { label: 'Monthly', value: `${formatMoney(model.monthlySavings)}/mo` }
  ];

  if (loading) {
    return (
      <div className="retirement-calculator-view">
        <PageHero
          id="retirement-title"
          variant="retirement"
          kicker="Long-Range Planning"
          title="Retirement Calculator"
          subtitle="Project household retirement balances, savings pace, income needs, and HSA bridge risk."
        />
        <div className="center-loading">
          <div className="spinner" />
        </div>
      </div>
    );
  }

  return (
    <div className="retirement-calculator-view">
      <PageHero
        id="retirement-title"
        variant="retirement"
        kicker="Long-Range Planning"
        title="Retirement Calculator"
        subtitle="Project household retirement balances, savings pace, income needs, and HSA bridge risk."
        stats={heroStats}
      />

      {error && <div className="error">{error}</div>}

      <div className="retcalc-sticky-summary" aria-label="Retirement summary">
        <span>Age {Math.round(model.retirementAge)}</span>
        <strong>{formatMoney(model.projectedBalance)}</strong>
        <em className={model.gap >= 0 ? 'income' : 'expense'}>{Math.round(model.readiness)}% Ready</em>
      </div>

      <section className="dashboard-card retcalc-workbench-card">
        <div className="retcalc-workbench-top">
          <div className="retcalc-answer-main">
            <span className="retcalc-eyebrow">At age {Math.round(model.retirementAge)}</span>
            <strong>{formatMoney(model.projectedBalance)}</strong>
            <em className={model.gap >= 0 ? 'income' : 'expense'}>
              {model.gap >= 0
                ? `${formatMoney(model.gap)} above target`
                : `${formatMoney(Math.abs(model.gap))} below target`}
            </em>
          </div>
          <div className="retcalc-readiness" style={{ '--retcalc-progress': `${Math.min(100, model.readiness)}%` }}>
            <span>{Math.round(model.readiness)}%</span>
            <em>Ready</em>
          </div>
        </div>

        <div className="retcalc-workbench-body">
          <div className="retcalc-control-panel">
            <div className="retcalc-control-bar">
              <button type="button" className="dashboard-card-link button-link" onClick={() => setEditingAssumptions(true)}>
                Edit Assumptions
              </button>
            </div>
            <div className="housing-segmented retcalc-mode-tabs" role="tablist" aria-label="Retirement calculator question">
              {[
                ['have', 'What Will We Have?'],
                ['when', 'When Can We Retire?'],
                ['save', 'How Much To Save?']
              ].map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  className={mode === key ? 'active' : ''}
                  onClick={() => setMode(key)}
                >
                  {label}
                </button>
              ))}
            </div>

            {mode === 'have' && (
              <div className="retcalc-lever-stack">
                <div className="retcalc-question-answer">
                  <span>Projected at {Math.round(model.retirementAge)}</span>
                  <strong>{formatMoney(model.projectedBalance)}</strong>
                  <em className={model.gap >= 0 ? 'income' : 'expense'}>
                    {model.gap >= 0 ? `${formatMoney(model.gap)} above target` : `${formatMoney(Math.abs(model.gap))} below target`}
                  </em>
                </div>
                <SliderLever
                  label="Retirement Age"
                  value={model.retirementAge}
                  display={String(Math.round(model.retirementAge))}
                  min={Math.max(40, Math.floor(model.currentAge))}
                  max="80"
                  step="1"
                  onChange={(value) => update('retirementAge', Number(value))}
                />
                <MoneyLever
                  label={`Monthly Savings (Currently ${formatMoney(model.plannedMonthly)})`}
                  linkTo="/household"
                  linkLabel="Edit Household"
                  value={model.monthlySavings}
                  max={MONTHLY_SAVINGS_MAX}
                  onChange={(value) => update('monthlySavings', formatCurrencyInput(value))}
                />
                <MoneyLever
                  label="Retirement Spending"
                  value={model.annualSpending}
                  suffix="/yr"
                  max={Math.max(60000, Math.ceil((model.baselineSpending * 1.6 || 100000) / 10000) * 10000)}
                  step="1000"
                  onChange={(value) => update('annualSpending', formatCurrencyInput(value))}
                />
              </div>
            )}

            {mode === 'when' && (
              <div className="retcalc-lever-stack">
                <div className="retcalc-question-answer">
                  <span>Earliest target age</span>
                  <strong>{model.earliest ? model.earliest : 'After 95'}</strong>
                  <em>{model.earliest ? `${formatMoney(model.monthlySavings)}/mo reaches the target` : `${formatMoney(model.requiredMonthly)}/mo needed by ${Math.round(model.retirementAge)}`}</em>
                </div>
                <MoneyLever
                  label={`Monthly Savings (Currently ${formatMoney(model.plannedMonthly)})`}
                  linkTo="/household"
                  linkLabel="Edit Household"
                  value={model.monthlySavings}
                  max={MONTHLY_SAVINGS_MAX}
                  onChange={(value) => update('monthlySavings', formatCurrencyInput(value))}
                />
                <MoneyLever
                  label="Retirement Spending"
                  value={model.annualSpending}
                  suffix="/yr"
                  max={Math.max(60000, Math.ceil((model.baselineSpending * 1.6 || 100000) / 10000) * 10000)}
                  step="1000"
                  onChange={(value) => update('annualSpending', formatCurrencyInput(value))}
                />
                <SliderLever
                  label="Current Age"
                  value={model.currentAge}
                  display={String(Math.round(model.currentAge))}
                  min="18"
                  max={Math.max(80, Math.round(model.retirementAge))}
                  step="1"
                  onChange={(value) => update('currentAge', Number(value))}
                />
              </div>
            )}

            {mode === 'save' && (
              <div className="retcalc-lever-stack">
                <div className="retcalc-question-answer">
                  <span>Required savings</span>
                  <strong>{formatMoney(model.requiredMonthly)}/mo</strong>
                  <em className={model.savingsGap > 0 ? 'expense' : 'income'}>
                    {model.savingsGap > 0 ? `${formatMoney(model.savingsGap)}/mo more than current pace` : 'Current pace clears the target'}
                  </em>
                </div>
                <SliderLever
                  label="Retirement Age"
                  value={model.retirementAge}
                  display={String(Math.round(model.retirementAge))}
                  min={Math.max(40, Math.floor(model.currentAge))}
                  max="80"
                  step="1"
                  onChange={(value) => update('retirementAge', Number(value))}
                />
                <MoneyLever
                  label="Retirement Spending"
                  value={model.annualSpending}
                  suffix="/yr"
                  max={Math.max(60000, Math.ceil((model.baselineSpending * 1.6 || 100000) / 10000) * 10000)}
                  step="1000"
                  onChange={(value) => update('annualSpending', formatCurrencyInput(value))}
                />
              </div>
            )}
          </div>

          <div className="retcalc-projection-panel">
            <header className="dashboard-card-header">
              <h3>Projection</h3>
            </header>
            <RetirementChart model={model} />
            <div className="retcalc-metric-grid retcalc-chart-summary">
              <Metric label="Required Monthly" value={`${formatMoney(model.requiredMonthly)}/mo`} detail={model.savingsGap > 0 ? `${formatMoney(model.savingsGap)}/mo gap` : 'Current pace clears target'} tone={model.savingsGap > 0 ? 'expense' : 'income'} />
              <Metric label="Income at Retirement" value={`${formatMoney(model.projectedAnnualIncome)}/yr`} detail={`${formatPercent(model.withdrawalRate * 100)} withdrawal`} />
              <Metric label="Earliest Target Age" value={model.earliest ? String(model.earliest) : 'After 95'} detail={`${formatPercent(model.savingsRate)} savings rate`} />
              <Metric label="Real Return" value={formatPercent(model.realReturn * 100)} detail={`${formatPercent(model.annualReturn * 100)} return minus ${formatPercent(model.inflation * 100)} inflation`} />
            </div>
          </div>
        </div>
      </section>

      <div className="retcalc-audit-list">
        <AuditSection
          title="How HSA Counts"
          summary={`${formatMoney(model.projectedHsaAtRetirement)} projected HSA portion`}
          open={openSections.hsa}
          onToggle={() => toggleSection('hsa')}
        >
          <p className="retcalc-bridge-copy">
            The top projection includes your HSA. Before age 65, this check also separates the HSA from the rest of
            retirement savings, because non-HSA money usually needs to cover general living expenses until HSA access
            is simpler.
          </p>
          <div className="retcalc-bridge-editor">
            <Metric label="Current HSA Inputs" value={formatMoney(model.hsaBalance)} detail={`${formatMoney(model.hsaMonthlyForProjection)}/mo HSA contributions`} />
            <Metric label="HSA in Top Number" value={formatMoney(model.projectedHsaAtRetirement)} detail="Projected HSA portion at retirement" />
            <Metric label="Non-HSA at Retirement" value={formatMoney(model.nonHsaAtRetirement)} detail="Used for the pre-65 bridge check" />
            <Metric
              label="Before Age 65"
              value={model.hsaBridgeStatus}
              detail={model.hsaBridgeDetail}
              tone={model.bridgeGap >= 0 ? 'income' : 'expense'}
            />
          </div>
        </AuditSection>

        <AuditSection
          title="Household Inputs"
          summary={`${formatMoney(model.plannedMonthly)}/mo planned savings`}
          action={<Link to="/household" className="dashboard-card-link">Edit Household</Link>}
          open={openSections.household}
          onToggle={() => toggleSection('household')}
        >
          <div className="retcalc-member-list">
            {model.members.length === 0 ? (
              <p className="subtle">Add household income and retirement details to fill this automatically.</p>
            ) : model.members.map((member) => (
              <div key={member.id} className="retcalc-member-row">
                <div>
                  <strong>{member.name}</strong>
                  <span>{formatPercent(member.employee_contribution_percent)} employee | {formatPercent(member.employer_match_percent)} match</span>
                </div>
                <em>{formatMoney((Number(member.employee_retirement_annual) || 0) + (Number(member.employer_retirement_annual) || 0))}/yr</em>
              </div>
            ))}
          </div>
        </AuditSection>

        <AuditSection
          title="Retirement Accounts"
          summary={`${formatMoney(model.currentBalance)} across ${model.accounts.length} accounts`}
          action={<Link to="/accounts" className="dashboard-card-link">View Accounts</Link>}
          open={openSections.accounts}
          onToggle={() => toggleSection('accounts')}
        >
          {model.accounts.length === 0 ? (
            <p className="subtle">Link retirement accounts from Household to include balances here.</p>
          ) : (
            <>
              <div className="retcalc-account-total">
                <Metric label="Total Tracked" value={formatMoney(model.currentBalance)} detail={`${model.accounts.length} linked accounts`} />
                <Metric label="HSA Portion" value={formatMoney(model.hsaBalance)} detail={`${formatMoney(model.hsaAnnual)}/yr HSA contributions`} tone={model.hsaBalance > 0 || model.hsaAnnual > 0 ? 'income' : ''} />
              </div>
              <div className="retcalc-account-mix" aria-label="Retirement account mix">
                {mixForAccounts(model.accounts).map((item) => {
                  const width = model.currentBalance > 0 ? (item.amount / model.currentBalance) * 100 : 0;
                  return (
                    <span
                      key={item.label}
                      style={{ width: `${Math.max(3, width)}%` }}
                      title={`${item.label}: ${formatMoney(item.amount)}`}
                    />
                  );
                })}
              </div>
              <div className="retcalc-account-list">
                {model.accounts.map((account) => (
                  <div key={account.id} className={`retcalc-account-row ${account.kind === 'hsa' || account.label === 'HSA' ? 'hsa' : ''}`}>
                    <div>
                      <strong>{account.name || account.label}</strong>
                      <span>{account.owner ? `${account.owner} | ` : ''}{account.label}</span>
                    </div>
                    <em>{formatMoney(account.balance)}</em>
                  </div>
                ))}
              </div>
            </>
          )}
        </AuditSection>
      </div>

      {editingAssumptions && (
        <AnimatedModal onClose={() => setEditingAssumptions(false)} size="lg" animation="zoom">
          {({ close }) => (
            <div className="retcalc-assumption-modal">
              <header className="dashboard-card-header">
                <h3>Assumptions</h3>
              </header>
              <div className="retcalc-preset-row">
                {Object.entries(PRESETS).map(([key, preset]) => (
                  <button key={key} type="button" className="btn-secondary" onClick={() => applyPreset(key)}>
                    {preset.label}
                  </button>
                ))}
              </div>
              <div className="retcalc-assumption-grid">
                <PercentLever
                  label="Market Return"
                  value={values.annualReturn}
                  numericValue={model.annualReturn * 100}
                  min="0"
                  max="12"
                  step="0.25"
                  onChange={(value) => update('annualReturn', `${value}%`)}
                  onInput={(value) => update('annualReturn', value)}
                />
                <PercentLever
                  label="Inflation"
                  value={values.inflation}
                  numericValue={model.inflation * 100}
                  min="0"
                  max="8"
                  step="0.25"
                  onChange={(value) => update('inflation', `${value}%`)}
                  onInput={(value) => update('inflation', value)}
                />
                <PercentLever
                  label="Withdrawal Rate"
                  value={values.withdrawalRate}
                  numericValue={model.withdrawalRate * 100}
                  min="2"
                  max="6"
                  step="0.25"
                  onChange={(value) => update('withdrawalRate', `${value}%`)}
                  onInput={(value) => update('withdrawalRate', value)}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-primary" onClick={close}>Done</button>
              </div>
            </div>
          )}
        </AnimatedModal>
      )}
    </div>
  );
}

function SliderLever({ label, value, display, min, max, step = 1, onChange }) {
  return (
    <label className="retcalc-lever">
      <span>{label}</span>
      <strong>{display}</strong>
      <AppRangeSlider
        min={min}
        max={max}
        step={step}
        hapticStep={step}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
      />
    </label>
  );
}

function MoneyLever({ label, value, suffix = '/mo', max, step = 50, linkTo, linkLabel, onChange }) {
  return (
    <div className="retcalc-lever">
      <span className="retcalc-lever-label">
        <span>{label}</span>
        {linkTo && <Link to={linkTo}>{linkLabel}</Link>}
      </span>
      <strong>{formatMoney(value)}{suffix}</strong>
      <AppRangeSlider
        min="0"
        max={max}
        step={step}
        hapticStep={step}
        value={Math.min(max, Math.max(0, value))}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
      />
      <CurrencyInput
        value={formatCurrencyInput(value)}
        onChange={onChange}
        aria-label={label}
      />
    </div>
  );
}

function PercentLever({ label, value, numericValue, min, max, step, onChange, onInput }) {
  return (
    <div className="retcalc-lever">
      <span>{label}</span>
      <strong>{formatPercent(numericValue)}</strong>
      <AppRangeSlider
        min={min}
        max={max}
        step={step}
        hapticStep={step}
        value={numericValue}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
      />
      <PercentInput value={value} onChange={onInput} aria-label={label} />
    </div>
  );
}

function Metric({ label, value, detail, tone = '' }) {
  return (
    <div className={`retcalc-metric ${tone}`.trim()}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}

function AuditSection({ title, summary, action, open, onToggle, children }) {
  return (
    <section className={`dashboard-card retcalc-audit-card ${open ? 'open' : ''}`.trim()}>
      <div className="retcalc-audit-header">
        <button
          type="button"
          className="retcalc-audit-toggle"
          onClick={onToggle}
          aria-expanded={open}
        >
          <span>
            <strong>{title}</strong>
            <em>{summary}</em>
          </span>
          <span className={`collapse-indicator ${open ? 'expanded' : ''}`.trim()} aria-hidden="true">
            <span className="collapse-indicator-chevron" />
          </span>
        </button>
        {action && <div className="retcalc-audit-action">{action}</div>}
      </div>
      {open && <div className="retcalc-audit-body">{children}</div>}
    </section>
  );
}

function RetirementChart({ model }) {
  const width = 760;
  const height = 260;
  const maxValue = model.chartMax;
  const line = chartPath(model.points, width, height, 0, maxValue);
  const targetLine = chartPath(model.targetPoints, width, height, 0, maxValue);
  const area = areaPath(line, width, height);
  const retirementPoint = model.points.find((point) => point.age === Math.round(model.retirementAge)) || model.points[0];
  const retirementX = model.points.length <= 1
    ? 0
    : ((retirementPoint.age - model.points[0].age) / (model.points[model.points.length - 1].age - model.points[0].age)) * width;
  const ticks = [1, 0.5, 0].map((ratio) => ({
    y: height - height * ratio,
    value: maxValue * ratio
  }));

  return (
    <div className="retcalc-chart-wrap">
      <svg className="retcalc-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Retirement projection chart">
        <defs>
          <linearGradient id="retcalcArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.28" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        {ticks.map((tick) => (
          <g key={tick.y}>
            <line x1="0" y1={tick.y} x2={width} y2={tick.y} className="retcalc-chart-gridline" />
            <text x="8" y={Math.max(16, tick.y - 8)} className="retcalc-chart-label">
              {formatMoney(tick.value)}
            </text>
          </g>
        ))}
        <path d={area} fill="url(#retcalcArea)" />
        <path d={targetLine} fill="none" className="retcalc-chart-target" />
        <line x1={retirementX} y1="0" x2={retirementX} y2={height} className="retcalc-chart-retirement" />
        <path d={line} fill="none" className="retcalc-chart-line" />
      </svg>
      <div className="retcalc-chart-label-row">
        <span>Age {Math.round(model.currentAge)}</span>
        <strong>Target {formatMoney(model.targetNestEgg)}</strong>
        <span>Age {Math.round(model.endAge)}</span>
      </div>
    </div>
  );
}
