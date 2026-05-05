import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppSelect from '../components/AppSelect.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import PageHero from '../components/PageHero.jsx';
import PeriodNav from '../components/PeriodNav.jsx';
import PercentInput from '../components/PercentInput.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { TransactionRow, EditTransactionModal } from '../components/transactions/TransactionRow.jsx';
import { RuleEditor } from '../components/rules/RuleEditor.jsx';
import {
  formatCurrency,
  formatPercent,
  formatPercentInput,
  parsePercentInput
} from '../lib/formatters.js';

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

const DEDUCTION_OPTIONS = [
  { value: 'standard', label: 'Standard Deduction' },
  { value: 'itemized', label: 'Itemized Deduction' }
];

function formatMoney(amount, digits = 2) {
  return formatCurrency(amount, { maximumFractionDigits: digits });
}

function formatRate(rate) {
  return formatPercent(Number(rate || 0) * 100, { maximumFractionDigits: 2 });
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

function daysInYear(year) {
  return new Date(year, 1, 29).getMonth() === 1 ? 366 : 365;
}

function dayOfYear(date) {
  const start = new Date(date.getFullYear(), 0, 1);
  return Math.floor((date - start) / 86400000) + 1;
}

function annualizeSavings(savings, year) {
  const selectedYear = Number(year);
  const today = new Date();
  const value = Number(savings || 0);
  if (!Number.isFinite(selectedYear) || selectedYear < today.getFullYear()) return value;
  if (selectedYear > today.getFullYear()) return value;
  return value * (daysInYear(selectedYear) / Math.max(1, dayOfYear(today)));
}

function appSelectOptions(options) {
  return (options || []).map((option) => ({
    value: option.value ?? option.code,
    label: option.label ?? option.name
  }));
}

function toTaxDraft(profile) {
  return {
    mode: profile?.mode || 'simple',
    simpleFederalRate: formatPercentInput(profile?.simpleFederalRate ?? 22),
    simpleStateRate: formatPercentInput(profile?.simpleStateRate ?? 4.95),
    filingStatus: profile?.filingStatus || 'married_joint',
    state: profile?.state || 'IL',
    deductionMode: profile?.deductionMode || 'standard',
    itemizedDeduction: profile?.itemizedDeduction ? formatCurrencyInput(profile.itemizedDeduction) : ''
  };
}

function draftToPayload(draft) {
  return {
    mode: draft.mode,
    simpleFederalRate: parsePercentInput(draft.simpleFederalRate),
    simpleStateRate: parsePercentInput(draft.simpleStateRate),
    filingStatus: draft.filingStatus,
    state: draft.state,
    deductionMode: draft.deductionMode,
    itemizedDeduction: parseCurrencyInput(draft.itemizedDeduction),
    taxableIncomeOverride: null,
    stateRateOverride: null
  };
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
  const [editingRule, setEditingRule] = useState(null);
  const [editingTaxProfile, setEditingTaxProfile] = useState(false);

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

  async function handleViewRule(ruleId) {
    try {
      const data = await api.get(`/api/rules/${ruleId}`);
      if (!data?.rule) throw new Error('Rule not found.');
      setEditingRule(data.rule);
    } catch (err) {
      alert(err.message || 'Could not open rule', { title: 'Could Not Open Rule' });
    }
  }

  const summary = data?.summary || {};
  const taxProfile = data?.taxProfile || {};
  const taxEstimate = data?.taxEstimate || {};
  const taxReference = data?.taxReference || {};
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
        stats={[]}
        statsExtra={(
          <div className="page-hero-stat mha-year-stat">
            <PeriodNav
              className="year-nav"
              value={selectedYear}
              options={yearOptions.map((option) => ({
                value: option,
                label: formatYearLabel(option)
              }))}
              canGoForward={canGoForward}
              onPrev={() => goToYear(selectedYear - 1)}
              onNext={() => goToYear(selectedYear + 1)}
              onJump={(value) => goToYear(Number(value))}
              previousLabel="Previous year"
              nextLabel="Next year"
              jumpLabel="Jump to year"
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
          <MhaDashboard
            summary={summary}
            taxProfile={taxProfile}
            taxEstimate={taxEstimate}
            year={selectedYear}
            onEdit={() => setEditingTaxProfile(true)}
          />

          <MhaCard
            title="Auto-Include Accounts"
            className="mha-picker-card mha-accounts-card"
            action={
              <button
                type="button"
                className="dashboard-card-link dashboard-card-action-button"
                onClick={() => setAccountsExpanded((value) => !value)}
              >
                {accountsExpanded ? 'Show Selected' : 'Show All'} ({enabledAccounts.length})
              </button>
            }
          >
              {visibleAccounts.length === 0 ? (
                <p className="subtle dash-card-note">
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
          </MhaCard>

          <MhaCard
            title="Auto-Include Categories"
            className="mha-picker-card"
            action={
              <button
                type="button"
                className="dashboard-card-link dashboard-card-action-button"
                onClick={() => setCategoriesExpanded((value) => !value)}
              >
                {categoriesExpanded ? 'Show Selected' : 'Show All'} ({enabledCategories.length})
              </button>
            }
          >
              {visibleCategories.length === 0 ? (
                <p className="subtle dash-card-note">
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
          </MhaCard>

          <MhaCard
            title="Auto-Ignore Categories"
            className="mha-picker-card"
            action={
              <button
                type="button"
                className="dashboard-card-link dashboard-card-action-button"
                onClick={() => setIgnoredCategoriesExpanded((value) => !value)}
              >
                {ignoredCategoriesExpanded ? 'Show Ignored' : 'Show All'} ({ignoredCategories.length})
              </button>
            }
          >
              {visibleIgnoredCategories.length === 0 ? (
                <p className="subtle dash-card-note">
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
          </MhaCard>

          <MhaCard
            title="MHA-Eligible Transactions"
            className="mha-transactions-card"
            action={
              <span className="dashboard-card-link">
                {Number(summary.transactionCount || 0).toLocaleString()} in {selectedYear}
              </span>
            }
          >
              {transactions.length === 0 ? (
                <p className="subtle dash-card-note">
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
                      onViewRule={handleViewRule}
                      onLogoChanged={(updated) => replaceLocalTransaction(txn.id, updated)}
                      hideMerchantLogo
                      mhaTrackerEnabled
                    />
                  ))}
                </ul>
              )}
          </MhaCard>

          {editingTaxProfile && (
            <TaxProfileModal
              profile={taxProfile}
              reference={taxReference}
              onClose={() => setEditingTaxProfile(false)}
              onSaved={() => {
                setEditingTaxProfile(false);
                load({ silent: true });
              }}
            />
          )}

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
              sourceTransaction={newRuleFromTxn}
              onClose={() => setNewRuleFromTxn(null)}
              onSaved={() => {
                setNewRuleFromTxn(null);
                load({ silent: true });
              }}
            />
          )}

          {editingRule && (
            <RuleEditor
              rule={editingRule}
              accounts={accounts}
              categories={categories}
              onClose={() => setEditingRule(null)}
              onSaved={() => {
                setEditingRule(null);
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

function MhaCard({ title, action, className = '', children }) {
  const cardClassName = ['dashboard-card', className].filter(Boolean).join(' ');

  return (
    <section className={cardClassName}>
      <header className="dashboard-card-header">
        <h3>{title}</h3>
        {action}
      </header>
      <div className="dashboard-card-body">{children}</div>
    </section>
  );
}

function MhaDashboard({ summary, taxProfile, taxEstimate, year, onEdit }) {
  const household = taxEstimate?.household || null;
  const taxableIncome = household ? formatMoney(household.taxableIncome, 0) : 'Not Estimated';
  const taxableIncomeReduction = household
    ? Number(household.pretaxContributions || 0) + Number(household.deduction || 0)
    : 0;
  const taxableIncomeCalc = household
    ? `${formatMoney(household.grossIncome, 0)} - ${formatMoney(taxableIncomeReduction, 0)}`
    : null;
  const editLabel = taxProfile?.mode === 'household' ? 'Edit Mode (Advanced)' : 'Edit Mode (Simple)';
  const stateNeedsReview = taxEstimate?.stateRateConfidence === 'needs_review';
  const projectedAnnualSavings = annualizeSavings(summary.savings, year);

  return (
    <MhaCard
      title="MHA Dashboard"
      className="mha-dashboard-card"
      action={
        <button
          type="button"
          className="dashboard-card-link dashboard-card-action-button"
          onClick={onEdit}
        >
          {editLabel}
        </button>
      }
    >
      <div className="metric-grid mha-dashboard-grid">
        <div className="metric-card mha-dashboard-metric mha-dashboard-savings mha-dashboard-current-savings">
          <span>Current Savings</span>
          <strong>{formatMoney(summary.savings)}</strong>
        </div>
        <div className="metric-card mha-dashboard-metric">
          <span>Eligible Spending</span>
          <strong>{formatMoney(summary.transactionTotal)}</strong>
          <em>{Number(summary.transactionCount || 0).toLocaleString()} Transactions</em>
        </div>
        <div className="metric-card mha-dashboard-metric">
          <span>Taxable Income</span>
          <strong>{taxableIncome}</strong>
          {taxableIncomeCalc && <em>{taxableIncomeCalc}</em>}
          {!household && <em>Simple Mode</em>}
        </div>
        <div className="metric-card mha-dashboard-metric mha-dashboard-savings">
          <span>Projected Annual Savings</span>
          <strong>{formatMoney(projectedAnnualSavings)}</strong>
          <em>Based on Current Rate</em>
        </div>
        <div className="metric-card mha-dashboard-metric">
          <span>Federal Tax Savings</span>
          <strong>{formatMoney(taxEstimate?.federalSavings)}</strong>
          <em>Federal - {formatRate(taxEstimate?.federalRate)}</em>
        </div>
        <div className="metric-card mha-dashboard-metric">
          <span>State Tax Savings</span>
          <strong>{formatMoney(taxEstimate?.stateSavings)}</strong>
          <em>State - {formatRate(taxEstimate?.stateRate)}</em>
          {stateNeedsReview && (
            <small>State savings are not auto-estimated for graduated-rate states yet.</small>
          )}
        </div>
      </div>
    </MhaCard>
  );
}

function TaxProfileModal({ profile, reference, onClose, onSaved }) {
  const [draft, setDraft] = useState(() => toTaxDraft(profile));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const filingStatuses = reference?.filingStatuses || [];
  const selectedFiling = filingStatuses.find((option) => option.value === draft.filingStatus);
  const standardDeduction = selectedFiling?.standardDeduction || 0;

  function update(key, value) {
    setDraft((prev) => ({ ...prev, [key]: value }));
  }

  async function save(close) {
    setSaving(true);
    setError('');
    try {
      await api.put('/api/mha/tax-profile', draftToPayload(draft));
      close({ animation: 'zoom' });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Could not save tax assumptions');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg" animation="zoom">
      {({ close }) => (
        <div className="mha-tax-modal">
          <h3>MHA Tax Assumptions</h3>

          <div className="segmented-control mha-tax-mode-tabs" role="tablist" aria-label="MHA tax mode">
            <button
              type="button"
              role="tab"
              className={draft.mode === 'simple' ? 'active' : ''}
              aria-selected={draft.mode === 'simple'}
              onClick={() => update('mode', 'simple')}
            >
              Simple
            </button>
            <button
              type="button"
              role="tab"
              className={draft.mode === 'household' ? 'active' : ''}
              aria-selected={draft.mode === 'household'}
              onClick={() => update('mode', 'household')}
            >
              Estimate From Household
            </button>
          </div>

          {draft.mode === 'simple' ? (
            <div className="household-form-section">
              <h4>Manual Rates</h4>
              <div className="goal-form-grid">
                <label className="field">
                  <span>Federal Rate</span>
                  <PercentInput
                    value={draft.simpleFederalRate}
                    onChange={(value) => update('simpleFederalRate', value)}
                    placeholder="22%"
                  />
                </label>
                <label className="field">
                  <span>State Income Rate</span>
                  <PercentInput
                    value={draft.simpleStateRate}
                    onChange={(value) => update('simpleStateRate', value)}
                    placeholder="4.95%"
                  />
                </label>
              </div>
            </div>
          ) : (
            <>
              <div className="household-form-section">
                <h4>Household</h4>
                <div className="goal-form-grid">
                  <label className="field">
                    <span>Filing Status</span>
                    <AppSelect
                      value={draft.filingStatus}
                      options={appSelectOptions(filingStatuses)}
                      onChange={(value) => update('filingStatus', value)}
                      ariaLabel="Filing status"
                    />
                  </label>
                  <label className="field">
                    <span>State</span>
                    <AppSelect
                      value={draft.state}
                      options={appSelectOptions(reference?.states || [])}
                      onChange={(value) => update('state', value)}
                      ariaLabel="State"
                    />
                  </label>
                </div>
              </div>

              <div className="household-form-section">
                <h4>Deductions</h4>
                <div className="goal-form-grid">
                  <label className="field">
                    <span>Deduction Method</span>
                    <AppSelect
                      value={draft.deductionMode}
                      options={DEDUCTION_OPTIONS}
                      onChange={(value) => update('deductionMode', value)}
                      ariaLabel="Deduction method"
                    />
                  </label>
                  <div className="mha-tax-standard-note">
                    <span>Standard Deduction</span>
                    <strong>{formatMoney(standardDeduction, 0)}</strong>
                    <em>{selectedFiling?.label || 'Selected Filing Status'}</em>
                  </div>
                  {draft.deductionMode === 'itemized' && (
                    <label className="field">
                      <span>Itemized Deduction Amount</span>
                      <CurrencyInput
                        value={draft.itemizedDeduction}
                        onChange={(value) => update('itemizedDeduction', value)}
                        placeholder="$32,200"
                      />
                    </label>
                  )}
                </div>
              </div>

              <p className="subtle dash-card-note">
                Household mode assumes listed paycheck contributions are pre-tax.
              </p>
            </>
          )}

          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>Cancel</button>
            <button type="button" className="btn-primary" onClick={() => save(close)} disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </div>
        </div>
      )}
    </AnimatedModal>
  );
}
