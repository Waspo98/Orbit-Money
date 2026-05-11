import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import AppRangeSlider from '../components/AppRangeSlider.jsx';
import CollapseIndicator from '../components/CollapseIndicator.jsx';
import CurrencyInput, { formatCurrencyInput, parseCurrencyInput } from '../components/CurrencyInput.jsx';
import ExpandingSection from '../components/ExpandingSection.jsx';
import PageHero from '../components/PageHero.jsx';
import SegmentedControl from '../components/SegmentedControl.jsx';
import TapIndicatorText from '../components/TapIndicatorText.jsx';
import { formatCompactCurrency, formatCurrency, formatPercent, parsePercentInput } from '../lib/formatters.js';

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
    color: '#2563eb'
  },
  balanced: {
    label: 'Balanced',
    annualReturn: '7%',
    inflation: '2.5%',
    color: '#059669'
  },
  aggressive: {
    label: 'Aggressive',
    annualReturn: '8.5%',
    inflation: '2.25%',
    color: '#7c3aed'
  }
};

const RETIREMENT_PREFS_STORAGE_KEY = 'orbit-money-retirement-preferences-v1';
const RETIREMENT_CALCULATOR_PREFS_STORAGE_KEY = 'orbit-money-retirement-calculator-preferences-v1';
const MONTHLY_SAVINGS_MAX = 10000;
const DEFAULT_CHART_MAX = 4000000;
const DEFAULT_WITHDRAWAL_RATE = 0.04;
const RETIREMENT_RUNWAY_END_AGE = 95;
const DEFAULT_RETIREMENT_SPENDING_RATIO = 0.8;
const ACCOUNT_MIX_COLORS = ['#10b981', '#38bdf8', '#a78bfa', '#f59e0b', '#f472b6', '#22d3ee'];

function formatMoney(amount, digits = 0) {
  return formatCurrency(amount, { maximumFractionDigits: digits });
}

function chartCeiling(value) {
  const needed = Math.max(DEFAULT_CHART_MAX, Number(value) || 0);
  if (needed <= DEFAULT_CHART_MAX) return DEFAULT_CHART_MAX;
  const increment = needed <= 10000000 ? 500000 : 1000000;
  return Math.ceil(needed / increment) * increment;
}

function readRetirementCalculatorPreferences() {
  try {
    const calculator = JSON.parse(localStorage.getItem(RETIREMENT_CALCULATOR_PREFS_STORAGE_KEY) || '{}');
    const classic = JSON.parse(localStorage.getItem(RETIREMENT_PREFS_STORAGE_KEY) || '{}');
    const runwayEndAge = Number(calculator.runwayEndAge) || RETIREMENT_RUNWAY_END_AGE;
    return {
      retirementAge: Number(calculator.retirementAge || classic.retirementAge) || 67,
      runwayEndAge: Math.min(110, Math.max(70, Math.round(runwayEndAge))),
      scenarioKey: PRESETS[calculator.scenarioKey || classic.presetKey] ? (calculator.scenarioKey || classic.presetKey) : 'balanced'
    };
  } catch {
    return { retirementAge: 67, runwayEndAge: RETIREMENT_RUNWAY_END_AGE, scenarioKey: 'balanced' };
  }
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
  if (monthCount <= 0) return 0;
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
      accountId: Number(account.account_id) || null,
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
    accountId: Number(allocation.account_id) || null,
    name: allocation.account_name,
    owner: null,
    label: classifyAccount(allocation),
    kind: classifyAccount(allocation).toLowerCase(),
    balance: Number(allocation.current_amount) || 0
  }));
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

function chartPathByAge(points, width, height, max, minAge, maxAge) {
  if (!points.length) return '';
  const span = Math.max(1, maxAge - minAge);
  return points.map((point, index) => {
    const x = ((point.age - minAge) / span) * width;
    const y = height - (Math.max(0, Math.min(max, point.amount)) / max) * height;
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join(' ');
}

function areaPath(line, width, height) {
  if (!line) return '';
  return `${line} L ${width} ${height} L 0 ${height} Z`;
}

function valueAtAge(points, age) {
  if (!points.length) return null;
  if (age <= points[0].age) return points[0].amount;
  if (age >= points[points.length - 1].age) return points[points.length - 1].amount;
  for (let index = 1; index < points.length; index += 1) {
    const prev = points[index - 1];
    const next = points[index];
    if (age <= next.age) {
      const span = Math.max(0.0001, next.age - prev.age);
      const ratio = (age - prev.age) / span;
      return prev.amount + (next.amount - prev.amount) * ratio;
    }
  }
  return points[points.length - 1].amount;
}

function monthAge(month, currentAge) {
  const [year, monthNumber] = String(month || '').split('-').map(Number);
  if (!year || !monthNumber) return currentAge;
  const now = new Date();
  const monthDiff = (now.getFullYear() - year) * 12 + (now.getMonth() + 1 - monthNumber);
  return currentAge - (monthDiff / 12);
}

function formatAgeLabel(age) {
  const rounded = Math.round(age * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

function makeDrawdownPoints({ retirementAge, endAge, startingBalance, annualSpending, realReturn }) {
  const monthlyReturn = effectiveMonthlyRate(realReturn);
  let balance = Math.max(0, Number(startingBalance) || 0);
  const monthlySpending = Math.max(0, Number(annualSpending) || 0) / 12;
  const points = [];
  let depletionAge = null;

  for (let age = Math.round(retirementAge); age <= Math.round(endAge); age += 1) {
    points.push({ age, amount: balance });
    for (let month = 0; month < 12; month += 1) {
      balance = Math.max(0, balance * (1 + monthlyReturn) - monthlySpending);
      if (balance <= 0 && depletionAge === null) {
        depletionAge = age + ((month + 1) / 12);
      }
    }
  }

  return {
    points,
    depletionAge,
    finalBalance: points[points.length - 1]?.amount || 0
  };
}

function buildScenarioModel({ preset, currentAge, retirementAge, endAge, runwayEndAge, currentBalance, monthlySavings, annualSpending, withdrawalRate }) {
  const annualReturn = parsePercentInput(preset.annualReturn) / 100;
  const inflation = parsePercentInput(preset.inflation) / 100;
  const realReturn = ((1 + annualReturn) / (1 + inflation)) - 1;
  const safeWithdrawalRate = Math.max(0.001, withdrawalRate);
  const months = Math.max(0, Math.round((retirementAge - currentAge) * 12));
  const targetNestEgg = annualSpending / safeWithdrawalRate;
  const projectedBalance = futureValue(currentBalance, monthlySavings, realReturn, months);
  const retirementMonthlyReturn = effectiveMonthlyRate(realReturn);
  const breakevenAnnualSpending = Math.max(0, projectedBalance * retirementMonthlyReturn * 12);
  const gap = projectedBalance - targetNestEgg;
  const targetFundingGap = Math.max(0, targetNestEgg - projectedBalance);
  const readiness = targetNestEgg > 0 ? Math.min(150, (projectedBalance / targetNestEgg) * 100) : 0;
  const requiredMonthly = requiredMonthlyContribution(currentBalance, targetNestEgg, realReturn, months);
  const points = makeProjectionPoints({ currentAge, endAge: retirementAge, currentBalance, monthlySavings, realReturn });
  const longRangePoints = makeProjectionPoints({ currentAge, endAge, currentBalance, monthlySavings, realReturn });
  const earliest = longRangePoints.find((point) => point.age >= currentAge && point.amount >= targetNestEgg)?.age || null;
  const runway = makeDrawdownPoints({
    retirementAge,
    endAge: runwayEndAge,
    startingBalance: projectedBalance,
    annualSpending,
    realReturn
  });

  return {
    annualReturn,
    breakevenAnnualSpending,
    depletionAge: runway.depletionAge,
    drawdownPoints: runway.points,
    finalAgeBalance: runway.finalBalance,
    gap,
    inflation,
    earliest,
    points,
    projectedBalance,
    readiness,
    realReturn,
    requiredMonthly,
    monthsToRetirement: months,
    savingsGap: months > 0 ? Math.max(0, requiredMonthly - monthlySavings) : targetFundingGap,
    targetFundingGap,
    targetNestEgg,
    withdrawalRate: safeWithdrawalRate
  };
}

function useRetirementCalculatorData() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [household, setHousehold] = useState(null);
  const [goals, setGoals] = useState([]);
  const [history, setHistory] = useState({ accounts: [], history: [] });
  const [warnings, setWarnings] = useState({});
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      setWarnings({});
      try {
        const householdNext = await api.get('/api/household');
        const [goalsResult, historyResult] = await Promise.all([
          api.get('/api/goals?months=24')
            .then((data) => ({ data }))
            .catch((err) => ({ error: err })),
          api.get('/api/household/retirement-history?months=120')
            .then((data) => ({ data }))
            .catch((err) => ({ error: err }))
        ]);
        if (cancelled) return;
        setHousehold(householdNext);
        setGoals(goalsResult.data?.goals || []);
        setHistory(historyResult.data || { accounts: [], history: [] });
        setWarnings({
          goals: goalsResult.error ? 'Goal allocations could not be loaded, so this view is using household-linked retirement accounts only.' : '',
          history: historyResult.error ? 'Retirement account history could not be loaded.' : ''
        });
      } catch (err) {
        if (!cancelled) {
          setHousehold(null);
          setError(err.message || 'Failed to load household retirement inputs');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  return {
    loading,
    error,
    household,
    goals,
    history,
    warnings,
    retry: () => setReloadKey((key) => key + 1)
  };
}

export default function RetirementCalculator() {
  const savedPreferences = useMemo(readRetirementCalculatorPreferences, []);
  const { loading, error, household, goals, history, warnings, retry } = useRetirementCalculatorData();
  const [scenarioKey, setScenarioKey] = useState(savedPreferences.scenarioKey);
  const [expandedCurrentStat, setExpandedCurrentStat] = useState(null);
  const [values, setValues] = useState({
    retirementAge: savedPreferences.retirementAge,
    runwayEndAge: savedPreferences.runwayEndAge,
    monthlySavings: '',
    annualSpending: ''
  });

  useEffect(() => {
    try {
      localStorage.setItem(
        RETIREMENT_CALCULATOR_PREFS_STORAGE_KEY,
        JSON.stringify({
          retirementAge: Number(values.retirementAge) || 67,
          runwayEndAge: Number(values.runwayEndAge) || RETIREMENT_RUNWAY_END_AGE,
          scenarioKey
        })
      );
    } catch {
      /* ignore */
    }
  }, [scenarioKey, values.retirementAge, values.runwayEndAge]);

  const model = useMemo(() => {
    const members = household?.members || [];
    const summary = household?.summary || {};
    const retirementGoal = retirementGoalFrom(goals);
    const accounts = buildAccountSources(household, retirementGoal);
    const currentBalance = accounts.reduce((sum, account) => sum + account.balance, 0);
    const hsaBalance = accounts
      .filter((account) => account.kind === 'hsa' || account.label === 'HSA')
      .reduce((sum, account) => sum + account.balance, 0);
    const memberAges = members.map((member) => ageFromBirthDate(member.birth_date)).filter((age) => age !== null);
    const inferredAge = memberAges.sort((a, b) => b - a)[0] ?? 35;
    const hasAgeSource = memberAges.length > 0;
    const currentAge = Math.max(0, inferredAge);
    const retirementAge = Math.max(currentAge, Number(values.retirementAge) || 67);
    const requestedRunwayEndAge = Number(values.runwayEndAge) || RETIREMENT_RUNWAY_END_AGE;
    const runwayEndAge = Math.min(
      110,
      Math.max(Math.round(retirementAge) + 1, Math.round(requestedRunwayEndAge))
    );
    const employeeAnnual = Number(summary.employee_retirement_annual) || 0;
    const employerAnnual = Number(summary.employer_retirement_annual) || 0;
    const hsaAnnual = members.reduce((sum, member) => sum + (Number(member.hsa_contribution_annual) || 0), 0);
    const plannedMonthly = (employeeAnnual + employerAnnual + hsaAnnual) / 12;
    const grossIncome = Number(summary.gross_income_annual) || 0;
    const netIncome = Number(summary.net_pay_annual) || 0;
    const incomeForSpendingDefault = grossIncome || netIncome;
    const baselineSpending = Math.max(0, Math.round(incomeForSpendingDefault * DEFAULT_RETIREMENT_SPENDING_RATIO));
    const monthlySavings = String(values.monthlySavings).trim() === ''
      ? plannedMonthly
      : parseCurrencyInput(values.monthlySavings);
    const annualSpending = String(values.annualSpending).trim() === ''
      ? baselineSpending
      : parseCurrencyInput(values.annualSpending);
    const endAge = Math.max(95, runwayEndAge, retirementAge + 10);
    const withdrawalRate = DEFAULT_WITHDRAWAL_RATE;
    const scenarios = Object.fromEntries(
      Object.entries(PRESETS).map(([key, preset]) => [
        key,
        buildScenarioModel({
          preset,
          currentAge,
          retirementAge,
          endAge,
          runwayEndAge,
          currentBalance,
          monthlySavings,
          annualSpending,
          withdrawalRate
        })
      ])
    );
    const selected = scenarios[scenarioKey] || scenarios.balanced;
    const rawHistory = Array.isArray(history?.history) ? history.history : [];
    const actualHistory = rawHistory
      .map((point) => ({
        month: point.month,
        age: monthAge(point.month, currentAge),
        amount: Number(point.balance) || 0
      }))
      .filter((point) => point.age <= currentAge + 0.05)
      .sort((a, b) => a.age - b.age);
    if (actualHistory.length === 0 && currentBalance > 0) {
      actualHistory.push({ month: 'Today', age: currentAge, amount: currentBalance });
    }
    const firstActual = actualHistory[0] || null;
    const latestActual = actualHistory[actualHistory.length - 1] || null;
    const actualChange = firstActual && latestActual ? latestActual.amount - firstActual.amount : 0;
    const historyByAccount = new Map(
      (Array.isArray(history?.accounts) ? history.accounts : [])
        .map((account) => [Number(account.id), account])
    );
    const accountDetails = accounts.map((account) => {
      const stats = account.accountId ? historyByAccount.get(Number(account.accountId)) : null;
      const ytdChange = stats?.ytd_change === null || stats?.ytd_change === undefined
        ? null
        : Number(stats.ytd_change) || 0;
      const ytdStartBalance = stats?.ytd_start_balance === null || stats?.ytd_start_balance === undefined
        ? null
        : Number(stats.ytd_start_balance) || 0;
      return {
        ...account,
        ytdChange,
        ytdGrowthPercent: stats?.ytd_growth_percent === null || stats?.ytd_growth_percent === undefined
          ? null
          : Number(stats.ytd_growth_percent) || 0,
        ytdStartBalance
      };
    });
    const ytdStartBalance = accountDetails.reduce((sum, account) => sum + (Number(account.ytdStartBalance) || 0), 0);
    const ytdChange = accountDetails.reduce((sum, account) => sum + (Number(account.ytdChange) || 0), 0);
    const ytdGrowthPercent = ytdStartBalance > 0 ? (ytdChange / ytdStartBalance) * 100 : null;
    const maxRetirementProjection = Math.max(
      currentBalance,
      selected.targetNestEgg,
      ...actualHistory.map((point) => point.amount),
      ...Object.values(scenarios).map((scenario) => scenario.projectedBalance)
    );
    const months = Math.max(0, Math.round((retirementAge - currentAge) * 12));
    const hsaMonthly = hsaAnnual / 12;
    const hsaMonthlyForProjection = Math.min(hsaMonthly, monthlySavings);
    const nonHsaBalance = Math.max(0, currentBalance - hsaBalance);
    const nonHsaMonthly = Math.max(0, monthlySavings - hsaMonthlyForProjection);
    const projectedHsaAtRetirement = futureValue(
      hsaBalance,
      hsaMonthlyForProjection,
      selected.realReturn,
      months
    );
    const projectedNonHsa = futureValue(
      nonHsaBalance,
      nonHsaMonthly,
      selected.realReturn,
      months
    );
    const hsaAccessAge = Math.max(currentAge, 65);
    const bridgeYears = Math.max(0, hsaAccessAge - retirementAge);
    const bridgeNeed = bridgeYears * annualSpending;
    const bridgeGap = bridgeNeed > 0 ? projectedNonHsa - bridgeNeed : projectedNonHsa;
    const needsHsaBridge = retirementAge < hsaAccessAge;
    const hsaBridgeStatus = !needsHsaBridge
      ? 'Not Needed'
      : bridgeGap >= 0
        ? 'Covered'
        : `${formatMoney(Math.abs(bridgeGap))} Short`;
    const hsaBridgeDetail = !needsHsaBridge
      ? 'Retirement age is 65 or later'
      : `${formatMoney(bridgeNeed)} estimated spending before 65`;
    const contributionMembers = members
      .map((member) => {
        const employeeContributionAnnual = Number(member.employee_retirement_annual) || 0;
        const employerContributionAnnual = Number(member.employer_retirement_annual) || 0;
        const memberRows = [
          {
            label: 'Employee Retirement',
            detail: employeeContributionDetail(member, employeeContributionAnnual),
            annual: employeeContributionAnnual
          },
          {
            label: 'Employer Match',
            detail: employerMatchDetail(member, employerContributionAnnual, employeeContributionAnnual),
            annual: employerContributionAnnual
          },
          {
            label: 'HSA Contributions',
            detail: 'Included in retirement',
            annual: Number(member.hsa_contribution_annual) || 0
          }
        ]
          .map((row) => ({ ...row, monthly: row.annual / 12 }))
          .filter((row) => row.monthly > 0);
        const totalAnnual = memberRows.reduce((sum, row) => sum + row.annual, 0);
        return {
          id: member.id,
          name: member.name || 'Household Member',
          rows: memberRows,
          totalAnnual,
          totalMonthly: totalAnnual / 12
        };
      })
      .filter((member) => member.totalMonthly > 0);

    return {
      accountDetails,
      accounts,
      actualChange,
      actualHistory,
      annualSpending,
      baselineSpending,
      bridgeGap,
      bridgeNeed,
      bridgeYears,
      chartMax: chartCeiling(maxRetirementProjection),
      currentAge,
      currentBalance,
      endAge,
      grossIncome,
      hasAgeSource,
      hasIncomeSource: incomeForSpendingDefault > 0,
      hsaAccessAge,
      hsaAnnual,
      hsaBalance,
      hsaBridgeDetail,
      hsaBridgeStatus,
      hsaMonthlyForProjection,
      members,
      monthlySavings,
      nonHsaAtRetirement: projectedNonHsa,
      plannedMonthly,
      projectedHsaAtRetirement,
      retirementAge,
      runwayEndAge,
      savingsRate: grossIncome > 0 ? ((monthlySavings * 12) / grossIncome) * 100 : 0,
      scenarios,
      selected,
      withdrawalRate,
      contributionMembers,
      ytdChange,
      ytdGrowthPercent
    };
  }, [goals, history, household, scenarioKey, values]);

  function update(key, value) {
    setValues((prev) => ({ ...prev, [key]: value }));
  }

  function toggleCurrentStat(key) {
    setExpandedCurrentStat((current) => (current === key ? null : key));
  }

  const selectedPreset = PRESETS[scenarioKey] || PRESETS.balanced;
  const baselineRetirementAge = Number(savedPreferences.retirementAge) || 67;
  const customPlan =
    Math.round(Number(values.retirementAge) || baselineRetirementAge) !== Math.round(baselineRetirementAge) ||
    String(values.monthlySavings).trim() !== '' ||
    String(values.annualSpending).trim() !== '';
  const scenarioDisplayLabel = customPlan ? 'Custom Plan' : selectedPreset.label;
  const planChips = [
    `Market Scenario: ${selectedPreset.label}`,
    `Retirement Age: ${Math.round(model.retirementAge)}`,
    `${formatMoney(model.monthlySavings)}/mo saving`,
    `${formatMoney(model.annualSpending)}/yr spending`
  ];
  const heroStats = [
    { label: 'Scenario', value: scenarioDisplayLabel },
    { label: 'Projected Savings', value: loading ? 'Loading' : formatMoney(model.selected.projectedBalance), tone: model.selected.gap >= 0 ? 'good' : 'caution' }
  ];
  const spendingMax = Math.max(60000, Math.ceil((model.baselineSpending * 1.6 || 100000) / 10000) * 10000);
  const spendingGuideMarkers = model.grossIncome > 0
    ? [
        { key: '55', label: '55%', value: model.grossIncome * 0.55 },
        { key: '80', label: '80%', value: model.grossIncome * 0.8 }
      ]
    : [];
  const spendingSnapTolerance = model.grossIncome > 0 ? model.grossIncome * 0.04 : 0;
  const spendingGuideHint = model.grossIncome > 0
    ? `Fidelity suggests estimating 55-80% of current gross income; this defaults to 80% (${formatMoney(model.baselineSpending)}/yr).`
    : (
        <>
          <span>Fidelity suggests estimating 55-80% of current gross income.</span>
          <Link to="/household" className="retcalc-inline-link">Add Household Income</Link>
        </>
      );
  const retirementAgeHint = (
    <>
      <span>Penalty-free HSA Withdrawal: 65</span>
      <span>Social Security (min, full, max): 62/67/70</span>
      {!model.hasAgeSource && (
        <span>
          Using age 35 until household birthdates are added.{' '}
          <Link to="/household" className="retcalc-inline-link">Edit Household</Link>
        </span>
      )}
    </>
  );
  const runwayDepletionAge = model.selected.depletionAge && model.selected.depletionAge <= model.runwayEndAge
    ? model.selected.depletionAge
    : null;
  const requiredFundingMetric = model.selected.monthsToRetirement > 0
    ? {
        label: 'Required Monthly',
        value: `${formatMoney(model.selected.requiredMonthly)}/mo`,
        detail: model.selected.savingsGap > 0 ? `${formatMoney(model.selected.savingsGap)}/mo gap` : 'Current pace clears target',
        tone: model.selected.savingsGap > 0 ? 'expense' : 'income'
      }
    : {
        label: 'Required Today',
        value: formatMoney(model.selected.targetFundingGap),
        detail: model.selected.targetFundingGap > 0 ? 'Needed now because retirement age is current age' : 'Target already covered',
        tone: model.selected.targetFundingGap > 0 ? 'expense' : 'income'
      };

  return (
    <div className="retirement-calculator-view">
      <PageHero
        id="retirement-calculator-title"
        variant="retirement"
        kicker="Long-Range Planning"
        title="Retirement Calculator"
        subtitle="All projections are shown in today's dollars, so market returns are adjusted for inflation before comparing conservative, balanced, and aggressive paths."
        stats={heroStats}
      />

      {loading ? (
        <div className="center-loading app-page-width">
          <div className="spinner" />
        </div>
      ) : error ? (
        <RetirementInputsError message={error} onRetry={retry} />
      ) : (
        <>
          <section className="retcalc-stage app-page-width" aria-labelledby="retirement-calculator-chart-title">
            <div className="retcalc-stage-copy">
              <div>
                <span className="retcalc-kicker">Selected Scenario</span>
                <h2 id="retirement-calculator-chart-title">{scenarioDisplayLabel}</h2>
                <div className="retcalc-plan-chips" aria-label="Retirement plan assumptions">
                  {planChips.map((chip) => <span key={chip}>{chip}</span>)}
                </div>
              </div>
            </div>

            <div className="retcalc-stage-grid">
              <div className="retcalc-chart-panel">
                {warnings.goals && (
                  <div className="retcalc-soft-note">
                    <span>{warnings.goals}</span>
                    <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={retry}>
                      Retry
                    </button>
                  </div>
                )}
                <RetirementProjectionChart
                  currentAge={model.currentAge}
                  actualHistory={model.actualHistory}
                  chartMax={model.chartMax}
                  currentBalance={model.currentBalance}
                  hsaBridgeStatus={model.hsaBridgeStatus}
                  needsHsaBridge={model.bridgeYears > 0}
                  retirementAge={model.retirementAge}
                  scenarioLabel={scenarioDisplayLabel}
                  scenarios={model.scenarios}
                  selectedKey={scenarioKey}
                  targetNestEgg={model.selected.targetNestEgg}
                />
                <div className="retcalc-scenario-row" aria-label="Choose highlighted retirement scenario">
                  <SegmentedControl
                    className="retcalc-scenario-tabs"
                    ariaLabel="Highlighted retirement scenario"
                    options={Object.entries(PRESETS).map(([key, preset]) => ({
                      value: key,
                      label: preset.label,
                      subtitle: (
                        <>
                          <span>{preset.annualReturn} market</span>
                          <span>{preset.inflation} inflation</span>
                        </>
                      )
                    }))}
                    value={scenarioKey}
                    onChange={setScenarioKey}
                    getOptionSubtitle={(option) => option.subtitle}
                  />
                </div>
              </div>

              <aside className="retcalc-control-panel" aria-label="Retirement calculator controls">
                <div className="retcalc-answer">
                  <span>At age {Math.round(model.retirementAge)}</span>
                  <strong>{formatMoney(model.selected.projectedBalance)}</strong>
                  <em className={model.selected.gap >= 0 ? 'income' : 'expense'}>
                    {model.selected.gap >= 0
                      ? `${formatMoney(model.selected.gap)} above target`
                      : `${formatMoney(Math.abs(model.selected.gap))} below target`}
                  </em>
                </div>

              <div className="retcalc-metric-list">
                <MetricRow
                  label="Retirement Spending Target"
                  value={formatMoney(model.selected.targetNestEgg)}
                  detail={`${formatMoney(model.annualSpending)}/yr spending at ${formatPercent(model.selected.withdrawalRate * 100)} withdrawal`}
                />
                <MetricRow
                  label={requiredFundingMetric.label}
                  value={requiredFundingMetric.value}
                  detail={requiredFundingMetric.detail}
                  tone={requiredFundingMetric.tone}
                />
                  <MetricRow
                    label="Earliest Target Age"
                    value={model.selected.earliest ? String(model.selected.earliest) : `After ${Math.round(model.endAge)}`}
                    detail={`${formatPercent(model.savingsRate)} savings rate`}
                  />
                </div>

                <div className="retcalc-lever-stack">
                  <SliderLever
                    label="Retirement Age"
                    value={model.retirementAge}
                    display={String(Math.round(model.retirementAge))}
                    min={Math.max(40, Math.floor(model.currentAge))}
                    max="80"
                    step="1"
                    hint={retirementAgeHint}
                    onChange={(value) => update('retirementAge', Number(value))}
                  />
                  <MoneyLever
                    label="Monthly Savings"
                    value={model.monthlySavings}
                    max={MONTHLY_SAVINGS_MAX}
                    resetLabel="Current Savings"
                    onReset={() => update('monthlySavings', '')}
                    onChange={(value) => update('monthlySavings', value)}
                  />
                </div>
                <MoneyLever
                  label="Retirement Spending (Today's Dollars)"
                  value={model.annualSpending}
                  suffix="/yr"
                  max={spendingMax}
                  step="1000"
                  hint={spendingGuideHint}
                  markers={spendingGuideMarkers}
                  snapTolerance={spendingSnapTolerance}
                  resetLabel="80% Income"
                  onReset={() => update('annualSpending', '')}
                  onChange={(value) => update('annualSpending', value)}
                  className="retcalc-stage-spending-control"
                />
              </aside>
            </div>
          </section>

          <section className="retcalc-runway-card app-page-width" aria-labelledby="retirement-calculator-runway-title">
            <div className="retcalc-runway-copy">
              <span className="retcalc-kicker">Retirement Runway</span>
              <h3 id="retirement-calculator-runway-title">Will It Last?</h3>
              <p>
                This drawdown keeps spending in today's dollars and uses the market path after retirement.
              </p>
            </div>
            <div className="retcalc-runway-chart-panel">
              <DrawdownChart
                depletionAge={runwayDepletionAge}
                points={model.selected.drawdownPoints}
                retirementAge={model.retirementAge}
                endAge={model.runwayEndAge}
              />
              <div className="retcalc-runway-summary">
                <MoneyLever
                  label="Retirement Spending (Today's Dollars)"
                  value={model.annualSpending}
                  suffix="/yr"
                  max={spendingMax}
                  step="1000"
                  resetLabel="80% Income"
                  onReset={() => update('annualSpending', '')}
                  onChange={(value) => update('annualSpending', value)}
                />
                <SliderLever
                  label="Final Age"
                  value={model.runwayEndAge}
                  display={String(Math.round(model.runwayEndAge))}
                  min={Math.round(model.retirementAge) + 1}
                  max="110"
                  step="1"
                  onChange={(value) => update('runwayEndAge', Number(value))}
                />
                <MetricRow
                  label="Breakeven Spending"
                  value={`${formatMoney(model.selected.breakevenAnnualSpending)}/yr`}
                  detail={model.annualSpending <= model.selected.breakevenAnnualSpending
                    ? `${formatMoney(model.selected.breakevenAnnualSpending - model.annualSpending)}/yr below static savings pace`
                    : `${formatMoney(model.annualSpending - model.selected.breakevenAnnualSpending)}/yr above static savings pace`}
                  tone={model.annualSpending <= model.selected.breakevenAnnualSpending ? 'income' : 'expense'}
                />
                <MetricRow
                  label={`At Age ${Math.round(model.runwayEndAge)}`}
                  value={formatMoney(model.selected.finalAgeBalance)}
                  detail="Projected balance in today's dollars"
                  tone={model.selected.finalAgeBalance > 0 ? 'income' : 'expense'}
                />
                <MetricRow
                  label="Runway"
                  value={runwayDepletionAge ? `Age ${formatAgeLabel(runwayDepletionAge)}` : `Past ${Math.round(model.runwayEndAge)}`}
                  detail={runwayDepletionAge ? 'Projected balance reaches zero' : `Projected balance is positive at age ${Math.round(model.runwayEndAge)}`}
                  tone={runwayDepletionAge ? 'expense' : 'income'}
                />
              </div>
            </div>
          </section>

          <section className="retcalc-current-card app-page-width" aria-label="Current Retirement Stats">
            <div className="retcalc-current-header">
              <span className="retcalc-kicker">Current Retirement Stats</span>
              <TapIndicatorText>Tap to Expand</TapIndicatorText>
            </div>
            <div className="retcalc-current-grid">
              <ExpandableStatCard
                statKey="balance"
                label="Current Balance"
                value={formatMoney(model.currentBalance)}
                detail={`${model.accounts.length} linked retirement account${model.accounts.length === 1 ? '' : 's'}`}
                expanded={expandedCurrentStat === 'balance'}
                showIndicator={false}
                onToggle={toggleCurrentStat}
              >
                <CurrentBalanceDetails accounts={model.accountDetails} />
              </ExpandableStatCard>
              <ExpandableStatCard
                statKey="history"
                label="Actual History"
                value={warnings.history ? 'Unavailable' : model.ytdGrowthPercent === null ? 'No YTD' : formatPercent(model.ytdGrowthPercent)}
                detail={warnings.history || (model.ytdGrowthPercent === null ? 'Add snapshots to build YTD history' : `${formatMoney(model.ytdChange)} YTD balance growth`)}
                tone={warnings.history ? 'expense' : model.ytdChange >= 0 ? 'income' : 'expense'}
                expanded={expandedCurrentStat === 'history'}
                showIndicator={false}
                onToggle={toggleCurrentStat}
              >
                <HistoryDetails accounts={model.accountDetails} unavailable={Boolean(warnings.history)} onRetry={retry} />
              </ExpandableStatCard>
              <ExpandableStatCard
                statKey="savings"
                label="Monthly Savings"
                value={`${formatMoney(model.plannedMonthly)}/mo`}
                detail={model.contributionMembers.length > 0
                  ? `${model.contributionMembers.length} contributing household member${model.contributionMembers.length === 1 ? '' : 's'}`
                  : 'No configured contributions'}
                expanded={expandedCurrentStat === 'savings'}
                showIndicator={false}
                onToggle={toggleCurrentStat}
              >
                <MonthlySavingsDetails members={model.contributionMembers} plannedMonthly={model.plannedMonthly} />
              </ExpandableStatCard>
            </div>
          </section>

          <section className="retcalc-hsa-card app-page-width" aria-labelledby="retirement-calculator-hsa-title">
            <div className="retcalc-hsa-copy">
              <span className="retcalc-kicker">Non-HSA Savings</span>
              <h3 id="retirement-calculator-hsa-title">Before Age 65</h3>
              <p>
                Your projected retirement savings includes your HSA, but there's a tax penalty for withdrawing from your HSA
                for non-medical expenses before age 65. This checks to be sure you have enough non-HSA retirement savings
                to get you to age 65 where you can treat your HSA like a traditional retirement account.
              </p>
            </div>
            <div className="retcalc-hsa-grid">
              <MetricRow
                label="HSA in Projection"
                value={formatMoney(model.projectedHsaAtRetirement)}
                detail={`${formatMoney(model.hsaMonthlyForProjection)}/mo HSA contributions`}
              />
              <MetricRow
                label="Non-HSA at Retirement"
                value={formatMoney(model.nonHsaAtRetirement)}
                detail="Used for the pre-65 check"
              />
              <MetricRow
                label="Pre-65 Status"
                value={model.hsaBridgeStatus}
                detail={model.hsaBridgeDetail}
                tone={model.bridgeGap >= 0 ? 'income' : 'expense'}
              />
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function RetirementInputsError({ message, onRetry }) {
  return (
    <section className="retcalc-load-card app-page-width" aria-labelledby="retirement-calculator-load-error-title">
      <span className="retcalc-kicker">Retirement Inputs</span>
      <h2 id="retirement-calculator-load-error-title">Couldn&apos;t Load Retirement Inputs</h2>
      <p>
        Retirement Calculator needs household income, ages, contributions, and linked retirement accounts before it can calculate safely.
      </p>
      <p className="retcalc-load-card-detail">{message}</p>
      <div className="retcalc-load-actions">
        <button type="button" className="btn-primary" onClick={onRetry}>Retry</button>
        <Link to="/household" className="btn-secondary">Edit Household</Link>
      </div>
    </section>
  );
}

function joinDetail(parts) {
  return parts.filter(Boolean).join(' / ');
}

function formatGrowthPercent(value) {
  if (value === null || value === undefined) return 'No YTD';
  return formatPercent(value, { maximumFractionDigits: 1 });
}

function formatContributionPercent(value) {
  const percent = Number(value) || 0;
  if (percent <= 0) return null;
  return formatPercent(percent, { maximumFractionDigits: percent >= 10 ? 0 : 1 });
}

function employeeContributionDetail(member, annual) {
  const explicitPercent = Number(member.employee_contribution_percent) || 0;
  const grossIncome = Number(member.gross_income_annual) || 0;
  const derivedPercent = grossIncome > 0 ? ((Number(annual) || 0) / grossIncome) * 100 : 0;
  return formatContributionPercent(explicitPercent || derivedPercent) || 'Employee contribution';
}

function employerMatchDetail(member, employerAnnual, employeeAnnual) {
  const matchPercent = Number(member.employer_match_percent) || 0;
  const limitPercent = Number(member.employer_match_limit_percent) || Number(member.employee_contribution_percent) || 0;
  if (matchPercent > 0 && limitPercent > 0) {
    return `${formatContributionPercent(matchPercent)} of first ${formatContributionPercent(limitPercent)}`;
  }

  const grossIncome = Number(member.gross_income_annual) || 0;
  const derivedMatchPercent = Number(employeeAnnual) > 0 ? ((Number(employerAnnual) || 0) / Number(employeeAnnual)) * 100 : 0;
  const derivedLimitPercent = grossIncome > 0 && Number(employeeAnnual) > 0
    ? (Number(employeeAnnual) / grossIncome) * 100
    : 0;
  if (derivedMatchPercent > 0 && derivedLimitPercent > 0) {
    return `${formatContributionPercent(derivedMatchPercent)} of first ${formatContributionPercent(derivedLimitPercent)}`;
  }

  return 'Employer match';
}

function ExpandableStatCard({ statKey, label, value, detail, tone = '', expanded, showIndicator = true, onToggle, children }) {
  return (
    <article className={`retcalc-stat-card ${tone} ${expanded ? 'expanded' : ''}`.trim()}>
      <button
        type="button"
        className={`retcalc-stat-summary ${showIndicator ? 'has-indicator' : ''}`.trim()}
        aria-expanded={expanded}
        aria-controls={`retcalc-stat-detail-${statKey}`}
        onClick={() => onToggle(statKey)}
      >
        <span>{label}</span>
        <strong>{value}</strong>
        <em>{detail}</em>
        <CollapseIndicator
          expanded={expanded}
          label={expanded ? 'Collapse details' : 'Expand details'}
          visible={showIndicator}
        />
      </button>
      <ExpandingSection
        id={`retcalc-stat-detail-${statKey}`}
        expanded={expanded}
        className="retcalc-stat-detail"
        innerClassName="retcalc-stat-detail-inner"
      >
        {children}
      </ExpandingSection>
    </article>
  );
}

function CurrentBalanceDetails({ accounts }) {
  if (!accounts.length) {
    return (
      <p className="retcalc-empty-detail">
        No linked retirement accounts yet.{' '}
        <Link to="/household" className="retcalc-inline-link">Link Retirement Accounts</Link>
      </p>
    );
  }

  const totalBalance = accounts.reduce((sum, account) => sum + Math.max(0, Number(account.balance) || 0), 0);
  const positiveAccounts = accounts.filter((account) => Math.max(0, Number(account.balance) || 0) > 0);

  return (
    <div className="retcalc-account-breakdown">
      {positiveAccounts.length > 0 && (
        <div className="retcalc-account-mix" aria-label="Current balance by linked retirement account">
          {positiveAccounts.map((account, index) => {
            const balance = Math.max(0, Number(account.balance) || 0);
            const width = totalBalance > 0 ? (balance / totalBalance) * 100 : 0;
            const color = ACCOUNT_MIX_COLORS[index % ACCOUNT_MIX_COLORS.length];
            return (
              <span
                key={`${account.id}-mix`}
                style={{ width: `${Math.max(3, width)}%`, '--account-color': color }}
                title={`${account.name || account.label || 'Retirement Account'}: ${formatMoney(balance)}`}
              />
            );
          })}
        </div>
      )}
      <ul className="retcalc-detail-list retcalc-account-list">
        {accounts.map((account, index) => {
          const color = ACCOUNT_MIX_COLORS[index % ACCOUNT_MIX_COLORS.length];
          return (
            <li
              key={account.id}
              className="retcalc-detail-row retcalc-account-row"
              style={{ '--account-color': color }}
            >
              <div>
                <strong>{account.name || account.label || 'Retirement Account'}</strong>
                <em>{joinDetail([account.owner, account.label, account.institution]) || 'Linked retirement account'}</em>
              </div>
              <span>{formatMoney(account.balance)}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function HistoryDetails({ accounts, unavailable, onRetry }) {
  if (unavailable) {
    return (
      <div className="retcalc-empty-detail">
        <span>History unavailable.</span>
        <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={onRetry}>
          Retry
        </button>
      </div>
    );
  }

  if (!accounts.length) {
    return (
      <p className="retcalc-empty-detail">
        No linked retirement accounts yet.{' '}
        <Link to="/household" className="retcalc-inline-link">Link Retirement Accounts</Link>
      </p>
    );
  }

  return (
    <ul className="retcalc-detail-list">
      {accounts.map((account) => {
        const tone = Number(account.ytdChange) < 0 ? 'expense' : 'income';
        return (
          <li key={account.id} className={`retcalc-detail-row ${tone}`}>
            <div>
              <strong>{account.name || account.label || 'Retirement Account'}</strong>
              <em>
                {account.ytdChange === null
                  ? 'No YTD baseline yet'
                  : `${formatMoney(account.ytdChange)} since year start`}
              </em>
            </div>
            <span>{formatGrowthPercent(account.ytdGrowthPercent)}</span>
          </li>
        );
      })}
    </ul>
  );
}

function MonthlySavingsDetails({ members, plannedMonthly }) {
  if (!members.length) {
    return (
      <p className="retcalc-empty-detail">
        No retirement contributions are configured.{' '}
        <Link to="/household" className="retcalc-inline-link">Edit Household</Link>
      </p>
    );
  }

  return (
    <ul className="retcalc-member-contribution-list">
      {members.map((member) => (
        <li key={member.id} className="retcalc-member-contribution-group">
          <div className="retcalc-member-contribution-heading">
            <strong>{member.name}</strong>
            <span>{`${formatMoney(member.totalMonthly)}/mo`}</span>
          </div>
          <ul className="retcalc-detail-list retcalc-member-contribution-items">
            {member.rows.map((row) => (
              <li key={`${member.id}-${row.label}`} className="retcalc-detail-row">
                <div>
                  <strong>{row.label}</strong>
                  <em>{`${formatMoney(row.annual)}/yr - ${row.detail}`}</em>
                </div>
                <span>{`${formatMoney(row.monthly)}/mo`}</span>
              </li>
            ))}
          </ul>
        </li>
      ))}
      <li className="retcalc-detail-row total">
        <div>
          <strong>Total Current Savings</strong>
          <em>Household contribution pace</em>
        </div>
        <span>{`${formatMoney(plannedMonthly)}/mo`}</span>
      </li>
    </ul>
  );
}

function RetirementProjectionChart({
  actualHistory,
  chartMax,
  currentAge,
  currentBalance,
  hsaBridgeStatus,
  needsHsaBridge,
  retirementAge,
  scenarioLabel,
  scenarios,
  selectedKey,
  targetNestEgg
}) {
  const [activeAge, setActiveAge] = useState(null);
  const [isScrubbing, setIsScrubbing] = useState(false);
  const width = 900;
  const height = 380;
  const labels = [chartMax, chartMax / 2, 0];
  const firstActualAge = actualHistory[0]?.age ?? currentAge;
  const minAge = Math.min(firstActualAge, currentAge);
  const maxAge = Math.max(retirementAge, currentAge + 1);
  const selectedLine = chartPathByAge(scenarios[selectedKey]?.points || [], width, height, chartMax, minAge, maxAge);
  const actualLine = chartPathByAge(actualHistory || [], width, height, chartMax, minAge, maxAge);
  const toX = (age) => ((age - minAge) / Math.max(1, maxAge - minAge)) * width;
  const toY = (amount) => height - (Math.max(0, Math.min(chartMax, amount)) / chartMax) * height;
  const todayX = toX(currentAge);
  const retirementX = width;
  const targetY = height - (Math.max(0, Math.min(chartMax, targetNestEgg)) / chartMax) * height;
  const selectedPoint = scenarios[selectedKey]?.points.find((point) => point.age === Math.round(retirementAge));
  const selectedY = selectedPoint
    ? height - (Math.max(0, Math.min(chartMax, selectedPoint.amount)) / chartMax) * height
    : height;
  const labelX = retirementX > width - 180 ? retirementX - 174 : retirementX + 12;
  const labelY = Math.max(34, Math.min(height - 28, selectedY - 18));
  const scrubAge = activeAge ?? retirementAge;
  const scrubX = toX(scrubAge);
  const actualValue = scrubAge <= currentAge ? valueAtAge(actualHistory || [], scrubAge) : null;
  const tooltipRows = Object.entries(PRESETS).map(([key, preset]) => ({
    key,
    label: preset.label,
    color: preset.color,
    value: valueAtAge(scenarios[key]?.points || [], scrubAge)
  }));
  const tooltipWidth = 252;
  const tooltipHeight = actualValue !== null ? 142 : 118;
  const tooltipX = scrubX > width - (tooltipWidth + 20) ? scrubX - (tooltipWidth + 12) : scrubX + 12;
  const tooltipY = 20;

  function updateScrubAge(event) {
    const rect = event.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    setActiveAge(minAge + ratio * (maxAge - minAge));
  }

  function handlePointerDown(event) {
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setIsScrubbing(true);
    updateScrubAge(event);
  }

  function handlePointerMove(event) {
    if (event.pointerType !== 'mouse' && !isScrubbing) return;
    updateScrubAge(event);
  }

  function handlePointerUp(event) {
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    setIsScrubbing(false);
  }

  return (
    <div className="retcalc-chart-wrap">
      <svg
        className="retcalc-chart"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Retirement projection comparison chart"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onPointerLeave={(event) => {
          if (event.pointerType === 'mouse' && !isScrubbing) setActiveAge(null);
        }}
      >
        <defs>
          <linearGradient id="retcalcSelectedArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={PRESETS[selectedKey]?.color || PRESETS.balanced.color} stopOpacity="0.18" />
            <stop offset="100%" stopColor={PRESETS[selectedKey]?.color || PRESETS.balanced.color} stopOpacity="0.02" />
          </linearGradient>
        </defs>

        {labels.map((value) => {
          const y = height - (value / chartMax) * height;
          return (
            <g key={value}>
              <line x1="0" x2={width} y1={y} y2={y} className="retcalc-gridline" />
              <text x="10" y={Math.max(16, y - 8)} className="retcalc-axis-label">
                {formatMoney(value)}
              </text>
            </g>
          );
        })}

        <line x1="0" x2={width} y1={targetY} y2={targetY} className="retcalc-target-line">
          <title>{`Target: ${formatMoney(targetNestEgg)}`}</title>
        </line>
        <text x={width - 12} y={Math.max(18, targetY - 10)} className="retcalc-target-label">
          Target {formatMoney(targetNestEgg)}
        </text>

        <line x1={todayX} x2={todayX} y1="0" y2={height} className="retcalc-today-line" />
        <line x1={retirementX} x2={retirementX} y1="0" y2={height} className="retcalc-retirement-line" />

        {selectedLine && <path d={areaPath(selectedLine, width, height)} fill="url(#retcalcSelectedArea)" />}

        {actualLine && (
          <path d={actualLine} fill="none" className="retcalc-actual-line">
            <title>{`Actual retirement balance history through today: ${formatMoney(currentBalance)}`}</title>
          </path>
        )}

        {Object.entries(PRESETS).map(([key, preset]) => {
          const path = chartPathByAge(scenarios[key]?.points || [], width, height, chartMax, minAge, maxAge);
          const isSelected = key === selectedKey;
          return (
            <path
              key={key}
              d={path}
              fill="none"
              className={`retcalc-scenario-line ${isSelected ? 'selected' : ''}`.trim()}
              style={{ '--scenario-color': preset.color }}
            >
              <title>{`${preset.label}: ${formatMoney(scenarios[key]?.projectedBalance || 0)} at age ${Math.round(retirementAge)}`}</title>
            </path>
          );
        })}

        <circle
          cx={todayX}
          cy={toY(currentBalance)}
          r="7"
          className="retcalc-current-dot"
        >
          <title>{`Current balance: ${formatMoney(currentBalance)}`}</title>
        </circle>

        <g className="retcalc-callout" transform={`translate(${labelX.toFixed(2)} ${labelY.toFixed(2)})`}>
          <rect width="162" height="46" rx="12" />
          <text x="14" y="19">{scenarioLabel}</text>
          <text x="14" y="36">{selectedPoint ? formatMoney(selectedPoint.amount) : formatMoney(0)}</text>
        </g>

        {needsHsaBridge && (
          <g className="retcalc-hsa-chart-badge" transform={`translate(${Math.max(16, retirementX - 176)} 68)`}>
            <rect width="160" height="38" rx="11" />
            <text x="12" y="16">Non-HSA Savings</text>
            <text x="12" y="31">{hsaBridgeStatus}</text>
          </g>
        )}

        {activeAge !== null && (
          <g className="retcalc-scrub">
            <line className="retcalc-scrub-rail" x1={scrubX} x2={scrubX} y1="0" y2={height} />
            <circle className="retcalc-scrub-handle" cx={scrubX} cy={height - 28} r="13" />
            <line className="retcalc-scrub-handle-mark" x1={scrubX - 4} x2={scrubX - 4} y1={height - 34} y2={height - 22} />
            <line className="retcalc-scrub-handle-mark" x1={scrubX + 4} x2={scrubX + 4} y1={height - 34} y2={height - 22} />
            <g transform={`translate(${tooltipX.toFixed(2)} ${tooltipY})`}>
              <rect width={tooltipWidth} height={tooltipHeight} rx="16" />
              <text x="16" y="25">Age {formatAgeLabel(scrubAge)}</text>
              {actualValue !== null && <text x="16" y="50">Actual {formatMoney(actualValue)}</text>}
              {tooltipRows.map((row, index) => (
                <text
                  key={row.key}
                  x="16"
                  y={(actualValue !== null ? 78 : 53) + index * 22}
                  style={{ '--scenario-color': row.color }}
                >
                  {row.label}: {formatMoney(row.value || 0)}
                </text>
              ))}
            </g>
          </g>
        )}

        <text x={Math.max(12, todayX + 10)} y={height - 14} className="retcalc-date-label">Today</text>
        <text x={Math.max(54, Math.min(width - 92, retirementX + 8))} y={height - 14} className="retcalc-date-label">
          Age {Math.round(retirementAge)}
        </text>
      </svg>
      <div className="retcalc-chart-label-row">
        <span>Age {Math.round(currentAge)}</span>
        <span>Age {Math.round(retirementAge)}</span>
      </div>
    </div>
  );
}

function DrawdownChart({ depletionAge, points, retirementAge, endAge }) {
  const width = 640;
  const height = 230;
  const margin = { top: 26, right: 18, bottom: 34, left: 64 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;
  const max = Math.max(1, ...points.map((point) => point.amount)) * 1.08;
  const finalAge = Math.max(Math.round(retirementAge) + 1, Math.round(endAge) || RETIREMENT_RUNWAY_END_AGE);
  const ageSpan = Math.max(1, finalAge - retirementAge);
  const zeroY = margin.top + plotHeight;
  const topY = margin.top;
  const xForAge = (age) => margin.left + ((age - retirementAge) / ageSpan) * plotWidth;
  const yForAmount = (amount) => zeroY - (Math.max(0, Math.min(max, amount)) / max) * plotHeight;
  const path = points.map((point, index) => {
    const x = xForAge(point.age);
    const y = yForAmount(point.amount);
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join(' ');
  const hasDepletion = depletionAge && depletionAge <= finalAge;
  const depletionX = hasDepletion ? xForAge(depletionAge) : 0;
  const depletionLabel = hasDepletion ? `Age ${formatAgeLabel(depletionAge)}` : '';
  const depletionLabelWidth = Math.max(68, depletionLabel.length * 8);
  const depletionLabelX = hasDepletion
    ? Math.max(margin.left + 4, Math.min(width - margin.right - depletionLabelWidth, depletionX - (depletionLabelWidth / 2)))
    : 0;

  return (
    <div className="retcalc-drawdown-wrap">
      <svg className="retcalc-drawdown-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Projected retirement drawdown">
        <line x1={margin.left} x2={width - margin.right} y1={topY} y2={topY} className="retcalc-gridline" />
        <line x1={margin.left} x2={width - margin.right} y1={zeroY} y2={zeroY} className="retcalc-gridline retcalc-zero-line" />
        <line x1={margin.left} x2={margin.left} y1={topY} y2={zeroY} className="retcalc-axis-line" />
        <text x={margin.left} y="15" className="retcalc-drawdown-axis-title">Balance</text>
        <text x={margin.left - 8} y={topY + 4} className="retcalc-drawdown-tick">{formatCompactCurrency(max)}</text>
        <text x={margin.left - 8} y={zeroY + 4} className="retcalc-drawdown-tick">$0</text>
        <path d={path} fill="none" className="retcalc-drawdown-line">
          <title>{depletionAge ? `Projected depletion around age ${formatAgeLabel(depletionAge)}` : `Projected balance is positive at age ${finalAge}`}</title>
        </path>
        {hasDepletion && (
          <g className="retcalc-drawdown-zero-marker">
            <circle
              cx={depletionX}
              cy={zeroY}
              r="6"
              className="retcalc-drawdown-dot"
            >
              <title>{`Projected depletion: age ${formatAgeLabel(depletionAge)}`}</title>
            </circle>
            <rect x={depletionLabelX} y={zeroY - 31} width={depletionLabelWidth} height="24" rx="12" />
            <text x={depletionLabelX + (depletionLabelWidth / 2)} y={zeroY - 14}>{depletionLabel}</text>
          </g>
        )}
      </svg>
      <div className="retcalc-chart-label-row">
        <span>Age {Math.round(retirementAge)}</span>
        <span>Age {finalAge}</span>
      </div>
    </div>
  );
}

function SliderLever({ label, value, display, min, max, step = 1, hint, onChange }) {
  return (
    <label className="retcalc-lever">
      <span>{label}</span>
      <strong>{display}</strong>
      {hint && <em className="retcalc-lever-hint">{hint}</em>}
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

function MoneyLever({
  label,
  value,
  suffix = '/mo',
  max,
  step = 50,
  hint,
  resetLabel,
  markers = [],
  snapTolerance = 0,
  className = '',
  onReset,
  onChange
}) {
  const formattedValue = formatCurrencyInput(value);
  const numericMax = Number(max) || 0;
  const numericValue = parseCurrencyInput(value);
  const visibleMarkers = markers
    .map((marker) => ({
      ...marker,
      value: Number(marker.value) || 0
    }))
    .filter((marker) => marker.value >= 0 && marker.value <= numericMax);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(formattedValue);

  useEffect(() => {
    if (!editing) setDraft(formattedValue);
  }, [editing, formattedValue]);

  function commitDraft() {
    setEditing(false);
    if (!String(draft || '').trim()) {
      setDraft(formattedValue);
      return;
    }
    const formattedDraft = formatCurrencyInput(draft);
    setDraft(formattedDraft);
    onChange(formattedDraft);
  }

  function snapRangeValue(nextValue) {
    if (!visibleMarkers.length || snapTolerance <= 0) return nextValue;
    const closest = visibleMarkers
      .map((marker) => ({ marker, distance: Math.abs(nextValue - marker.value) }))
      .filter((candidate) => candidate.distance <= snapTolerance)
      .sort((a, b) => a.distance - b.distance)[0];
    return closest ? closest.marker.value : nextValue;
  }

  return (
    <div className={`retcalc-lever ${className}`.trim()}>
      <span>{label}</span>
      <strong>{formatMoney(value)}{suffix}</strong>
      {hint && <em className="retcalc-lever-hint">{hint}</em>}
      <AppRangeSlider
        min="0"
        max={numericMax}
        step={step}
        hapticStep={step}
        value={Math.min(numericMax, Math.max(0, numericValue))}
        onChange={(event) => onChange(snapRangeValue(Number(event.target.value) || 0))}
        aria-label={label}
      />
      {visibleMarkers.length > 0 && (
        <div className="retcalc-slider-markers" aria-hidden="true">
          {visibleMarkers.map((marker) => {
            const left = numericMax > 0 ? (marker.value / numericMax) * 100 : 0;
            const active = Math.abs(numericValue - marker.value) <= Math.max(Number(step) || 1, 1);
            return (
              <span
                key={marker.key || marker.label}
                className={`retcalc-slider-marker ${active ? 'active' : ''}`.trim()}
                style={{ '--marker-left': `${left}%` }}
              >
                <em>{marker.label}</em>
              </span>
            );
          })}
        </div>
      )}
      <div className="retcalc-money-entry">
        <CurrencyInput
          value={editing ? draft : formattedValue}
          onFocus={() => {
            setEditing(true);
            setDraft(formattedValue);
          }}
          onChange={(nextValue) => {
            setDraft(nextValue);
            if (String(nextValue || '').trim()) onChange(nextValue);
          }}
          onBlur={commitDraft}
          aria-label={label}
        />
        {resetLabel && (
          <button
            type="button"
            className="btn-secondary retcalc-reset-button"
            onClick={() => {
              setEditing(false);
              setDraft('');
              onReset?.();
            }}
          >
            {resetLabel}
          </button>
        )}
      </div>
    </div>
  );
}

function MetricRow({ label, value, detail, tone = '' }) {
  return (
    <div className={`retcalc-metric-row ${tone}`.trim()}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}
