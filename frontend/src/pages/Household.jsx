import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppSelect from '../components/AppSelect.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import DateInput from '../components/DateInput.jsx';
import DashboardCard from '../components/dashboard/DashboardCard.jsx';
import EmptyState from '../components/EmptyState.jsx';
import {
  FinancialField,
  FinancialFormGrid
} from '../components/FinancialForm.jsx';
import PageActionRow from '../components/PageActionRow.jsx';
import PageHero from '../components/PageHero.jsx';
import PercentInput from '../components/PercentInput.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import AppIcon from '../components/AppIcon.jsx';
import SignalRow from '../components/SignalRow.jsx';
import {
  formatCurrency,
  formatPercent,
  formatPercentInput,
  parsePercentInput
} from '../lib/formatters.js';
import {
  ageFromDate,
  formatLocalDate,
  formatMediumDate
} from '../lib/localDate.js';

const ROLE_OPTIONS = [
  ['adult', 'Adult'],
  ['child', 'Child']
];

const EMPLOYMENT_OPTIONS = [
  ['employed', 'Employed'],
  ['self_employed', 'Self-Employed'],
  ['stay_at_home', 'Stay At Home'],
  ['student', 'Student'],
  ['retired', 'Retired'],
  ['unemployed', 'Unemployed'],
  ['other', 'Other']
];

const PAY_FREQUENCY_OPTIONS = [
  ['weekly', 'Weekly'],
  ['biweekly', 'Biweekly'],
  ['semimonthly', 'Semimonthly'],
  ['monthly', 'Monthly'],
  ['annual', 'Annual'],
  ['none', 'None']
];

const RETIREMENT_OPTIONS = [
  ['none', 'None'],
  ['401k', '401(k)'],
  ['403b', '403(b)'],
  ['457b', '457(b)'],
  ['ira', 'IRA'],
  ['roth_ira', 'Roth IRA'],
  ['hsa', 'HSA'],
  ['sep_ira', 'SEP IRA'],
  ['simple_ira', 'SIMPLE IRA'],
  ['pension', 'Pension'],
  ['other', 'Other']
];

const RETIREMENT_ACCOUNT_KIND_OPTIONS = RETIREMENT_OPTIONS.filter(([value]) => value !== 'none');
const HSA_RETIREMENT_RATE_STORAGE_KEY = 'orbit-money-hsa-retirement-rate-members-v1';

const PAY_PERIODS = {
  weekly: 52,
  biweekly: 26,
  semimonthly: 24,
  monthly: 12,
  annual: 1,
  none: 0
};

function safePayPeriods(payFrequency) {
  return Math.max(1, PAY_PERIODS[payFrequency] || 0);
}

function annualToPerPaycheck(value, payFrequency) {
  return formatCurrencyInput((Number(value) || 0) / safePayPeriods(payFrequency));
}

function perPaycheckToAnnual(value, payFrequency) {
  return parseCurrencyInput(value) * safePayPeriods(payFrequency);
}

function monthlyToPerPaycheck(value, payFrequency) {
  return formatCurrencyInput((Number(value) || 0) * 12 / safePayPeriods(payFrequency));
}

function perPaycheckToMonthly(value, payFrequency) {
  return (parseCurrencyInput(value) * safePayPeriods(payFrequency)) / 12;
}

function labelFor(options, value) {
  return options.find(([key]) => key === value)?.[1] || value || 'Not set';
}

function readHsaRetirementRateMemberIds() {
  try {
    const saved = JSON.parse(localStorage.getItem(HSA_RETIREMENT_RATE_STORAGE_KEY));
    return Array.isArray(saved) ? saved.map(Number).filter(Number.isFinite) : [];
  } catch {
    return [];
  }
}

function shouldIncludeHsaInRetirementRate(memberId) {
  return readHsaRetirementRateMemberIds().includes(Number(memberId));
}

function setIncludeHsaInRetirementRate(memberId, include) {
  try {
    const ids = new Set(readHsaRetirementRateMemberIds());
    const id = Number(memberId);
    if (!Number.isFinite(id)) return;
    if (include) ids.add(id);
    else ids.delete(id);
    localStorage.setItem(HSA_RETIREMENT_RATE_STORAGE_KEY, JSON.stringify(Array.from(ids)));
  } catch {
    /* Local preference only; ignore storage failures. */
  }
}

function emptyDraft() {
  return {
    name: '',
    role: 'adult',
    birth_date: '',
    employment_status: 'employed',
    employer: '',
    job_title: '',
    gross_income_annual: '',
    net_pay_per_period: '',
    pay_frequency: 'biweekly',
    retirement_account_type: '401k',
    employee_contribution_percent: '',
    employer_match_percent: '',
    employer_match_limit_percent: '',
    health_premium_per_paycheck: '',
    hsa_contribution_annual: '',
    dependent_care_fsa_annual: '',
    other_benefits_annual: '',
    effective_date: formatLocalDate(),
    retirement_accounts: [],
    notes: ''
  };
}

function toDraft(member) {
  if (!member) return emptyDraft();
  return {
    ...emptyDraft(),
    ...member,
    gross_income_annual: formatCurrencyInput(member.gross_income_annual),
    net_pay_per_period: formatCurrencyInput(member.net_pay_per_period),
    health_premium_per_paycheck: monthlyToPerPaycheck(member.health_premium_per_month, member.pay_frequency),
    hsa_contribution_annual: annualToPerPaycheck(member.hsa_contribution_annual, member.pay_frequency),
    dependent_care_fsa_annual: annualToPerPaycheck(member.dependent_care_fsa_annual, member.pay_frequency),
    other_benefits_annual: annualToPerPaycheck(member.other_benefits_annual, member.pay_frequency),
    employee_contribution_percent: formatPercentInput(member.employee_contribution_percent),
    employer_match_percent: formatPercentInput(member.employer_match_percent),
    employer_match_limit_percent: formatPercentInput(member.employer_match_limit_percent),
    effective_date: member.effective_date || formatLocalDate(),
    retirement_accounts: (member.retirement_accounts || []).map((account) => ({
      account_id: Number(account.account_id),
      account_kind: account.account_kind || 'other'
    })),
    notes: member.notes || ''
  };
}

function toPayload(draft) {
  return {
    name: draft.name.trim(),
    role: draft.role,
    birth_date: draft.birth_date || null,
    employment_status: draft.employment_status,
    employer: draft.employer,
    job_title: draft.job_title,
    gross_income_annual: parseCurrencyInput(draft.gross_income_annual),
    net_pay_per_period: parseCurrencyInput(draft.net_pay_per_period),
    pay_frequency: draft.pay_frequency,
    pay_periods_per_year: PAY_PERIODS[draft.pay_frequency] || 0,
    retirement_account_type: draft.retirement_account_type,
    employee_contribution_percent: parsePercentInput(draft.employee_contribution_percent),
    employee_contribution_annual: 0,
    employer_match_percent: parsePercentInput(draft.employer_match_percent),
    employer_match_limit_percent: parsePercentInput(draft.employer_match_limit_percent),
    employer_match_annual_cap: 0,
    health_premium_per_month: perPaycheckToMonthly(draft.health_premium_per_paycheck, draft.pay_frequency),
    hsa_contribution_annual: perPaycheckToAnnual(draft.hsa_contribution_annual, draft.pay_frequency),
    dependent_care_fsa_annual: perPaycheckToAnnual(draft.dependent_care_fsa_annual, draft.pay_frequency),
    other_benefits_annual: perPaycheckToAnnual(draft.other_benefits_annual, draft.pay_frequency),
    retirement_accounts: (draft.retirement_accounts || []).map((account) => ({
      account_id: Number(account.account_id),
      account_kind: account.account_kind || 'other'
    })),
    notes: draft.notes
  };
}

export default function Household() {
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingMember, setEditingMember] = useState(null);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [householdData, accountData] = await Promise.all([
        api.get('/api/household'),
        api.get('/api/accounts')
      ]);
      setData(householdData);
      setAccounts(accountData.items || []);
    } catch (err) {
      setError(err.message || 'Failed to load household');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const summary = data?.summary || {};
  const members = data?.members || [];
  const records = data?.records || [];
  const topMember = useMemo(
    () => [...members].sort((a, b) => Number(b.household_value_annual || 0) - Number(a.household_value_annual || 0))[0],
    [members]
  );
  async function handleDelete(member) {
    const ok = await confirm(`Delete ${member.name} and their income history?`, {
      title: 'Delete household member',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      setData(await api.del(`/api/household/members/${member.id}`));
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  return (
    <div className="household-view">
      <PageHero
        id="household-title"
        variant="household"
        kicker="Income And Benefits"
        title="Household"
        subtitle="Track net pay, employer benefits, retirement inputs, and compensation history."
      />

      <PageActionRow className="household-page-actions" label="Household actions">
        <button type="button" className="btn-primary" onClick={() => setEditingMember({ mode: 'new' })}>
          + New Member
        </button>
      </PageActionRow>

      {error && <div className="error app-page-width">{error}</div>}

      {loading ? (
        <div className="center-loading app-page-width">
          <div className="spinner" />
        </div>
      ) : members.length === 0 ? (
        <EmptyState
          className="app-page-width"
          icon={<AppIcon name="household" />}
          title="No household details yet"
          description="Add each adult or child whose income, benefits, or retirement inputs should influence future planning."
          action={(
            <button type="button" className="btn-primary" onClick={() => setEditingMember({ mode: 'new' })}>
              + New Member
            </button>
          )}
        />
      ) : (
        <div className="household-grid app-page-width">
          <DashboardCard
            title="Members"
            className="household-members-card"
            action={(
              <button type="button" className="dashboard-card-link button-link" onClick={() => setEditingMember({ mode: 'new' })}>
                Add
              </button>
            )}
          >
            <div className="household-member-list">
              {members.map((member) => (
                <MemberCard
                  key={member.id}
                  member={member}
                  onEdit={() => setEditingMember(member)}
                  onDelete={() => handleDelete(member)}
                />
              ))}
            </div>
          </DashboardCard>

          <DashboardCard title="Planning Signals" className="household-summary-card">
            <SignalRow label="Take-home pay" value={formatCurrency(summary.net_pay_annual)} detail="Annualized from pay cadence" />
            <SignalRow label="Retirement savings" value={formatCurrency((summary.employee_retirement_annual || 0) + (summary.employer_retirement_annual || 0))} detail={`${formatCurrency(summary.employer_retirement_annual)} employer`} />
            <SignalRow label="Benefits value" value={formatCurrency(summary.total_benefits_annual)} detail="Match, HSA, FSA, other" />
            <SignalRow label="Largest contributor" value={topMember?.name || 'None'} detail={topMember ? formatCurrency(topMember.household_value_annual) : formatCurrency(0)} />
          </DashboardCard>

          <DashboardCard title="Income History" className="household-history-card">
            {records.length === 0 ? (
              <p className="subtle" style={{ margin: 0 }}>Save a member profile to record the first dated income snapshot.</p>
            ) : (
              <div className="household-history-list">
                {records.slice(0, 10).map((record) => (
                  <div key={record.id} className="household-history-row">
                    <div>
                      <strong>{record.member_name}</strong>
                      <span>{formatMediumDate(record.effective_date)}</span>
                    </div>
                    <div>
                      <strong>{formatCurrency(record.gross_income_annual)}</strong>
                      <span>{formatCurrency(record.net_pay_annual)} net</span>
                    </div>
                    <div>
                      <strong>{formatCurrency(record.employer_retirement_annual)}</strong>
                      <span>Employer match</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </DashboardCard>
        </div>
      )}

      {editingMember && (
        <MemberModal
          member={editingMember.mode === 'new' ? null : editingMember}
          accounts={accounts}
          onClose={() => setEditingMember(null)}
          onSaved={(nextData) => {
            setData(nextData);
            setEditingMember(null);
          }}
        />
      )}

      <Dialog />
    </div>
  );
}

function MemberCard({ member, onEdit, onDelete }) {
  const age = ageFromDate(member.birth_date);
  const includeHsa = shouldIncludeHsaInRetirementRate(member.id);
  const retirementContributions =
    Number(member.employee_retirement_annual || 0) +
    Number(member.employer_retirement_annual || 0) +
    (includeHsa ? Number(member.hsa_contribution_annual || 0) : 0);
  const retirementRate =
    Number(member.gross_income_annual || 0) > 0
      ? (retirementContributions / Number(member.gross_income_annual)) * 100
      : 0;

  return (
    <article className="household-member-card">
      <div className="household-member-top">
        <div>
          <h4>{member.name}</h4>
          <p>
            {labelFor(EMPLOYMENT_OPTIONS, member.employment_status)}
            {member.employer ? ` at ${member.employer}` : ''}
            {age !== null ? ` | Age ${age}` : ''}
          </p>
        </div>
        <div className="household-member-actions">
          <button type="button" className="dashboard-card-link button-link" onClick={onEdit}>Edit</button>
          <button type="button" className="dashboard-card-link button-link expense" onClick={onDelete}>Delete</button>
        </div>
      </div>

      <div className="household-member-metrics">
        <Metric label="Gross" value={formatCurrency(member.gross_income_annual)} />
        <Metric label="Net Pay" value={formatCurrency(member.net_pay_annual)} />
        <Metric label="Employer Match" value={formatCurrency(member.employer_retirement_annual)} />
        <Metric label="Retirement Rate" value={formatPercent(retirementRate)} />
      </div>

      <div className="household-member-meta">
        <span>{labelFor(ROLE_OPTIONS, member.role)}</span>
        <span>
          {member.retirement_accounts?.length
            ? `${member.retirement_accounts.length} linked ${member.retirement_accounts.length === 1 ? 'account' : 'accounts'}`
            : labelFor(RETIREMENT_OPTIONS, member.retirement_account_type)}
        </span>
        <span>{labelFor(PAY_FREQUENCY_OPTIONS, member.pay_frequency)}</span>
      </div>
    </article>
  );
}

function Metric({ label, value }) {
  return (
    <div className="household-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function MemberModal({ member, accounts, onClose, onSaved }) {
  const [draft, setDraft] = useState(() => toDraft(member));
  const [includeHsaInRate, setIncludeHsaInRate] = useState(
    () => !!member?.id && shouldIncludeHsaInRetirementRate(member.id)
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const isEdit = !!member;

  function update(key, value) {
    setDraft((prev) => {
      const next = { ...prev, [key]: value };
      return next;
    });
  }

  async function save(close) {
    if (!draft.name.trim()) {
      setError('Name is required.');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const payload = toPayload(draft);
      const nextData = isEdit
        ? await api.put(`/api/household/members/${member.id}`, payload)
        : await api.post('/api/household/members', payload);
      if (isEdit) {
        setIncludeHsaInRetirementRate(member.id, includeHsaInRate);
      }
      close({ animation: 'zoom' });
      setTimeout(() => onSaved(nextData), 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg" animation="zoom">
      {({ close }) => (
        <div className="household-modal">
          <h3>{isEdit ? 'Edit household member' : 'Add household member'}</h3>

          <div className="household-form-section">
            <h4>Person</h4>
            <FinancialFormGrid>
              <FinancialField label="Name">
                <input value={draft.name} onChange={(e) => update('name', e.target.value)} placeholder="Alex" />
              </FinancialField>
              <FinancialField label="Role">
                <AppSelect
                  value={draft.role}
                  options={ROLE_OPTIONS}
                  onChange={(value) => update('role', value)}
                  ariaLabel="Household role"
                />
              </FinancialField>
              <FinancialField label="Birth Date">
                <DateInput value={draft.birth_date || ''} onChange={(value) => update('birth_date', value)} />
              </FinancialField>
              <FinancialField label="Status">
                <AppSelect
                  value={draft.employment_status}
                  options={EMPLOYMENT_OPTIONS}
                  onChange={(value) => update('employment_status', value)}
                  ariaLabel="Employment status"
                />
              </FinancialField>
              <FinancialField label="Employer">
                <input value={draft.employer || ''} onChange={(e) => update('employer', e.target.value)} placeholder="Employer name" />
              </FinancialField>
              <FinancialField label="Job Title">
                <input value={draft.job_title || ''} onChange={(e) => update('job_title', e.target.value)} placeholder="Role or title" />
              </FinancialField>
            </FinancialFormGrid>
          </div>

          <div className="household-form-section">
            <h4>Pay</h4>
            <div className="goal-form-grid">
              <label className="field">
                <span>Gross Income</span>
                <CurrencyInput value={draft.gross_income_annual} onChange={(value) => update('gross_income_annual', value)} placeholder="$90,000" />
              </label>
              <label className="field">
                <span>Net Pay (Paycheck)</span>
                <CurrencyInput value={draft.net_pay_per_period} onChange={(value) => update('net_pay_per_period', value)} placeholder="$2,500" />
              </label>
              <label className="field">
                <span>Pay Frequency</span>
                <AppSelect
                  value={draft.pay_frequency}
                  options={PAY_FREQUENCY_OPTIONS}
                  onChange={(value) => update('pay_frequency', value)}
                  ariaLabel="Pay frequency"
                />
              </label>
            </div>
          </div>

          <div className="household-form-section">
            <h4>Retirement</h4>
            <div className="goal-form-grid">
              <label className="field">
                <span>Account Type</span>
                <AppSelect
                  value={draft.retirement_account_type}
                  options={RETIREMENT_OPTIONS}
                  onChange={(value) => update('retirement_account_type', value)}
                  ariaLabel="Retirement account type"
                />
              </label>
              <label className="field">
                <span>Employee Contribution</span>
                <PercentInput value={draft.employee_contribution_percent} onChange={(value) => update('employee_contribution_percent', value)} placeholder="6%" />
              </label>
              <label className="field">
                <span>Employer Match</span>
                <PercentInput value={draft.employer_match_percent} onChange={(value) => update('employer_match_percent', value)} placeholder="50%" />
              </label>
              <label className="field">
                <span>Matchable Contribution</span>
                <PercentInput
                  value={draft.employer_match_limit_percent}
                  onChange={(value) => update('employer_match_limit_percent', value)}
                  placeholder="6%"
                  aria-label="Maximum employee contribution percentage eligible for employer match"
                />
              </label>
            </div>
            <AccountLinkPicker
              accounts={accounts}
              linkedAccounts={draft.retirement_accounts}
              onChange={(value) => update('retirement_accounts', value)}
            />
          </div>

          <div className="household-form-section">
            <h4>Paycheck Deductions</h4>
            <p className="subtle" style={{ margin: 0 }}>
              Enter each deduction as it appears on one paycheck. Orbit annualizes it from the pay frequency above.
            </p>
            <div className="goal-form-grid">
              <label className="field">
                <span>Health Premium (Per Paycheck)</span>
                <CurrencyInput value={draft.health_premium_per_paycheck} onChange={(value) => update('health_premium_per_paycheck', value)} placeholder="$0" />
              </label>
              <label className="field">
                <span>HSA Contribution (Per Paycheck)</span>
                <CurrencyInput value={draft.hsa_contribution_annual} onChange={(value) => update('hsa_contribution_annual', value)} placeholder="$0" />
                {isEdit && (
                  <label className="inline-check household-hsa-rate-check">
                    <input
                      type="checkbox"
                      checked={includeHsaInRate}
                      onChange={(event) => setIncludeHsaInRate(event.target.checked)}
                    />
                    <span>Include HSA in retirement rate</span>
                  </label>
                )}
              </label>
              <label className="field">
                <span>Dependent Care FSA (Per Paycheck)</span>
                <CurrencyInput value={draft.dependent_care_fsa_annual} onChange={(value) => update('dependent_care_fsa_annual', value)} placeholder="$0" />
              </label>
              <label className="field">
                <span>Other Deductions (Per Paycheck)</span>
                <CurrencyInput value={draft.other_benefits_annual} onChange={(value) => update('other_benefits_annual', value)} placeholder="$0" />
              </label>
            </div>
            <label className="field">
              <span>Notes</span>
              <textarea value={draft.notes || ''} onChange={(e) => update('notes', e.target.value)} placeholder="Bonus cadence, vesting schedule, coverage notes..." />
            </label>
          </div>

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

function AccountLinkPicker({ accounts, linkedAccounts, onChange }) {
  const activeAccounts = (accounts || []).filter((account) => !account.is_archived && account.type === 'investment');
  const linkedIds = new Set((linkedAccounts || []).map((account) => Number(account.account_id)));

  function toggleAccount(account) {
    if (linkedIds.has(Number(account.id))) {
      onChange(linkedAccounts.filter((item) => Number(item.account_id) !== Number(account.id)));
      return;
    }
    onChange([
      ...(linkedAccounts || []),
      {
        account_id: Number(account.id),
        account_kind: accountKindFromAccount(account)
      }
    ]);
  }

  function updateKind(accountId, accountKind) {
    onChange((linkedAccounts || []).map((item) => (
      Number(item.account_id) === Number(accountId) ? { ...item, account_kind: accountKind } : item
    )));
  }

  return (
    <div className="household-account-links">
      <div className="household-subsection-header">
        <span>Linked Investment Accounts</span>
        <em>Balances compound into retirement projections.</em>
      </div>

      {activeAccounts.length === 0 ? (
        <p className="subtle">Add an account first, then link it here for retirement planning.</p>
      ) : (
        <div className="goal-account-picker household-account-picker">
          {activeAccounts.map((account) => {
            const active = linkedIds.has(Number(account.id));
            return (
              <button
                key={account.id}
                type="button"
                className={active ? 'active' : ''}
                onClick={() => toggleAccount(account)}
              >
                <span className="goal-picker-check" aria-hidden="true" />
                <span className="goal-picker-main">
                  <strong>{account.name}</strong>
                  <em>{account.institution || labelFor(RETIREMENT_ACCOUNT_KIND_OPTIONS, accountKindFromAccount(account))}</em>
                </span>
                <span className="goal-picker-side">
                  <strong>{formatCurrency(account.estimated_value || account.current_balance)}</strong>
                  <em>{active ? 'Linked' : 'Available'}</em>
                </span>
              </button>
            );
          })}
        </div>
      )}

      {(linkedAccounts || []).length > 0 && (
        <div className="goal-allocation-editor household-linked-account-editor">
          {linkedAccounts.map((link) => {
            const account = activeAccounts.find((item) => Number(item.id) === Number(link.account_id));
            if (!account) return null;
            return (
              <div key={link.account_id} className="goal-allocation-row">
                <div className="goal-allocation-header">
                  <div>
                    <strong>{account.name}</strong>
                    <span>{formatCurrency(account.estimated_value || account.current_balance)} current balance</span>
                  </div>
                  <label className="field">
                    <span>Type</span>
                    <AppSelect
                      value={link.account_kind || 'other'}
                      options={RETIREMENT_ACCOUNT_KIND_OPTIONS}
                      onChange={(value) => updateKind(link.account_id, value)}
                      ariaLabel={`${account.name} retirement account type`}
                    />
                  </label>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function accountKindFromAccount(account) {
  const text = `${account?.name || ''} ${account?.institution || ''}`.toLowerCase();
  if (text.includes('hsa')) return 'hsa';
  if (text.includes('roth')) return 'roth_ira';
  if (text.includes('403')) return '403b';
  if (text.includes('401')) return '401k';
  if (text.includes('457')) return '457b';
  if (text.includes('ira')) return 'ira';
  if (text.includes('pension')) return 'pension';
  return account?.type === 'investment' ? 'other' : 'other';
}
