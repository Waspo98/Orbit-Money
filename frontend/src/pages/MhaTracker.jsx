import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';

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

export default function MhaTracker() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingAccountId, setSavingAccountId] = useState(null);

  async function load({ silent = false } = {}) {
    if (!silent) setLoading(true);
    setError('');
    try {
      setData(await api.get('/api/mha'));
    } catch (err) {
      setError(err.message || 'Failed to load MHA Tracker');
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

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

  const summary = data?.summary || {};
  const accounts = data?.accounts || [];
  const transactions = data?.transactions || [];

  const enabledAccounts = useMemo(
    () => accounts.filter((account) => account.mha_default_eligible),
    [accounts]
  );

  return (
    <div className="mha-view">
      <div className="view-header">
        <div>
          <h2>MHA Tracker</h2>
          <p className="muted">Ministerial Housing Allowance transaction tracking.</p>
        </div>
        <Link to="/settings" className="btn-secondary settings-action-link">
          Settings
        </Link>
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : (
        <>
          <dl className="stat-grid mha-stat-grid">
            <div>
              <dt>MHA Transaction Total</dt>
              <dd>{formatMoney(summary.transactionTotal)}</dd>
            </div>
            <div>
              <dt>MHA Savings</dt>
              <dd>{formatMoney(summary.savings)}</dd>
            </div>
            <div>
              <dt>Eligible Transactions</dt>
              <dd>{Number(summary.transactionCount || 0).toLocaleString()}</dd>
            </div>
          </dl>

          <section className="dashboard-card mha-account-card">
            <header className="dashboard-card-header">
              <h3>Auto-include accounts</h3>
              <span className="dashboard-card-link">
                {enabledAccounts.length} selected
              </span>
            </header>
            <div className="dashboard-card-body">
              <div className="mha-account-list">
                {accounts.map((account) => (
                  <button
                    key={account.id}
                    type="button"
                    className={`mha-account-row ${account.mha_default_eligible ? 'active' : ''}`}
                    onClick={() => toggleAccount(account)}
                    disabled={savingAccountId === account.id}
                    aria-pressed={!!account.mha_default_eligible}
                  >
                    <span className="mha-account-main">
                      <strong>{account.name}</strong>
                      <span>
                        <span className={`type-pill type-${account.type}`}>
                          {ACCOUNT_TYPE_LABELS[account.type] || account.type}
                        </span>
                        {account.institution && <em>{account.institution}</em>}
                      </span>
                    </span>
                    <span className="mha-account-state">
                      {account.mha_default_eligible ? 'Included' : 'Off'}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </section>

          <section className="dashboard-card mha-transactions-card">
            <header className="dashboard-card-header">
              <h3>MHA-eligible transactions</h3>
              <Link to="/transactions" className="dashboard-card-link">Transactions</Link>
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
        </>
      )}
    </div>
  );
}
