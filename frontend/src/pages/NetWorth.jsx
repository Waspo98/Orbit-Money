import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import PageHero from '../components/PageHero.jsx';

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

function formatMoney(amount, digits = 0) {
  return Number(amount || 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: digits
  });
}

function formatSignedMoney(amount) {
  const value = Number(amount || 0);
  if (value === 0) return formatMoney(0);
  return `${value > 0 ? '+' : '-'}${formatMoney(Math.abs(value))}`;
}

function formatMonth(key) {
  if (!key) return '';
  const [year, month] = key.split('-').map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString(undefined, {
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

function useNetWorth() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      setData(await api.get('/api/net-worth?months=24'));
    } catch (err) {
      setError(err.message || 'Failed to load net worth');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  return { data, loading, error, reload: load };
}

export default function NetWorth() {
  const { data, loading, error, reload } = useNetWorth();

  const summary = data?.summary || {};
  const history = data?.history || [];
  const breakdown = data?.breakdown || [];
  const latest = history[history.length - 1];
  const first = history[0];
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
  const periodTone = Number(summary.periodChange || 0) >= 0 ? 'income' : 'expense';
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
      label: first ? `Since ${formatMonth(first.month)}` : 'Since Start',
      value: loading ? 'Loading' : formatSignedMoney(summary.periodChange),
      tone: periodTone === 'income' ? 'good' : 'caution'
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

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : !data || summary.accountCount === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">$</div>
          <h2>No net worth yet</h2>
          <p>Add or import accounts to start tracking assets, debts, and progress.</p>
          <Link to="/accounts" className="btn-primary">Manage accounts</Link>
        </div>
      ) : (
        <>
          <div className="networth-grid">
            <section className="dashboard-card networth-trend-card">
              <header className="dashboard-card-header">
                <h3>Net worth over time</h3>
                <span className="dashboard-card-link">{history.length} months</span>
              </header>
              <NetWorthChart history={history} />
            </section>

            <section className="dashboard-card">
              <header className="dashboard-card-header">
                <h3>Allocation</h3>
                <Link to="/accounts" className="dashboard-card-link">Accounts</Link>
              </header>
              <div className="dashboard-card-body">
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
              </div>
            </section>

            <section className="dashboard-card">
              <header className="dashboard-card-header">
                <h3>Signals</h3>
              </header>
              <div className="dashboard-card-body">
                <SignalRow
                  label="Largest asset"
                  value={largestPositive ? largestPositive.name : 'None yet'}
                  detail={largestPositive ? formatMoney(largestPositive.contribution) : formatMoney(0)}
                />
                <SignalRow
                  label="Largest liability"
                  value={largestDebt ? largestDebt.name : 'None yet'}
                  detail={largestDebt ? formatMoney(largestDebt.contribution) : formatMoney(0)}
                  danger={!!largestDebt}
                />
                <SignalRow
                  label="Tracked accounts"
                  value={`${summary.accountCount.toLocaleString()} active`}
                  detail={`${positiveAccounts.length} positive, ${debtAccounts.length} negative`}
                />
              </div>
            </section>

            <section className="dashboard-card networth-account-card">
              <header className="dashboard-card-header">
                <h3>Account contributions</h3>
                <Link to="/accounts" className="dashboard-card-link">Edit values</Link>
              </header>
              <div className="dashboard-card-body">
                <ul className="networth-account-list">
                  {topAccounts.map((account) => (
                    <AccountContribution key={account.id} account={account} total={summary.netWorth} />
                  ))}
                </ul>
              </div>
            </section>
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
      <svg className="networth-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Net worth trend">
        <defs>
          <linearGradient id="networthArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.24" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        <path d={areaPath} fill="url(#networthArea)" />
        <path d={path} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        {history.map((point, index) => {
          if (history.length > 18 && index !== 0 && index !== history.length - 1 && index % 4 !== 0) {
            return null;
          }
          const { min, max } = getRange(history);
          const x = history.length === 1 ? width / 2 : (index / (history.length - 1)) * width;
          const y = height - ((Number(point.netWorth) - min) / (max - min)) * height;
          return <circle key={point.month} cx={x} cy={y} r="4" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" />;
        })}
      </svg>
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

function SignalRow({ label, value, detail, danger = false }) {
  return (
    <div className="networth-signal-row">
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <em className={danger ? 'expense' : ''}>{detail}</em>
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
