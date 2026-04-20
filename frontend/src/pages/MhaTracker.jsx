import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import PageHero from '../components/PageHero.jsx';

const ACCOUNT_TYPE_LABELS = {
  checking: 'Checking',
  savings: 'Savings',
  credit: 'Credit',
  investment: 'Investment',
  loan: 'Loan',
  mortgage: 'Mortgage',
  cash: 'Cash',
  other: 'Other'
};

function formatMoney(amount, digits = 2) {
  return Number(amount || 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: digits
  });
}

function formatDate(iso) {
  if (!iso) return '';
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  });
}

function currentYear() {
  return new Date().getFullYear();
}

function parseYearParam(value) {
  const year = parseInt(value, 10);
  return Number.isFinite(year) ? year : currentYear();
}

function formatYearLabel(year) {
  return String(year || currentYear());
}

export default function MhaTracker({ onOpenMenu }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedYear = parseYearParam(searchParams.get('year'));
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [savingAccountId, setSavingAccountId] = useState(null);
  const [savingCategoryId, setSavingCategoryId] = useState(null);
  const [accountsExpanded, setAccountsExpanded] = useState(false);
  const [categoriesExpanded, setCategoriesExpanded] = useState(false);

  async function load({ silent = false } = {}) {
    if (!silent && data == null) setLoading(true);
    else setRefreshing(true);
    setError('');
    try {
      setData(await api.get(`/api/mha?year=${encodeURIComponent(selectedYear)}`));
    } catch (err) {
      setError(err.message || 'Failed to load MHA Tracker');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedYear]);

  function goToYear(year) {
    const next = new URLSearchParams(searchParams);
    if (year === currentYear()) next.delete('year');
    else next.set('year', String(year));
    setSearchParams(next);
  }

  async function toggleAccount(account) {
    const next = !account.mha_default_eligible;
    setSavingAccountId(account.id);
    setError('');
    try {
      setData((prev) => ({
        ...prev,
        accounts: prev.accounts.map((item) =>
          item.id === account.id ? { ...item, mha_default_eligible: next ? 1 : 0 } : item
        )
      }));
      await api.put(`/api/mha/accounts/${account.id}/default`, {
        mha_default_eligible: next
      });
      await load({ silent: true });
    } catch (err) {
      setError(err.message || 'Could not update MHA account default');
      await load({ silent: true });
    } finally {
      setSavingAccountId(null);
    }
  }

  async function toggleCategory(category) {
    const next = !category.mha_default_eligible;
    setSavingCategoryId(category.id);
    setError('');
    try {
      setData((prev) => ({
        ...prev,
        categories: prev.categories.map((item) =>
          item.id === category.id ? { ...item, mha_default_eligible: next ? 1 : 0 } : item
        )
      }));
      await api.put(`/api/mha/categories/${category.id}/default`, {
        mha_default_eligible: next
      });
      await load({ silent: true });
    } catch (err) {
      setError(err.message || 'Could not update MHA category default');
      await load({ silent: true });
    } finally {
      setSavingCategoryId(null);
    }
  }

  const summary = data?.summary || {};
  const years = data?.years || [selectedYear];
  const accounts = data?.accounts || [];
  const categories = data?.categories || [];
  const transactions = data?.transactions || [];

  const enabledAccounts = useMemo(
    () => accounts.filter((account) => account.mha_default_eligible),
    [accounts]
  );
  const enabledCategories = useMemo(
    () => categories.filter((category) => category.mha_default_eligible),
    [categories]
  );

  const visibleAccounts = accountsExpanded ? accounts : enabledAccounts;
  const visibleCategories = categoriesExpanded ? categories : enabledCategories;
  const yearOptions = useMemo(() => {
    const set = new Set(years);
    set.add(currentYear());
    set.add(selectedYear);
    return Array.from(set).sort((a, b) => b - a);
  }, [selectedYear, years]);
  const canGoForward = selectedYear < Math.max(currentYear() + 1, ...yearOptions);

  return (
    <div className="mha-view">
      <PageHero
        id="mha-title"
        variant="mha"
        kicker="Ministerial Housing Allowance"
        title="MHA Tracker"
        subtitle={`${formatYearLabel(selectedYear)} ministerial housing allowance tracking`}
        stats={[
          { label: 'MHA Eligible Total', value: formatMoney(summary.transactionTotal), tone: 'good' },
          { label: 'MHA Savings', value: formatMoney(summary.savings), tone: 'warn' }
        ]}
        toolbar={(
          <div className="mha-hero-toolbar">
            <YearNav
              year={selectedYear}
              yearOptions={yearOptions}
              canGoForward={canGoForward}
              onPrev={() => goToYear(selectedYear - 1)}
              onNext={() => goToYear(selectedYear + 1)}
              onJump={goToYear}
            />
          </div>
        )}
        onOpenMenu={onOpenMenu}
        statLabel="MHA summary"
      />

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : (
        <div className={`mha-content ${refreshing ? 'refreshing' : ''}`}>
          <section className="dashboard-card mha-picker-card">
            <header className="dashboard-card-header">
              <h3>Auto-include accounts</h3>
              <button
                type="button"
                className="dashboard-card-link button-link"
                onClick={() => setAccountsExpanded((value) => !value)}
              >
                {accountsExpanded ? 'Show selected' : 'Show all'} ({enabledAccounts.length})
              </button>
            </header>
            <div className="dashboard-card-body">
              {visibleAccounts.length === 0 ? (
                <p className="muted" style={{ margin: 0 }}>
                  No accounts selected.
                </p>
              ) : (
                <div className="mha-picker-list">
                  {visibleAccounts.map((account) => (
                    <button
                      key={account.id}
                      type="button"
                      className={`mha-picker-row ${account.mha_default_eligible ? 'active' : ''}`}
                      onClick={() => toggleAccount(account)}
                      disabled={savingAccountId === account.id}
                      aria-pressed={!!account.mha_default_eligible}
                    >
                      <span className="mha-picker-main">
                        <strong>{account.name}</strong>
                        <span>
                          <span className={`type-pill type-${account.type}`}>
                            {ACCOUNT_TYPE_LABELS[account.type] || account.type}
                          </span>
                          {account.institution && <em>{account.institution}</em>}
                        </span>
                      </span>
                      <span className="mha-picker-state">
                        {account.mha_default_eligible ? 'Included' : 'Off'}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="dashboard-card mha-picker-card">
            <header className="dashboard-card-header">
              <h3>Auto-include categories</h3>
              <button
                type="button"
                className="dashboard-card-link button-link"
                onClick={() => setCategoriesExpanded((value) => !value)}
              >
                {categoriesExpanded ? 'Show selected' : 'Show all'} ({enabledCategories.length})
              </button>
            </header>
            <div className="dashboard-card-body">
              {visibleCategories.length === 0 ? (
                <p className="muted" style={{ margin: 0 }}>
                  No categories selected.
                </p>
              ) : (
                <div className="mha-picker-list">
                  {visibleCategories.map((category) => (
                    <button
                      key={category.id}
                      type="button"
                      className={`mha-picker-row ${category.mha_default_eligible ? 'active' : ''}`}
                      onClick={() => toggleCategory(category)}
                      disabled={savingCategoryId === category.id}
                      aria-pressed={!!category.mha_default_eligible}
                    >
                      <span className="mha-picker-main">
                        <strong>
                          {category.icon && <span style={{ color: category.color }}>{category.icon} </span>}
                          {category.name}
                        </strong>
                        <span>
                          <span className="type-pill">
                            {category.is_income ? 'Income' : category.is_transfer ? 'Transfer' : 'Spending'}
                          </span>
                          {category.is_transfer ? <em>Excluded from budgets</em> : null}
                        </span>
                      </span>
                      <span className="mha-picker-state">
                        {category.mha_default_eligible ? 'Included' : 'Off'}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="dashboard-card mha-transactions-card">
            <header className="dashboard-card-header">
              <h3>MHA-eligible transactions</h3>
              <span className="dashboard-card-link">
                {Number(summary.transactionCount || 0).toLocaleString()} in {selectedYear}
              </span>
            </header>
            <div className="dashboard-card-body">
              {transactions.length === 0 ? (
                <p className="muted" style={{ margin: 0 }}>
                  No MHA-eligible transactions yet.
                </p>
              ) : (
                <ul className="networth-account-list mha-transaction-list">
                  {transactions.map((txn) => (
                    <li key={txn.id} className="networth-account-row mha-transaction-row">
                      <div className="networth-account-main">
                        <div className="networth-account-name">{txn.merchant}</div>
                        <div className="networth-account-meta">
                          <span>{formatDate(txn.date)}</span>
                          <span>{txn.account_name}</span>
                          {txn.category_name && (
                            <span style={{ color: txn.category_color }}>
                              {txn.category_icon} {txn.category_name}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="networth-account-side">
                        <strong>{formatMoney(txn.amount_abs)}</strong>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

function YearNav({ year, yearOptions, canGoForward, onPrev, onNext, onJump }) {
  return (
    <div className="month-nav year-nav">
      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onPrev}
        aria-label="Previous year"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="M14.5 6.5 9 12l5.5 5.5" />
        </svg>
      </button>

      <div className="month-nav-label-wrap">
        <span className="month-nav-label-text">{formatYearLabel(year)}</span>
        <select
          className="month-nav-select"
          value={year}
          onChange={(e) => onJump(Number(e.target.value))}
          aria-label="Jump to year"
        >
          {yearOptions.map((option) => (
            <option key={option} value={option}>
              {formatYearLabel(option)}
            </option>
          ))}
        </select>
      </div>

      <button
        type="button"
        className="btn-icon month-nav-arrow"
        onClick={onNext}
        disabled={!canGoForward}
        aria-label="Next year"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="m9.5 6.5L15 12l-5.5 5.5" />
        </svg>
      </button>
    </div>
  );
}
