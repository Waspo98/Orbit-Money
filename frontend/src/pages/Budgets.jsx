import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppSelect from '../components/AppSelect.jsx';
import CollapseIndicator from '../components/CollapseIndicator.jsx';
import DropdownMenu from '../components/DropdownMenu.jsx';
import PageHero from '../components/PageHero.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import { formatCurrency } from '../lib/formatters.js';
import {
  addMonthsToLocalMonth,
  daysLeftInLocalMonth,
  formatLocalMonth,
  formatMonthDay,
  formatMonthKeyLabel,
  getLocalMonthBounds
} from '../lib/localDate.js';

// ============================================================================
// Budgets — v15
//
// Budgets are global per category: one amount per category, applied to
// every month. Editing the amount for Groceries updates the cap everywhere
// — past, present, and future.
//
// The Budgets page still shows data per-month (spending, progress, totals)
// because spending is inherently monthly. Month navigation lets you flip
// through to see how past months measured up against the same caps.
// ============================================================================

function currentMonth() {
  return formatLocalMonth();
}

function addMonths(m, delta) {
  return addMonthsToLocalMonth(m, delta);
}

function monthBounds(m) {
  return getLocalMonthBounds(m);
}

function formatMonthLabel(m) {
  return formatMonthKeyLabel(m);
}

function daysLeftInMonth(m) {
  return daysLeftInLocalMonth(m);
}

function formatMoney(n) {
  return formatCurrency(n);
}

function formatTransactionDate(date) {
  return formatMonthDay(date);
}

const BUDGET_SORT_OPTIONS = [
  { value: 'pct_desc', label: '% Spent High to Low' },
  { value: 'pct_asc', label: '% Spent Low to High' },
  { value: 'spent_desc', label: '$ Spent High to Low' },
  { value: 'spent_asc', label: '$ Spent Low to High' }
];

// ============================================================================
// Main page
// ============================================================================

export default function Budgets() {
  const { alert, confirm, Dialog } = useAppDialog();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedMonth = searchParams.get('month') || currentMonth();

  const [data, setData] = useState(null);
  // `loading` = "we have never loaded data yet" (initial spinner).
  // `refreshing` = "a background refetch is in flight" (no spinner — keeps
  // old content on screen so the page height doesn't collapse). Mobile
  // browsers that watch for rapid document-height changes can latch onto a
  // small viewport zoom when content shrinks to a spinner then grows back;
  // keeping the old content visible during a month-change refetch avoids
  // that layout shift entirely.
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const [monthsWithActivity, setMonthsWithActivity] = useState([]);

  const [addingBudget, setAddingBudget] = useState(false);
  const [editingBudget, setEditingBudget] = useState(null);
  const [showInactive, setShowInactive] = useState(false);
  const [selectedSpendingKey, setSelectedSpendingKey] = useState(null);
  const [expandedBudgetKey, setExpandedBudgetKey] = useState(null);
  const [budgetTransactions, setBudgetTransactions] = useState({});

  // Sort order for the Budgeted section. Persisted in localStorage so a
  // user's choice sticks across navigations without needing a URL param.
  // Default matches the previous backend-sorted behavior (most over-budget
  // first = highest % spent first).
  const [budgetedSort, setBudgetedSort] = useState(() => {
    try {
      return localStorage.getItem('orbit-money-budgeted-sort') || 'pct_desc';
    } catch {
      return 'pct_desc';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('orbit-money-budgeted-sort', budgetedSort);
    } catch {
      /* ignore */
    }
  }, [budgetedSort]);

  async function load() {
    // First load: show the spinner. Subsequent loads (month changes, after
    // budget edits): silent refresh — old content stays on screen until
    // new data arrives, so the document height never collapses.
    const isFirstLoad = data == null;
    if (isFirstLoad) setLoading(true);
    else setRefreshing(true);
    setError('');
    try {
      const d = await api.get(
        `/api/budgets?month=${encodeURIComponent(selectedMonth)}`
      );
      setData(d);
    } catch (err) {
      setError(err.message || 'Failed to load budgets');
    } finally {
      if (isFirstLoad) setLoading(false);
      else setRefreshing(false);
    }
  }

  async function loadMonths() {
    try {
      const d = await api.get('/api/budgets/months');
      setMonthsWithActivity(d.items || []);
    } catch {
      /* non-fatal — arrow nav still works */
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedMonth]);

  useEffect(() => {
    loadMonths();
  }, []);

  useEffect(() => {
    setExpandedBudgetKey(null);
    setBudgetTransactions({});
  }, [selectedMonth]);

  function goToMonth(m) {
    const next = new URLSearchParams(searchParams);
    if (m === currentMonth()) next.delete('month');
    else next.set('month', m);
    setSearchParams(next);
  }

  // --- Month picker options: months with activity + current month + 3 ahead.
  // Sorted newest-first so the list reads naturally.
  const monthOptions = useMemo(() => {
    const set = new Set(monthsWithActivity);
    set.add(currentMonth());
    // Forward buffer for planning future months.
    for (let i = 1; i <= 3; i++) set.add(addMonths(currentMonth(), i));
    // Always include selectedMonth in case it's an unusual value.
    set.add(selectedMonth);
    return Array.from(set).sort((a, b) => b.localeCompare(a));
  }, [monthsWithActivity, selectedMonth]);

  const canGoForward = selectedMonth < addMonths(currentMonth(), 12);
  const isCurrentMonth = selectedMonth === currentMonth();

  // --- Summary derived ---
  const summary = data?.summary;
  const totalMonthlySpending = summary ? Number(summary.total_expenses || 0) : 0;
  const overallPercent =
    summary && summary.total_budgeted > 0
      ? (totalMonthlySpending / summary.total_budgeted) * 100
      : null;
  const remaining =
    summary && summary.total_budgeted > 0
      ? summary.total_budgeted - totalMonthlySpending
      : null;

  const daysLeft = daysLeftInMonth(selectedMonth);
  const budgetSubtitle = `${isCurrentMonth ? 'This month' : formatMonthLabel(selectedMonth)}${
    daysLeft !== null
      ? ` · ${
          daysLeft === 0
            ? 'last day of the month'
            : `${daysLeft} ${daysLeft === 1 ? 'day' : 'days'} left`
        }`
      : ''
  }`;
  const budgetHeroStats = [
    {
      label: 'Income',
      value: summary ? formatMoney(summary.total_income) : '—',
      tone: 'good'
    },
    {
      label: 'Expenses',
      value: summary ? formatMoney(summary.total_expenses) : '—',
      tone: 'caution'
    },
    {
      label: 'Net',
      value: summary ? formatMoney(summary.total_net) : '—',
      tone: summary && summary.total_net < 0 ? 'caution' : 'good'
    },
    {
      label: 'Remaining',
      value: remaining !== null ? formatMoney(remaining) : '—',
      tone: remaining !== null && remaining < 0 ? 'caution' : 'good'
    }
  ];

  const hasAnyBudgets = (data?.budgeted?.length || 0) > 0;
  const hasAnyActivity =
    (data?.budgeted?.length || 0) > 0 ||
    (data?.unbudgeted?.length || 0) > 0;

  // Sort the budgeted list based on the user's chosen order. Categories
  // with no budgeted amount (amount <= 0) sort to the end regardless —
  // their "percent" is meaningless.
  const sortedBudgeted = useMemo(() => {
    const list = [...(data?.budgeted || [])];
    const percent = (i) => (i.amount > 0 ? i.spent / i.amount : -Infinity);
    const cmpPct = (a, b) => percent(a) - percent(b);
    const cmpSpent = (a, b) => a.spent - b.spent;
    switch (budgetedSort) {
      case 'pct_asc':    list.sort(cmpPct); break;
      case 'spent_desc': list.sort((a, b) => cmpSpent(b, a)); break;
      case 'spent_asc':  list.sort(cmpSpent); break;
      case 'pct_desc':
      default:           list.sort((a, b) => cmpPct(b, a)); break;
    }
    return list;
  }, [data, budgetedSort]);

  const spendingByCategory = useMemo(() => {
    const rows = [
      ...(data?.budgeted || []),
      ...(data?.unbudgeted || [])
    ]
      .filter((item) => Number(item.spent) > 0)
      .map((item) => ({
        key: String(item.category.id),
        name: item.category.name,
        icon: item.category.icon,
        color: item.category.color || 'var(--accent)',
        amount: Number(item.spent)
      }))
      .sort((a, b) => {
        const aUncategorized = a.name.toLowerCase() === 'uncategorized';
        const bUncategorized = b.name.toLowerCase() === 'uncategorized';
        if (aUncategorized !== bUncategorized) return aUncategorized ? 1 : -1;
        return b.amount - a.amount;
      });

    const total = rows.reduce((sum, item) => sum + item.amount, 0);
    return { items: rows, total };
  }, [data]);

  // --- Mutations ---
  async function upsertBudget({ category_id, amount }) {
    await api.put('/api/budgets', { category_id, amount });
    await load();
    await loadMonths();
  }

  async function deleteBudget(budgetId) {
    const ok = await confirm('Delete this budget? It will be removed for every month.', {
      title: 'Delete budget',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/budgets/${budgetId}`);
      await load();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  async function loadBudgetTransactions(item) {
    const key = String(item.category.id);
    if (budgetTransactions[key]?.items || budgetTransactions[key]?.loading) return;
    setBudgetTransactions((current) => ({
      ...current,
      [key]: { loading: true, items: [], error: '' }
    }));
    try {
      const { start, end } = monthBounds(selectedMonth);
      const categoryToken = item.category.id || 'uncategorized';
      const qs = new URLSearchParams({
        categories: String(categoryToken),
        date_from: start,
        date_to: end,
        type: 'expense',
        include_ignored: '0',
        include_transfers: '0',
        sort: 'date_desc',
        limit: '100'
      });
      const result = await api.get(`/api/transactions?${qs.toString()}`);
      setBudgetTransactions((current) => ({
        ...current,
        [key]: { loading: false, items: result.items || [], error: '' }
      }));
    } catch (err) {
      setBudgetTransactions((current) => ({
        ...current,
        [key]: {
          loading: false,
          items: [],
          error: err.message || 'Failed to load transactions'
        }
      }));
    }
  }

  function toggleBudgetRow(item) {
    const key = String(item.category.id);
    setExpandedBudgetKey((current) => (current === key ? null : key));
    loadBudgetTransactions(item);
  }

  return (
    <div className="budgets-view">
      <PageHero
        id="budgets-title"
        variant="budgets"
        kicker="Budget planning"
        title="Budgets"
        subtitle={budgetSubtitle}
        stats={budgetHeroStats}
        toolbar={(
          <MonthNav
            month={selectedMonth}
            monthOptions={monthOptions}
            canGoForward={canGoForward}
            onPrev={() => goToMonth(addMonths(selectedMonth, -1))}
            onNext={() => goToMonth(addMonths(selectedMonth, 1))}
            onJump={(m) => goToMonth(m)}
          />
        )}
      />

      <div className="view-header" hidden>
        <div>
          <h2>Budgets</h2>
          <p className="muted">
            {isCurrentMonth ? 'This month' : formatMonthLabel(selectedMonth)}
            {daysLeft !== null && (
              <span className="subtle">
                {' · '}
                {daysLeft === 0
                  ? 'last day of the month'
                  : `${daysLeft} ${daysLeft === 1 ? 'day' : 'days'} left`}
              </span>
            )}
          </p>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : (
        <div className={`budget-content ${refreshing ? 'is-refreshing' : ''}`}>
          {/* ---------- Budget progress summary card ---------- */}
          {hasAnyBudgets && summary && (
            <div className="budget-summary-card">
              <div className="budget-summary-top">
                <div>
                  <div className="budget-summary-amounts">
                    <span className="budget-summary-spent">
                      {formatMoney(totalMonthlySpending)}
                    </span>
                    <span className="subtle">
                      {' '}of {formatMoney(summary.total_budgeted)} budgeted
                    </span>
                  </div>
                  <div className="subtle budget-summary-sub">
                    {remaining >= 0
                      ? `${formatMoney(remaining)} remaining`
                      : `${formatMoney(-remaining)} over budget`}
                    {summary.total_spent_unbudgeted > 0 && (
                      <>
                        {' · '}
                        {formatMoney(summary.total_spent_unbudgeted)} in
                        unbudgeted categories
                      </>
                    )}
                  </div>
                </div>
                <div className="budget-summary-percent">
                  {overallPercent !== null
                    ? `${Math.round(overallPercent)}%`
                    : '—'}
                </div>
              </div>
              <ProgressBar
                percent={overallPercent ?? 0}
                overBudget={overallPercent > 100}
              />
            </div>
          )}

          {spendingByCategory.total > 0 && (
            <SpendingPieChart
              items={spendingByCategory.items}
              total={spendingByCategory.total}
              selectedKey={selectedSpendingKey}
              onSelect={setSelectedSpendingKey}
            />
          )}

          {/* ---------- Action row ---------- */}
          <div className="budget-actions-row">
            <button
              type="button"
              className="btn-primary"
              onClick={() => setAddingBudget(true)}
            >
              + Add Budget
            </button>
          </div>

          {/* ---------- Budgeted categories ---------- */}
          {data.budgeted.length > 0 && (
            <section className="budget-section">
              <header className="budget-section-header">
                <h3>Budgeted</h3>
                <div className="budget-section-header-right">
                  <span className="muted">
                    {data.budgeted.length}{' '}
                    {data.budgeted.length === 1 ? 'category' : 'categories'}
                  </span>
                  <AppSelect
                    className="budget-sort-select"
                    value={budgetedSort}
                    options={BUDGET_SORT_OPTIONS}
                    onChange={setBudgetedSort}
                    ariaLabel="Sort budgeted categories"
                  >
                    <option value="pct_desc">% spent · high → low</option>
                    <option value="pct_asc">% spent · low → high</option>
                    <option value="spent_desc">$ spent · high → low</option>
                    <option value="spent_asc">$ spent · low → high</option>
                  </AppSelect>
                </div>
              </header>
              <ul className="budget-list">
                {sortedBudgeted.map((item) => (
                  <BudgetedRow
                    key={item.category.id}
                    item={item}
                    expanded={expandedBudgetKey === String(item.category.id)}
                    transactionsState={budgetTransactions[String(item.category.id)]}
                    onToggle={() => toggleBudgetRow(item)}
                    onEdit={() => setEditingBudget(item)}
                    onDelete={() => deleteBudget(item.budget_id)}
                  />
                ))}
              </ul>
            </section>
          )}

          {/* ---------- Unbudgeted with spending ---------- */}
          {data.unbudgeted.length > 0 && (
            <section className="budget-section">
              <header className="budget-section-header">
                <h3>Spent without a budget</h3>
                <span className="muted">
                  {data.unbudgeted.length}{' '}
                  {data.unbudgeted.length === 1 ? 'category' : 'categories'}
                </span>
              </header>
              <ul className="budget-list">
                {data.unbudgeted.map((item) => (
                  <UnbudgetedRow
                    key={item.category.id}
                    item={item}
                    onAdd={() =>
                      setEditingBudget({
                        ...item,
                        budget_id: null,
                        amount: null
                      })
                    }
                  />
                ))}
              </ul>
            </section>
          )}

          {/* ---------- Onboarding ---------- */}
          {!hasAnyActivity && (
            <div className="empty-state">
              <div className="empty-state-icon">◯</div>
              <h2>No budgets or activity this month</h2>
              <p>
                Add a monthly budget for a category to start tracking progress,
                or pick a different month.
              </p>
              <button
                type="button"
                className="btn-primary"
                onClick={() => setAddingBudget(true)}
              >
                + Add Budget
              </button>
            </div>
          )}

          {/* ---------- Inactive (collapsible) ---------- */}
          {data.inactive.length > 0 && hasAnyActivity && (
            <section className="budget-section">
              <button
                type="button"
                className="budget-inactive-toggle"
                onClick={() => setShowInactive((v) => !v)}
              >
                <span>{showInactive ? '▾' : '▸'}</span>
                <span>
                  {data.inactive.length} categor
                  {data.inactive.length === 1 ? 'y' : 'ies'} with no activity
                </span>
              </button>
              {showInactive && (
                <ul className="budget-list budget-list-compact">
                  {data.inactive.map((item) => (
                    <li key={item.category.id} className="budget-row inactive">
                      <span
                        className="budget-row-icon"
                        style={{ color: item.category.color }}
                      >
                        {item.category.icon}
                      </span>
                      <span className="budget-row-name">
                        {item.category.name}
                      </span>
                      <button
                        type="button"
                        className="linkish"
                        onClick={() =>
                          setEditingBudget({
                            ...item,
                            budget_id: null,
                            amount: null
                          })
                        }
                      >
                        Add Budget
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </div>
      )}

      {/* ---------- Modals ---------- */}
      {addingBudget && (
        <AddBudgetModal
          existingCategoryIds={new Set(
            [...(data?.budgeted || [])].map((x) => x.category.id)
          )}
          allCategories={
            [
              ...(data?.budgeted || []),
              ...(data?.unbudgeted || []),
              ...(data?.inactive || [])
            ].map((x) => x.category)
          }
          onClose={() => setAddingBudget(false)}
          onSaved={async (payload) => {
            await upsertBudget(payload);
            setAddingBudget(false);
          }}
        />
      )}

      {editingBudget && (
        <EditBudgetModal
          item={editingBudget}
          onClose={() => setEditingBudget(null)}
          onSaved={async (payload) => {
            await upsertBudget(payload);
            setEditingBudget(null);
          }}
          onDelete={
            editingBudget.budget_id
              ? async () => {
                  await deleteBudget(editingBudget.budget_id);
                  setEditingBudget(null);
                }
              : null
          }
        />
      )}

      <Dialog />
    </div>
  );
}

// ============================================================================
// Month navigator — arrow buttons flanking a native <select>.
//
// Using <select> rather than a hidden <input type="month"> because the
// latter caused unexpected iOS zoom behavior on page load — Safari treats
// type="month" inputs with kid-glove layout rules even when hidden. A
// standard <select> opens a native mobile picker without touching zoom.
// ============================================================================

function MonthNav({ month, monthOptions, canGoForward, onPrev, onNext, onJump }) {
  return (
    <div className="month-nav">
      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onPrev}
        aria-label="Previous month"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="M14.5 6.5 9 12l5.5 5.5" />
        </svg>
      </button>

      <div className="month-nav-label-wrap">
        <span className="month-nav-caret" aria-hidden="true">▾</span>
        <AppSelect
          className="month-nav-select"
          value={month}
          options={monthOptions.map((m) => ({
            value: m,
            label: formatMonthLabel(m)
          }))}
          onChange={onJump}
          ariaLabel="Jump to month"
        >
          {monthOptions.map((m) => (
            <option key={m} value={m}>
              {formatMonthLabel(m)}
            </option>
          ))}
        </AppSelect>
      </div>

      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onNext}
        disabled={!canGoForward}
        aria-label="Next month"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="m9.5 6.5L15 12l-5.5 5.5" />
        </svg>
      </button>
    </div>
  );
}

// ============================================================================
// Progress bar
// ============================================================================

function ProgressBar({ percent, overBudget = false, compact = false }) {
  const clamped = Math.max(0, Math.min(100, percent));
  const label = `${Math.round(percent)}%${overBudget ? ' over budget' : ' used'}`;
  return (
    <div
      className={`budget-progress ${compact ? 'budget-progress-compact' : ''}`}
      role="progressbar"
      aria-valuenow={Math.round(percent)}
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

// ============================================================================
// Spending by category pie chart
// ============================================================================

function SpendingPieChart({ items, total, selectedKey, onSelect }) {
  const radius = 44;
  const strokeWidth = 18;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  const selected =
    items.find((item) => item.key === selectedKey) ||
    items[0] ||
    null;
  const segments = items.map((item) => {
    const length = (item.amount / total) * circumference;
    const segment = {
      ...item,
      length,
      dashOffset: -offset,
      active: selected?.key === item.key
    };
    offset += length;
    return segment;
  });

  return (
    <section className="budget-pie-card" aria-labelledby="budget-pie-title">
      <div className="budget-pie-header">
        <div>
          <h3 id="budget-pie-title">Spending by Category</h3>
          <p className="subtle">{formatMoney(total)} spent this month</p>
        </div>
        {selected && (
          <div className="budget-pie-selected">
            <span>{selected.icon}</span>
            <strong>{formatMoney(selected.amount)}</strong>
            <em>{selected.name}</em>
          </div>
        )}
      </div>

      <div className="budget-pie-body">
        <svg
          className="budget-pie-chart"
          viewBox="0 0 120 120"
          role="img"
          aria-label="Spending by category pie chart"
        >
          <circle
            className="budget-pie-track"
            cx="60"
            cy="60"
            r={radius}
            fill="none"
            strokeWidth={strokeWidth}
          />
          {segments.map((item) => (
            <circle
              key={`${item.key}-glow`}
              className={`budget-pie-slice-glow ${item.active ? 'active' : ''}`}
              cx="60"
              cy="60"
              r={radius}
              fill="none"
              stroke={item.color}
              strokeWidth={strokeWidth + 7}
              strokeDasharray={`${item.length} ${circumference - item.length}`}
              strokeDashoffset={item.dashOffset}
              transform="rotate(-90 60 60)"
              aria-hidden="true"
            />
          ))}
          {segments.map((item) => {
            return (
              <circle
                key={item.key}
                className={`budget-pie-slice ${item.active ? 'active' : ''}`}
                cx="60"
                cy="60"
                r={radius}
                fill="none"
                stroke={item.color}
                strokeWidth={strokeWidth}
                strokeDasharray={`${item.length} ${circumference - item.length}`}
                strokeDashoffset={item.dashOffset}
                transform="rotate(-90 60 60)"
                tabIndex={0}
                role="button"
                aria-label={`${item.name}: ${formatMoney(item.amount)}`}
                onClick={() => onSelect(item.key)}
                onFocus={() => onSelect(item.key)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onSelect(item.key);
                  }
                }}
                onMouseEnter={() => onSelect(item.key)}
              >
                <title>
                  {`${item.name}: ${formatMoney(item.amount)} (${Math.round((item.amount / total) * 100)}%)`}
                </title>
              </circle>
            );
          })}
          <text className="budget-pie-total-label" x="60" y="56" textAnchor="middle">
            Total
          </text>
          <text className="budget-pie-total-value" x="60" y="74" textAnchor="middle">
            {formatMoney(total)}
          </text>
        </svg>

        <div className="budget-pie-list" role="list">
          {items.map((item) => {
            const active = selected?.key === item.key;
            const percent = total > 0 ? Math.round((item.amount / total) * 100) : 0;
            return (
              <SelectableListItem
                key={item.key}
                className="budget-pie-item"
                active={active}
                onClick={() => onSelect(item.key)}
                leading={(
                  <>
                    <span
                      className="budget-pie-dot"
                      style={{ backgroundColor: item.color }}
                      aria-hidden="true"
                    />
                    <span aria-hidden="true">{item.icon}</span>
                  </>
                )}
                title={item.name}
                sidePrimary={formatMoney(item.amount)}
                sideSecondary={`${percent}%`}
                ariaLabel={`Select ${item.name}: ${formatMoney(item.amount)}`}
              />
            );
          })}
        </div>
      </div>
    </section>
  );
}

// ============================================================================
// Budget row (has a budget)
// ============================================================================

function BudgetedRow({ item, expanded, transactionsState, onToggle, onEdit, onDelete }) {
  const { category, amount, spent } = item;
  const percent = amount > 0 ? (spent / amount) * 100 : 0;
  const overBudget = spent > amount;
  const remaining = amount - spent;

  const menuItems = [
    { label: 'Edit Amount', icon: '✎', onClick: onEdit },
    { divider: true },
    { label: 'Delete', icon: '✕', destructive: true, onClick: onDelete }
  ];

  function handleClick(e) {
    if (e.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    onToggle();
  }

  function handleKeyDown(e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    if (e.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    e.preventDefault();
    onToggle();
  }

  return (
    <li
      className={`budget-row ${overBudget ? 'over-budget' : ''} ${expanded ? 'expanded' : ''}`}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      tabIndex={0}
      aria-expanded={expanded}
    >
      <div className="budget-row-head">
        <div className="budget-row-main">
          <span className="budget-row-icon" style={{ color: category.color }}>
            {category.icon}
          </span>
          <span className="budget-row-name">{category.name}</span>
          <span className="budget-row-amounts">
            <span className="budget-row-spent">{formatMoney(spent)}</span>
            <span className="subtle"> / {formatMoney(amount)}</span>
          </span>
        </div>
        <CollapseIndicator expanded={expanded} className="budget-row-collapse-indicator" />
        <DropdownMenu
          items={menuItems}
          ariaLabel={`Actions for ${category.name} budget`}
        />
      </div>
      <div className="budget-row-progress">
        <ProgressBar percent={percent} overBudget={overBudget} />
        <div className="budget-row-footnote">
          {overBudget ? (
            <span className="over-label">
              {Math.round(percent)}% · {formatMoney(-remaining)} over
            </span>
          ) : (
            <span className="subtle">
              {Math.round(percent)}% · {formatMoney(remaining)} left
            </span>
          )}
          <span className="subtle">
            {item.transaction_count}{' '}
            {item.transaction_count === 1 ? 'transaction' : 'transactions'}
          </span>
        </div>
      </div>
      <div className="budget-row-detail" aria-hidden={!expanded}>
        <div className="budget-row-detail-inner">
          <h4>Transactions</h4>
          {transactionsState?.loading ? (
            <p className="subtle">Loading transactions...</p>
          ) : transactionsState?.error ? (
            <p className="error">{transactionsState.error}</p>
          ) : transactionsState?.items?.length ? (
            <ul className="budget-transaction-list">
              {transactionsState.items.map((txn) => (
                <li key={txn.id}>
                  <span>
                    <strong>{txn.merchant}</strong>
                    <em>{formatTransactionDate(txn.date)}</em>
                  </span>
                  <strong>{formatMoney(Math.abs(Number(txn.amount || 0)))}</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p className="subtle">No matching transactions this month.</p>
          )}
        </div>
      </div>
    </li>
  );
}

// ============================================================================
// Unbudgeted-but-spent row
// ============================================================================

function UnbudgetedRow({ item, onAdd }) {
  const { category, spent, transaction_count } = item;
  return (
    <li className="budget-row unbudgeted">
      <span className="budget-row-icon" style={{ color: category.color }}>
        {category.icon}
      </span>
      <div className="budget-row-grow">
        <div className="budget-row-name">{category.name}</div>
        <div className="subtle" style={{ fontSize: '0.82rem' }}>
          {formatMoney(spent)} across {transaction_count}{' '}
          {transaction_count === 1 ? 'transaction' : 'transactions'}
        </div>
      </div>
      <button type="button" className="btn-secondary btn-compact" onClick={onAdd}>
        + Budget
      </button>
    </li>
  );
}

// ============================================================================
// Add budget modal
// ============================================================================

function AddBudgetModal({ existingCategoryIds, allCategories, onClose, onSaved }) {
  const [categoryId, setCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const availableCategories = useMemo(() => {
    const seen = new Set();
    const out = [];
    for (const c of allCategories) {
      if (!c || seen.has(c.id)) continue;
      seen.add(c.id);
      if (c.is_transfer) continue;
      if (existingCategoryIds.has(c.id)) continue;
      out.push(c);
    }
    out.sort((a, b) => a.name.localeCompare(b.name));
    return out;
  }, [allCategories, existingCategoryIds]);

  async function handleSave(e, close) {
    e.preventDefault();
    setError('');

    const cid = parseInt(categoryId, 10);
    if (!Number.isFinite(cid)) {
      setError('Pick a category.');
      return;
    }
    const amt = parseCurrencyInput(amount, NaN);
    if (!Number.isFinite(amt) || amt < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: cid, amount: amt });
      close({ animate: true });
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Add monthly budget</h3>
          <p className="subtle">
            This amount will be your target for every month — past, present,
            and future.
          </p>

          {availableCategories.length === 0 ? (
            <>
              <p>Every category already has a budget.</p>
              <div className="modal-actions">
                <button type="button" className="btn-primary" onClick={close}>
                  OK
                </button>
              </div>
            </>
          ) : (
            <form onSubmit={(e) => handleSave(e, close)}>
              <label className="field">
                <span>Category</span>
                <AppSelect
                  value={categoryId}
                  onChange={setCategoryId}
                  placeholder="Pick One"
                  ariaLabel="Budget category"
                  options={availableCategories.map((c) => ({
                    value: c.id,
                    label: `${c.icon} ${c.name}`
                  }))}
                >
                  <option value="">(Pick one)</option>
                  {availableCategories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.icon} {c.name}
                    </option>
                  ))}
                </AppSelect>
              </label>

              <label className="field">
                <span>Monthly limit</span>
                <CurrencyInput
                  placeholder="$0"
                  value={amount}
                  onChange={setAmount}
                  required
                />
              </label>

              {error && <div className="error">{error}</div>}

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={close}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Saving…' : 'Add Budget'}
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Edit budget modal — amount only; edits apply to every month
// ============================================================================

function EditBudgetModal({ item, onClose, onSaved, onDelete }) {
  const [amount, setAmount] = useState(
    item.amount != null ? formatCurrencyInput(item.amount) : ''
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave(e, close) {
    e.preventDefault();
    setError('');

    const amt = parseCurrencyInput(amount, NaN);
    if (!Number.isFinite(amt) || amt < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: item.category.id, amount: amt });
      close({ animate: true });
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  const isNew = !item.budget_id;

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>
            {isNew ? 'Add' : 'Edit'} budget · {item.category.icon}{' '}
            {item.category.name}
          </h3>
          <p className="subtle">
            Applies to every month.
            {!isNew && item.spent > 0 && (
              <>
                {' '}Spent {formatMoney(item.spent)} this month so far.
              </>
            )}
          </p>

          <form onSubmit={(e) => handleSave(e, close)}>
            <label className="field">
              <span>Monthly limit</span>
              <CurrencyInput
                placeholder="$0"
                value={amount}
                onChange={setAmount}
                required
                autoFocus
              />
            </label>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              {!isNew && onDelete && (
                <button
                  type="button"
                  className="btn-danger"
                  onClick={onDelete}
                  style={{ marginRight: 'auto' }}
                >
                  Delete
                </button>
              )}
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving…' : isNew ? 'Add Budget' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
