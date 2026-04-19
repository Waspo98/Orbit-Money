import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import DropdownMenu from '../components/DropdownMenu.jsx';

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

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

// ---------- Date utilities ----------

function monthFromDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function currentMonth() {
  return monthFromDate(new Date());
}

function parseMonthStr(m) {
  if (!m || !/^\d{4}-\d{2}$/.test(m)) return null;
  const [y, mo] = m.split('-').map(Number);
  return new Date(y, mo - 1, 1);
}

function addMonths(m, delta) {
  const d = parseMonthStr(m) || new Date();
  d.setMonth(d.getMonth() + delta);
  return monthFromDate(d);
}

function formatMonthLabel(m) {
  const d = parseMonthStr(m);
  if (!d) return m;
  return `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`;
}

function daysLeftInMonth(m) {
  const target = parseMonthStr(m);
  if (!target) return null;
  const now = new Date();
  const cur = new Date(now.getFullYear(), now.getMonth(), 1);
  if (target.getTime() !== cur.getTime()) return null;
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  return lastDay - now.getDate();
}

function formatMoney(n) {
  return Number(n).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
}

// ============================================================================
// Main page
// ============================================================================

export default function Budgets() {
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

  // Sort order for the Budgeted section. Persisted in localStorage so a
  // user's choice sticks across navigations without needing a URL param.
  // Default matches the previous backend-sorted behavior (most over-budget
  // first = highest % spent first).
  const [budgetedSort, setBudgetedSort] = useState(() => {
    try {
      return localStorage.getItem('budget-tracker-budgeted-sort') || 'pct_desc';
    } catch {
      return 'pct_desc';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('budget-tracker-budgeted-sort', budgetedSort);
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
  const overallPercent =
    summary && summary.total_budgeted > 0
      ? (summary.total_spent_in_budgets / summary.total_budgeted) * 100
      : null;
  const remaining =
    summary && summary.total_budgeted > 0
      ? summary.total_budgeted - summary.total_spent_in_budgets
      : null;

  const daysLeft = daysLeftInMonth(selectedMonth);

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

  // --- Mutations ---
  async function upsertBudget({ category_id, amount }) {
    await api.put('/api/budgets', { category_id, amount });
    await load();
    await loadMonths();
  }

  async function deleteBudget(budgetId) {
    if (!confirm('Delete this budget? It will be removed for every month.')) return;
    try {
      await api.del(`/api/budgets/${budgetId}`);
      await load();
    } catch (err) {
      alert(err.message || 'Delete failed');
    }
  }

  return (
    <div className="budgets-view">
      <div className="view-header">
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

      {/* ---------- Month navigator ---------- */}
      <MonthNav
        month={selectedMonth}
        monthOptions={monthOptions}
        canGoForward={canGoForward}
        onPrev={() => goToMonth(addMonths(selectedMonth, -1))}
        onNext={() => goToMonth(addMonths(selectedMonth, 1))}
        onJump={(m) => goToMonth(m)}
      />

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : (
        <div className={`budget-content ${refreshing ? 'is-refreshing' : ''}`}>
          {/* ---------- Income / Expenses / Net stat row ---------- */}
          {summary && (
            <div className="budget-stats">
              <div className="budget-stat">
                <div className="budget-stat-label">Income</div>
                <div className="budget-stat-value income">
                  {formatMoney(summary.total_income)}
                </div>
              </div>
              <div className="budget-stat">
                <div className="budget-stat-label">Expenses</div>
                <div className="budget-stat-value expense">
                  {formatMoney(summary.total_expenses)}
                </div>
              </div>
              <div className="budget-stat">
                <div className="budget-stat-label">Net</div>
                <div
                  className={`budget-stat-value ${
                    summary.total_net >= 0 ? 'income' : 'expense'
                  }`}
                >
                  {summary.total_net >= 0 ? '+' : '−'}
                  {formatMoney(Math.abs(summary.total_net))}
                </div>
              </div>
            </div>
          )}

          {/* ---------- Budget progress summary card ---------- */}
          {hasAnyBudgets && summary && (
            <div className="budget-summary-card">
              <div className="budget-summary-top">
                <div>
                  <div className="budget-summary-amounts">
                    <span className="budget-summary-spent">
                      {formatMoney(summary.total_spent_in_budgets)}
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

          {/* ---------- Action row ---------- */}
          <div className="budget-actions-row">
            <button
              type="button"
              className="btn-primary"
              onClick={() => setAddingBudget(true)}
            >
              + Add budget
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
                  <select
                    className="budget-sort-select"
                    value={budgetedSort}
                    onChange={(e) => setBudgetedSort(e.target.value)}
                    aria-label="Sort budgeted categories"
                  >
                    <option value="pct_desc">% spent · high → low</option>
                    <option value="pct_asc">% spent · low → high</option>
                    <option value="spent_desc">$ spent · high → low</option>
                    <option value="spent_asc">$ spent · low → high</option>
                  </select>
                </div>
              </header>
              <ul className="budget-list">
                {sortedBudgeted.map((item) => (
                  <BudgetedRow
                    key={item.category.id}
                    item={item}
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
                + Add budget
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
                        Add budget
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
        ‹
      </button>

      <div className="month-nav-label-wrap">
        <span className="month-nav-label-text">{formatMonthLabel(month)}</span>
        <span className="month-nav-caret" aria-hidden="true">▾</span>
        <select
          className="month-nav-select"
          value={month}
          onChange={(e) => onJump(e.target.value)}
          aria-label="Jump to month"
        >
          {monthOptions.map((m) => (
            <option key={m} value={m}>
              {formatMonthLabel(m)}
            </option>
          ))}
        </select>
      </div>

      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onNext}
        disabled={!canGoForward}
        aria-label="Next month"
      >
        ›
      </button>
    </div>
  );
}

// ============================================================================
// Progress bar
// ============================================================================

function ProgressBar({ percent, overBudget = false, compact = false }) {
  const clamped = Math.max(0, Math.min(100, percent));
  return (
    <div
      className={`budget-progress ${compact ? 'budget-progress-compact' : ''}`}
      role="progressbar"
      aria-valuenow={Math.round(percent)}
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

// ============================================================================
// Budget row (has a budget)
// ============================================================================

function BudgetedRow({ item, onEdit, onDelete }) {
  const { category, amount, spent } = item;
  const percent = amount > 0 ? (spent / amount) * 100 : 0;
  const overBudget = spent > amount;
  const remaining = amount - spent;

  const menuItems = [
    { label: 'Edit amount', icon: '✎', onClick: onEdit },
    { divider: true },
    { label: 'Delete', icon: '✕', destructive: true, onClick: onDelete }
  ];

  return (
    <li className={`budget-row ${overBudget ? 'over-budget' : ''}`}>
      <div className="budget-row-head">
        <button
          type="button"
          className="budget-row-main"
          onClick={onEdit}
          aria-label={`Edit budget for ${category.name}`}
        >
          <span className="budget-row-icon" style={{ color: category.color }}>
            {category.icon}
          </span>
          <span className="budget-row-name">{category.name}</span>
          <span className="budget-row-amounts">
            <span className="budget-row-spent">{formatMoney(spent)}</span>
            <span className="subtle"> / {formatMoney(amount)}</span>
          </span>
        </button>
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
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: cid, amount: amt });
      close();
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
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  required
                  autoFocus
                >
                  <option value="">(Pick one)</option>
                  {availableCategories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.icon} {c.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="field">
                <span>Monthly limit</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  placeholder="0.00"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  required
                />
              </label>

              {error && <div className="error">{error}</div>}

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={close}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Saving…' : 'Add budget'}
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
    item.amount != null ? String(item.amount) : ''
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave(e, close) {
    e.preventDefault();
    setError('');

    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: item.category.id, amount: amt });
      close();
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
              <input
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
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
                {saving ? 'Saving…' : isNew ? 'Add budget' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
