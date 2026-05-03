import { useEffect, useMemo, useRef, useState } from 'react';
import {
  DndContext,
  closestCenter
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  verticalListSortingStrategy
} from '@dnd-kit/sortable';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import BrandLogo from '../components/BrandLogo.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { APP_ICON_192 } from '../brandAssets.js';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import { RuleEditor } from '../components/rules/RuleEditor.jsx';
import {
  EditTransactionModal,
  TransactionRow
} from '../components/transactions/TransactionRow.jsx';
import RecurringItemEditor, {
  formFromTransaction,
  recurringFrequencyLabel
} from '../components/upcoming/RecurringItemEditor.jsx';
import {
  formatCompactCurrency,
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import {
  addMonthsToLocalMonth,
  formatLocalMonth,
  formatMonthKeyLabel
} from '../lib/localDate.js';
import { sortCategoriesByName } from '../lib/categorySort.js';
import { USER_PREFERENCE_KEYS } from '../userPreferences.js';

// ============================================================================
// Dashboard - v16
//
// "Where I stand right now" overview. Cards are user-configurable, stacked on
// mobile and flowing into a responsive grid on desktop. This stays frontend
// persisted per account and reuses existing API surfaces.
//
// Account-type groupings (fed into the Accounts card):
//   Assets     = checking + savings + cash + investment + other
//   Credit     = credit   (shown as "owed" - abs value of sum)
//   Loans      = loan (shown as "owed")
//   Real Estate = estimated mortgage home value minus mortgage balance
//   Net worth  = assets/debts plus real estate equity
//
// This assumes balances are stored with the conventional signs (debts
// negative, assets positive) - SimpleFIN's norm. If a bank reports
// otherwise, the displayed groups may look off but net worth is still
// the literal sum.
// ============================================================================

function formatLongDate(d) {
  return d.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric'
  });
}

function timeGreeting(d) {
  const hour = d.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

function firstNameFromDisplayName(value) {
  const text = String(value || '').trim();
  if (!text) return 'there';
  return text.split(/\s+/)[0];
}

function greetingEmoji(d) {
  const hour = d.getHours();
  if (hour < 12) return '🌅';
  if (hour < 17) return '☀️';
  return '🌙';
}

function formatTransactionAmount(amount) {
  return formatSignedCurrency(amount);
}

function formatWholeCurrency(amount) {
  return formatCurrency(Math.round(Number(amount) || 0), {
    maximumFractionDigits: 0,
    minimumFractionDigits: 0
  });
}

function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function useAnimatedNumber(value, duration = 700) {
  const target = Number(value) || 0;
  const [displayValue, setDisplayValue] = useState(() =>
    prefersReducedMotion() ? target : 0
  );
  const previousTargetRef = useRef(target);
  const mountedRef = useRef(false);

  useEffect(() => {
    if (prefersReducedMotion()) {
      previousTargetRef.current = target;
      mountedRef.current = true;
      setDisplayValue(target);
      return undefined;
    }

    const from = mountedRef.current ? previousTargetRef.current : 0;
    previousTargetRef.current = target;
    mountedRef.current = true;

    if (from === target) {
      setDisplayValue(target);
      return undefined;
    }

    let frameId = 0;
    const startedAt = window.performance.now();

    function step(now) {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplayValue(from + (target - from) * eased);

      if (progress < 1) {
        frameId = window.requestAnimationFrame(step);
      }
    }

    frameId = window.requestAnimationFrame(step);
    return () => window.cancelAnimationFrame(frameId);
  }, [duration, target]);

  return displayValue;
}

function AnimatedMoney({
  as: Component = 'span',
  value,
  formatter = formatCurrency,
  className = ''
}) {
  const animatedValue = useAnimatedNumber(value);

  return (
    <Component className={className}>
      {formatter(animatedValue)}
    </Component>
  );
}

function daysInCurrentMonth() {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
}

function currentDayOfMonth() {
  return new Date().getDate();
}

// Account type classification - grouped so the dashboard can show a clean
// breakdown regardless of how many individual accounts the household has.
const ASSET_TYPES = new Set(['checking', 'savings', 'cash']);
const INVESTMENT_TYPES = new Set(['investment']);
const CREDIT_TYPES = new Set(['credit']);
const LOAN_TYPES = new Set(['loan']);
const DASHBOARD_RETIREMENT_PRESETS = {
  conservative: { label: 'Conservative', annualReturn: 0.05, inflation: 0.03 },
  balanced: { label: 'Balanced', annualReturn: 0.07, inflation: 0.025 },
  aggressive: { label: 'Aggressive', annualReturn: 0.085, inflation: 0.0225 }
};

const DASHBOARD_CARD_DEFS = [
  {
    id: 'accounts',
    title: 'Accounts',
    description: 'Net worth and account balance groups.'
  },
  {
    id: 'top-spending',
    title: 'Top Spending',
    description: 'Largest spending categories this month.'
  },
  {
    id: 'this-month',
    title: 'This Month',
    description: 'Income, expenses, and net cash flow.'
  },
  {
    id: 'budget-pulse',
    title: 'Budget Pulse',
    description: 'Budget usage, pace, and categories to watch.'
  },
  {
    id: 'biggest-transactions',
    title: 'Biggest Monthly Transactions',
    description: 'Top 5 current-month expense transactions with dashboard-only hiding.'
  },
  {
    id: 'subscriptions',
    title: 'Subscriptions / Recurring',
    description: 'Saved subscription items from Upcoming.'
  },
  {
    id: 'uncategorized',
    title: 'Uncategorized Transactions',
    description: 'Recent transactions that need a category.'
  },
  {
    id: 'categorize-recent',
    title: 'Categorize Recent Transactions',
    description: 'Swipe through transactions that need a quick category check.'
  },
  {
    id: 'month-comparison',
    title: 'Month vs Last Month',
    description: 'Spending, income, and net change.'
  },
  {
    id: 'goals-progress',
    title: 'Goals Progress',
    description: 'Overall progress across active goals.'
  },
  {
    id: 'goal-focus',
    title: 'Goal Focus',
    description: 'One goal that deserves attention.'
  },
  {
    id: 'retirement',
    title: 'Retirement Snapshot',
    description: 'Linked retirement balance and contributions.'
  },
  {
    id: 'mha',
    title: 'MHA Tracker Summary',
    description: 'Year-to-date MHA eligible spending.'
  },
  {
    id: 'upcoming',
    title: 'Upcoming',
    description: 'Saved bills, subscriptions, and income.'
  },
  {
    id: 'mortgage',
    title: 'Mortgage Snapshot',
    description: 'Home value, balance, and equity.'
  },
  {
    id: 'recent-activity',
    title: 'Recent Transactions',
    description: 'Latest activity with full transaction actions.'
  }
];

const DEFAULT_DASHBOARD_CARD_IDS = DASHBOARD_CARD_DEFS.map((card) => card.id);

function normalizeDashboardLayout(saved) {
  const fallback = DEFAULT_DASHBOARD_CARD_IDS.map((id) => ({ id, visible: true }));
  if (!Array.isArray(saved)) return fallback;

  const known = new Set(DEFAULT_DASHBOARD_CARD_IDS);
  const rows = [];
  for (const item of saved) {
    const id = typeof item === 'string' ? item : item?.id;
    if (!known.has(id) || rows.some((row) => row.id === id)) continue;
    rows.push({ id, visible: item?.visible !== false });
  }
  for (const id of DEFAULT_DASHBOARD_CARD_IDS) {
    if (!rows.some((row) => row.id === id)) rows.push({ id, visible: true });
  }
  return rows;
}

function normalizeIdArray(value) {
  return Array.isArray(value)
    ? Array.from(new Set(value.map(Number).filter(Number.isFinite))).slice(-1000)
    : [];
}

function normalizeGoalFocusId(value) {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : null;
}

function normalizeRetirementPreferences(value) {
  const prefs = value && typeof value === 'object' ? value : {};
  return {
    retirementAge: Number(prefs.retirementAge) || 67,
    presetKey: DASHBOARD_RETIREMENT_PRESETS[prefs.presetKey] ? prefs.presetKey : 'balanced'
  };
}

function groupAccountBalances(accounts) {
  let cash = 0;
  let investments = 0;
  let credit = 0;
  let loans = 0;
  let realEstate = 0;
  let other = 0;

  for (const a of accounts) {
    if (a.is_archived) continue;
    const b = Number(a.current_balance) || 0;

    if (ASSET_TYPES.has(a.type)) cash += b;
    else if (INVESTMENT_TYPES.has(a.type)) investments += b;
    else if (CREDIT_TYPES.has(a.type)) credit += b;
    else if (LOAN_TYPES.has(a.type)) loans += b;
    else if (a.type === 'mortgage') {
      const estimatedValue = Number(a.estimated_value) || 0;
      realEstate += estimatedValue - Math.abs(b);
    }
    else other += b;
  }

  const net = cash + investments + credit + loans + realEstate + other;

  return { cash, investments, credit, loans, realEstate, other, net };
}

function parseDateValue(value) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatShortDate(value) {
  const date = parseDateValue(value);
  if (!date) return '';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function formatShortMonth(value) {
  if (!value) return '';
  return formatMonthKeyLabel(value).replace(/\s\d{4}$/, '');
}

function transactionMonthUrl(month, extra = '') {
  return `/transactions?date_from=${month}-01${extra}`;
}

function monthEndDate(month) {
  const [year, monthNumber] = String(month || '').split('-').map(Number);
  if (!year || !monthNumber) return '';
  const lastDay = new Date(year, monthNumber, 0).getDate();
  return `${month}-${String(lastDay).padStart(2, '0')}`;
}

function kindLabel(kind) {
  if (kind === 'bill') return 'Bill';
  if (kind === 'income') return 'Income';
  return 'Subscription';
}

function frequencyLabel(item) {
  return recurringFrequencyLabel(item);
}

function ageFromBirthDate(date) {
  if (!date) return null;
  const birth = parseDateValue(date);
  if (!birth) return null;
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

function retirementProjection(householdData, preferences) {
  const summary = householdData?.summary || {};
  const members = householdData?.members || [];
  const currentBalance = Number(summary.retirement_account_balance) || 0;
  const monthlyContributions =
    ((Number(summary.employee_retirement_annual) || 0) +
    (Number(summary.employer_retirement_annual) || 0)) / 12;
  const currentAge =
    members
      .map((member) => ageFromBirthDate(member.birth_date))
      .filter((age) => age !== null)
      .sort((a, b) => b - a)[0] ?? 35;
  const retirementAge = Math.max(currentAge, Number(preferences?.retirementAge) || 67);
  const preset = DASHBOARD_RETIREMENT_PRESETS[preferences?.presetKey] || DASHBOARD_RETIREMENT_PRESETS.balanced;
  const realReturn = ((1 + preset.annualReturn) / (1 + preset.inflation)) - 1;
  const months = Math.max(0, Math.round((retirementAge - currentAge) * 12));

  return {
    currentBalance,
    monthlyContributions,
    projectedBalance: futureValue(currentBalance, monthlyContributions, realReturn, months),
    preset,
    retirementAge
  };
}

// ============================================================================
// Main page
// ============================================================================

export default function Dashboard({
  accounts = [],
  categories = [],
  mhaTrackerEnabled = false,
  userPreferences = {},
  onUserPreferenceChange
}) {
  const navigate = useNavigate();
  const dashboardLayoutPreference =
    userPreferences[USER_PREFERENCE_KEYS.dashboardLayout];
  const hiddenBiggestPreference =
    userPreferences[USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions];
  const handledReviewPreference =
    userPreferences[USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions];
  const goalFocusPreference =
    userPreferences[USER_PREFERENCE_KEYS.dashboardGoalFocusId];
  const retirementPreference =
    userPreferences[USER_PREFERENCE_KEYS.dashboardRetirement];

  const [budgetData, setBudgetData] = useState(null);
  const [previousBudgetData, setPreviousBudgetData] = useState(null);
  const [recent, setRecent] = useState([]);
  const [dashboardTransactions, setDashboardTransactions] = useState([]);
  const [uncategorizedData, setUncategorizedData] = useState(null);
  const [upcomingData, setUpcomingData] = useState(null);
  const [goalsData, setGoalsData] = useState(null);
  const [householdData, setHouseholdData] = useState(null);
  const [mhaData, setMhaData] = useState(null);
  const [userName, setUserName] = useState('there');
  const [dashboardLayout, setDashboardLayout] = useState(() =>
    normalizeDashboardLayout(dashboardLayoutPreference)
  );
  const [customizing, setCustomizing] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [hiddenBiggestTransactionIds, setHiddenBiggestTransactionIds] = useState(() =>
    normalizeIdArray(hiddenBiggestPreference)
  );
  const [handledReviewTransactionIds, setHandledReviewTransactionIds] = useState(() =>
    normalizeIdArray(handledReviewPreference)
  );
  const [goalFocusId, setGoalFocusId] = useState(() =>
    normalizeGoalFocusId(goalFocusPreference)
  );
  const [retirementPreferences, setRetirementPreferences] = useState(() =>
    normalizeRetirementPreferences(retirementPreference)
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const month = formatLocalMonth();
  const previousMonth = addMonthsToLocalMonth(month, -1);

  async function loadDashboard() {
    setLoading(true);
    setError('');
    try {
      const optional = (path) => api.get(path).catch(() => null);
      const [b, t, prevBudget, txns, uncategorized, upcoming, goals, household, mha, me] = await Promise.all([
        api.get(`/api/budgets?month=${month}`),
        api.get('/api/transactions?limit=10&page=1'),
        optional(`/api/budgets?month=${previousMonth}`),
        optional(`/api/transactions?limit=50&page=1&type=expense&date_from=${month}-01&date_to=${monthEndDate(month)}&include_ignored=0&include_transfers=0&exclude_credit_card_payments=1&sort=abs_amount_desc`),
        optional(`/api/transactions?limit=5&page=1&categories=${
          categories.find((category) => String(category.name || '').toLowerCase() === 'uncategorized')?.id || 'uncategorized'
        }&include_ignored=0&include_transfers=0&sort=date_desc`),
        optional('/api/upcoming'),
        optional('/api/goals?months=12'),
        optional('/api/household'),
        optional('/api/mha'),
        optional('/api/auth/me')
      ]);
      setBudgetData(b);
      setRecent(t.items || []);
      setPreviousBudgetData(prevBudget);
      setDashboardTransactions(txns?.items || []);
      setUncategorizedData({
        ...uncategorized,
        categoryId: categories.find(
          (category) => String(category.name || '').toLowerCase() === 'uncategorized'
        )?.id || null
      });
      setUpcomingData(upcoming);
      setGoalsData(goals);
      setHouseholdData(household);
      setMhaData(mha);
      setUserName(firstNameFromDisplayName(me?.displayName || me?.username || me?.email));
    } catch (err) {
      setError(err.message || 'Failed to load dashboard');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setDashboardLayout(normalizeDashboardLayout(dashboardLayoutPreference));
  }, [dashboardLayoutPreference]);

  useEffect(() => {
    setHiddenBiggestTransactionIds(normalizeIdArray(hiddenBiggestPreference));
  }, [hiddenBiggestPreference]);

  useEffect(() => {
    setHandledReviewTransactionIds(normalizeIdArray(handledReviewPreference));
  }, [handledReviewPreference]);

  useEffect(() => {
    setGoalFocusId(normalizeGoalFocusId(goalFocusPreference));
  }, [goalFocusPreference]);

  useEffect(() => {
    setRetirementPreferences(normalizeRetirementPreferences(retirementPreference));
  }, [retirementPreference]);

  const now = new Date();
  const totals = useMemo(() => groupAccountBalances(accounts), [accounts]);
  const activeAccountCount = useMemo(
    () => accounts.filter((a) => !a.is_archived).length,
    [accounts]
  );

  const accountById = useMemo(
    () => new Map(accounts.map((a) => [a.id, a])),
    [accounts]
  );
  const categoryById = useMemo(
    () => new Map(categories.map((c) => [c.id, c])),
    [categories]
  );

  // --- Top-N spending this month --------------------------------------------
  // Reuses budgetData: combines `budgeted` + `unbudgeted` items and sorts by
  // spent desc. Each item already has category + spent.
  const topSpending = useMemo(() => {
    if (!budgetData) return [];
    const combined = [
      ...(budgetData.budgeted || []),
      ...(budgetData.unbudgeted || [])
    ].filter((i) => i.spent > 0);
    combined.sort((a, b) => b.spent - a.spent);
    return combined.slice(0, 7);
  }, [budgetData]);

  // --- Budget pulse: attention-needing categories ---------------------------
  const budgetAttention = useMemo(() => {
    if (!budgetData) return [];
    const budgeted = budgetData.budgeted || [];
    const overBudget = budgeted.filter((b) => b.spent > b.amount && b.amount > 0);
    const closeToOver = budgeted
      .filter((b) => b.amount > 0 && b.spent <= b.amount && b.spent / b.amount >= 0.85)
      .sort((a, b) => b.spent / b.amount - a.spent / a.amount);

    const picks = [];
    for (const b of overBudget.sort(
      (a, b) => b.spent / b.amount - a.spent / a.amount
    )) {
      picks.push({ ...b, status: 'over' });
      if (picks.length >= 5) break;
    }
    for (const b of closeToOver) {
      if (picks.length >= 5) break;
      picks.push({ ...b, status: 'warning' });
    }
    return picks;
  }, [budgetData]);

  const summary = budgetData?.summary;
  const totalSpent = Number(summary?.total_spent || 0);
  const overallPercent =
    summary && summary.total_budgeted > 0
      ? (totalSpent / summary.total_budgeted) * 100
      : null;

  const dayOfMonth = currentDayOfMonth();
  const totalDays = daysInCurrentMonth();
  const daysRemaining = Math.max(0, totalDays - dayOfMonth);
  const dailySpendPace =
    summary && summary.total_budgeted > 0 && daysRemaining > 0
      ? (summary.total_budgeted - totalSpent) / daysRemaining
      : null;
  const previousSummary = previousBudgetData?.summary;
  const biggestTransactions = useMemo(() => {
    const hidden = new Set(hiddenBiggestTransactionIds);
    return dashboardTransactions
      .filter((txn) => !hidden.has(txn.id))
      .sort((a, b) => Math.abs(Number(b.amount) || 0) - Math.abs(Number(a.amount) || 0))
      .slice(0, 5);
  }, [dashboardTransactions, hiddenBiggestTransactionIds]);
  const mortgageAccounts = useMemo(
    () => accounts.filter((account) => !account.is_archived && account.type === 'mortgage'),
    [accounts]
  );
  const visibleDashboardCards = dashboardLayout.filter((item) => item.visible);

  function updateDashboardLayout(nextOrUpdater) {
    const raw = typeof nextOrUpdater === 'function' ? nextOrUpdater(dashboardLayout) : nextOrUpdater;
    const next = normalizeDashboardLayout(raw);
    setDashboardLayout(next);
    onUserPreferenceChange?.(USER_PREFERENCE_KEYS.dashboardLayout, next);
  }

  function updateHiddenBiggestTransactionIds(nextOrUpdater) {
    const raw = typeof nextOrUpdater === 'function'
      ? nextOrUpdater(hiddenBiggestTransactionIds)
      : nextOrUpdater;
    const next = normalizeIdArray(raw);
    setHiddenBiggestTransactionIds(next);
    onUserPreferenceChange?.(USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions, next);
  }

  function updateHandledReviewTransactionIds(nextOrUpdater) {
    const raw = typeof nextOrUpdater === 'function'
      ? nextOrUpdater(handledReviewTransactionIds)
      : nextOrUpdater;
    const next = normalizeIdArray(raw);
    setHandledReviewTransactionIds(next);
    onUserPreferenceChange?.(USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions, next);
  }

  function updateGoalFocusId(nextOrUpdater) {
    const raw = typeof nextOrUpdater === 'function' ? nextOrUpdater(goalFocusId) : nextOrUpdater;
    const next = normalizeGoalFocusId(raw);
    setGoalFocusId(next);
    onUserPreferenceChange?.(USER_PREFERENCE_KEYS.dashboardGoalFocusId, next);
  }

  function hideBiggestTransaction(id) {
    updateHiddenBiggestTransactionIds((prev) =>
      prev.includes(id) ? prev : [...prev, id]
    );
  }

  function resetHiddenBiggestTransactions() {
    updateHiddenBiggestTransactionIds([]);
  }

  function updateRetirementPreferences(patch) {
    const next = normalizeRetirementPreferences({ ...retirementPreferences, ...patch });
    setRetirementPreferences(next);
    onUserPreferenceChange?.(USER_PREFERENCE_KEYS.dashboardRetirement, next);
  }

  function renderDashboardCard(cardId) {
    switch (cardId) {
      case 'accounts':
        return (
          <AccountsCard
            totals={totals}
            activeCount={activeAccountCount}
            loading={loading && accounts.length === 0}
          />
        );
      case 'top-spending':
        return (
          <TopSpendingCard
            topSpending={topSpending}
            totalSpent={totalSpent}
            loading={loading && !budgetData}
          />
        );
      case 'this-month':
        return (
          <MonthCard
            summary={summary}
            dayOfMonth={dayOfMonth}
            totalDays={totalDays}
            loading={loading && !summary}
          />
        );
      case 'budget-pulse':
        return (
          <BudgetPulseCard
            summary={summary}
            attention={budgetAttention}
            overallPercent={overallPercent}
            totalSpent={totalSpent}
            totalBudgeted={summary?.total_budgeted || 0}
            dailySpendPace={dailySpendPace}
            daysRemaining={daysRemaining}
            loading={loading && !budgetData}
          />
        );
      case 'biggest-transactions':
        return (
          <BiggestTransactionsCard
            transactions={biggestTransactions}
            hiddenCount={hiddenBiggestTransactionIds.length}
            loading={loading && dashboardTransactions.length === 0}
            onHide={hideBiggestTransaction}
            onResetHidden={resetHiddenBiggestTransactions}
          />
        );
      case 'subscriptions':
        return (
          <RecurringCard
            items={(upcomingData?.items || []).filter((item) => item.kind === 'subscription').slice(0, 5)}
            loading={loading && !upcomingData}
          />
        );
      case 'uncategorized':
        return (
          <UncategorizedCard
            data={uncategorizedData}
            loading={loading && !uncategorizedData}
          />
        );
      case 'categorize-recent':
        return (
          <CategorizeRecentCard
            loading={loading}
            onOpen={() => setReviewOpen(true)}
          />
        );
      case 'month-comparison':
        return (
          <MonthComparisonCard
            month={month}
            previousMonth={previousMonth}
            summary={summary}
            previousSummary={previousSummary}
            loading={loading && (!summary || !previousSummary)}
          />
        );
      case 'goals-progress':
        return <GoalsProgressCard goalsData={goalsData} loading={loading && !goalsData} />;
      case 'goal-focus':
        return (
          <GoalFocusCard
            goalsData={goalsData}
            selectedGoalId={goalFocusId}
            onSelectGoal={updateGoalFocusId}
            loading={loading && !goalsData}
          />
        );
      case 'retirement':
        return (
          <RetirementSnapshotCard
            householdData={householdData}
            preferences={retirementPreferences}
            onChangePreferences={updateRetirementPreferences}
            loading={loading && !householdData}
          />
        );
      case 'mha':
        return <MhaSummaryCard data={mhaData} enabled={mhaTrackerEnabled} loading={loading && !mhaData} />;
      case 'upcoming':
        return (
          <UpcomingCard
            items={upcomingData?.upcoming || []}
            loading={loading && !upcomingData}
          />
        );
      case 'mortgage':
        return <MortgageSnapshotCard accounts={mortgageAccounts} loading={loading && accounts.length === 0} />;
      case 'recent-activity':
        return (
          <RecentActivityCard
            transactions={recent}
            accountById={accountById}
            categoryById={categoryById}
            accounts={accounts}
            categories={categories}
            mhaTrackerEnabled={mhaTrackerEnabled}
            loading={loading && recent.length === 0}
            onRefresh={loadDashboard}
          />
        );
      default:
        return null;
    }
  }

  // --- First-time onboarding ------------------------------------------------
  const noActivity = !loading && recent.length === 0 && activeAccountCount === 0;
  if (noActivity) {
    return (
      <div className="dashboard-view">
        <DashboardHero
          dateLabel={formatLongDate(now)}
          greeting={timeGreeting(now)}
          userName={userName}
        />
        <div className="empty-state">
          <div className="empty-state-icon">$</div>
          <h2>Nothing to show yet</h2>
          <p>
            Import your Rocket Money history or connect SimpleFIN to start
            seeing a dashboard.
          </p>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
            <button
              type="button"
              className="btn-primary"
              onClick={() => navigate('/settings')}
            >
              Import from Rocket Money
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate('/settings')}
            >
              Connect SimpleFIN
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-view">
      <DashboardHero
        dateLabel={formatLongDate(now)}
        greeting={timeGreeting(now)}
        userName={userName}
      />

      {error && <div className="error">{error}</div>}

      <div className="dashboard-grid">
        {visibleDashboardCards.map((item) => (
          <div key={item.id} className={`dashboard-card-slot dashboard-card-slot-${item.id}`}>
            {renderDashboardCard(item.id)}
          </div>
        ))}
      </div>

      <div className="dashboard-customize-bar">
        <button
          type="button"
          className="btn-secondary"
          onClick={() => setCustomizing(true)}
        >
          Customize My Dashboard
        </button>
      </div>

      {customizing && (
        <DashboardCustomizeModal
          layout={dashboardLayout}
          onChange={updateDashboardLayout}
          onClose={() => setCustomizing(false)}
        />
      )}

      {reviewOpen && (
        <TransactionReviewModal
          categories={categories}
          accounts={accounts}
          handledIds={handledReviewTransactionIds}
          onHandledIdsChange={updateHandledReviewTransactionIds}
          onClose={() => setReviewOpen(false)}
          onRefresh={loadDashboard}
        />
      )}
    </div>
  );
}

function DashboardHero({ dateLabel, greeting, userName }) {
  const navigate = useNavigate();

  return (
    <PageHero
      id="dashboard-title"
      variant="dashboard"
      kicker="Financial Orbit"
      title="Dashboard"
      subtitle={`${dateLabel} · ${greeting}, ${userName} ${greetingEmoji(new Date())}`}
      statLabel="Dashboard summary"
      chrome={(hero) => (
        <div className="page-hero-chrome">
          <button
            type="button"
            className="hero-brand brand-home"
            onClick={() => navigate('/dashboard')}
            aria-label="Go to dashboard"
          >
            <img src={APP_ICON_192} alt="" className="brand-mark brand-mark-image" />
            <BrandLogo tone="white" />
          </button>
        </div>
      )}
    />
  );
}

// ============================================================================
// Accounts card
// ============================================================================

function AccountsCard({ totals, activeCount, loading }) {
  const rows = [];
  if (totals.cash !== 0) rows.push({ label: 'Cash', value: totals.cash });
  if (totals.investments !== 0) {
    rows.push({ label: 'Investments', value: totals.investments });
  }
  if (totals.credit !== 0) {
    rows.push({ label: 'Credit cards', value: totals.credit, isDebt: true });
  }
  if (totals.loans !== 0) {
    rows.push({ label: 'Loans', value: totals.loans, isDebt: true });
  }
  if (totals.realEstate !== 0) {
    rows.push({ label: 'Real Estate', value: totals.realEstate });
  }
  if (totals.other !== 0) rows.push({ label: 'Other', value: totals.other });

  const netPositive = totals.net >= 0;

  return (
    <DashboardCard
      title="Accounts"
      action={<Link to="/accounts" className="dashboard-card-link">See all</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : activeCount === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No active accounts. <Link to="/accounts" className="linkish">Manage accounts</Link>.
        </p>
      ) : (
        <>
          <div className="dash-networth">
            <div className="dash-networth-label">Net worth</div>
            <AnimatedMoney
              as="div"
              value={totals.net}
              formatter={formatCurrency}
              className={`dash-networth-value ${netPositive ? 'income' : 'expense'}`}
            />
            <div className="subtle dash-networth-sub">
              Across {activeCount} {activeCount === 1 ? 'account' : 'accounts'}
            </div>
          </div>

          {rows.length > 0 && (
            <ul className="dash-balance-list">
              {rows.map((r) => (
                <li key={r.label} className="dash-balance-row">
                  <span className="dash-balance-label">{r.label}</span>
                  <span
                    className={`dash-balance-value ${
                      r.isDebt || r.value < 0 ? 'expense' : ''
                    }`}
                  >
                    <AnimatedMoney value={r.value} formatter={formatCurrency} />
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// This month card
// ============================================================================

function MonthCard({ summary, dayOfMonth, totalDays, loading }) {
  return (
    <DashboardCard
      title="This month"
      action={
        <Link to="/budgets" className="dashboard-card-link">
          Details
        </Link>
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : !summary ? (
        <p className="subtle" style={{ margin: 0 }}>No activity yet this month.</p>
      ) : (
        <>
          <div className="dash-month-day">
            Day {dayOfMonth} of {totalDays}
          </div>
          <div className="dash-month-stats">
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Income</div>
              <AnimatedMoney
                as="div"
                value={summary.total_income}
                formatter={formatCompactCurrency}
                className="dash-month-stat-value income"
              />
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Expenses</div>
              <AnimatedMoney
                as="div"
                value={Math.abs(summary.total_expenses)}
                formatter={formatCompactCurrency}
                className="dash-month-stat-value expense"
              />
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Net</div>
              <AnimatedMoney
                as="div"
                value={Math.abs(summary.total_net)}
                formatter={formatCompactCurrency}
                className={`dash-month-stat-value ${
                  summary.total_net >= 0 ? 'income' : 'expense'
                }`}
              />
            </div>
          </div>
        </>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Budget pulse card
// ============================================================================

function BudgetPulseCard({
  summary,
  attention,
  overallPercent,
  totalBudgeted,
  totalSpent,
  dailySpendPace,
  daysRemaining,
  loading
}) {
  const hasBudgets = totalBudgeted > 0;

  return (
    <DashboardCard
      title="Budget pulse"
      action={
        <Link to="/budgets" className="dashboard-card-link">
          Manage
        </Link>
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : !hasBudgets ? (
        <p className="subtle" style={{ margin: 0 }}>
          No budgets set.{' '}
          <Link to="/budgets" className="linkish">
            Add one
          </Link>{' '}
          to start tracking.
        </p>
      ) : (
        <>
          <div className="dash-budget-overall">
            <div className="dash-budget-overall-text">
              {formatCurrency(totalSpent)} of{' '}
              {formatCurrency(summary.total_budgeted)} used
            </div>
            <div className="dash-budget-overall-percent">
              {Math.round(overallPercent)}%
            </div>
          </div>
          <DashProgressBar
            percent={overallPercent}
            overBudget={overallPercent > 100}
          />

          <div className="dash-mini-stat-row">
            <span>Daily Spend Remaining</span>
            <strong className={dailySpendPace !== null && dailySpendPace < 0 ? 'expense' : ''}>
              {dailySpendPace === null
                ? 'Not set'
                : dailySpendPace >= 0
                  ? `${formatCurrency(dailySpendPace)} / day`
                  : `${formatCurrency(Math.abs(dailySpendPace))} over`}
            </strong>
            <em>
              {daysRemaining > 0 ? `${daysRemaining} days left` : 'Month ends today'}
            </em>
          </div>

          {attention.length > 0 ? (
            <ul className="dash-attention-list">
              {attention.map((b) => {
                const pct = Math.round((b.spent / b.amount) * 100);
                return (
                  <li key={b.category.id} className="dash-attention-row">
                    <span
                      className="dash-attention-icon"
                      style={{ color: b.category.color }}
                    >
                      {b.category.icon}
                    </span>
                    <span className="dash-attention-name">{b.category.name}</span>
                    <span
                      className={`dash-attention-pct ${
                        b.status === 'over' ? 'over' : 'warning'
                      }`}
                    >
                      {b.status === 'over' ? `${pct}% over` : `${pct}%`}
                    </span>
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="subtle dash-no-attention">
              OK All budgets comfortably on track.
            </p>
          )}
        </>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Top spending card
// ============================================================================

function TopSpendingCard({ topSpending, totalSpent, loading }) {
  return (
    <DashboardCard
      title="Top spending this month"
      action={
        <Link
          to={`/transactions?date_from=${formatLocalMonth()}-01&type=expense&sort=abs_amount_desc`}
          className="dashboard-card-link"
        >
          Browse
        </Link>
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : topSpending.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>No spending yet this month.</p>
      ) : (
        <>
          <div className="dash-mini-stat-row">
            <span>Total Spend</span>
            <strong>{formatCurrency(totalSpent)}</strong>
          </div>
          <ul className="dash-top-list">
            {topSpending.map((item) => {
              const pct = totalSpent > 0 ? (item.spent / totalSpent) * 100 : 0;
              return (
                <li key={item.category.id} className="dash-top-row">
                  <div className="dash-top-head">
                    <span
                      className="dash-top-icon"
                      style={{ color: item.category.color }}
                    >
                      {item.category.icon}
                    </span>
                    <span className="dash-top-name">{item.category.name}</span>
                    <span className="dash-top-amount">
                      {formatCurrency(item.spent)}
                      <em>{Math.round(pct)}%</em>
                    </span>
                  </div>
                  <div className="dash-top-barwrap">
                    <div
                      className="dash-top-bar"
                      title={`${item.category.name}: ${formatCurrency(item.spent)} (${Math.round(pct)}%)`}
                      style={{
                        width: `${Math.max(2, pct)}%`,
                        background: item.category.color || 'var(--accent)'
                      }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Biggest transactions card
// ============================================================================

function BiggestTransactionsCard({
  transactions,
  hiddenCount,
  loading,
  onHide,
  onResetHidden
}) {
  return (
    <DashboardCard
      title="Biggest Monthly Transactions"
      action={
        hiddenCount > 0 ? (
          <button
            type="button"
            className="dashboard-card-link dashboard-card-action-button"
            onClick={onResetHidden}
          >
            Show Hidden
          </button>
        ) : (
          <Link
            to={`/transactions?date_from=${formatLocalMonth()}-01&date_to=${monthEndDate(formatLocalMonth())}&type=expense&include_ignored=0&include_transfers=0&exclude_credit_card_payments=1&sort=abs_amount_desc`}
            className="dashboard-card-link"
          >
            Browse
          </Link>
        )
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : transactions.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No transactions to rank right now.
        </p>
      ) : (
        <ul className="dash-compact-list">
          {transactions.map((txn) => (
            <li key={txn.id} className="dash-compact-row">
              <div className="dash-compact-main">
                <strong>{txn.merchant || 'Transaction'}</strong>
                <span>{formatShortDate(txn.date)}</span>
              </div>
              <div className="dash-compact-side">
                <strong className={Number(txn.amount) < 0 ? 'expense' : 'income'}>
                  {formatCurrency(Math.abs(Number(txn.amount) || 0))}
                </strong>
                <button
                  type="button"
                  className="dash-inline-action"
                  onClick={() => onHide(txn.id)}
                >
                  Hide From Dashboard
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {hiddenCount > 0 && (
        <p className="dash-card-note">
          {hiddenCount} hidden only from this card. Budget ignore is unchanged.
        </p>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Recurring and upcoming cards
// ============================================================================

function RecurringCard({ items, loading }) {
  return (
    <DashboardCard
      title="Subscriptions / Recurring"
      action={<Link to="/upcoming" className="dashboard-card-link">Review</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : items.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No subscriptions saved yet.
        </p>
      ) : (
        <>
          <ul className="dash-compact-list">
            {items.map((item) => (
              <li key={item.id} className="dash-compact-row">
                <div className="dash-compact-main">
                  <strong>{item.name || item.merchant}</strong>
                  <span>{item.category_name || 'Uncategorized'} - {frequencyLabel(item)}</span>
                </div>
                <div className="dash-compact-side">
                  <strong className="expense">{formatCurrency(item.projected_amount ?? item.amount)}</strong>
                  <em>Next {formatShortDate(item.next_date)}</em>
                </div>
              </li>
            ))}
          </ul>
          <p className="dash-card-note">
            Managed from Upcoming. Suggestions are based on recurring merchants in transaction history.
          </p>
        </>
      )}
    </DashboardCard>
  );
}

function UpcomingCard({ items, loading }) {
  return (
    <DashboardCard
      title="Upcoming: Bills and Income"
      action={<Link to="/upcoming" className="dashboard-card-link">Upcoming</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : items.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No upcoming bills, subscriptions, or income saved.
        </p>
      ) : (
        <ul className="dash-compact-list">
          {items.map((item) => (
            <li key={item.id} className="dash-compact-row">
              <div className="dash-compact-main">
                <strong>{item.name || item.merchant}</strong>
                <span>{kindLabel(item.kind)} - {item.category_name || 'Uncategorized'}</span>
              </div>
              <div className="dash-compact-side">
                <strong className={item.direction === 'income' ? 'income' : 'expense'}>
                  {item.direction === 'income' ? '+' : '-'}{formatCurrency(item.amount)}
                </strong>
                <em>{formatShortDate(item.next_date)}</em>
              </div>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Uncategorized and month comparison cards
// ============================================================================

function UncategorizedCard({ data, loading }) {
  const items = data?.items || [];
  const total = Number(data?.total || 0);

  return (
    <DashboardCard
      title="Uncategorized Transactions"
      action={
        <Link
          to={`/transactions?categories=${data?.categoryId || 'uncategorized'}&include_ignored=0&include_transfers=0`}
          className="dashboard-card-link"
        >
          Categorize
        </Link>
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : total === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          Everything recent has a category.
        </p>
      ) : (
        <>
          <div className="dash-mini-stat-row">
            <span>Needs category</span>
            <strong>{total.toLocaleString()}</strong>
            <em>{items.length} shown</em>
          </div>
          <ul className="dash-compact-list">
            {items.map((txn) => (
              <li key={txn.id} className="dash-compact-row">
                <div className="dash-compact-main">
                  <strong>{txn.merchant || 'Transaction'}</strong>
                  <span>{formatShortDate(txn.date)}</span>
                </div>
                <div className="dash-compact-side">
                  <strong className={Number(txn.amount) < 0 ? 'expense' : 'income'}>
                    {formatTransactionAmount(Number(txn.amount) || 0)}
                  </strong>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </DashboardCard>
  );
}

function CategorizeRecentCard({ loading, onOpen }) {
  if (loading) {
    return (
      <DashboardCard title="Categorize Recent Transactions">
        <CardSkeleton />
      </DashboardCard>
    );
  }

  return (
    <button
      type="button"
      className="dashboard-card review-launch-card-button"
      onClick={onOpen}
      aria-label="Categorize recent transactions"
    >
      <span className="dashboard-card-header review-launch-header">
        <h3>Categorize Recent Transactions</h3>
        <span className="dashboard-card-link" aria-hidden="true">Open</span>
      </span>
      <span className="review-launch-card">
        <span className="review-launch-graphic" aria-hidden="true">
          <span className="review-launch-ghost-card left" />
          <span className="review-launch-ghost-card right" />
          <span className="review-launch-main-card">
            <span />
            <span />
          </span>
        </span>
        <span>
          <strong>Quick category check</strong>
          <p>
            Swipe through recent transactions that need a little human judgment.
          </p>
        </span>
      </span>
    </button>
  );
}

function TransactionReviewModal({
  categories,
  accounts,
  handledIds: initialHandledIds = [],
  onHandledIdsChange,
  onClose,
  onRefresh
}) {
  const { confirm, Dialog } = useAppDialog();
  const [handledIds, setHandledIds] = useState(() => normalizeIdArray(initialHandledIds));
  const [items, setItems] = useState([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [batchReviewed, setBatchReviewed] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [categoryPickerOpen, setCategoryPickerOpen] = useState(false);
  const [pickerClosing, setPickerClosing] = useState(false);
  const [ruleTxn, setRuleTxn] = useState(null);
  const [allDone, setAllDone] = useState(false);
  const [funMode, setFunMode] = useState(false);
  const [dragX, setDragX] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [transitioning, setTransitioning] = useState(false);
  const dragRef = useRef({ active: false, startX: 0, readySide: null });
  const transitionTimerRef = useRef(null);
  const pickerTimerRef = useRef(null);

  const currentTxn = items[currentIndex] || null;
  const batchComplete = batchReviewed >= 10 || (!currentTxn && items.length > 0);
  const accountById = useMemo(
    () => new Map(accounts.map((account) => [account.id, account])),
    [accounts]
  );
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.id, category])),
    [categories]
  );

  useEffect(() => {
    loadQueue({ handled: handledIds, fun: funMode });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [funMode]);

  useEffect(() => () => {
    window.clearTimeout(transitionTimerRef.current);
    window.clearTimeout(pickerTimerRef.current);
  }, []);

  function rememberHandled(id) {
    setHandledIds((prev) => {
      const next = normalizeIdArray(prev.includes(id) ? prev : [...prev, id]);
      onHandledIdsChange?.(next);
      return next;
    });
  }

  function hydrateFunItems(rows) {
    return rows.map((txn) => {
      const category = categoryById.get(txn.category_id);
      const account = accountById.get(txn.account_id);
      return {
        ...txn,
        category_name: category?.name || null,
        category_color: category?.color || null,
        category_icon: category?.icon || null,
        account_name: account?.name || null,
        review_reasons: ['fun_review']
      };
    });
  }

  async function loadQueue(options = {}) {
    const handled = options.handled || handledIds;
    const fun = options.fun ?? funMode;
    setLoading(true);
    setError('');
    setCategoryPickerOpen(false);
    setPickerClosing(false);
    setCurrentIndex(0);
    setBatchReviewed(0);
    setDragX(0);
    setTransitioning(false);
    dragRef.current.readySide = null;

    try {
      if (fun) {
        const data = await api.get('/api/transactions?limit=100&page=1&include_ignored=0&sort=date_desc');
        const handledSet = new Set(handled);
        const nextItems = hydrateFunItems(data.items || [])
          .filter((txn) => !handledSet.has(txn.id))
          .slice(0, 10);
        setItems(nextItems);
        setAllDone(nextItems.length === 0);
      } else {
        const exclude = handled.length ? `&exclude=${handled.join(',')}` : '';
        const data = await api.get(`/api/transactions/review-queue?limit=10${exclude}`);
        setItems(data.items || []);
        setAllDone((data.items || []).length === 0);
      }
    } catch (err) {
      setError(err.message || 'Could not load review queue.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }

  function advance(txn) {
    if (txn) rememberHandled(txn.id);
    setCategoryPickerOpen(false);
    setPickerClosing(false);
    setDragX(0);
    setTransitioning(true);
    dragRef.current.readySide = null;
    setBatchReviewed((count) => count + 1);
    setCurrentIndex((index) => index + 1);
    window.clearTimeout(transitionTimerRef.current);
    transitionTimerRef.current = window.setTimeout(() => {
      setTransitioning(false);
    }, 240);
  }

  function handleApprove() {
    if (!currentTxn || saving || transitioning) return;
    advance(currentTxn);
  }

  function handleReject() {
    if (!currentTxn || saving || transitioning) return;
    setCategoryPickerOpen(true);
    setPickerClosing(false);
    setDragX(0);
  }

  async function chooseCategory(categoryId) {
    if (!currentTxn || saving) return;
    const nextCategory = categoryById.get(categoryId);
    const confirmed = await confirm(
      `Change "${currentTxn.merchant || 'this transaction'}" to "${nextCategory?.name || 'this category'}"?`,
      {
        title: 'Confirm Category',
        confirmLabel: 'Change Category',
        cancelLabel: 'Keep Looking'
      }
    );
    if (!confirmed) return;

    setSaving(true);
    setError('');
    try {
      await api.patch(`/api/transactions/${currentTxn.id}`, {
        category_id: categoryId
      });
      setPickerClosing(true);
      window.clearTimeout(pickerTimerRef.current);
      pickerTimerRef.current = window.setTimeout(() => {
        advance(currentTxn);
        onRefresh?.();
        setSaving(false);
      }, 170);
    } catch (err) {
      setError(err.message || 'Could not update category.');
      setSaving(false);
    }
  }

  function skipCurrent() {
    if (!currentTxn || saving) return;
    advance(currentTxn);
  }

  function viewMore() {
    loadQueue({ handled: handledIds, fun: funMode });
  }

  function startFunMode() {
    setFunMode(true);
    setAllDone(false);
  }

  function closeCategoryPicker() {
    setPickerClosing(true);
    window.clearTimeout(pickerTimerRef.current);
    pickerTimerRef.current = window.setTimeout(() => {
      setCategoryPickerOpen(false);
      setPickerClosing(false);
    }, 170);
  }

  function goBackReview() {
    if (currentIndex <= 0 || saving || transitioning) return;
    setCategoryPickerOpen(false);
    setPickerClosing(false);
    setDragX(0);
    dragRef.current.readySide = null;
    setCurrentIndex((index) => Math.max(0, index - 1));
    setBatchReviewed((count) => Math.max(0, count - 1));
    setTransitioning(true);
    window.clearTimeout(transitionTimerRef.current);
    transitionTimerRef.current = window.setTimeout(() => {
      setTransitioning(false);
    }, 220);
  }

  function onPointerDown(event) {
    if (!currentTxn || categoryPickerOpen || saving || transitioning) return;
    if (event.target.closest('button, a, input, select, textarea')) return;
    dragRef.current = { active: true, startX: event.clientX, readySide: null };
    setDragging(true);
    event.currentTarget.setPointerCapture?.(event.pointerId);
  }

  function onPointerMove(event) {
    if (!dragRef.current.active) return;
    const nextX = event.clientX - dragRef.current.startX;
    const readySide = nextX > 90 ? 'yes' : nextX < -90 ? 'no' : null;
    if (readySide && readySide !== dragRef.current.readySide) {
      navigator.vibrate?.(12);
    }
    dragRef.current.readySide = readySide;
    setDragX(nextX);
  }

  function onPointerUp(event) {
    if (!dragRef.current.active) return;
    dragRef.current.active = false;
    dragRef.current.readySide = null;
    setDragging(false);
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    if (dragX > 90) handleApprove();
    else if (dragX < -90) handleReject();
    else setDragX(0);
  }

  function makeRuleDraft(txn) {
    const categoryId = txn.category_id || txn.suggested_category_id || categories[0]?.id || '';
    return {
      name: `Categorize ${txn.merchant || 'merchant'}`,
      conditions: [
        {
          field: 'merchant',
          operator: 'contains',
          value: txn.merchant || txn.original_merchant || ''
        }
      ],
      actions: categoryId
        ? [{ type: 'categorize', value: categoryId }]
        : [{ type: 'rename', value: txn.merchant || '' }],
      enabled: true
    };
  }

  const cardRotation = Math.max(-10, Math.min(10, dragX / 18));
  const cardTone = dragX > 40 ? 'yes' : dragX < -40 ? 'no' : '';

  return (
    <AnimatedModal onClose={onClose} size="lg" animation="zoom">
      {({ close }) => (
        <>
          <div className="modal-header">
            <h3>Is this category correct?</h3>
            <button type="button" className="modal-close" onClick={close} aria-label="Close">
              x
            </button>
          </div>

          <div className="transaction-review-modal">
            {loading ? (
              <div className="center-loading review-loading">
                <div className="spinner" />
              </div>
            ) : error ? (
              <div className="error">{error}</div>
            ) : allDone ? (
              <div className="review-empty-state">
                <div className="review-launch-icon" aria-hidden="true">
                  !
                </div>
                <h4>PHEW all transactions are categorized for now.</h4>
                <p>Want to go through more transactions just for fun, ya sicko?</p>
                <div className="review-empty-actions">
                  <button type="button" className="btn-primary" onClick={startFunMode}>
                    Yes I need the dopamine
                  </button>
                  <button type="button" className="btn-secondary" onClick={close}>
                    No thanks, I have self-control
                  </button>
                </div>
              </div>
            ) : batchComplete ? (
              <div className="review-empty-state">
                <div className="review-launch-icon" aria-hidden="true">
                  10
                </div>
                <h4>Ten down.</h4>
                <p>Want another stack, or are we calling that a responsible little victory?</p>
                <div className="review-empty-actions">
                  <button type="button" className="btn-primary" onClick={viewMore}>
                    View More Transactions
                  </button>
                  <button type="button" className="btn-secondary" onClick={close}>
                    Done For Now
                  </button>
                </div>
              </div>
            ) : currentTxn && categoryPickerOpen ? (
              <CategoryReviewPicker
                txn={currentTxn}
                categories={categories}
                currentCategory={categoryById.get(currentTxn.category_id)}
                suggestedCategory={categoryById.get(currentTxn.suggested_category_id)}
                saving={saving}
                closing={pickerClosing}
                onChoose={chooseCategory}
                onBack={closeCategoryPicker}
              />
            ) : currentTxn ? (
              <>
                <div className="review-progress-row">
                  <span className="review-counter-wrap">
                    <button
                      type="button"
                      className="review-counter-back"
                      onClick={goBackReview}
                      disabled={currentIndex <= 0 || saving || transitioning}
                      aria-label="Go back to previous transaction"
                    >
                      <span aria-hidden="true">&larr;</span>
                    </button>
                    <span>{Math.min(batchReviewed + 1, 10)} of 10</span>
                  </span>
                  <strong>Is this category correct?</strong>
                </div>

                <div className="review-swipe-stage">
                  <div className="review-swipe-track-hint" aria-hidden="true">
                    <span>Change</span>
                    <span>Confirm</span>
                  </div>
                  <div
                    key={currentTxn.id}
                    className={`review-swipe-card ${dragging ? 'dragging' : ''} ${transitioning ? 'entering' : ''} ${cardTone}`}
                    style={{
                      transform: `translateX(${dragX}px) rotate(${cardRotation}deg)`
                    }}
                    onPointerDown={onPointerDown}
                    onPointerMove={onPointerMove}
                    onPointerUp={onPointerUp}
                    onPointerCancel={() => {
                      dragRef.current.active = false;
                      dragRef.current.readySide = null;
                      setDragging(false);
                      setDragX(0);
                    }}
                  >
                    <div className="review-swipe-choice yes">Yes</div>
                    <div className="review-swipe-choice no">No</div>
                    <ReviewTransactionCard
                      txn={currentTxn}
                      category={categoryById.get(currentTxn.category_id)}
                      account={accountById.get(currentTxn.account_id)}
                    />
                  </div>
                </div>

                <div className="review-swipe-hint">
                  <span>← Change</span>
                  <span className="review-swipe-pill" aria-hidden="true" />
                  <span>Confirm →</span>
                </div>
              </>
            ) : null}

            {!loading && !allDone && !batchComplete && currentTxn && (
              <div className="review-bottom-actions">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setRuleTxn(currentTxn)}
                  disabled={saving}
                >
                  Create A Rule
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={skipCurrent}
                  disabled={saving}
                >
                  Skip For Now
                </button>
              </div>
            )}
          </div>

          {ruleTxn && (
            <RuleEditor
              rule={makeRuleDraft(ruleTxn)}
              accounts={accounts}
              categories={categories}
              sourceTransaction={ruleTxn}
              onClose={() => setRuleTxn(null)}
              onSaved={() => {
                setRuleTxn(null);
                if (currentTxn) advance(currentTxn);
                onRefresh?.();
              }}
            />
          )}
          <Dialog />
        </>
      )}
    </AnimatedModal>
  );
}

function ReviewTransactionCard({ txn, category, account }) {
  const logoUrl = txn.merchant_logo?.url;
  return (
    <div className="review-card-content">
      <div className="review-card-topline">
        <span>{formatShortDate(txn.date)}</span>
        {account?.name && <span>{account.name}</span>}
      </div>
      <div className="review-card-merchant-row">
        <span
          className={`review-card-logo ${logoUrl ? 'has-logo' : ''}`}
          style={!logoUrl && category?.color ? { color: category.color } : undefined}
          aria-hidden="true"
        >
          {logoUrl ? <img src={logoUrl} alt="" loading="lazy" /> : category?.icon || '?'}
        </span>
        <strong className="review-card-merchant">{txn.merchant || 'Transaction'}</strong>
      </div>
      {txn.original_description && (
        <p>{txn.original_description}</p>
      )}
      <div className="review-card-category">
        <span
          className="review-category-mark"
          style={category?.color ? { color: category.color } : undefined}
          aria-hidden="true"
        >
          {category?.icon || '?'}
        </span>
        <div>
          <span>Current category</span>
          <strong>{category?.name || txn.category_name || 'Uncategorized'}</strong>
        </div>
      </div>
      <div className="review-card-footer">
        <strong className={Number(txn.amount) < 0 ? 'expense' : 'income'}>
          {formatTransactionAmount(Number(txn.amount) || 0)}
        </strong>
        <ReviewReasonPills reasons={txn.review_reasons || []} />
      </div>
    </div>
  );
}

function ReviewReasonPills({ reasons }) {
  const labels = {
    uncategorized: 'Uncategorized',
    merchant_review: 'Merchant Check',
    changed_pattern: 'Changed Pattern',
    fun_review: 'Bonus Round'
  };
  return (
    <span className="review-reason-list">
      {reasons.slice(0, 3).map((reason) => (
        <span key={reason} className="pill accent">
          {labels[reason] || 'Review'}
        </span>
      ))}
    </span>
  );
}

function CategoryReviewPicker({
  txn,
  categories,
  currentCategory,
  suggestedCategory,
  saving,
  closing,
  onChoose,
  onBack
}) {
  const orderedCategories = useMemo(() => {
    const suggestedId = suggestedCategory?.id;
    return sortCategoriesByName(categories).sort((a, b) => {
      if (a.id === suggestedId) return -1;
      if (b.id === suggestedId) return 1;
      return 0;
    });
  }, [categories, suggestedCategory?.id]);

  return (
    <div className={`review-category-picker ${closing ? 'closing' : ''}`}>
      <button type="button" className="btn-secondary review-back-button" onClick={onBack}>
        Back
      </button>
      <div className="review-picker-heading">
        <span>{txn.merchant || 'Transaction'}</span>
        <strong>Pick the right category</strong>
        <em>Currently {currentCategory?.name || 'Uncategorized'}</em>
      </div>
      <div className="review-category-list">
        {orderedCategories.map((category) => (
          <SelectableListItem
            key={category.id}
            active={category.id === txn.category_id}
            disabled={saving}
            leading={
              <span
                className="review-category-dot"
                style={{ background: category.color }}
                aria-hidden="true"
              >
                {category.icon || '$'}
              </span>
            }
            title={category.name}
            subtitle={category.id === suggestedCategory?.id ? 'Usual category for this merchant' : ''}
            sidePrimary={category.id === txn.category_id ? 'Current' : ''}
            onClick={() => onChoose(category.id)}
          />
        ))}
      </div>
    </div>
  );
}

function MonthComparisonCard({ month, previousMonth, summary, previousSummary, loading }) {
  const rows = [
    {
      label: 'Spending',
      current: summary?.total_expenses,
      previous: previousSummary?.total_expenses,
      expense: true
    },
    {
      label: 'Income',
      current: summary?.total_income,
      previous: previousSummary?.total_income
    },
    {
      label: 'Net',
      current: summary?.total_net,
      previous: previousSummary?.total_net
    }
  ];

  return (
    <DashboardCard
      title="Month vs Last Month"
      action={
        <Link to={transactionMonthUrl(month)} className="dashboard-card-link">
          Details
        </Link>
      }
    >
      {loading ? (
        <CardSkeleton />
      ) : !summary || !previousSummary ? (
        <p className="subtle" style={{ margin: 0 }}>
          Need two months of activity to compare.
        </p>
      ) : (
        <div className="dash-month-compare-table">
          <div className="dash-month-compare-head">
            <span>Metric</span>
            <strong>{formatShortMonth(month)}</strong>
            <strong>{formatShortMonth(previousMonth)}</strong>
            <strong>Change</strong>
          </div>
          {rows.map((row) => {
            const current = Number(row.current || 0);
            const previous = Number(row.previous || 0);
            const delta = current - previous;
            return (
              <div key={row.label} className="dash-month-compare-row">
                <span>{row.label}</span>
                <strong>{formatWholeCurrency(current)}</strong>
                <strong>{formatWholeCurrency(previous)}</strong>
                <em className={delta < 0 ? 'income' : delta > 0 ? 'expense' : ''}>
                  {delta === 0 ? 'No change' : `${delta > 0 ? '+' : ''}${formatWholeCurrency(delta)}`}
                </em>
              </div>
            );
          })}
        </div>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Goals, retirement, MHA, mortgage, and attention cards
// ============================================================================

function GoalsProgressCard({ goalsData, loading }) {
  const goals = goalsData?.goals || [];
  const summary = goalsData?.summary;

  return (
    <DashboardCard
      title="Goals Progress"
      action={<Link to="/goals" className="dashboard-card-link">Goals</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : goals.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No goals set yet.
        </p>
      ) : (
        <>
          <div className="dash-goal-total">
            <strong>{Math.round(summary?.progress_percent || 0)}%</strong>
            <span>{formatCurrency(summary?.total_saved || 0)} of {formatCurrency(summary?.total_target || 0)}</span>
          </div>
          <DashProgressBar percent={summary?.progress_percent || 0} />
          <ul className="dash-compact-list">
            {goals.slice(0, 4).map((goal) => (
              <li key={goal.id} className="dash-compact-row">
                <div className="dash-compact-main">
                  <strong>{goal.name}</strong>
                  <span>{Math.round(goal.progress_percent || 0)}% funded</span>
                </div>
                <div className="dash-compact-side">
                  <strong>{formatCurrency(goal.current_amount || 0)}</strong>
                  <em>{goal.eta?.status === 'complete' ? 'Reached' : goal.eta?.date ? formatShortDate(goal.eta.date) : 'No ETA'}</em>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </DashboardCard>
  );
}

function GoalFocusCard({ goalsData, selectedGoalId, onSelectGoal, loading }) {
  const [picking, setPicking] = useState(false);
  const goals = goalsData?.goals || [];
  const goal =
    goals.find((item) => item.id === selectedGoalId) ||
    goals.find((item) => item.progress_percent < 100 && item.eta?.status === 'stalled') ||
    goals.find((item) => item.progress_percent < 100) ||
    goals[0];

  return (
    <>
      <DashboardCard
        title="Goal Focus"
        action={
          <button
            type="button"
            className="dashboard-card-link dashboard-card-action-button"
            onClick={() => setPicking(true)}
          >
            Pick Goal
          </button>
        }
      >
        {loading ? (
          <CardSkeleton />
        ) : !goal ? (
          <p className="subtle" style={{ margin: 0 }}>
            Add a goal to focus on.
          </p>
        ) : (
          <div className="dash-focus-goal">
            <div
              className="dash-focus-ring"
              style={{ '--goal-progress': `${Math.max(0, Math.min(100, goal.progress_percent || 0))}%` }}
            >
              <span>{Math.round(goal.progress_percent || 0)}%</span>
            </div>
            <div className="dash-focus-copy">
              <strong>{goal.name}</strong>
              <span>{formatCurrency(goal.current_amount || 0)} saved</span>
              <em>
                {goal.eta?.status === 'complete'
                  ? 'Reached'
                  : goal.eta?.date
                    ? `ETA ${formatShortDate(goal.eta.date)}`
                    : 'Needs monthly pace'}
              </em>
            </div>
          </div>
        )}
      </DashboardCard>
      {picking && (
        <GoalFocusPickerModal
          goals={goals}
          selectedGoalId={goal?.id || null}
          onPick={(id) => {
            onSelectGoal(id);
            setPicking(false);
          }}
          onClose={() => setPicking(false)}
        />
      )}
    </>
  );
}

function GoalFocusPickerModal({ goals, selectedGoalId, onPick, onClose }) {
  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <div className="modal-header">
            <h3>Pick Goal</h3>
            <button type="button" className="modal-close" onClick={close} aria-label="Close">
              x
            </button>
          </div>
          <div className="dashboard-goal-picker-list">
            {goals.length === 0 ? (
              <p className="subtle">No goals are available yet.</p>
            ) : goals.map((goal) => (
              <SelectableListItem
                key={goal.id}
                active={goal.id === selectedGoalId}
                leading={goal.icon || '$'}
                title={goal.name}
                subtitle={`${Math.round(goal.progress_percent || 0)}% funded`}
                sidePrimary={formatCurrency(goal.current_amount || 0)}
                sideSecondary={goal.eta?.date ? formatShortDate(goal.eta.date) : 'No ETA'}
                onClick={() => onPick(goal.id)}
                ariaLabel={`Focus dashboard on ${goal.name}`}
              />
            ))}
          </div>
          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

function RetirementSnapshotCard({
  householdData,
  preferences,
  onChangePreferences,
  loading
}) {
  const summary = householdData?.summary;
  const projection = retirementProjection(householdData, preferences);
  const presetKeys = Object.keys(DASHBOARD_RETIREMENT_PRESETS);
  const currentPresetIndex = Math.max(0, presetKeys.indexOf(preferences.presetKey));
  const nextPresetKey = presetKeys[(currentPresetIndex + 1) % presetKeys.length];

  function adjustAge(delta) {
    const current = Number(preferences.retirementAge) || 67;
    onChangePreferences({
      retirementAge: Math.max(40, Math.min(80, current + delta))
    });
  }

  return (
    <DashboardCard
      title="Retirement Snapshot"
      action={<Link to="/retirement-calculator" className="dashboard-card-link">Retirement Calc</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : !summary ? (
        <p className="subtle" style={{ margin: 0 }}>
          Household retirement details are not set yet.
        </p>
      ) : (
        <div className="dash-stat-stack">
          <div className="dash-mini-stat-row">
            <span>Current Retirement Balance</span>
            <strong>{formatCurrency(projection.currentBalance)}</strong>
            <em>{summary.linked_retirement_account_count || 0} accounts</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Current Monthly Contributions</span>
            <strong>{formatCurrency(projection.monthlyContributions)}</strong>
            <em>Employee + match</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Projected Retirement</span>
            <strong>{formatCurrency(projection.projectedBalance)}</strong>
            <em>Age {Math.round(projection.retirementAge)} - {projection.preset.label}</em>
            <div className="dash-retirement-controls" aria-label="Retirement projection controls">
              <button
                type="button"
                className="btn-secondary btn-compact"
                onClick={() => adjustAge(-1)}
              >
                -1 Age
              </button>
              <button
                type="button"
                className="btn-secondary btn-compact"
                onClick={() => adjustAge(1)}
              >
                +1 Age
              </button>
              <button
                type="button"
                className="btn-secondary btn-compact"
                onClick={() => onChangePreferences({ presetKey: nextPresetKey })}
              >
                {projection.preset.label}
              </button>
            </div>
          </div>
        </div>
      )}
    </DashboardCard>
  );
}

function MhaSummaryCard({ data, enabled, loading }) {
  return (
    <DashboardCard
      title="MHA Tracker Summary"
      action={<Link to="/mha-tracker" className="dashboard-card-link">MHA Tracker</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : !enabled && !data?.enabled ? (
        <p className="subtle" style={{ margin: 0 }}>
          MHA Tracker is turned off.
        </p>
      ) : (
        <div className="dash-stat-stack">
          <div className="dash-mini-stat-row">
            <span>Eligible spending</span>
            <strong>{formatCurrency(data?.summary?.transactionTotal || 0)}</strong>
            <em>{data?.year || new Date().getFullYear()}</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Estimated savings</span>
            <strong className="income">{formatCurrency(data?.summary?.savings || 0)}</strong>
            <em>{Math.round((data?.savingsRate || 0) * 100)}% rate</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Transactions</span>
            <strong>{(data?.summary?.transactionCount || 0).toLocaleString()}</strong>
            <em>YTD</em>
          </div>
        </div>
      )}
    </DashboardCard>
  );
}

function MortgageSnapshotCard({ accounts, loading }) {
  const totalValue = accounts.reduce((sum, account) => sum + (Number(account.estimated_value) || 0), 0);
  const balance = accounts.reduce((sum, account) => sum + Math.abs(Number(account.current_balance) || 0), 0);
  const equity = totalValue - balance;

  return (
    <DashboardCard
      title="Mortgage Snapshot"
      action={<Link to="/accounts" className="dashboard-card-link">Accounts</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : accounts.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No mortgage account is active.
        </p>
      ) : (
        <div className="dash-stat-stack">
          <div className="dash-mini-stat-row">
            <span>Estimated value</span>
            <strong>{formatCurrency(totalValue)}</strong>
            <em>{accounts.length} mortgage{accounts.length === 1 ? '' : 's'}</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Mortgage balance</span>
            <strong className="expense">{formatCurrency(balance)}</strong>
            <em>Outstanding</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Estimated equity</span>
            <strong className={equity >= 0 ? 'income' : 'expense'}>{formatCurrency(equity)}</strong>
            <em>{totalValue > 0 ? `${Math.round((equity / totalValue) * 100)}% equity` : 'No value set'}</em>
          </div>
        </div>
      )}
    </DashboardCard>
  );
}

// ============================================================================
// Recent transactions card
//
// Uses the same TransactionRow component as the Transactions page so the
// expand/hover/edit UX is identical. Full edit, rule-from-transaction,
// transfer/ignore toggle, and delete are all wired up - mutations refresh
// the local list so the dashboard stays current without a full reload.
// ============================================================================

function RecentActivityCard({
  transactions,
  accountById,
  categoryById,
  accounts,
  categories,
  mhaTrackerEnabled = false,
  loading,
  onRefresh
}) {
  const { alert, confirm, Dialog } = useAppDialog();
  const [items, setItems] = useState(transactions);
  const [expandedId, setExpandedId] = useState(null);
  const [editingTxn, setEditingTxn] = useState(null);
  const [newRuleFromTxn, setNewRuleFromTxn] = useState(null);
  const [editingRule, setEditingRule] = useState(null);
  const [recurringFromTxn, setRecurringFromTxn] = useState(null);

  // Keep local copy in sync when parent refetches (e.g., on mount, on
  // tab-switch back to Dashboard).
  useEffect(() => {
    setItems(transactions);
  }, [transactions]);

  // ---------- Local mutation helpers (same pattern as Transactions page) ----
  function applyLocalPatch(id, patch) {
    setItems((prev) => prev.map((t) => (t.id === id ? { ...t, ...patch } : t)));
  }
  function replaceLocal(id, full) {
    setItems((prev) => prev.map((t) => (t.id === id ? full : t)));
  }
  function removeLocal(id) {
    setItems((prev) => prev.filter((t) => t.id !== id));
    setExpandedId(null);
  }

  async function handleToggle(txn, field) {
    const nextValue = !txn[field];
    applyLocalPatch(txn.id, { [field]: nextValue ? 1 : 0 });
    try {
      const result = await api.patch(`/api/transactions/${txn.id}`, {
        [field]: nextValue
      });
      if (result?.transaction) replaceLocal(txn.id, result.transaction);
      // A transfer/ignored flip changes budget spend totals, so refresh
      // the rest of the dashboard too.
      if (onRefresh) onRefresh();
    } catch (err) {
      applyLocalPatch(txn.id, { [field]: txn[field] });
      alert(err.message || 'Toggle failed', { title: 'Could not update transaction' });
    }
  }

  async function handleDelete(txn) {
    const ok = await confirm(
      `Delete this transaction? "${txn.merchant}" for ${formatTransactionAmount(txn.amount)}`,
      {
        title: 'Delete transaction',
        confirmLabel: 'Delete',
        destructive: true
      }
    );
    if (!ok) {
      return;
    }
    try {
      await api.del(`/api/transactions/${txn.id}`);
      removeLocal(txn.id);
      if (onRefresh) onRefresh();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  async function handleResetField(txn, field) {
    try {
      const result = await api.post(`/api/transactions/${txn.id}/reset`, {
        fields: [field]
      });
      if (result?.transaction) replaceLocal(txn.id, result.transaction);
      if (onRefresh) onRefresh();
    } catch (err) {
      alert(err.message || 'Reset failed', { title: 'Reset failed' });
    }
  }

  async function handleViewRule(ruleId) {
    try {
      const data = await api.get(`/api/rules/${ruleId}`);
      if (!data?.rule) throw new Error('Rule not found.');
      setEditingRule(data.rule);
    } catch (err) {
      alert(err.message || 'Could not open rule', { title: 'Could Not Open Rule' });
    }
  }

  function handleMarkRecurring(txn) {
    setRecurringFromTxn({
      txn,
      form: formFromTransaction(txn, categoryById.get(txn.category_id))
    });
  }

  async function saveRecurringFromTxn(payload) {
    await api.post('/api/upcoming', payload);
    if (onRefresh) onRefresh();
  }

  return (
    <>
      <DashboardCard
        title="Recent Transactions"
        action={
          <Link to="/transactions" className="dashboard-card-link">
            See all
          </Link>
        }
      >
        {loading ? (
          <CardSkeleton />
        ) : items.length === 0 ? (
          <p className="subtle" style={{ margin: 0 }}>
            No transactions yet.
          </p>
        ) : (
          <ul className="txn-list dash-recent-txn-list">
            {items.map((t) => (
              <TransactionRow
                key={t.id}
                txn={t}
                expanded={expandedId === t.id}
                onExpand={() => setExpandedId(expandedId === t.id ? null : t.id)}
                account={accountById.get(t.account_id)}
                category={categoryById.get(t.category_id)}
                originalCategory={categoryById.get(t.original_category_id)}
                hideAccountInMeta={false}
                onEdit={() => setEditingTxn(t)}
                onCreateRule={() => setNewRuleFromTxn(t)}
                onMarkRecurring={() => handleMarkRecurring(t)}
                onToggleTransfer={() => handleToggle(t, 'is_transfer')}
                onToggleIgnored={() => handleToggle(t, 'is_ignored')}
                onToggleMhaEligible={() => handleToggle(t, 'mha_eligible')}
                onDelete={() => handleDelete(t)}
                onResetField={(field) => handleResetField(t, field)}
                onViewRule={handleViewRule}
                onLogoChanged={(updated) => replaceLocal(t.id, updated)}
                hideMerchantLogo
                mhaTrackerEnabled={mhaTrackerEnabled}
              />
            ))}
          </ul>
        )}
      </DashboardCard>

      {editingTxn && (
        <EditTransactionModal
          txn={editingTxn}
          categories={categories}
          onClose={() => setEditingTxn(null)}
          onSaved={(updated) => {
            if (updated) replaceLocal(editingTxn.id, updated);
            setEditingTxn(null);
            if (onRefresh) onRefresh();
          }}
          onReset={(field) => handleResetField(editingTxn, field)}
        />
      )}

      {newRuleFromTxn && (
        <RuleEditor
          rule={{
            conditions: [
              {
                field: 'merchant',
                operator: 'contains',
                value: (newRuleFromTxn.original_merchant || newRuleFromTxn.merchant || '').trim()
              }
            ],
            actions: [
              { type: 'rename', value: newRuleFromTxn.merchant || '' }
            ],
            enabled: true
          }}
          accounts={accounts}
          categories={categories}
          sourceTransaction={newRuleFromTxn}
          onClose={() => setNewRuleFromTxn(null)}
          onSaved={() => {
            setNewRuleFromTxn(null);
            if (onRefresh) onRefresh();
          }}
        />
      )}

      {editingRule && (
        <RuleEditor
          rule={editingRule}
          accounts={accounts}
          categories={categories}
          onClose={() => setEditingRule(null)}
          onSaved={() => {
            setEditingRule(null);
            if (onRefresh) onRefresh();
          }}
        />
      )}

      {recurringFromTxn && (
        <RecurringItemEditor
          item={{ form: recurringFromTxn.form }}
          title="Mark As Recurring"
          accounts={accounts}
          categories={categories}
          saveLabel="Add Recurring"
          onSave={saveRecurringFromTxn}
          onClose={() => setRecurringFromTxn(null)}
        />
      )}

      <Dialog />
    </>
  );
}

// ============================================================================
// Customize dashboard modal
// ============================================================================

function DashboardCustomizeModal({ layout, onChange, onClose }) {
  const sensors = useReorderSensors();
  const [draggingId, setDraggingId] = useState(null);
  useDragInteractionLock(!!draggingId);

  function toggleCard(id) {
    onChange((prev) =>
      normalizeDashboardLayout(prev).map((item) =>
        item.id === id ? { ...item, visible: !item.visible } : item
      )
    );
  }

  function handleDragEnd(event) {
    const { active, over } = event;
    setDraggingId(null);
    if (!over || active.id === over.id) return;
    onChange((prev) => {
      const normalized = normalizeDashboardLayout(prev);
      const oldIndex = normalized.findIndex((item) => item.id === active.id);
      const newIndex = normalized.findIndex((item) => item.id === over.id);
      if (oldIndex < 0 || newIndex < 0) return normalized;
      return arrayMove(normalized, oldIndex, newIndex);
    });
  }

  const normalizedLayout = normalizeDashboardLayout(layout);
  const cardById = new Map(DASHBOARD_CARD_DEFS.map((card) => [card.id, card]));

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <div className="modal-header">
            <h3>Customize My Dashboard</h3>
            <button type="button" className="modal-close" onClick={close} aria-label="Close">
              x
            </button>
          </div>
          <div className="dashboard-customize-modal">
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragStart={(event) => setDraggingId(event.active.id)}
              onDragCancel={() => setDraggingId(null)}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={normalizedLayout.map((item) => item.id)}
                strategy={verticalListSortingStrategy}
              >
                <div className="dashboard-customize-list">
                  {normalizedLayout.map((item) => {
                    const card = cardById.get(item.id);
                    if (!card) return null;
                    return (
                      <DashboardCustomizeRow
                        key={item.id}
                        id={item.id}
                        title={card.title}
                        description={card.description}
                        visible={item.visible}
                        onToggle={() => toggleCard(item.id)}
                      />
                    );
                  })}
                </div>
              </SortableContext>
            </DndContext>
          </div>
          <div className="modal-actions">
            <button type="button" className="btn-primary" onClick={close}>
              Done
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

function DashboardCustomizeRow({ id, title, description, visible, onToggle }) {
  return (
    <ReorderListItem
      id={id}
      className="dashboard-customize-row"
      handleLabel={`Move ${title}`}
      title={title}
      subtitle={description}
      side={(
        <label className="toggle-switch dashboard-card-toggle">
          <input
            type="checkbox"
            aria-label={`Show ${title}`}
            checked={visible}
            onChange={onToggle}
          />
          <span className="toggle-slider" aria-hidden="true" />
        </label>
      )}
    />
  );
}

// ============================================================================
// Shared card wrapper + progress bar + skeleton
// ============================================================================

function DashboardCard({ title, action, children }) {
  return (
    <section className="dashboard-card">
      <header className="dashboard-card-header">
        <h3>{title}</h3>
        {action}
      </header>
      <div className="dashboard-card-body">{children}</div>
    </section>
  );
}

function DashProgressBar({ percent, overBudget = false }) {
  const clamped = Math.max(0, Math.min(100, percent || 0));
  const label = `${Math.round(percent || 0)}%${overBudget ? ' over target' : ' complete'}`;
  return (
    <div
      className="budget-progress"
      role="progressbar"
      aria-valuenow={Math.round(percent || 0)}
      aria-valuemin="0"
      aria-valuemax="100"
      title={label}
    >
      <div
        className={`budget-progress-fill ${
          overBudget ? 'over' : percent >= 85 ? 'warning' : ''
        }`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}

function CardSkeleton() {
  return (
    <div className="dash-skeleton">
      <div className="dash-skeleton-line wide" />
      <div className="dash-skeleton-line" />
      <div className="dash-skeleton-line short" />
    </div>
  );
}
