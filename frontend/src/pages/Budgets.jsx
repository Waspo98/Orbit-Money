import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppSelect from '../components/AppSelect.jsx';
import BudgetAmountModal from '../components/BudgetAmountModal.jsx';
import CollapseIndicator from '../components/CollapseIndicator.jsx';
import CurrencyInput, {
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import DropdownMenu from '../components/DropdownMenu.jsx';
import ExpandingSection from '../components/ExpandingSection.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { formatCurrency } from '../lib/formatters.js';
import {
  addMonthsToLocalMonth,
  daysLeftInLocalMonth,
  formatLocalMonth,
  formatMonthDay,
  formatMonthKeyLabel,
  getLocalMonthBounds
} from '../lib/localDate.js';

const CATEGORY_SORT_OPTIONS = [
  { value: 'variance_desc', label: 'Over Budget First' },
  { value: 'remaining_asc', label: 'Least Room Left' },
  { value: 'spent_desc', label: 'Most Spent' },
  { value: 'name_asc', label: 'Category A-Z' }
];
const CATEGORY_SORT_VALUES = new Set(CATEGORY_SORT_OPTIONS.map((option) => option.value));

const MAX_MIX_ITEMS = 8;
const MAX_TRANSACTION_PREVIEW = 6;

function normalizeCategorySort(value) {
  return CATEGORY_SORT_VALUES.has(value) ? value : 'variance_desc';
}
function currentMonth() {
  return formatLocalMonth();
}

function addMonths(month, delta) {
  return addMonthsToLocalMonth(month, delta);
}

function monthBounds(month) {
  return getLocalMonthBounds(month);
}

function formatMonthLabel(month) {
  return formatMonthKeyLabel(month);
}

function formatMoney(value) {
  return formatCurrency(value);
}

function asNumber(value) {
  const number = Number(value || 0);
  return Number.isFinite(number) ? number : 0;
}

function percentOf(part, total) {
  if (!total) return 0;
  return (part / total) * 100;
}

function clampPercent(value) {
  return Math.max(0, Math.min(100, value));
}

function formatPercent(value) {
  return `${Math.round(value)}%`;
}

function formatTransactionDate(date) {
  return formatMonthDay(date);
}

function buildTransactionsHref(item, selectedMonth) {
  const { start, end } = monthBounds(selectedMonth);
  const categoryId = item.category.id == null ? 'uncategorized' : String(item.category.id);
  const params = new URLSearchParams({
    categories: categoryId,
    date_from: start,
    date_to: end,
    type: 'expense',
    include_ignored: '0',
    include_transfers: '0',
    sort: 'date_desc'
  });
  return `/transactions?${params.toString()}`;
}

function getCategoryKey(item) {
  return item.category.id == null ? 'uncategorized' : String(item.category.id);
}

function isBudgetableCategory(item) {
  const id = Number(item?.category?.id);
  return Number.isInteger(id) && id > 0;
}

function getStatus(item) {
  const amount = asNumber(item.amount);
  const spent = asNumber(item.spent);
  if (amount <= 0) return { label: 'No Limit', tone: 'neutral' };
  if (spent > amount) return { label: `${formatMoney(spent - amount)} Over`, tone: 'over' };
  const remaining = amount - spent;
  const pct = percentOf(spent, amount);
  return {
    label: `${formatMoney(remaining)} Left`,
    tone: pct >= 85 ? 'watch' : 'good'
  };
}

function getMonthDayCount(month) {
  const { end } = monthBounds(month);
  const count = Number(end.slice(8, 10));
  return Number.isFinite(count) && count > 0 ? count : null;
}

function getSpendPace(model, selectedMonth, currentMonthDaysLeft) {
  if (model.totalBudgeted <= 0) {
    return {
      value: 'No Budget',
      detail: 'Add category budgets',
      tone: 'neutral'
    };
  }

  if (selectedMonth < currentMonth()) {
    return {
      value: `${formatMoney(0)} / day`,
      detail: 'Month complete',
      tone: model.remaining < 0 ? 'over' : 'neutral'
    };
  }

  const daysRemaining = selectedMonth === currentMonth()
    ? Math.max(1, currentMonthDaysLeft ?? 1)
    : getMonthDayCount(selectedMonth);

  if (!daysRemaining) {
    return {
      value: `${formatMoney(0)} / day`,
      detail: 'No days left',
      tone: model.remaining < 0 ? 'over' : 'neutral'
    };
  }

  if (model.remaining <= 0) {
    return {
      value: `${formatMoney(0)} / day`,
      detail: `${formatMoney(Math.abs(model.remaining))} over budget`,
      tone: 'over'
    };
  }

  return {
    value: `${formatMoney(model.remaining / daysRemaining)} / day`,
    detail: `${daysRemaining} ${daysRemaining === 1 ? 'day' : 'days'} left`,
    tone: 'good'
  };
}

export default function Budgets({
  categorySortPreference = 'variance_desc',
  onCategorySortPreferenceChange
}) {
  const { alert, confirm, Dialog } = useAppDialog();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedMonth = searchParams.get('month') || currentMonth();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [monthsWithActivity, setMonthsWithActivity] = useState([]);
  const [categorySort, setCategorySort] = useState(() =>
    normalizeCategorySort(categorySortPreference)
  );
  const [expandedKey, setExpandedKey] = useState(null);
  const [expandedMixKey, setExpandedMixKey] = useState(null);
  const [expandedCompactKey, setExpandedCompactKey] = useState(null);
  const [budgetTransactions, setBudgetTransactions] = useState({});
  const [addingBudget, setAddingBudget] = useState(false);
  const [editingBudget, setEditingBudget] = useState(null);
  const [showInactive, setShowInactive] = useState(false);

  async function load() {
    const firstLoad = data == null;
    if (firstLoad) setLoading(true);
    else setRefreshing(true);
    setError('');
    try {
      const result = await api.get(
        `/api/budgets?month=${encodeURIComponent(selectedMonth)}`
      );
      setData(result);
    } catch (err) {
      setError(err.message || 'Failed to load budgets');
    } finally {
      if (firstLoad) setLoading(false);
      else setRefreshing(false);
    }
  }

  async function loadMonths() {
    try {
      const result = await api.get('/api/budgets/months');
      setMonthsWithActivity(result.items || []);
    } catch {
      /* Month arrows still work without this list. */
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
    setCategorySort(normalizeCategorySort(categorySortPreference));
  }, [categorySortPreference]);

  useEffect(() => {
    setExpandedKey(null);
    setExpandedMixKey(null);
    setExpandedCompactKey(null);
    setBudgetTransactions({});
  }, [selectedMonth]);

  function goToMonth(month) {
    const next = new URLSearchParams(searchParams);
    if (month === currentMonth()) next.delete('month');
    else next.set('month', month);
    setSearchParams(next);
  }

  function updateCategorySort(value) {
    const next = normalizeCategorySort(value);
    setCategorySort(next);
    onCategorySortPreferenceChange?.(next);
  }

  const monthOptions = useMemo(() => {
    const set = new Set(monthsWithActivity);
    set.add(currentMonth());
    for (let i = 1; i <= 3; i++) set.add(addMonths(currentMonth(), i));
    set.add(selectedMonth);
    return Array.from(set).sort((a, b) => b.localeCompare(a));
  }, [monthsWithActivity, selectedMonth]);

  const canGoForward = selectedMonth < addMonths(currentMonth(), 12);
  const isCurrentMonth = selectedMonth === currentMonth();
  const daysLeft = daysLeftInLocalMonth(selectedMonth);

  const budgetModel = useMemo(() => {
    const summary = data?.summary || {};
    const budgeted = data?.budgeted || [];
    const unbudgeted = data?.unbudgeted || [];
    const inactive = data?.inactive || [];

    const totalBudgeted = asNumber(summary.total_budgeted);
    const totalExpenses = asNumber(summary.total_expenses);
    const spentInBudgets = asNumber(summary.total_spent_in_budgets);
    const spentUnbudgeted = asNumber(summary.total_spent_unbudgeted);
    const totalIncome = asNumber(summary.total_income);
    const totalNet = asNumber(summary.total_net);
    const remaining = totalBudgeted - totalExpenses;
    const overallPercent = percentOf(totalExpenses, totalBudgeted);
    const budgetedPercent = percentOf(spentInBudgets, totalBudgeted);
    const unbudgetedShare = percentOf(spentUnbudgeted, totalExpenses);

    const rows = budgeted.map((item) => {
      const amount = asNumber(item.amount);
      const spent = asNumber(item.spent);
      const variance = spent - amount;
      const pct = amount > 0 ? percentOf(spent, amount) : 0;
      return {
        ...item,
        amount,
        spent,
        variance,
        pct,
        remaining: amount - spent,
        over: amount > 0 && spent > amount
      };
    });

    const sortedRows = [...rows].sort((a, b) => {
      switch (categorySort) {
        case 'remaining_asc':
          return a.remaining - b.remaining;
        case 'spent_desc':
          return b.spent - a.spent;
        case 'name_asc':
          return a.category.name.localeCompare(b.category.name);
        case 'variance_desc':
        default:
          return b.variance - a.variance;
      }
    });

    const overRows = rows.filter((item) => item.over).sort((a, b) => b.variance - a.variance);
    const watchRows = rows
      .filter((item) => !item.over && item.amount > 0 && item.pct >= 85)
      .sort((a, b) => b.pct - a.pct);
    const unbudgetedRows = [...unbudgeted]
      .map((item) => ({ ...item, spent: asNumber(item.spent) }))
      .sort((a, b) => b.spent - a.spent);

    const notable = [
      ...overRows.map((item) => ({ kind: 'Over Budget', tone: 'over', item })),
      ...watchRows.map((item) => ({ kind: 'High Usage', tone: 'watch', item })),
      ...unbudgetedRows.map((item) => ({ kind: 'Unbudgeted', tone: 'neutral', item }))
    ].slice(0, 7);

    const spendingMix = [...rows, ...unbudgetedRows]
      .filter((item) => asNumber(item.spent) > 0)
      .sort((a, b) => asNumber(b.spent) - asNumber(a.spent));

    return {
      budgeted,
      inactive,
      rows,
      sortedRows,
      unbudgetedRows,
      notable,
      spendingMix,
      totalBudgeted,
      totalExpenses,
      spentInBudgets,
      spentUnbudgeted,
      totalIncome,
      totalNet,
      remaining,
      overallPercent,
      budgetedPercent,
      unbudgetedShare,
      overCount: overRows.length,
      watchCount: watchRows.length
    };
  }, [data, categorySort]);

  const budgetSubtitle = `${isCurrentMonth ? 'This month' : formatMonthLabel(selectedMonth)}${
    daysLeft !== null
      ? ` · ${daysLeft === 0 ? 'last day of the month' : `${daysLeft} ${daysLeft === 1 ? 'day' : 'days'} left`}`
      : ''
  }`;

  async function upsertBudget(payload) {
    await api.put('/api/budgets', payload);
    await load();
    await loadMonths();
  }

  async function deleteBudget(budgetId) {
    const ok = await confirm('Delete this budget? It will be removed for every month.', {
      title: 'Delete Budget',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/budgets/${budgetId}`);
      await load();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete Failed' });
    }
  }

  async function loadBudgetTransactions(item) {
    const key = getCategoryKey(item);
    if (budgetTransactions[key]?.items || budgetTransactions[key]?.loading) return;
    setBudgetTransactions((current) => ({
      ...current,
      [key]: { loading: true, items: [], error: '' }
    }));

    try {
      const { start, end } = monthBounds(selectedMonth);
      const params = new URLSearchParams({
        categories: item.category.id == null ? 'uncategorized' : String(item.category.id),
        date_from: start,
        date_to: end,
        type: 'expense',
        include_ignored: '0',
        include_transfers: '0',
        sort: 'date_desc',
        limit: '100'
      });
      const result = await api.get(`/api/transactions?${params.toString()}`);
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
    const key = getCategoryKey(item);
    setExpandedKey((current) => (current === key ? null : key));
    loadBudgetTransactions(item);
  }

  function toggleSpendingMixRow(item) {
    const key = getCategoryKey(item);
    setExpandedMixKey((current) => (current === key ? null : key));
    loadBudgetTransactions(item);
  }

  function toggleCompactCard(item, cardKey) {
    setExpandedCompactKey((current) => (current === cardKey ? null : cardKey));
    loadBudgetTransactions(item);
  }

  const allCategories = [
    ...(data?.budgeted || []),
    ...(data?.unbudgeted || []),
    ...(data?.inactive || [])
  ].map((item) => item.category);

  return (
    <div className="budget-beta-view">
      <PageHero
        id="budgets-title"
        variant="budgets"
        kicker="Budget planning"
        title="Budgets"
        subtitle={budgetSubtitle}
        toolbar={(
          <MonthNav
            month={selectedMonth}
            monthOptions={monthOptions}
            canGoForward={canGoForward}
            onPrev={() => goToMonth(addMonths(selectedMonth, -1))}
            onNext={() => goToMonth(addMonths(selectedMonth, 1))}
            onJump={goToMonth}
          />
        )}
      />

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : (
        <div className={`budget-beta-content ${refreshing ? 'is-refreshing' : ''}`}>
          <SnapshotPanel
            model={budgetModel}
            monthLabel={formatMonthLabel(selectedMonth)}
            selectedMonth={selectedMonth}
            daysLeft={daysLeft}
            onAddBudget={() => setAddingBudget(true)}
          />

          {budgetModel.totalExpenses > 0 && (
            <SpendingMixPanel
              items={budgetModel.spendingMix}
              total={budgetModel.totalExpenses}
              selectedMonth={selectedMonth}
              expandedKey={expandedMixKey}
              transactionsByCategory={budgetTransactions}
              onToggle={toggleSpendingMixRow}
            />
          )}

          <NotablePanel
            notable={budgetModel.notable}
            selectedMonth={selectedMonth}
            expandedKey={expandedCompactKey}
            transactionsByCategory={budgetTransactions}
            onToggle={toggleCompactCard}
            onAdd={(item) =>
              setEditingBudget({
                ...item,
                budget_id: null,
                amount: null
              })
            }
          />

          {budgetModel.unbudgetedRows.length > 0 && (
            <section className="budget-beta-section budget-beta-unbudgeted-section">
              <header className="budget-beta-section-header">
                <div>
                  <h3>Unbudgeted Spending</h3>
                  <p>{formatMoney(budgetModel.spentUnbudgeted)} outside budgeted categories</p>
                  <p className="budget-beta-helper">Tap a category to see transactions</p>
                </div>
              </header>
              <ul className="budget-beta-unbudgeted-list">
                {budgetModel.unbudgetedRows.map((item) => (
                  <UnbudgetedBetaRow
                    key={getCategoryKey(item)}
                    item={item}
                    selectedMonth={selectedMonth}
                    expanded={expandedCompactKey === `unbudgeted-${getCategoryKey(item)}`}
                    transactionsState={budgetTransactions[getCategoryKey(item)]}
                    onToggle={() => toggleCompactCard(item, `unbudgeted-${getCategoryKey(item)}`)}
                    onAdd={
                      isBudgetableCategory(item)
                        ? () =>
                            setEditingBudget({
                              ...item,
                              budget_id: null,
                              amount: null
                            })
                        : null
                    }
                  />
                ))}
              </ul>
            </section>
          )}

          {(budgetModel.sortedRows.length > 0 || budgetModel.inactive.length > 0) && (
            <section className="budget-beta-section budget-beta-categories-section">
              <header className="budget-beta-section-header">
                <div>
                  <h3>Budgeted Categories</h3>
                  <p>{budgetModel.sortedRows.length} tracked categories</p>
                  <p className="budget-beta-helper">Tap a category to see transactions</p>
                </div>
                <AppSelect
                  className="budget-beta-sort"
                  value={categorySort}
                  options={CATEGORY_SORT_OPTIONS}
                  onChange={updateCategorySort}
                  ariaLabel="Sort budget categories"
                />
              </header>

              <ul className="budget-beta-category-list">
                {budgetModel.sortedRows.map((item) => {
                  const key = getCategoryKey(item);
                  return (
                    <BudgetRow
                      key={key}
                      item={item}
                      expanded={expandedKey === key}
                      selectedMonth={selectedMonth}
                      transactionsState={budgetTransactions[key]}
                      onToggle={() => toggleBudgetRow(item)}
                      onEdit={() => setEditingBudget(item)}
                      onDelete={() => deleteBudget(item.budget_id)}
                    />
                  );
                })}
              </ul>

              {budgetModel.inactive.length > 0 && (
                <div className="budget-beta-inactive-section">
                  <button
                    type="button"
                    className="budget-beta-inactive-toggle"
                    onClick={() => setShowInactive((value) => !value)}
                    aria-expanded={showInactive}
                  >
                    <CollapseIndicator
                      expanded={showInactive}
                      className="budget-beta-inactive-collapse"
                      visible={false}
                    />
                    <span>
                      {showInactive ? 'Hide' : 'Show'}{' '}
                      {budgetModel.inactive.length} categor
                      {budgetModel.inactive.length === 1 ? 'y' : 'ies'} with no activity
                    </span>
                  </button>
                  {showInactive && (
                    <ul className="budget-beta-inactive-list">
                      {budgetModel.inactive.map((item) => (
                        <li key={getCategoryKey(item)}>
                          <span className="budget-beta-category-icon" style={{ color: item.category.color }}>
                            {item.category.icon}
                          </span>
                          <span>{item.category.name}</span>
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
                </div>
              )}
            </section>
          )}

          {budgetModel.rows.length === 0 && budgetModel.unbudgetedRows.length === 0 && (
            <div className="empty-state budget-beta-empty">
              <div className="empty-state-icon">+</div>
              <h2>No Budget Activity Yet</h2>
              <p>Add budgets or pick another month to start tracking category spending.</p>
              <button
                type="button"
                className="btn-primary"
                onClick={() => setAddingBudget(true)}
              >
                Add Budget
              </button>
            </div>
          )}
        </div>
      )}

      {addingBudget && (
        <AddBudgetModal
          existingCategoryIds={new Set((data?.budgeted || []).map((item) => item.category.id))}
          allCategories={allCategories}
          onClose={() => setAddingBudget(false)}
          onSaved={async (payload) => {
            await upsertBudget(payload);
            setAddingBudget(false);
          }}
        />
      )}

      {editingBudget && (
        <BudgetAmountModal
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
        <AppSelect
          className="month-nav-select"
          value={month}
          options={monthOptions.map((item) => ({
            value: item,
            label: formatMonthLabel(item)
          }))}
          onChange={onJump}
          ariaLabel="Jump to month"
          menuPlacement="page-center"
        />
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

function SnapshotPanel({ model, monthLabel, selectedMonth, daysLeft, onAddBudget }) {
  const remainingTone = model.remaining < 0 ? 'over' : 'good';
  const hasBudget = model.totalBudgeted > 0;
  const spendPace = getSpendPace(model, selectedMonth, daysLeft);

  return (
    <section className="budget-beta-snapshot" aria-labelledby="budget-beta-snapshot-title">
      <div className={`budget-beta-snapshot-main ${remainingTone}`}>
        <div className="budget-beta-eyebrow" id="budget-beta-snapshot-title">
          {monthLabel} Snapshot
        </div>
        <div className="budget-beta-main-number">
          {formatMoney(model.totalExpenses)}
        </div>
        <div className="budget-beta-main-sub">
          spent of {formatMoney(model.totalBudgeted)} budgeted
        </div>
        <ProgressMeter percent={model.overallPercent} over={model.remaining < 0} size="large" />
        <div className="budget-beta-snapshot-result">
          {hasBudget ? (
            model.remaining < 0
              ? `${formatMoney(Math.abs(model.remaining))} over budget`
              : `${formatMoney(model.remaining)} remaining`
          ) : (
            'No category budgets yet'
          )}
        </div>
      </div>

      <div className="budget-beta-snapshot-grid">
        <SnapshotStat
          label="Budgeted Categories"
          value={`${formatMoney(model.spentInBudgets)} / ${formatMoney(model.totalBudgeted)}`}
          detail={`${formatPercent(model.budgetedPercent)} used`}
          tone={model.budgetedPercent > 100 ? 'over' : model.budgetedPercent >= 85 ? 'watch' : 'good'}
        />
        <SnapshotStat
          label="Unbudgeted Spending"
          value={formatMoney(model.spentUnbudgeted)}
          detail={`${formatPercent(model.unbudgetedShare)} of expenses`}
          tone={model.spentUnbudgeted > 0 ? 'watch' : 'good'}
        />
        <SnapshotStat
          label="Cash Flow"
          value={formatMoney(model.totalNet)}
          detail={`${formatMoney(model.totalIncome)} income`}
          tone={model.totalNet < 0 ? 'watch' : 'good'}
        />
        <SnapshotStat
          label="Spend Pace"
          value={spendPace.value}
          detail={spendPace.detail}
          tone={spendPace.tone}
        />
      </div>

      <div className="budget-beta-snapshot-actions">
        <button type="button" className="btn-primary" onClick={onAddBudget}>
          Add Budget
        </button>
      </div>
    </section>
  );
}

function SnapshotStat({ label, value, detail, tone }) {
  return (
    <div className={`budget-beta-stat ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}

function ProgressMeter({ percent, over = false, size = 'normal' }) {
  const clamped = clampPercent(percent);
  const label = `${formatPercent(percent)} used`;
  return (
    <div
      className={`budget-beta-progress ${size === 'large' ? 'large' : ''}`}
      role="progressbar"
      aria-valuenow={Math.round(percent)}
      aria-valuemin="0"
      aria-valuemax="100"
      title={label}
    >
      <div
        className={`budget-beta-progress-fill ${over ? 'over' : percent >= 85 ? 'watch' : ''}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}

function SpendingMixPanel({
  items,
  total,
  selectedMonth,
  expandedKey,
  transactionsByCategory,
  onToggle
}) {
  const visibleItems = items.slice(0, MAX_MIX_ITEMS);
  const otherAmount = items
    .slice(MAX_MIX_ITEMS)
    .reduce((sum, item) => sum + asNumber(item.spent), 0);
  const chartItems = otherAmount > 0
    ? [
        ...visibleItems,
        {
          category: { id: 'other', name: 'Other', color: 'var(--text-muted)', icon: '' },
          spent: otherAmount
        }
      ]
    : visibleItems;

  return (
    <section className="budget-beta-section budget-beta-mix-panel">
      <header className="budget-beta-section-header">
        <div>
          <h3>Spending Mix</h3>
          <p>Largest category shares this month</p>
          <p className="budget-beta-helper">Tap a category to see transactions</p>
        </div>
      </header>

      <div className="budget-beta-mix-strip" aria-label="Spending mix by category">
        {chartItems.map((item) => {
          const width = Math.max(2, percentOf(asNumber(item.spent), total));
          return (
            <span
              key={getCategoryKey(item)}
              style={{
                width: `${width}%`,
                backgroundColor: item.category.color || 'var(--accent)'
              }}
              title={`${item.category.name}: ${formatMoney(item.spent)}`}
            />
          );
        })}
      </div>

      <ul className="budget-beta-mix-list">
        {visibleItems.map((item) => {
          const key = getCategoryKey(item);
          const share = percentOf(asNumber(item.spent), total);
          const expanded = expandedKey === key;
          return (
            <li
              key={key}
              className={`budget-beta-mix-item ${expanded ? 'expanded' : ''}`}
            >
              <button
                type="button"
                className="budget-beta-mix-trigger"
                onClick={() => onToggle(item)}
                aria-expanded={expanded}
              >
                <span
                  className="budget-beta-dot"
                  style={{ backgroundColor: item.category.color || 'var(--accent)' }}
                  aria-hidden="true"
                />
                <span className="budget-beta-mix-icon" aria-hidden="true">
                  {item.category.icon}
                </span>
                <strong>{item.category.name}</strong>
                <em>{formatMoney(item.spent)}</em>
                <span className="budget-beta-mix-share">{formatPercent(share)}</span>
                <CollapseIndicator
                  expanded={expanded}
                  className="budget-beta-mix-collapse"
                  visible={false}
                />
              </button>

              <ExpandingSection
                expanded={expanded}
                className="budget-beta-mix-detail"
                innerClassName="budget-beta-mix-detail-inner"
              >
                <TransactionPreview
                  item={item}
                  selectedMonth={selectedMonth}
                  transactionsState={transactionsByCategory[key]}
                />
              </ExpandingSection>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function NotablePanel({
  notable,
  selectedMonth,
  expandedKey,
  transactionsByCategory,
  onToggle,
  onAdd
}) {
  return (
    <section className="budget-beta-section budget-beta-notable">
      <header className="budget-beta-section-header">
        <div>
          <h3>Notable This Month</h3>
          <p>Overages, high usage, and spending outside the plan</p>
          <p className="budget-beta-helper">Tap a category to see transactions</p>
        </div>
      </header>

      {notable.length > 0 ? (
        <ul className="budget-beta-notable-list">
          {notable.map(({ kind, tone, item }) => {
            const amount = asNumber(item.amount);
            const spent = asNumber(item.spent);
            const isUnbudgeted = kind === 'Unbudgeted';
            const detail = isUnbudgeted
              ? `${formatMoney(spent)} across ${item.transaction_count} ${item.transaction_count === 1 ? 'transaction' : 'transactions'}`
              : amount > 0
                ? `${formatMoney(spent)} of ${formatMoney(amount)}`
                : `${formatMoney(spent)} spent`;
            const cardKey = `notable-${kind}-${getCategoryKey(item)}`;

            return (
              <CompactTransactionCard
                key={cardKey}
                item={item}
                kind={kind}
                tone={tone}
                detail={detail}
                selectedMonth={selectedMonth}
                expanded={expandedKey === cardKey}
                transactionsState={transactionsByCategory[getCategoryKey(item)]}
                onToggle={() => onToggle(item, cardKey)}
                onAddBudget={isUnbudgeted && isBudgetableCategory(item) ? () => onAdd(item) : null}
              />
            );
          })}
        </ul>
      ) : (
        <div className="budget-beta-clear-state">
          <strong>No notable budget exceptions.</strong>
          <span>Budgeted categories are below high-usage thresholds and no unbudgeted spending is showing.</span>
        </div>
      )}
    </section>
  );
}

function BudgetRow({
  item,
  expanded,
  selectedMonth,
  transactionsState,
  onToggle,
  onEdit,
  onDelete
}) {
  const status = getStatus(item);
  const percent = item.amount > 0 ? item.pct : 0;
  const menuItems = [
    { label: 'Edit Amount', onClick: onEdit },
    { divider: true },
    { label: 'Delete', destructive: true, onClick: onDelete }
  ];

  function handleClick(event) {
    if (event.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    onToggle();
  }

  function handleKeyDown(event) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    if (event.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    event.preventDefault();
    onToggle();
  }

  return (
    <li
      className={`budget-beta-row ${status.tone} ${expanded ? 'expanded' : ''}`}
      tabIndex={0}
      aria-expanded={expanded}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
    >
      <div className="budget-beta-row-top">
        <span className="budget-beta-category-icon" style={{ color: item.category.color }}>
          {item.category.icon}
        </span>
        <div className="budget-beta-row-title">
          <div className="budget-beta-row-title-line">
            <strong>{item.category.name}</strong>
            <span className={`budget-beta-chip ${status.tone}`}>{status.label}</span>
          </div>
          <span>
            {formatMoney(item.spent)} spent of {formatMoney(item.amount)}
          </span>
        </div>
        <CollapseIndicator
          expanded={expanded}
          className="budget-beta-collapse"
          visible={false}
        />
        <DropdownMenu items={menuItems} ariaLabel={`Actions for ${item.category.name} budget`} />
      </div>

      <div className="budget-beta-row-meter">
        <ProgressMeter percent={percent} over={item.over} />
        <div className="budget-beta-row-meta">
          <span>{formatPercent(percent)} used</span>
          <span>
            {item.transaction_count} {item.transaction_count === 1 ? 'transaction' : 'transactions'}
          </span>
        </div>
      </div>

      <ExpandingSection
        expanded={expanded}
        className="budget-beta-row-detail"
        innerClassName="budget-beta-row-detail-inner"
      >
        <div className="budget-beta-row-detail-head">
          <h4>Transactions</h4>
          <Link to={buildTransactionsHref(item, selectedMonth)}>View All</Link>
        </div>
        <TransactionPreview
          item={item}
          selectedMonth={selectedMonth}
          transactionsState={transactionsState}
          showViewAll={false}
        />
      </ExpandingSection>
    </li>
  );
}

function CompactTransactionCard({
  item,
  kind,
  tone,
  detail,
  selectedMonth,
  expanded,
  transactionsState,
  onToggle,
  onAddBudget
}) {
  function handleClick(event) {
    if (event.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    onToggle();
  }

  function handleKeyDown(event) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    if (event.target.closest('button, a, input, select, textarea, [role="button"]')) return;
    event.preventDefault();
    onToggle();
  }

  return (
    <li
      className={`budget-beta-compact-card ${tone} ${expanded ? 'expanded' : ''}`}
      tabIndex={0}
      aria-expanded={expanded}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
    >
      <div className="budget-beta-compact-summary">
        <span className="budget-beta-category-icon" style={{ color: item.category.color }}>
          {item.category.icon}
        </span>
        <div className="budget-beta-compact-body">
          <div className="budget-beta-compact-top">
            <strong>{item.category.name}</strong>
            <span className={`budget-beta-chip ${tone}`}>{kind}</span>
            <CollapseIndicator
              expanded={expanded}
              className="budget-beta-compact-collapse"
              visible={false}
            />
          </div>
          <div className="budget-beta-compact-bottom">
            <em>{detail}</em>
            {onAddBudget && (
              <button
                type="button"
                className="btn-primary btn-compact"
                onClick={onAddBudget}
              >
                + Budget
              </button>
            )}
          </div>
        </div>
      </div>

      <ExpandingSection
        expanded={expanded}
        className="budget-beta-compact-detail"
        innerClassName="budget-beta-compact-detail-inner"
      >
        <TransactionPreview
          item={item}
          selectedMonth={selectedMonth}
          transactionsState={transactionsState}
        />
      </ExpandingSection>
    </li>
  );
}

function UnbudgetedBetaRow({
  item,
  selectedMonth,
  expanded,
  transactionsState,
  onToggle,
  onAdd
}) {
  return (
    <CompactTransactionCard
      item={item}
      kind="Unbudgeted"
      tone="neutral"
      detail={`${formatMoney(item.spent)} across ${item.transaction_count} ${
        item.transaction_count === 1 ? 'transaction' : 'transactions'
      }`}
      selectedMonth={selectedMonth}
      expanded={expanded}
      transactionsState={transactionsState}
      onToggle={onToggle}
      onAddBudget={onAdd}
    />
  );
}

function TransactionPreview({
  item,
  selectedMonth,
  transactionsState,
  showViewAll = true
}) {
  return (
    <>
      {showViewAll && (
        <div className="budget-beta-row-detail-head">
          <h4>Transactions</h4>
          <Link to={buildTransactionsHref(item, selectedMonth)}>View All</Link>
        </div>
      )}
      {transactionsState?.loading ? (
        <p className="subtle">Loading transactions...</p>
      ) : transactionsState?.error ? (
        <p className="error">{transactionsState.error}</p>
      ) : transactionsState?.items?.length ? (
        <>
          <ul className="budget-beta-transaction-list">
            {transactionsState.items.slice(0, MAX_TRANSACTION_PREVIEW).map((txn) => (
              <li key={txn.id}>
                <span>
                  <strong>{txn.merchant}</strong>
                  <em>{formatTransactionDate(txn.date)}</em>
                </span>
                <strong>{formatMoney(Math.abs(asNumber(txn.amount)))}</strong>
              </li>
            ))}
          </ul>
          {transactionsState.items.length > MAX_TRANSACTION_PREVIEW && (
            <p className="subtle">
              Showing {MAX_TRANSACTION_PREVIEW} of {transactionsState.items.length} transactions.
            </p>
          )}
        </>
      ) : (
        <p className="subtle">No matching transactions this month.</p>
      )}
    </>
  );
}

function AddBudgetModal({ existingCategoryIds, allCategories, onClose, onSaved }) {
  const [categoryId, setCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const availableCategories = useMemo(() => {
    const seen = new Set();
    const result = [];
    for (const category of allCategories) {
      if (!category || category.id == null || seen.has(category.id)) continue;
      seen.add(category.id);
      if (category.is_transfer) continue;
      if (existingCategoryIds.has(category.id)) continue;
      result.push(category);
    }
    return result.sort((a, b) => a.name.localeCompare(b.name));
  }, [allCategories, existingCategoryIds]);

  async function handleSave(event, close) {
    event.preventDefault();
    setError('');

    const cid = parseInt(categoryId, 10);
    if (!Number.isFinite(cid)) {
      setError('Pick a category.');
      return;
    }

    const parsedAmount = parseCurrencyInput(amount, NaN);
    if (!Number.isFinite(parsedAmount) || parsedAmount < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: cid, amount: parsedAmount });
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
          <h3>Add Budget</h3>
          <p className="subtle">
            This default monthly amount applies to every month.
          </p>

          {availableCategories.length === 0 ? (
            <>
              <p>Every spending category already has a budget.</p>
              <div className="modal-actions">
                <button type="button" className="btn-primary" onClick={close}>
                  OK
                </button>
              </div>
            </>
          ) : (
            <form onSubmit={(event) => handleSave(event, close)}>
              <label className="field">
                <span>Category</span>
                <AppSelect
                  value={categoryId}
                  onChange={setCategoryId}
                  placeholder="Pick One"
                  ariaLabel="Budget category"
                  options={availableCategories.map((category) => ({
                    value: category.id,
                    label: `${category.icon} ${category.name}`
                  }))}
                />
              </label>

              <label className="field">
                <span>Default Monthly Budget</span>
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
                  {saving ? 'Saving...' : 'Add Budget'}
                </button>
              </div>
            </form>
          )}
        </>
      )}
    </AnimatedModal>
  );
}
