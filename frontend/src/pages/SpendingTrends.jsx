import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AppSelect from '../components/AppSelect.jsx';
import PageHero from '../components/PageHero.jsx';
import {
  formatCompactCurrency,
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import { formatMonthKeyLabel } from '../lib/localDate.js';

const RANGE_OPTIONS = [
  { value: '6', label: '6M', months: 6 },
  { value: '12', label: '12M', months: 12 },
  { value: '24', label: '24M', months: 24 }
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

function formatMonthWithYear(month) {
  if (!month) return '';
  return formatMonthKeyLabel(month);
}

function percentOf(value, max) {
  if (!max) return 0;
  return (asNumber(value) / max) * 100;
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

  return { data, loading, refreshing, error };
}

export default function SpendingTrends() {
  const [rangeValue, setRangeValue] = useState('6');
  const [selectedCategoryKey, setSelectedCategoryKey] = useState('');
  const range = RANGE_OPTIONS.find((option) => option.value === rangeValue) || RANGE_OPTIONS[0];
  const { data, loading, refreshing, error } = useSpendingTrends(range);
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
  const selectedCategoryAverage = selectedCategory
    ? formatMoney(selectedCategory.average_spent)
    : formatMoney(0);

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
                {selectedCategory && (
                  <span className="muted">
                    {selectedCategory.category.name} averages {selectedCategoryAverage}
                  </span>
                )}
              </div>
              {categoryOptions.length > 0 && (
                <AppSelect
                  value={selectedCategoryKey}
                  options={categoryOptions}
                  onChange={setSelectedCategoryKey}
                  className="spending-category-select"
                  ariaLabel="Choose spending category"
                  menuPlacement="page-center"
                />
              )}
            </header>

            {selectedCategory ? (
              <div className="spending-category-layout">
                <CategoryTrendChart trend={selectedCategory} />
                <CategoryStats trend={selectedCategory} rangeMonths={range.months} />
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
  const width = 720;
  const height = 260;
  const top = 18;
  const bottom = 48;
  const left = 34;
  const right = 14;
  const chartWidth = width - left - right;
  const chartHeight = height - top - bottom;
  const maxValue = Math.max(
    1,
    ...flow.flatMap((item) => [asNumber(item.income), asNumber(item.expenses)])
  );
  const band = chartWidth / Math.max(1, flow.length);
  const barWidth = Math.max(8, Math.min(22, band * 0.22));
  const gap = Math.max(3, Math.min(8, band * 0.08));
  const gridLines = [0.25, 0.5, 0.75, 1];
  const labelEvery = flow.length > 12 ? 3 : flow.length > 8 ? 2 : 1;

  function yFor(value) {
    return top + chartHeight - (asNumber(value) / maxValue) * chartHeight;
  }

  return (
    <div className="spending-chart-wrap">
      <svg className="spending-flow-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Monthly income and expense chart">
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
          const center = left + band * index + band / 2;
          const incomeHeight = top + chartHeight - yFor(item.income);
          const expenseHeight = top + chartHeight - yFor(item.expenses);
          const showLabel = index % labelEvery === 0 || index === flow.length - 1;

          return (
            <g key={item.month}>
              <rect
                x={center - barWidth - gap / 2}
                y={yFor(item.income)}
                width={barWidth}
                height={Math.max(2, incomeHeight)}
                rx="4"
                className="spending-flow-bar income"
              >
                <title>{`${formatMonthWithYear(item.month)} income: ${formatMoney(item.income)}`}</title>
              </rect>
              <rect
                x={center + gap / 2}
                y={yFor(item.expenses)}
                width={barWidth}
                height={Math.max(2, expenseHeight)}
                rx="4"
                className="spending-flow-bar expense"
              >
                <title>{`${formatMonthWithYear(item.month)} expenses: ${formatMoney(item.expenses)}`}</title>
              </rect>
              {showLabel && (
                <text x={center} y={height - 18} textAnchor="middle" className="spending-chart-month-label">
                  {formatMonthShort(item.month)}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      <div className="spending-chart-legend">
        <span><i className="income" />Income</span>
        <span><i className="expense" />Expenses</span>
      </div>
    </div>
  );
}

function CategoryTrendChart({ trend }) {
  const width = 720;
  const height = 260;
  const top = 20;
  const bottom = 48;
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
  const band = chartWidth / Math.max(1, months.length);
  const barWidth = Math.max(12, Math.min(34, band * 0.48));
  const labelEvery = months.length > 12 ? 3 : months.length > 8 ? 2 : 1;
  const budgetY = top + chartHeight - (asNumber(trend.budget_amount) / maxValue) * chartHeight;
  const categoryColor = trend.category?.color || 'var(--accent)';

  function yFor(value) {
    return top + chartHeight - (asNumber(value) / maxValue) * chartHeight;
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
          const center = left + band * index + band / 2;
          const barHeight = top + chartHeight - yFor(item.spent);
          const showLabel = index % labelEvery === 0 || index === months.length - 1;

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
                <title>{`${formatMonthWithYear(item.month)}: ${formatMoney(item.spent)}`}</title>
              </rect>
              {showLabel && (
                <text x={center} y={height - 18} textAnchor="middle" className="spending-chart-month-label">
                  {formatMonthShort(item.month)}
                </text>
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
    ? `${formatMoney(highest.spent)} in ${formatMonthShort(highest.month)}`
    : formatMoney(0);
  const stats = [
    ['Average', formatMoney(trend.average_spent)],
    ['Median', formatMoney(trend.median_spent)],
    ['Highest', highLabel],
    ['Budget', budget],
    ['Over Budget', overBudget],
    ['Transactions', String(trend.transaction_count || 0)]
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
