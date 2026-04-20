import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../api.js';
import { TransactionRow, EditTransactionModal } from './Transactions.jsx';
import { RuleEditor } from './Rules.jsx';

// ============================================================================
// Dashboard — v16
//
// "Where I stand right now" overview. Six cards, stacked on mobile and
// flowing into a responsive grid on desktop. No new backend: reuses
// /api/accounts, /api/budgets?month=<current>, and /api/transactions in
// parallel.
//
// Account-type groupings (fed into the Accounts card):
//   Assets     = checking + savings + cash + investment + other
//   Credit     = credit   (shown as "owed" — abs value of sum)
//   Loans      = loan (shown as "owed")
//   Real Estate = estimated mortgage home value minus mortgage balance
//   Net worth  = assets/debts plus real estate equity
//
// This assumes balances are stored with the conventional signs (debts
// negative, assets positive) — SimpleFIN's norm. If a bank reports
// otherwise, the displayed groups may look off but net worth is still
// the literal sum.
// ============================================================================

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const DAY_NAMES = [
  'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'
];

// ---------- Formatting ----------

function formatMoney(n) {
  return Number(n).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
}

function formatMoneyCompact(n) {
  const abs = Math.abs(n);
  if (abs >= 1_000_000) {
    return `${n < 0 ? '−' : ''}$${(abs / 1_000_000).toFixed(1)}M`;
  }
  if (abs >= 10_000) {
    return `${n < 0 ? '−' : ''}$${(abs / 1_000).toFixed(1)}k`;
  }
  return Number(n).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  });
}

function formatMoneyWhole(n) {
  return Number(n).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  });
}

function formatSignedMoney(n) {
  if (n === 0) return formatMoney(0);
  const abs = Math.abs(n);
  const formatted = abs.toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
  return n < 0 ? `−${formatted}` : `+${formatted}`;
}

function formatSignedCompact(n) {
  if (n === 0) return formatMoneyCompact(0);
  const compact = formatMoneyCompact(Math.abs(n));
  return n < 0 ? `−${compact}` : `+${compact}`;
}

function parseLocalDate(iso) {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function formatDayMonth(iso) {
  const dt = parseLocalDate(iso);
  if (!dt) return '';
  return dt.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function formatLongDate(d) {
  return `${DAY_NAMES[d.getDay()]}, ${MONTH_NAMES[d.getMonth()]} ${d.getDate()}`;
}

function timeGreeting(d) {
  const hour = d.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

function useCollapsedHero() {
  const [collapsed, setCollapsed] = useState(false);
  const collapsedRef = useRef(false);

  useEffect(() => {
    function update() {
      const shouldCollapse = collapsedRef.current
        ? window.scrollY > 28
        : window.scrollY > 150;

      if (shouldCollapse !== collapsedRef.current) {
        collapsedRef.current = shouldCollapse;
        setCollapsed(shouldCollapse);
      }
    }

    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);

  return collapsed;
}

function formatTransactionAmount(amount) {
  const abs = Math.abs(amount).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
  return amount < 0 ? `−${abs}` : `+${abs}`;
}

function currentMonthKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function daysInCurrentMonth() {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
}

function currentDayOfMonth() {
  return new Date().getDate();
}

// Account type classification — grouped so the dashboard can show a clean
// breakdown regardless of how many individual accounts Neal has.
const ASSET_TYPES = new Set(['checking', 'savings', 'cash']);
const INVESTMENT_TYPES = new Set(['investment']);
const CREDIT_TYPES = new Set(['credit']);
const LOAN_TYPES = new Set(['loan']);

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

// ============================================================================
// Main page
// ============================================================================

export default function Dashboard({ accounts = [], categories = [], onOpenMenu }) {
  const navigate = useNavigate();
  const heroCollapsed = useCollapsedHero();

  const [budgetData, setBudgetData] = useState(null);
  const [recent, setRecent] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const month = currentMonthKey();

  async function loadDashboard() {
    setLoading(true);
    setError('');
    try {
      const [b, t] = await Promise.all([
        api.get(`/api/budgets?month=${month}`),
        api.get('/api/transactions?limit=10&page=1')
      ]);
      setBudgetData(b);
      setRecent(t.items || []);
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
      if (picks.length >= 3) break;
    }
    for (const b of closeToOver) {
      if (picks.length >= 3) break;
      picks.push({ ...b, status: 'warning' });
    }
    return picks;
  }, [budgetData]);

  const summary = budgetData?.summary;
  const overallPercent =
    summary && summary.total_budgeted > 0
      ? (summary.total_spent_in_budgets / summary.total_budgeted) * 100
      : null;

  const dayOfMonth = currentDayOfMonth();
  const totalDays = daysInCurrentMonth();
  const budgetUsed =
    overallPercent !== null ? `${Math.round(overallPercent)}%` : 'Not set';

  // --- First-time onboarding ------------------------------------------------
  const noActivity = !loading && recent.length === 0 && activeAccountCount === 0;
  if (noActivity) {
    return (
      <div className="dashboard-view">
        <DashboardHero
          dateLabel={formatLongDate(now)}
          greeting={timeGreeting(now)}
          collapsed={heroCollapsed}
          onOpenMenu={onOpenMenu}
          stats={[
            { label: 'Net worth', value: formatMoneyWhole(0) },
            { label: 'Monthly net', value: formatMoneyCompact(0) },
            { label: 'Accounts', value: '0' }
          ]}
        />
        <div className="empty-state">
          <div className="empty-state-icon">◯</div>
          <h2>Nothing to show yet</h2>
          <p>
            Import your Rocket Money history or connect SimpleFIN to start
            seeing a dashboard.
          </p>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
            <button
              type="button"
              className="btn-primary"
              onClick={() => navigate('/import')}
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
        collapsed={heroCollapsed}
        onOpenMenu={onOpenMenu}
        stats={[
          { label: 'Net worth', value: formatMoneyWhole(totals.net), tone: totals.net >= 0 ? 'good' : 'caution' },
          { label: 'Monthly net', value: summary ? formatSignedCompact(summary.total_net) : 'Loading', tone: summary ? (summary.total_net >= 0 ? 'good' : 'caution') : '' },
          { label: 'Budget used', value: budgetUsed, tone: overallPercent > 100 ? 'caution' : overallPercent >= 85 ? 'warn' : 'good' },
          { label: 'Accounts', value: activeAccountCount.toLocaleString() }
        ]}
      />

      {error && <div className="error">{error}</div>}

      <div className="dashboard-grid">
        {/* ============ Accounts ============ */}
        <AccountsCard
          totals={totals}
          activeCount={activeAccountCount}
          loading={loading && accounts.length === 0}
        />

        {/* ============ This month ============ */}
        <MonthCard
          summary={summary}
          dayOfMonth={dayOfMonth}
          totalDays={totalDays}
          loading={loading && !summary}
        />

        {/* ============ Budget pulse ============ */}
        <BudgetPulseCard
          summary={summary}
          attention={budgetAttention}
          overallPercent={overallPercent}
          totalBudgeted={summary?.total_budgeted || 0}
          loading={loading && !budgetData}
        />

        {/* ============ Top spending ============ */}
        <TopSpendingCard
          topSpending={topSpending}
          max={topSpendingMax}
          loading={loading && !budgetData}
        />

        {/* ============ Recent transactions ============ */}
        <RecentActivityCard
          transactions={recent}
          accountById={accountById}
          categoryById={categoryById}
          accounts={accounts}
          categories={categories}
          loading={loading && recent.length === 0}
          onRefresh={loadDashboard}
        />
      </div>
    </div>
  );
}

function DashboardHero({ dateLabel, greeting, stats, collapsed, onOpenMenu }) {
  const navigate = useNavigate();

  return (
    <section className={`page-hero page-hero-dashboard ${collapsed ? 'collapsed' : ''}`} aria-labelledby="dashboard-title">
      <div className="page-hero-inner">
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
          <div className="page-hero-compact-title">Dashboard</div>
          <button
            type="button"
            className="btn-icon hero-menu-button"
            onClick={onOpenMenu}
            aria-label="Open menu"
          >
            ☰
          </button>
        </div>

        <div className="page-hero-content">
          <div className="page-hero-main">
            <div className="page-kicker">Financial Orbit</div>
            <h2 id="dashboard-title">Dashboard</h2>
            <p>{dateLabel} · {greeting}</p>
          </div>

          <div className="page-hero-stats" aria-label="Dashboard summary">
            {stats.map((stat) => (
              <div key={stat.label} className={`page-hero-stat ${stat.tone || ''}`}>
                <span>{stat.label}</span>
                <strong>{stat.value}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
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
      action={<Link to="/accounts" className="dashboard-card-link">See all →</Link>}
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
              {formatMoney(totals.net)}
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
                    {formatMoney(r.value)}
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
          Details →
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
                {formatMoneyCompact(summary.total_income)}
              </div>
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Expenses</div>
              <div className="dash-month-stat-value expense">
                {formatMoneyCompact(Math.abs(summary.total_expenses))}
              </div>
            </div>
            <div className="dash-month-stat">
              <div className="dash-month-stat-label">Net</div>
              <div
                className={`dash-month-stat-value ${
                  summary.total_net >= 0 ? 'income' : 'expense'
                }`}
              >
                {formatMoneyCompact(Math.abs(summary.total_net))}
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

function BudgetPulseCard({ summary, attention, overallPercent, totalBudgeted, loading }) {
  const hasBudgets = totalBudgeted > 0;

  return (
    <DashboardCard
      title="Budget pulse"
      action={
        <Link to="/budgets" className="dashboard-card-link">
          Manage →
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
              {formatMoney(summary.total_spent_in_budgets)} of{' '}
              {formatMoney(summary.total_budgeted)} used
            </div>
            <div className="dash-budget-overall-percent">
              {Math.round(overallPercent)}%
            </div>
          </div>
          <DashProgressBar
            percent={overallPercent}
            overBudget={overallPercent > 100}
          />

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
              ✓ All budgets comfortably on track.
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
          to={`/transactions?date_from=${currentMonthKey()}-01&type=expense&sort=abs_amount_desc`}
          className="dashboard-card-link"
        >
          Browse →
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
                  <span className="dash-top-amount">{formatMoney(item.spent)}</span>
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
// Recent transactions card
//
// Uses the same TransactionRow component as the Transactions page so the
// expand/hover/edit UX is identical. Full edit, rule-from-transaction,
// transfer/ignore toggle, and delete are all wired up — mutations refresh
// the local list so the dashboard stays current without a full reload.
// ============================================================================

function RecentActivityCard({
  transactions,
  accountById,
  categoryById,
  accounts,
  categories,
  loading,
  onRefresh
}) {
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
      alert(err.message || 'Toggle failed');
    }
  }

  async function handleDelete(txn) {
    if (!confirm(`Delete this transaction? "${txn.merchant}" for ${formatTransactionAmount(txn.amount)}`)) {
      return;
    }
    try {
      await api.del(`/api/transactions/${txn.id}`);
      removeLocal(txn.id);
      if (onRefresh) onRefresh();
    } catch (err) {
      alert(err.message || 'Delete failed');
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
      alert(err.message || 'Reset failed');
    }
  }

  return (
    <>
      <DashboardCard
        title="Recent Transactions"
        action={
          <Link to="/transactions" className="dashboard-card-link">
            See all →
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
                onDelete={() => handleDelete(t)}
                onResetField={(field) => handleResetField(t, field)}
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
    </>
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
