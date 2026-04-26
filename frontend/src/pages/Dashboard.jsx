import { useEffect, useMemo, useState } from 'react';
import {
  DndContext,
  closestCenter
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import { RuleEditor } from '../components/rules/RuleEditor.jsx';
import {
  EditTransactionModal,
  TransactionRow
} from '../components/transactions/TransactionRow.jsx';
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

// ============================================================================
// Dashboard - v16
//
// "Where I stand right now" overview. Cards are user-configurable, stacked on
// mobile and flowing into a responsive grid on desktop. This stays frontend
// persisted for now and reuses existing API surfaces.
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

function formatSignedCompact(n) {
  if (n === 0) return formatCompactCurrency(0);
  const compact = formatCompactCurrency(Math.abs(n));
  return n < 0 ? `-${compact}` : `+${compact}`;
}

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

function greetingEmoji(d) {
  const hour = d.getHours();
  if (hour < 12) return '🌅';
  if (hour < 17) return '☀️';
  return '🌙';
}

function formatTransactionAmount(amount) {
  return formatSignedCurrency(amount);
}

function daysInCurrentMonth() {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
}

function currentDayOfMonth() {
  return new Date().getDate();
}

// Account type classification - grouped so the dashboard can show a clean
// breakdown regardless of how many individual accounts Neal has.
const ASSET_TYPES = new Set(['checking', 'savings', 'cash']);
const INVESTMENT_TYPES = new Set(['investment']);
const CREDIT_TYPES = new Set(['credit']);
const LOAN_TYPES = new Set(['loan']);
const DASHBOARD_LAYOUT_STORAGE_KEY = 'orbit-money-dashboard-layout-v2';
const BIGGEST_TRANSACTIONS_HIDDEN_KEY = 'orbit-money-biggest-transactions-hidden-v1';

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
    title: 'Biggest Transactions',
    description: 'Top 5 transactions with dashboard-only hiding.'
  },
  {
    id: 'subscriptions',
    title: 'Subscriptions / Recurring',
    description: 'Recurring merchants detected from history.'
  },
  {
    id: 'uncategorized',
    title: 'Uncategorized Transactions',
    description: 'Recent transactions that need a category.'
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
    description: 'Estimated recurring bills and income.'
  },
  {
    id: 'mortgage',
    title: 'Mortgage Snapshot',
    description: 'Home value, balance, and equity.'
  },
  {
    id: 'needs-attention',
    title: 'Needs Attention',
    description: 'A combined list of dashboard follow-ups.'
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

function readDashboardLayout() {
  try {
    return normalizeDashboardLayout(JSON.parse(localStorage.getItem(DASHBOARD_LAYOUT_STORAGE_KEY)));
  } catch {
    return normalizeDashboardLayout(null);
  }
}

function readHiddenBiggestTransactions() {
  try {
    const saved = JSON.parse(localStorage.getItem(BIGGEST_TRANSACTIONS_HIDDEN_KEY));
    return Array.isArray(saved) ? saved.map(Number).filter(Number.isFinite) : [];
  } catch {
    return [];
  }
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

function monthKeyFromDate(value) {
  return String(value || '').slice(0, 7);
}

function parseDateValue(value) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function daysBetween(a, b) {
  const start = parseDateValue(a);
  const end = parseDateValue(b);
  if (!start || !end) return null;
  return Math.round((end.getTime() - start.getTime()) / 86400000);
}

function addDays(value, amount) {
  const date = parseDateValue(value) || new Date();
  date.setDate(date.getDate() + amount);
  return date.toISOString().slice(0, 10);
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

function categoryName(categoryById, categoryId) {
  return categoryById.get(categoryId)?.name || 'Uncategorized';
}

function buildRecurringGroups(transactions) {
  const byMerchant = new Map();
  for (const txn of transactions || []) {
    if (txn.is_ignored || txn.is_transfer) continue;
    const merchant = String(txn.merchant || '').trim();
    if (!merchant) continue;
    const key = `${merchant.toLowerCase()}|${Math.sign(Number(txn.amount) || 0)}`;
    if (!byMerchant.has(key)) byMerchant.set(key, []);
    byMerchant.get(key).push(txn);
  }

  return Array.from(byMerchant.values())
    .map((items) => {
      const sorted = [...items].sort((a, b) => a.date.localeCompare(b.date));
      const months = new Set(sorted.map((txn) => monthKeyFromDate(txn.date)));
      if (sorted.length < 2 || months.size < 2) return null;

      const intervals = [];
      for (let i = 1; i < sorted.length; i += 1) {
        const interval = daysBetween(sorted[i - 1].date, sorted[i].date);
        if (interval !== null && interval > 0) intervals.push(interval);
      }
      const averageInterval = intervals.length
        ? intervals.reduce((sum, value) => sum + value, 0) / intervals.length
        : 30;
      if (averageInterval < 21 || averageInterval > 45) return null;

      const latest = sorted[sorted.length - 1];
      const nextDate = addDays(latest.date, Math.round(averageInterval));
      const averageAmount =
        sorted.reduce((sum, txn) => sum + Math.abs(Number(txn.amount) || 0), 0) /
        sorted.length;
      return {
        merchant: latest.merchant,
        amount: averageAmount,
        direction: Number(latest.amount) < 0 ? 'expense' : 'income',
        category_id: latest.category_id,
        count: sorted.length,
        latestDate: latest.date,
        nextDate
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.nextDate.localeCompare(b.nextDate));
}

// ============================================================================
// Main page
// ============================================================================

export default function Dashboard({ accounts = [], categories = [], mhaTrackerEnabled = false }) {
  const navigate = useNavigate();

  const [budgetData, setBudgetData] = useState(null);
  const [previousBudgetData, setPreviousBudgetData] = useState(null);
  const [recent, setRecent] = useState([]);
  const [dashboardTransactions, setDashboardTransactions] = useState([]);
  const [uncategorizedData, setUncategorizedData] = useState(null);
  const [goalsData, setGoalsData] = useState(null);
  const [householdData, setHouseholdData] = useState(null);
  const [mhaData, setMhaData] = useState(null);
  const [dashboardLayout, setDashboardLayout] = useState(readDashboardLayout);
  const [customizing, setCustomizing] = useState(false);
  const [hiddenBiggestTransactionIds, setHiddenBiggestTransactionIds] = useState(
    readHiddenBiggestTransactions
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
      const [b, t, prevBudget, txns, uncategorized, goals, household, mha] = await Promise.all([
        api.get(`/api/budgets?month=${month}`),
        api.get('/api/transactions?limit=10&page=1'),
        optional(`/api/budgets?month=${previousMonth}`),
        optional('/api/transactions?limit=200&page=1&include_ignored=0&include_transfers=0&sort=date_desc'),
        optional('/api/transactions?limit=5&page=1&categories=uncategorized&include_ignored=0&include_transfers=0&sort=date_desc'),
        optional('/api/goals?months=12'),
        optional('/api/household'),
        optional('/api/mha')
      ]);
      setBudgetData(b);
      setRecent(t.items || []);
      setPreviousBudgetData(prevBudget);
      setDashboardTransactions(txns?.items || []);
      setUncategorizedData(uncategorized);
      setGoalsData(goals);
      setHouseholdData(household);
      setMhaData(mha);
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
    try {
      localStorage.setItem(DASHBOARD_LAYOUT_STORAGE_KEY, JSON.stringify(dashboardLayout));
    } catch {
      /* ignore */
    }
  }, [dashboardLayout]);

  useEffect(() => {
    try {
      localStorage.setItem(
        BIGGEST_TRANSACTIONS_HIDDEN_KEY,
        JSON.stringify(hiddenBiggestTransactionIds)
      );
    } catch {
      /* ignore */
    }
  }, [hiddenBiggestTransactionIds]);

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

  const topSpendingMax = topSpending.length > 0 ? topSpending[0].spent : 0;

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
  const budgetUsed =
    overallPercent !== null ? `${Math.round(overallPercent)}%` : 'Not set';
  const daysRemaining = Math.max(0, totalDays - dayOfMonth);
  const dailySpendPace =
    summary && summary.total_budgeted > 0 && daysRemaining > 0
      ? (summary.total_budgeted - totalSpent) / daysRemaining
      : null;
  const previousSummary = previousBudgetData?.summary;
  const recurringGroups = useMemo(
    () => buildRecurringGroups(dashboardTransactions),
    [dashboardTransactions]
  );
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
  const needsAttention = useMemo(() => {
    const rows = [];
    const uncategorizedCount = Number(uncategorizedData?.total || 0);
    if (uncategorizedCount > 0) {
      rows.push({
        label: 'Uncategorized',
        detail: `${uncategorizedCount} transaction${uncategorizedCount === 1 ? '' : 's'}`,
        to: '/transactions?categories=uncategorized&include_ignored=0&include_transfers=0'
      });
    }
    const overBudget = (budgetData?.budgeted || []).filter(
      (item) => item.amount > 0 && item.spent > item.amount
    );
    if (overBudget.length > 0) {
      rows.push({
        label: 'Over Budget',
        detail: `${overBudget.length} categor${overBudget.length === 1 ? 'y' : 'ies'}`,
        to: '/budgets'
      });
    }
    const closeBudget = (budgetData?.budgeted || []).filter(
      (item) => item.amount > 0 && item.spent <= item.amount && item.spent / item.amount >= 0.85
    );
    if (closeBudget.length > 0) {
      rows.push({
        label: 'Almost Over Budget',
        detail: `${closeBudget.length} categor${closeBudget.length === 1 ? 'y' : 'ies'}`,
        to: '/budgets'
      });
    }
    const stalledGoals = (goalsData?.goals || []).filter(
      (goal) => goal.progress_percent < 100 && goal.eta?.status === 'stalled'
    );
    if (stalledGoals.length > 0) {
      rows.push({
        label: 'Stalled Goals',
        detail: `${stalledGoals.length} goal${stalledGoals.length === 1 ? '' : 's'}`,
        to: '/goals'
      });
    }
    return rows.slice(0, 6);
  }, [budgetData, goalsData, uncategorizedData]);
  const visibleDashboardCards = dashboardLayout.filter((item) => item.visible);

  function hideBiggestTransaction(id) {
    setHiddenBiggestTransactionIds((prev) =>
      prev.includes(id) ? prev : [...prev, id]
    );
  }

  function resetHiddenBiggestTransactions() {
    setHiddenBiggestTransactionIds([]);
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
            max={topSpendingMax}
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
            groups={recurringGroups.filter((group) => group.direction === 'expense').slice(0, 5)}
            categoryById={categoryById}
            loading={loading && dashboardTransactions.length === 0}
          />
        );
      case 'uncategorized':
        return (
          <UncategorizedCard
            data={uncategorizedData}
            categoryById={categoryById}
            loading={loading && !uncategorizedData}
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
        return <GoalFocusCard goalsData={goalsData} loading={loading && !goalsData} />;
      case 'retirement':
        return <RetirementSnapshotCard householdData={householdData} loading={loading && !householdData} />;
      case 'mha':
        return <MhaSummaryCard data={mhaData} enabled={mhaTrackerEnabled} loading={loading && !mhaData} />;
      case 'upcoming':
        return (
          <UpcomingCard
            groups={recurringGroups.slice(0, 8)}
            categoryById={categoryById}
            loading={loading && dashboardTransactions.length === 0}
          />
        );
      case 'mortgage':
        return <MortgageSnapshotCard accounts={mortgageAccounts} loading={loading && accounts.length === 0} />;
      case 'needs-attention':
        return <NeedsAttentionCard items={needsAttention} loading={loading && !budgetData} />;
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
          stats={[
            { label: 'Net worth', value: formatCurrency(0, { maximumFractionDigits: 0 }) },
            { label: 'Monthly net', value: formatCompactCurrency(0) },
            { label: 'Accounts', value: '0' }
          ]}
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
        stats={[
          { label: 'Net worth', value: formatCurrency(totals.net, { maximumFractionDigits: 0 }), tone: totals.net >= 0 ? 'good' : 'caution' },
          { label: 'Monthly net', value: summary ? formatSignedCompact(summary.total_net) : 'Loading', tone: summary ? (summary.total_net >= 0 ? 'good' : 'caution') : '' },
          { label: 'Budget used', value: budgetUsed, tone: overallPercent > 100 ? 'caution' : overallPercent >= 85 ? 'warn' : 'good' },
          { label: 'Accounts', value: activeAccountCount.toLocaleString() }
        ]}
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
          onChange={setDashboardLayout}
          onClose={() => setCustomizing(false)}
        />
      )}
    </div>
  );
}

function DashboardHero({ dateLabel, greeting, stats }) {
  const navigate = useNavigate();

  return (
    <PageHero
      id="dashboard-title"
      variant="dashboard"
      kicker="Financial Orbit"
      title="Dashboard"
      subtitle={`${dateLabel} · ${greeting} ${greetingEmoji(new Date())}`}
      stats={stats}
      initialHeight={420}
      statLabel="Dashboard summary"
      chrome={(hero) => (
        <div className="page-hero-chrome">
          <button
            type="button"
            className="hero-brand brand-home"
            onClick={() => navigate('/dashboard')}
            aria-label="Go to dashboard"
          >
            <span className="brand-mark">$</span>
            <span className="brand-name">Orbit Money</span>
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
  if (totals.investments !== 0)
    rows.push({ label: 'Investments', value: totals.investments });
  if (totals.credit !== 0)
    rows.push({ label: 'Credit cards', value: totals.credit, isDebt: true });
  if (totals.loans !== 0)
    rows.push({ label: 'Loans', value: totals.loans, isDebt: true });
  if (totals.realEstate !== 0)
    rows.push({ label: 'Real Estate', value: totals.realEstate });
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
            <div
              className={`dash-networth-value ${netPositive ? 'income' : 'expense'}`}
            >
              {formatCurrency(totals.net)}
            </div>
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
                    {formatCurrency(r.value)}
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
              <div className="dash-month-stat-value income">
                {formatCompactCurrency(summary.total_income)}
              </div>
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Expenses</div>
              <div className="dash-month-stat-value expense">
                {formatCompactCurrency(Math.abs(summary.total_expenses))}
              </div>
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Net</div>
              <div
                className={`dash-month-stat-value ${
                  summary.total_net >= 0 ? 'income' : 'expense'
                }`}
              >
                {formatCompactCurrency(Math.abs(summary.total_net))}
              </div>
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
            <span>Daily spend pace</span>
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

function TopSpendingCard({ topSpending, max, loading }) {
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
        <ul className="dash-top-list">
          {topSpending.map((item) => {
            const pct = max > 0 ? (item.spent / max) * 100 : 0;
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
                  <span className="dash-top-amount">{formatCurrency(item.spent)}</span>
                </div>
                <div className="dash-top-barwrap">
                  <div
                    className="dash-top-bar"
                    style={{
                      width: `${pct}%`,
                      background: item.category.color || 'var(--accent)'
                    }}
                  />
                </div>
              </li>
            );
          })}
        </ul>
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
      title="Biggest Transactions"
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
          <Link to="/transactions?sort=abs_amount_desc" className="dashboard-card-link">
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
                  Hide From Card
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

function RecurringCard({ groups, categoryById, loading }) {
  return (
    <DashboardCard
      title="Subscriptions / Recurring"
      action={<Link to="/transactions" className="dashboard-card-link">Review</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : groups.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No recurring expenses detected yet.
        </p>
      ) : (
        <ul className="dash-compact-list">
          {groups.map((group) => (
            <li key={`${group.merchant}-${group.amount}`} className="dash-compact-row">
              <div className="dash-compact-main">
                <strong>{group.merchant}</strong>
                <span>{categoryName(categoryById, group.category_id)} - {group.count} hits</span>
              </div>
              <div className="dash-compact-side">
                <strong className="expense">{formatCurrency(group.amount)}</strong>
                <em>Next {formatShortDate(group.nextDate)}</em>
              </div>
            </li>
          ))}
        </ul>
      )}
    </DashboardCard>
  );
}

function UpcomingCard({ groups, categoryById, loading }) {
  const upcoming = groups
    .filter((group) => group.nextDate >= new Date().toISOString().slice(0, 10))
    .sort((a, b) => a.nextDate.localeCompare(b.nextDate))
    .slice(0, 6);

  return (
    <DashboardCard
      title="Upcoming: Bills and Income"
      action={<Link to="/transactions" className="dashboard-card-link">History</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : upcoming.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          No upcoming recurring activity detected.
        </p>
      ) : (
        <ul className="dash-compact-list">
          {upcoming.map((group) => (
            <li key={`${group.merchant}-${group.direction}`} className="dash-compact-row">
              <div className="dash-compact-main">
                <strong>{group.merchant}</strong>
                <span>{categoryName(categoryById, group.category_id)}</span>
              </div>
              <div className="dash-compact-side">
                <strong className={group.direction === 'income' ? 'income' : 'expense'}>
                  {group.direction === 'income' ? '+' : '-'}{formatCurrency(group.amount)}
                </strong>
                <em>{formatShortDate(group.nextDate)}</em>
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
          to="/transactions?categories=uncategorized&include_ignored=0&include_transfers=0"
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
          {formatShortMonth(month)}
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
        <ul className="dash-compare-list">
          {rows.map((row) => {
            const current = Number(row.current || 0);
            const previous = Number(row.previous || 0);
            const delta = current - previous;
            return (
              <li key={row.label}>
                <span>{row.label}</span>
                <strong>{formatCurrency(current)}</strong>
                <em className={delta < 0 ? 'income' : delta > 0 ? 'expense' : ''}>
                  {delta === 0 ? 'No change' : `${delta > 0 ? '+' : ''}${formatCurrency(delta)}`}
                </em>
              </li>
            );
          })}
        </ul>
      )}
      <p className="dash-card-note">
        Compared with {formatMonthKeyLabel(previousMonth)}.
      </p>
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

function GoalFocusCard({ goalsData, loading }) {
  const goals = goalsData?.goals || [];
  const goal =
    goals.find((item) => item.progress_percent < 100 && item.eta?.status === 'stalled') ||
    goals.find((item) => item.progress_percent < 100) ||
    goals[0];

  return (
    <DashboardCard
      title="Goal Focus"
      action={<Link to="/goals" className="dashboard-card-link">Open</Link>}
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
  );
}

function RetirementSnapshotCard({ householdData, loading }) {
  const summary = householdData?.summary;
  const annualContributions =
    Number(summary?.employee_retirement_annual || 0) +
    Number(summary?.employer_retirement_annual || 0);

  return (
    <DashboardCard
      title="Retirement Snapshot"
      action={<Link to="/household" className="dashboard-card-link">Household</Link>}
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
            <span>Linked balance</span>
            <strong>{formatCurrency(summary.retirement_account_balance || 0)}</strong>
            <em>{summary.linked_retirement_account_count || 0} accounts</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>Annual contributions</span>
            <strong>{formatCurrency(annualContributions)}</strong>
            <em>Employee + match</em>
          </div>
          <div className="dash-mini-stat-row">
            <span>HSA balance</span>
            <strong>{formatCurrency(summary.hsa_account_balance || 0)}</strong>
            <em>Linked accounts</em>
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
      action={<Link to="/mha-tracker" className="dashboard-card-link">MHA</Link>}
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

function NeedsAttentionCard({ items, loading }) {
  return (
    <DashboardCard
      title="Needs Attention"
      action={<Link to="/transactions" className="dashboard-card-link">Review</Link>}
    >
      {loading ? (
        <CardSkeleton />
      ) : items.length === 0 ? (
        <p className="subtle" style={{ margin: 0 }}>
          Nothing urgent on the dashboard right now.
        </p>
      ) : (
        <ul className="dash-compact-list">
          {items.map((item) => (
            <li key={item.label} className="dash-compact-row">
              <div className="dash-compact-main">
                <strong>{item.label}</strong>
                <span>{item.detail}</span>
              </div>
              <Link to={item.to} className="dashboard-card-link">Open</Link>
            </li>
          ))}
        </ul>
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
                onToggleTransfer={() => handleToggle(t, 'is_transfer')}
                onToggleIgnored={() => handleToggle(t, 'is_ignored')}
                onToggleMhaEligible={() => handleToggle(t, 'mha_eligible')}
                onDelete={() => handleDelete(t)}
                onResetField={(field) => handleResetField(t, field)}
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
          onClose={() => setNewRuleFromTxn(null)}
          onSaved={() => {
            setNewRuleFromTxn(null);
            if (onRefresh) onRefresh();
          }}
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
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`dashboard-customize-row ${isDragging ? 'dragging' : ''}`}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        className="drag-grip reorder-drag-handle"
        aria-label={`Move ${title}`}
        {...attributes}
        {...listeners}
      >
        <span aria-hidden="true">::</span>
      </button>
      <div className="dashboard-customize-copy">
        <strong>{title}</strong>
        <span>{description}</span>
      </div>
      <label className="toggle-switch dashboard-card-toggle">
        <input
          type="checkbox"
          aria-label={`Show ${title}`}
          checked={visible}
          onChange={onToggle}
        />
        <span className="toggle-slider" aria-hidden="true" />
      </label>
    </div>
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
  return (
    <div
      className="budget-progress"
      role="progressbar"
      aria-valuenow={Math.round(percent || 0)}
      aria-valuemin="0"
      aria-valuemax="100"
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
