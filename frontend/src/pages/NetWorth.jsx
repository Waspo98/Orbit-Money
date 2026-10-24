import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import ChartFrame from '../components/charts/ChartFrame.jsx';
import DashboardCard from '../components/dashboard/DashboardCard.jsx';
import EmptyState from '../components/EmptyState.jsx';
import PageHero from '../components/PageHero.jsx';
import DropdownMenu from '../components/DropdownMenu.jsx';
import SignalRow from '../components/SignalRow.jsx';
import {
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import { parseMonthParts } from '../lib/localDate.js';

const BUCKET_LABELS = {
  cash: 'Cash',
  investments: 'Investments',
  realEstate: 'Real estate',
  credit: 'Credit cards',
  loans: 'Loans',
  other: 'Other'
};

const BUCKET_COLORS = {
  cash: '#059669',
  investments: '#7c3aed',
  realEstate: '#0ea5e9',
  credit: '#dc2626',
  loans: '#d97706',
  other: '#64748b'
};

const TYPE_LABELS = {
  checking: 'Checking',
  savings: 'Savings',
  credit: 'Credit',
  investment: 'Investment',
  loan: 'Loan',
  mortgage: 'Mortgage',
  cash: 'Cash',
  other: 'Other'
};

const RANGE_OPTIONS = [
  { key: '12', label: '1 year', months: 12 },
  { key: '24', label: '2 years', months: 24 },
  { key: '36', label: '3 years', months: 36 },
  { key: '60', label: '5 years', months: 60 },
  { key: '120', label: '10 years', months: 120 },
  { key: 'all', label: 'All time', months: 'all' }
];

function formatMoney(amount, digits = 0) {
  return formatCurrency(amount, { maximumFractionDigits: digits });
}

function formatSignedMoney(amount) {
  return formatSignedCurrency(amount, { maximumFractionDigits: 0 });
}

function formatMonth(key) {
  const parts = parseMonthParts(key);
  if (!parts) return '';
  return new Date(parts.year, parts.month - 1, 1).toLocaleDateString(undefined, {
    month: 'short',
    year: 'numeric'
  });
}

function percent(part, total) {
  const denom = Math.abs(Number(total || 0));
  if (!denom) return 0;
  return (Math.abs(Number(part || 0)) / denom) * 100;
}

function getRange(history) {
  if (!history.length) return { min: 0, max: 1 };
  const values = history.map((p) => Number(p.netWorth) || 0);
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    min -= Math.max(100, Math.abs(min) * 0.1);
    max += Math.max(100, Math.abs(max) * 0.1);
  }
  const pad = (max - min) * 0.12;
  return { min: min - pad, max: max + pad };
}

function buildChartPath(history, width, height) {
  if (!history.length) return '';
  const { min, max } = getRange(history);
  return history
    .map((point, index) => {
      const x = history.length === 1 ? width / 2 : (index / (history.length - 1)) * width;
      const y = height - ((Number(point.netWorth) - min) / (max - min)) * height;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(' ');
}

function buildAreaPath(history, width, height) {
  const line = buildChartPath(history, width, height);
  if (!line) return '';
  return `${line} L ${width} ${height} L 0 ${height} Z`;
}

function useNetWorth(range) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      setData(await api.get(`/api/net-worth?months=${encodeURIComponent(range.months)}`));
    } catch (err) {
      setError(err.message || 'Failed to load net worth');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range.key]);

  return { data, loading, error, reload: load };
}

export default function NetWorth() {
  const [rangeKey, setRangeKey] = useState('24');
  const range = RANGE_OPTIONS.find((option) => option.key === rangeKey) || RANGE_OPTIONS[1];
  const { data, loading, error } = useNetWorth(range);

  const summary = data?.summary || {};
  const history = data?.history || [];
  const breakdown = data?.breakdown || [];
  const latest = history[history.length - 1];
  const topAccounts = breakdown.slice(0, 8);
  const positiveAccounts = breakdown.filter((a) => a.contribution > 0);
  const debtAccounts = breakdown.filter((a) => a.contribution < 0);

  const mix = useMemo(() => {
    const buckets = ['cash', 'investments', 'realEstate', 'credit', 'loans', 'other'];
    return buckets
      .map((key) => ({ key, label: BUCKET_LABELS[key], value: Number(summary[key]) || 0 }))
      .filter((item) => item.value !== 0);
  }, [summary]);

  const largestPositive = positiveAccounts[0];
  const largestDebt = debtAccounts[0];
  const debtShare = percent(summary.liabilities, summary.assets);
  const monthTone = Number(summary.monthOverMonth || 0) >= 0 ? 'income' : 'expense';
  const yearTone = Number(summary.yearToDateChange || 0) >= 0 ? 'income' : 'expense';
  const netWorthStats = [
    {
      label: 'Current',
      value: loading ? 'Loading' : formatMoney(summary.netWorth),
      tone: Number(summary.netWorth || 0) >= 0 ? 'good' : 'caution'
    },
    {
      label: 'This Month',
      value: loading ? 'Loading' : formatSignedMoney(summary.monthOverMonth),
      tone: monthTone === 'income' ? 'good' : 'caution'
    },
    {
      label: 'This Year',
      value: loading ? 'Loading' : formatSignedMoney(summary.yearToDateChange),
      tone: yearTone === 'income' ? 'good' : 'caution'
    },
    {
      label: 'Debt Share',
      value: loading ? 'Loading' : `${Math.round(debtShare)}%`,
      tone: debtShare > 65 ? 'caution' : debtShare > 35 ? 'warn' : 'good'
    }
  ];

  return (
    <div className="networth-view">
      <PageHero
        id="networth-title"
        variant="networth"
        kicker="Balance Sheet"
        title="Net Worth"
        subtitle={`Assets minus liabilities${latest ? `, updated through ${formatMonth(latest.month)}` : ''}`}
        stats={netWorthStats}
      />

      {error && <div className="error app-page-width">{error}</div>}

      {loading ? (
        <div className="center-loading app-page-width">
          <div className="spinner" />
        </div>
      ) : !data || summary.accountCount === 0 ? (
        <EmptyState
          className="app-page-width"
          icon="$"
          title="No net worth yet"
          description="Add or import accounts to start tracking assets, debts, and progress."
          action={<Link to="/accounts" className="btn-primary">Manage accounts</Link>}
        />
      ) : (
        <>
          <div className="networth-grid app-page-width">
            <DashboardCard
              title="Net worth over time"
              className="networth-trend-card"
              action={
                <DropdownMenu
                  ariaLabel="Choose net worth range"
                  triggerClassName="dashboard-card-link button-link networth-range-trigger"
                  renderTrigger={() => range.label}
                  items={RANGE_OPTIONS.map((option) => ({
                    label: option.label,
                    icon: option.key === range.key ? '✓' : '',
                    onClick: () => setRangeKey(option.key)
                  }))}
                />
              }
              bodyClassName="networth-chart-body"
            >
              <NetWorthChart history={history} />
            </DashboardCard>

            <DashboardCard
              title="Allocation"
              action={<Link to="/accounts" className="dashboard-card-link">Accounts</Link>}
            >
              <AllocationBar items={mix} total={summary.netWorth} />
              <ul className="networth-mix-list">
                {mix.map((item) => (
                  <li key={item.key}>
                    <span>
                      <i style={{ background: BUCKET_COLORS[item.key] }} />
                      {item.label}
                    </span>
                    <strong className={item.value < 0 ? 'expense' : ''}>
                      {formatMoney(item.value)}
                    </strong>
                  </li>
                ))}
              </ul>
            </DashboardCard>

            <DashboardCard title="Signals">
              <SignalRow
                label="Largest asset"
                value={largestPositive ? largestPositive.name : 'None yet'}
                detail={largestPositive ? formatMoney(largestPositive.contribution) : formatMoney(0)}
              />
              <SignalRow
                label="Largest liability"
                value={largestDebt ? largestDebt.name : 'None yet'}
                detail={largestDebt ? formatMoney(largestDebt.contribution) : formatMoney(0)}
                detailClassName={largestDebt ? 'expense' : ''}
              />
              <SignalRow
                label="Tracked accounts"
                value={`${summary.accountCount.toLocaleString()} active`}
                detail={`${positiveAccounts.length} positive, ${debtAccounts.length} negative`}
              />
            </DashboardCard>

            <DashboardCard
              title="Account contributions"
              className="networth-account-card"
              action={<Link to="/accounts" className="dashboard-card-link">Edit values</Link>}
            >
              <ul className="networth-account-list">
                {topAccounts.map((account) => (
                  <AccountContribution key={account.id} account={account} total={summary.netWorth} />
                ))}
              </ul>
            </DashboardCard>
          </div>
        </>
      )}
    </div>
  );
}

function NetWorthChart({ history }) {
  const width = 640;
  const height = 220;
  const path = buildChartPath(history, width, height);
  const areaPath = buildAreaPath(history, width, height);
  const latest = history[history.length - 1];
  const first = history[0];

  if (!history.length) {
    return <p className="subtle" style={{ margin: 0 }}>No history to chart yet.</p>;
  }

  return (
    <div className="networth-chart-wrap">
      <ChartFrame className="networth-chart" width={width} height={height} label="Net worth trend">
        <defs>
          <linearGradient id="networthArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.24" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        <path d={areaPath} fill="url(#networthArea)">
          <title>{`Net worth range: ${formatMoney(first?.netWorth || 0)} to ${formatMoney(latest?.netWorth || 0)}`}</title>
        </path>
        <path d={path} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
          <title>{`Latest net worth: ${formatMoney(latest?.netWorth || 0)} in ${formatMonth(latest?.month)}`}</title>
        </path>
        {history.map((point, index) => {
          if (history.length > 18 && index !== 0 && index !== history.length - 1 && index % 4 !== 0) {
            return null;
          }
          const { min, max } = getRange(history);
          const x = history.length === 1 ? width / 2 : (index / (history.length - 1)) * width;
          const y = height - ((Number(point.netWorth) - min) / (max - min)) * height;
          return (
            <circle key={point.month} cx={x} cy={y} r="4" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3">
              <title>{`${formatMonth(point.month)}: ${formatMoney(point.netWorth)}`}</title>
            </circle>
          );
        })}
      </ChartFrame>
      <div className="networth-chart-labels">
        <span>{formatMonth(first?.month)}</span>
        <strong>{latest ? formatMoney(latest.netWorth) : formatMoney(0)}</strong>
        <span>{formatMonth(latest?.month)}</span>
      </div>
    </div>
  );
}

function AllocationBar({ items, total }) {
  const denom = items.reduce((sum, item) => sum + Math.abs(item.value), 0);
  if (!denom) return null;

  return (
    <div className="networth-allocation-bar" aria-label={`Net worth allocation, total ${formatMoney(total)}`}>
      {items.map((item) => (
        <span
          key={item.key}
          style={{
            width: `${Math.max(4, (Math.abs(item.value) / denom) * 100)}%`,
            background: BUCKET_COLORS[item.key]
          }}
          title={`${item.label}: ${formatMoney(item.value)}`}
        />
      ))}
    </div>
  );
}

function AccountContribution({ account, total }) {
  const share = percent(account.contribution, total);
  const isDebt = account.contribution < 0;

  return (
    <li className="networth-account-row">
      <div className="networth-account-main">
        <div className="networth-account-name">{account.name}</div>
        <div className="networth-account-meta">
          <span className={`type-pill type-${account.type}`}>{TYPE_LABELS[account.type] || account.type}</span>
          {account.institution && <span>{account.institution}</span>}
        </div>
      </div>
      <div className="networth-account-side">
        <strong className={isDebt ? 'expense' : 'income'}>{formatMoney(account.contribution)}</strong>
        <div className="networth-account-bar">
          <span
            style={{
              width: `${Math.min(100, Math.max(5, share))}%`,
              background: BUCKET_COLORS[account.category] || 'var(--accent)'
            }}
          />
        </div>
      </div>
    </li>
  );
}
