import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import PageHero from '../components/PageHero.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { TransactionRow, EditTransactionModal } from './Transactions.jsx';
import { RuleEditor } from './Rules.jsx';

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

function categoryTypeLabel(category) {
  if (category.is_income) return 'Income';
  if (category.is_transfer) return 'Transfer';
  return 'Spending';
}

function normalizeMhaTransaction(txn) {
  return {
    ...txn,
    original_merchant: txn.original_merchant || txn.merchant,
    original_category_id: txn.original_category_id ?? txn.category_id ?? null,
    edited_merchant_source: txn.edited_merchant_source ?? null,
    edited_category_id_source: txn.edited_category_id_source ?? null,
    edited_is_transfer_source: txn.edited_is_transfer_source ?? null,
    edited_is_ignored_source: txn.edited_is_ignored_source ?? null,
    edited_mha_eligible_source: txn.edited_mha_eligible_source ?? null,
    has_edits: Boolean(
      txn.has_edits ||
      txn.edited_merchant_source ||
      txn.edited_category_id_source ||
      txn.edited_is_transfer_source ||
      txn.edited_is_ignored_source ||
      txn.edited_mha_eligible_source
    )
  };
}

export default function MhaTracker() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedYear = parseYearParam(searchParams.get('year'));
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [savingAccountId, setSavingAccountId] = useState(null);
  const [savingCategoryId, setSavingCategoryId] = useState(null);
  const [savingIgnoredCategoryId, setSavingIgnoredCategoryId] = useState(null);
  const [accountsExpanded, setAccountsExpanded] = useState(false);
  const [categoriesExpanded, setCategoriesExpanded] = useState(false);
  const [ignoredCategoriesExpanded, setIgnoredCategoriesExpanded] = useState(false);
  const [expandedTxnId, setExpandedTxnId] = useState(null);
  const [editingTxn, setEditingTxn] = useState(null);
  const [newRuleFromTxn, setNewRuleFromTxn] = useState(null);

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
          item.id === category.id
            ? {
                ...item,
                mha_default_eligible: next ? 1 : 0,
                mha_default_ignored: next ? 0 : item.mha_default_ignored
              }
            : item
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

  async function toggleIgnoredCategory(category) {
    const next = !category.mha_default_ignored;
    setSavingIgnoredCategoryId(category.id);
    setError('');
    try {
      setData((prev) => ({
        ...prev,
        categories: prev.categories.map((item) =>
          item.id === category.id
            ? {
                ...item,
                mha_default_ignored: next ? 1 : 0,
                mha_default_eligible: next ? 0 : item.mha_default_eligible
              }
            : item
        )
      }));
      await api.put(`/api/mha/categories/${category.id}/ignore-default`, {
        mha_default_ignored: next
      });
      await load({ silent: true });
    } catch (err) {
      setError(err.message || 'Could not update MHA category ignore default');
      await load({ silent: true });
    } finally {
      setSavingIgnoredCategoryId(null);
    }
  }

  function patchLocalTransaction(id, patch) {
    setData((prev) => prev ? {
      ...prev,
      transactions: prev.transactions.map((txn) =>
        txn.id === id ? normalizeMhaTransaction({ ...txn, ...patch }) : txn
      )
    } : prev);
  }

  function replaceLocalTransaction(id, nextTxn) {
    setData((prev) => prev ? {
      ...prev,
      transactions: prev.transactions.map((txn) =>
        txn.id === id ? normalizeMhaTransaction(nextTxn) : txn
      )
    } : prev);
  }

  function removeLocalTransaction(id) {
    setExpandedTxnId(null);
    setData((prev) => prev ? {
      ...prev,
      transactions: prev.transactions.filter((txn) => txn.id !== id)
    } : prev);
  }

  async function handleTransactionToggle(txn, field) {
    const nextValue = !txn[field];
    patchLocalTransaction(txn.id, { [field]: nextValue ? 1 : 0 });
    try {
      await api.patch(`/api/transactions/${txn.id}`, {
        [field]: nextValue
      });
      await load({ silent: true });
    } catch (err) {
      patchLocalTransaction(txn.id, { [field]: txn[field] });
      alert(err.message || 'Toggle failed', { title: 'Could not update transaction' });
    }
  }

  async function handleDeleteTransaction(txn) {
    const ok = await confirm(
      `Delete this transaction? "${txn.merchant}" for ${formatMoney(txn.amount)}`,
      {
        title: 'Delete transaction',
        confirmLabel: 'Delete',
        destructive: true
      }
    );
    if (!ok) return;
    try {
      await api.del(`/api/transactions/${txn.id}`);
      removeLocalTransaction(txn.id);
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  async function handleResetTransactionField(txn, field) {
    try {
      await api.post(`/api/transactions/${txn.id}/reset`, {
        fields: [field]
      });
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Reset failed', { title: 'Reset failed' });
    }
  }

  const summary = data?.summary || {};
  const years = data?.years || [selectedYear];
  const accounts = data?.accounts || [];
  const categories = data?.categories || [];
  const transactions = useMemo(
    () => (data?.transactions || []).map(normalizeMhaTransaction),
    [data?.transactions]
  );

  const enabledAccounts = useMemo(
    () => accounts.filter((account) => account.mha_default_eligible),
    [accounts]
  );
  const enabledCategories = useMemo(
    () => categories.filter((category) => category.mha_default_eligible),
    [categories]
  );
  const ignoredCategories = useMemo(
    () => categories.filter((category) => category.mha_default_ignored),
    [categories]
  );

  const visibleAccounts = accountsExpanded ? accounts : enabledAccounts;
  const visibleCategories = categoriesExpanded ? categories : enabledCategories;
  const visibleIgnoredCategories = ignoredCategoriesExpanded ? categories : ignoredCategories;
  const accountById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);
  const categoryById = useMemo(() => new Map(categories.map((category) => [category.id, category])), [categories]);
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
        subtitle="Calculate Projected MHA Tax Savings"
        stats={[
          { label: 'MHA Eligible Total', value: formatMoney(summary.transactionTotal), tone: 'good' },
          { label: 'MHA Savings', value: formatMoney(summary.savings), tone: 'good' }
        ]}
        statsExtra={(
          <div className="page-hero-stat mha-year-stat">
            <span>Year</span>
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
                    <SelectableListItem
                      key={account.id}
                      active={!!account.mha_default_eligible}
                      disabled={savingAccountId === account.id}
                      className="mha-selectable-row"
                      leading={(
                        <span className={`type-pill type-${account.type}`}>
                          {ACCOUNT_TYPE_LABELS[account.type] || account.type}
                        </span>
                      )}
                      title={account.name}
                      subtitle={account.institution || 'No institution'}
                      sidePrimary={account.mha_default_eligible ? 'Included' : 'Off'}
                      onClick={() => toggleAccount(account)}
                      ariaLabel={`${account.mha_default_eligible ? 'Exclude' : 'Include'} ${account.name} by default`}
                    />
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
                    <SelectableListItem
                      key={category.id}
                      active={!!category.mha_default_eligible}
                      disabled={savingCategoryId === category.id}
                      className="mha-selectable-row"
                      leading={category.icon ? (
                        <span style={{ color: category.color }}>{category.icon}</span>
                      ) : null}
                      title={category.name}
                      subtitle={`${categoryTypeLabel(category)}${category.is_transfer ? ' - Excluded from budgets' : ''}`}
                      sidePrimary={category.mha_default_eligible ? 'Included' : 'Off'}
                      onClick={() => toggleCategory(category)}
                      ariaLabel={`${category.mha_default_eligible ? 'Disable' : 'Enable'} MHA auto-include for ${category.name}`}
                    />
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="dashboard-card mha-picker-card">
            <header className="dashboard-card-header">
              <h3>Auto-ignore categories</h3>
              <button
                type="button"
                className="dashboard-card-link button-link"
                onClick={() => setIgnoredCategoriesExpanded((value) => !value)}
              >
                {ignoredCategoriesExpanded ? 'Show ignored' : 'Show all'} ({ignoredCategories.length})
              </button>
            </header>
            <div className="dashboard-card-body">
              {visibleIgnoredCategories.length === 0 ? (
                <p className="muted" style={{ margin: 0 }}>
                  No categories ignored.
                </p>
              ) : (
                <div className="mha-picker-list">
                  {visibleIgnoredCategories.map((category) => (
                    <SelectableListItem
                      key={category.id}
                      active={!!category.mha_default_ignored}
                      disabled={savingIgnoredCategoryId === category.id}
                      className="mha-selectable-row mha-ignore-row"
                      leading={category.icon ? (
                        <span style={{ color: category.color }}>{category.icon}</span>
                      ) : null}
                      title={category.name}
                      subtitle={`${categoryTypeLabel(category)}${category.is_transfer ? ' - Excluded from budgets' : ''}`}
                      sidePrimary={category.mha_default_ignored ? 'Ignored' : 'Allowed'}
                      onClick={() => toggleIgnoredCategory(category)}
                      ariaLabel={`${category.mha_default_ignored ? 'Allow' : 'Ignore'} ${category.name} for MHA defaults`}
                    />
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
                <ul className="txn-list dash-recent-txn-list mha-transaction-list">
                  {transactions.map((txn) => (
                    <TransactionRow
                      key={txn.id}
                      txn={txn}
                      expanded={expandedTxnId === txn.id}
                      onExpand={() => setExpandedTxnId(expandedTxnId === txn.id ? null : txn.id)}
                      account={accountById.get(txn.account_id)}
                      category={categoryById.get(txn.category_id)}
                      originalCategory={categoryById.get(txn.original_category_id)}
                      hideAccountInMeta={false}
                      onEdit={() => setEditingTxn(txn)}
                      onCreateRule={() => setNewRuleFromTxn(txn)}
                      onToggleTransfer={() => handleTransactionToggle(txn, 'is_transfer')}
                      onToggleIgnored={() => handleTransactionToggle(txn, 'is_ignored')}
                      onToggleMhaEligible={() => handleTransactionToggle(txn, 'mha_eligible')}
                      onDelete={() => handleDeleteTransaction(txn)}
                      onResetField={(field) => handleResetTransactionField(txn, field)}
                      onLogoChanged={(updated) => replaceLocalTransaction(txn.id, updated)}
                      hideMerchantLogo
                      mhaTrackerEnabled
                    />
                  ))}
                </ul>
              )}
            </div>
          </section>

          {editingTxn && (
            <EditTransactionModal
              txn={editingTxn}
              categories={categories}
              onClose={() => setEditingTxn(null)}
              onSaved={(updated) => {
                if (updated) replaceLocalTransaction(editingTxn.id, updated);
                setEditingTxn(null);
                load({ silent: true });
              }}
              onReset={(field) => handleResetTransactionField(editingTxn, field)}
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
                load({ silent: true });
              }}
            />
          )}

          <Dialog />
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
