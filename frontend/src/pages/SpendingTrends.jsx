import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import AppSelect from '../components/AppSelect.jsx';
import BudgetAmountModal from '../components/BudgetAmountModal.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import {
  formatCompactCurrency,
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import {
  formatMonthKeyLabel,
  getLocalMonthBounds
} from '../lib/localDate.js';

const RANGE_OPTIONS = [
  { value: '3', label: '3M', months: 3 },
  { value: '6', label: '6M', months: 6 },
  { value: '12', label: '12M', months: 12 }
];

function asNumber(value) {
  const number = Number(value || 0);
  return Number.isFinite(number) ? number : 0;
}

function categoryKey(category) {
  return category?.id == null ? 'uncategorized' : String(category.id);
}

function formatMoney(value, digits = 0) {
  return formatCurrency(value, { maximumFractionDigits: digits });
}

function formatSignedMoney(value) {
  return formatSignedCurrency(value, { maximumFractionDigits: 0 });
}

function formatMonthShort(month) {
  if (!month) return '';
  const [year, monthNumber] = month.split('-').map(Number);
  const date = new Date(year, monthNumber - 1, 1);
  return date.toLocaleDateString(undefined, { month: 'short' });
}

function formatMonthLong(month) {
  if (!month) return '';
  const [year, monthNumber] = month.split('-').map(Number);
  const date = new Date(year, monthNumber - 1, 1);
  return date.toLocaleDateString(undefined, { month: 'long' });
}

function formatMonthWithYear(month) {
  if (!month) return '';
  return formatMonthKeyLabel(month);
}

function percentOf(value, max) {
  if (!max) return 0;
  return (asNumber(value) / max) * 100;
}

function compactBandWidth(chartWidth, itemCount, maxBand) {
  if (itemCount <= 0) return chartWidth;
  return Math.min(chartWidth / itemCount, maxBand);
}

function centeredPlotLeft(left, chartWidth, band, itemCount) {
  return left + Math.max(0, chartWidth - band * itemCount) / 2;
}

function useSpendingTrends(range) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  async function load() {
    const firstLoad = data == null;
    if (firstLoad) setLoading(true);
    else setRefreshing(true);
    setError('');
    try {
      setData(await api.get(`/api/spending-trends?months=${encodeURIComponent(range.months)}`));
    } catch (err) {
      setError(err.message || 'Failed to load spending trends');
    } finally {
      if (firstLoad) setLoading(false);
      else setRefreshing(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range.value]);

  return { data, loading, refreshing, error, reload: load };
}

export default function SpendingTrends() {
  const navigate = useNavigate();
  const { alert, confirm, Dialog } = useAppDialog();
  const [rangeValue, setRangeValue] = useState('6');
  const [selectedCategoryKey, setSelectedCategoryKey] = useState('');
  const [editingBudget, setEditingBudget] = useState(null);
  const range = RANGE_OPTIONS.find((option) => option.value === rangeValue) || RANGE_OPTIONS[0];
  const { data, loading, refreshing, error, reload } = useSpendingTrends(range);
  const categories = data?.categories || [];
  const flow = data?.flow || [];
  const summary = data?.summary || {};

  useEffect(() => {
    if (!categories.length) {
      setSelectedCategoryKey('');
      return;
    }

    if (!categories.some((item) => categoryKey(item.category) === selectedCategoryKey)) {
      setSelectedCategoryKey(categoryKey(categories[0].category));
    }
  }, [categories, selectedCategoryKey]);

  const selectedCategory = useMemo(
    () => categories.find((item) => categoryKey(item.category) === selectedCategoryKey) || categories[0],
    [categories, selectedCategoryKey]
  );

  const categoryOptions = categories.map((item) => ({
    value: categoryKey(item.category),
    label: item.category.name
  }));

  const hasTrendData =
    flow.some((item) => asNumber(item.income) > 0 || asNumber(item.expenses) > 0) ||
    categories.some((item) => asNumber(item.total_spent) > 0);
  const canEditSelectedBudget =
    selectedCategory?.category?.id != null &&
    Number.isInteger(Number(selectedCategory.category.id));
  const selectedBudgetActionLabel = selectedCategory?.budget_id ? 'Edit Budget' : 'Add Budget';

  function openSelectedBudgetModal() {
    if (!canEditSelectedBudget || !selectedCategory) return;
    setEditingBudget({
      category: selectedCategory.category,
      budget_id: selectedCategory.budget_id,
      amount: selectedCategory.budget_amount,
      spent: selectedCategory.months?.[selectedCategory.months.length - 1]?.spent || 0
    });
  }

  async function upsertBudget(payload) {
    await api.put('/api/budgets', payload);
    await reload();
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
      await reload();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete Failed' });
      throw err;
    }
  }

  async function viewCategoryTransactions(month) {
    if (!selectedCategory?.category || !/^\d{4}-\d{2}$/.test(String(month || ''))) return;

    const category = selectedCategory.category;
    const categoryName = category.name || 'Uncategorized';
    const monthLabel = formatMonthWithYear(month);
    const ok = await confirm(
      `Do you want to view all of your ${categoryName} transactions in ${monthLabel}?`,
      {
        title: 'View Transactions',
        confirmLabel: 'View Transactions',
        confirmDelayMs: 150
      }
    );
    if (!ok) return;

    const { start, end } = getLocalMonthBounds(month);
    const params = new URLSearchParams({
      categories: category.id == null ? 'uncategorized' : String(category.id),
      date_from: start,
      date_to: end,
      include_ignored: '0',
      include_transfers: '0'
    });

    navigate(`/transactions?${params.toString()}`, { state: { transition: 'forward' } });
  }

  return (
    <div className="spending-trends-view">
      <PageHero
        id="spending-trends-title"
        variant="spending"
        kicker="History"
        title="Spending Trends"
        subtitle={`What has happened over the last ${range.months} months`}
        toolbar={
          <RangeTabs
            value={rangeValue}
            onChange={setRangeValue}
          />
        }
      />

      {error && <div className="error">{error}</div>}

      {loading && !data ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : !data || !hasTrendData ? (
        <div className="empty-state">
          <div className="empty-state-icon">$</div>
          <h2>No Spending Trends Yet</h2>
          <p>Import or add transactions to see income, expenses, and category history over time.</p>
        </div>
      ) : (
        <div className={`spending-trends-content ${refreshing ? 'is-refreshing' : ''}`}>
          <section className="dashboard-card spending-flow-card">
            <header className="dashboard-card-header spending-card-header">
              <div>
                <h3>Income Vs Expenses</h3>
                <span className="muted">
                  {formatMonthWithYear(flow[0]?.month)} - {formatMonthWithYear(flow[flow.length - 1]?.month)}
                </span>
              </div>
              <strong className={asNumber(summary.total_net) >= 0 ? 'income' : 'expense'}>
                {formatSignedMoney(summary.total_net)}
              </strong>
            </header>
            <CashFlowChart flow={flow} />
          </section>

          <section className="dashboard-card spending-category-card">
            <header className="dashboard-card-header spending-card-header">
              <div>
                <h3>Category History</h3>
              </div>
              {categoryOptions.length > 0 && (
                <div className="spending-category-actions">
                  <AppSelect
                    value={selectedCategoryKey}
                    options={categoryOptions}
                    onChange={setSelectedCategoryKey}
                    className="spending-category-select"
                    ariaLabel="Choose spending category"
                    menuPlacement="page-center"
                  />
                  {canEditSelectedBudget && (
                    <button
                      type="button"
                      className="btn-primary spending-budget-action"
                      onClick={openSelectedBudgetModal}
                    >
                      {selectedBudgetActionLabel}
                    </button>
                  )}
                </div>
              )}
            </header>

            {selectedCategory ? (
              <div className="spending-category-layout">
                <div className="spending-category-main">
                  <CategoryStats trend={selectedCategory} rangeMonths={range.months} />
                  <CategoryTrendChart
                    trend={selectedCategory}
                    onMonthSelect={viewCategoryTransactions}
                  />
                </div>
                <CategoryRankList
                  categories={categories}
                  selectedKey={selectedCategoryKey}
                  onSelect={setSelectedCategoryKey}
                />
              </div>
            ) : (
              <p className="subtle">No spending categories in this range.</p>
            )}
          </section>
        </div>
      )}

      {editingBudget && (
        <BudgetAmountModal
          item={editingBudget}
          onClose={() => setEditingBudget(null)}
          spendingDetail={`${formatMoney(selectedCategory?.average_spent || 0)} average over this range.`}
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

function RangeTabs({ value, onChange }) {
  return (
    <div className="housing-segmented spending-range-tabs" role="tablist" aria-label="Spending trend range">
      {RANGE_OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          className={value === option.value ? 'active' : ''}
          onClick={() => onChange(option.value)}
          role="tab"
          aria-selected={value === option.value}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function CashFlowChart({ flow }) {
  const [activeIndex, setActiveIndex] = useState(null);
  const touchStartRef = useRef(null);
  const ignoreNextClickRef = useRef(false);
  const width = 720;
  const height = 310;
  const top = 12;
  const bottom = 42;
  const left = 34;
  const right = 14;
  const chartWidth = width - left - right;
  const chartHeight = height - top - bottom;
  const maxValue = Math.max(
    1,
    ...flow.flatMap((item) => [asNumber(item.income), asNumber(item.expenses)])
  );
  const band = compactBandWidth(
    chartWidth,
    flow.length,
    flow.length <= 3 ? 126 : flow.length <= 6 ? 104 : 68
  );
  const plotLeft = centeredPlotLeft(left, chartWidth, band, flow.length);
  const barWidth = Math.max(20, Math.min(48, band * 0.34));
  const gap = Math.max(7, Math.min(14, band * 0.1));
  const gridLines = [0.25, 0.5, 0.75, 1];
  const labelEvery = flow.length > 12 ? 2 : 1;
  const activeItem = activeIndex == null ? null : flow[activeIndex];
  const activeCenter = activeIndex == null
    ? 0
    : plotLeft + band * activeIndex + band / 2;
  const activeNet = activeItem
    ? asNumber(activeItem.income) - asNumber(activeItem.expenses)
    : 0;
  const tooltipAlign =
    activeCenter < width * 0.28 ? 'align-left' : activeCenter > width * 0.72 ? 'align-right' : '';

  useEffect(() => {
    setActiveIndex(null);
    touchStartRef.current = null;
  }, [flow]);

  function startTouchSelection(event, index) {
    if (event.pointerType !== 'touch') return;
    touchStartRef.current = {
      index,
      x: event.clientX,
      y: event.clientY,
      moved: false
    };
  }

  function trackTouchSelection(event) {
    if (event.pointerType !== 'touch' || !touchStartRef.current) return;
    const dx = Math.abs(event.clientX - touchStartRef.current.x);
    const dy = Math.abs(event.clientY - touchStartRef.current.y);
    if (dx > 10 || dy > 10) {
      touchStartRef.current.moved = true;
    }
  }

  function finishTouchSelection(event, index) {
    if (event.pointerType !== 'touch') return;
    const touchStart = touchStartRef.current;
    touchStartRef.current = null;
    ignoreNextClickRef.current = true;
    window.setTimeout(() => {
      ignoreNextClickRef.current = false;
    }, 400);

    if (!touchStart || touchStart.index !== index || touchStart.moved) return;
    setActiveIndex(index);
  }

  function yFor(value) {
    return top + chartHeight - (asNumber(value) / maxValue) * chartHeight;
  }

  return (
    <div className="spending-chart-wrap spending-flow-chart-wrap">
      <svg
        className={`spending-flow-chart ${activeItem ? 'has-active' : ''}`}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Monthly income and expense chart"
        onPointerLeave={(event) => {
          if (event.pointerType !== 'touch') setActiveIndex(null);
        }}
      >
        {gridLines.map((line) => {
          const y = top + chartHeight - chartHeight * line;
          return (
            <g key={line}>
              <line x1={left} y1={y} x2={width - right} y2={y} className="spending-chart-gridline" />
              <text x={0} y={y + 4} className="spending-chart-axis-label">
                {formatCompactCurrency(maxValue * line)}
              </text>
            </g>
          );
        })}

        <line x1={left} y1={top + chartHeight} x2={width - right} y2={top + chartHeight} className="spending-chart-axis" />

        {flow.map((item, index) => {
          const center = plotLeft + band * index + band / 2;
          const incomeHeight = top + chartHeight - yFor(item.income);
          const expenseHeight = top + chartHeight - yFor(item.expenses);
          const showLabel = index % labelEvery === 0 || index === flow.length - 1;
          const monthNet = asNumber(item.income) - asNumber(item.expenses);
          const monthLabel = formatMonthWithYear(item.month);
          const hitboxLabel = `${monthLabel}: income ${formatMoney(item.income)}, expenses ${formatMoney(item.expenses)}, net ${formatSignedMoney(monthNet)}`;
          const barActiveClass = activeIndex === index ? ' is-active' : '';

          return (
            <g key={item.month}>
              <rect
                x={center - barWidth - gap / 2}
                y={yFor(item.income)}
                width={barWidth}
                height={Math.max(2, incomeHeight)}
                rx="4"
                className={`spending-flow-bar income${barActiveClass}`}
              >
                <title>{`${formatMonthWithYear(item.month)} income: ${formatMoney(item.income)}`}</title>
              </rect>
              <rect
                x={center + gap / 2}
                y={yFor(item.expenses)}
                width={barWidth}
                height={Math.max(2, expenseHeight)}
                rx="4"
                className={`spending-flow-bar expense${barActiveClass}`}
              >
                <title>{`${formatMonthWithYear(item.month)} expenses: ${formatMoney(item.expenses)}`}</title>
              </rect>
              {showLabel && (
                <text x={center} y={height - 18} textAnchor="middle" className="spending-chart-month-label">
                  {formatMonthShort(item.month)}
                </text>
              )}
              <rect
                x={plotLeft + band * index}
                y={top}
                width={band}
                height={chartHeight + 30}
                className="spending-chart-hitbox"
                tabIndex={0}
                role="button"
                aria-label={hitboxLabel}
                onPointerEnter={(event) => {
                  if (event.pointerType !== 'touch') setActiveIndex(index);
                }}
                onPointerMove={(event) => {
                  if (event.pointerType !== 'touch') {
                    setActiveIndex(index);
                    return;
                  }
                  trackTouchSelection(event);
                }}
                onPointerDown={(event) => {
                  startTouchSelection(event, index);
                }}
                onPointerUp={(event) => {
                  finishTouchSelection(event, index);
                }}
                onPointerCancel={() => {
                  touchStartRef.current = null;
                  ignoreNextClickRef.current = true;
                  window.setTimeout(() => {
                    ignoreNextClickRef.current = false;
                  }, 400);
                }}
                onClick={() => {
                  if (ignoreNextClickRef.current) return;
                  setActiveIndex(index);
                }}
                onFocus={() => setActiveIndex(index)}
                onBlur={() => setActiveIndex(null)}
                onKeyDown={(event) => {
                  if (event.key !== 'Enter' && event.key !== ' ') return;
                  event.preventDefault();
                  setActiveIndex(index);
                }}
              />
            </g>
          );
        })}
      </svg>
      {activeItem && (
        <div
          className={`spending-flow-tooltip ${tooltipAlign}`}
          role="status"
          style={{ '--tooltip-x': `${(activeCenter / width) * 100}%` }}
        >
          <strong>{formatMonthWithYear(activeItem.month)}</strong>
          <span>
            <em className="income">Income</em>
            <b className="income">{formatMoney(activeItem.income)}</b>
          </span>
          <span>
            <em className="expense">Expenses</em>
            <b className="expense">{formatMoney(activeItem.expenses)}</b>
          </span>
          <span>
            <em className={activeNet >= 0 ? 'income' : 'danger'}>Net</em>
            <b className={activeNet >= 0 ? 'income' : 'danger'}>{formatSignedMoney(activeNet)}</b>
          </span>
        </div>
      )}
      <div className="spending-chart-legend">
        <span><i className="income" />Income</span>
        <span><i className="expense" />Expenses</span>
      </div>
    </div>
  );
}

function CategoryTrendChart({ trend, onMonthSelect }) {
  const touchStartRef = useRef(null);
  const ignoreNextClickRef = useRef(false);
  const width = 720;
  const height = 500;
  const top = 16;
  const bottom = 42;
  const left = 34;
  const right = 18;
  const chartWidth = width - left - right;
  const chartHeight = height - top - bottom;
  const months = trend.months || [];
  const maxValue = Math.max(
    1,
    asNumber(trend.budget_amount),
    ...months.map((item) => asNumber(item.spent))
  );
  const band = compactBandWidth(chartWidth, months.length, months.length <= 6 ? 82 : 60);
  const plotLeft = centeredPlotLeft(left, chartWidth, band, months.length);
  const barWidth = Math.max(18, Math.min(46, band * 0.56));
  const labelEvery = months.length > 12 ? 3 : months.length > 8 ? 2 : 1;
  const budgetY = top + chartHeight - (asNumber(trend.budget_amount) / maxValue) * chartHeight;
  const categoryColor = trend.category?.color || 'var(--accent)';

  function yFor(value) {
    return top + chartHeight - (asNumber(value) / maxValue) * chartHeight;
  }

  function startTouchSelection(event, index) {
    if (event.pointerType !== 'touch') return;
    touchStartRef.current = {
      index,
      x: event.clientX,
      y: event.clientY,
      moved: false
    };
  }

  function trackTouchSelection(event) {
    if (event.pointerType !== 'touch' || !touchStartRef.current) return;
    const dx = Math.abs(event.clientX - touchStartRef.current.x);
    const dy = Math.abs(event.clientY - touchStartRef.current.y);
    if (dx > 10 || dy > 10) {
      touchStartRef.current.moved = true;
    }
  }

  function finishTouchSelection(event, index, item) {
    if (event.pointerType !== 'touch') return;
    const touchStart = touchStartRef.current;
    touchStartRef.current = null;
    ignoreNextClickRef.current = true;
    window.setTimeout(() => {
      ignoreNextClickRef.current = false;
    }, 400);

    if (!touchStart || touchStart.index !== index || touchStart.moved) return;
    onMonthSelect?.(item.month);
  }

  return (
    <div className="spending-chart-wrap">
      <svg className="spending-category-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${trend.category.name} spending by month`}>
        {[0.25, 0.5, 0.75, 1].map((line) => {
          const y = top + chartHeight - chartHeight * line;
          return (
            <g key={line}>
              <line x1={left} y1={y} x2={width - right} y2={y} className="spending-chart-gridline" />
              <text x={0} y={y + 4} className="spending-chart-axis-label">
                {formatCompactCurrency(maxValue * line)}
              </text>
            </g>
          );
        })}

        <line x1={left} y1={top + chartHeight} x2={width - right} y2={top + chartHeight} className="spending-chart-axis" />

        {asNumber(trend.budget_amount) > 0 && (
          <g>
            <line
              x1={left}
              y1={budgetY}
              x2={width - right}
              y2={budgetY}
              className="spending-budget-line"
            >
              <title>{`Budget: ${formatMoney(trend.budget_amount)}`}</title>
            </line>
            <text x={width - right - 4} y={Math.max(14, budgetY - 7)} textAnchor="end" className="spending-budget-label">
              Budget {formatCompactCurrency(trend.budget_amount)}
            </text>
          </g>
        )}

        {months.map((item, index) => {
          const center = plotLeft + band * index + band / 2;
          const barHeight = top + chartHeight - yFor(item.spent);
          const showLabel = index % labelEvery === 0 || index === months.length - 1;
          const canViewTransactions = Number(item.transaction_count || 0) > 0 && typeof onMonthSelect === 'function';
          const monthLabel = formatMonthWithYear(item.month);
          const transactionLabel = `${monthLabel}: ${formatMoney(item.spent)} across ${item.transaction_count} ${item.transaction_count === 1 ? 'transaction' : 'transactions'}`;

          return (
            <g key={item.month}>
              <rect
                x={center - barWidth / 2}
                y={yFor(item.spent)}
                width={barWidth}
                height={Math.max(2, barHeight)}
                rx="5"
                className={`spending-category-bar ${item.over_budget ? 'over' : ''}`}
                style={{ '--category-color': categoryColor }}
              >
                <title>{transactionLabel}</title>
              </rect>
              {showLabel && (
                <text x={center} y={height - 18} textAnchor="middle" className="spending-chart-month-label">
                  {formatMonthShort(item.month)}
                </text>
              )}
              {canViewTransactions && (
                <rect
                  x={plotLeft + band * index}
                  y={top}
                  width={band}
                  height={chartHeight + 30}
                  className="spending-chart-hitbox"
                  tabIndex={0}
                  role="button"
                  aria-label={`View ${trend.category.name} transactions for ${transactionLabel}`}
                  onPointerMove={trackTouchSelection}
                  onPointerDown={(event) => {
                    startTouchSelection(event, index);
                  }}
                  onPointerUp={(event) => {
                    finishTouchSelection(event, index, item);
                  }}
                  onPointerCancel={() => {
                    touchStartRef.current = null;
                    ignoreNextClickRef.current = true;
                    window.setTimeout(() => {
                      ignoreNextClickRef.current = false;
                    }, 400);
                  }}
                  onClick={() => {
                    if (ignoreNextClickRef.current) return;
                    onMonthSelect(item.month);
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' && event.key !== ' ') return;
                    event.preventDefault();
                    onMonthSelect(item.month);
                  }}
                >
                  <title>{`View ${trend.category.name} transactions for ${monthLabel}`}</title>
                </rect>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function CategoryStats({ trend, rangeMonths }) {
  const highest = trend.highest_month;
  const budget = trend.budget_amount == null ? 'No Budget' : formatMoney(trend.budget_amount);
  const overBudget = trend.budget_amount == null
    ? 'No budget line'
    : `${trend.over_budget_count} of ${rangeMonths} months`;
  const highLabel = highest?.month
    ? `${formatMoney(highest.spent)} in ${formatMonthLong(highest.month)}`
    : formatMoney(0);
  const stats = [
    ['Budget', budget],
    ['Average', formatMoney(trend.average_spent)],
    ['Highest', highLabel],
    ['Over Budget', overBudget],
  ];

  return (
    <div className="spending-category-stats">
      {stats.map(([label, value]) => (
        <div key={label} className="spending-category-stat">
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}

function CategoryRankList({ categories, selectedKey, onSelect }) {
  const visible = categories.slice(0, 8);
  if (!visible.length) return null;
  const maxAverage = Math.max(1, ...visible.map((item) => asNumber(item.average_spent)));

  return (
    <div className="spending-category-rank-list" aria-label="Top spending categories">
      {visible.map((item) => {
        const key = categoryKey(item.category);
        const active = key === selectedKey;
        return (
          <button
            key={key}
            type="button"
            className={active ? 'active' : ''}
            onClick={() => onSelect(key)}
            aria-pressed={active}
          >
            <span
              className="spending-category-dot"
              style={{ backgroundColor: item.category.color || 'var(--accent)' }}
              aria-hidden="true"
            />
            <span className="spending-category-rank-main">
              <strong>{item.category.name}</strong>
              <em>{formatMoney(item.average_spent)} average</em>
            </span>
            <span className="spending-category-rank-meter" aria-hidden="true">
              <i style={{ width: `${Math.max(5, percentOf(item.average_spent, maxAverage))}%` }} />
            </span>
          </button>
        );
      })}
    </div>
  );
}
