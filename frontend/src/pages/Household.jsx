import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import AppIcon from '../components/AppIcon.jsx';

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
  ['sep_ira', 'SEP IRA'],
  ['simple_ira', 'SIMPLE IRA'],
  ['pension', 'Pension'],
  ['other', 'Other']
];

const PAY_PERIODS = {
  weekly: 52,
  biweekly: 26,
  semimonthly: 24,
  monthly: 12,
  annual: 1,
  none: 0
};

function formatMoney(amount, digits = 0) {
  return Number(amount || 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: digits
  });
}

function formatPercent(value) {
  const number = Number(value) || 0;
  return `${number.toLocaleString(undefined, { maximumFractionDigits: 2 })}%`;
}

function parsePercentInput(value) {
  const parsed = Number(String(value ?? '').replace(/[^0-9.]/g, ''));
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatPercentInput(value) {
  const cleaned = String(value ?? '').replace(/[^0-9.]/g, '');
  if (!cleaned) return '';
  return `${cleaned}%`;
}

function labelFor(options, value) {
  return options.find(([key]) => key === value)?.[1] || value || 'Not set';
}

function ageFromBirthDate(date) {
  if (!date) return null;
  const birth = new Date(`${date}T00:00:00`);
  if (Number.isNaN(birth.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const hadBirthday =
    now.getMonth() > birth.getMonth() ||
    (now.getMonth() === birth.getMonth() && now.getDate() >= birth.getDate());
  if (!hadBirthday) age -= 1;
  return age >= 0 ? age : null;
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
    health_premium_per_month: '',
    hsa_contribution_annual: '',
    dependent_care_fsa_annual: '',
    other_benefits_annual: '',
    effective_date: new Date().toISOString().slice(0, 10),
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
    health_premium_per_month: formatCurrencyInput(member.health_premium_per_month),
    hsa_contribution_annual: formatCurrencyInput(member.hsa_contribution_annual),
    dependent_care_fsa_annual: formatCurrencyInput(member.dependent_care_fsa_annual),
    other_benefits_annual: formatCurrencyInput(member.other_benefits_annual),
    employee_contribution_percent: formatPercentInput(member.employee_contribution_percent),
    employer_match_percent: formatPercentInput(member.employer_match_percent),
    employer_match_limit_percent: formatPercentInput(member.employer_match_limit_percent),
    effective_date: new Date().toISOString().slice(0, 10),
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
    health_premium_per_month: parseCurrencyInput(draft.health_premium_per_month),
    hsa_contribution_annual: parseCurrencyInput(draft.hsa_contribution_annual),
    dependent_care_fsa_annual: parseCurrencyInput(draft.dependent_care_fsa_annual),
    other_benefits_annual: parseCurrencyInput(draft.other_benefits_annual),
    notes: draft.notes
  };
}

export default function Household() {
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingMember, setEditingMember] = useState(null);

  async function load() {
    setLoading(true);
    setError('');
    try {
      setData(await api.get('/api/household'));
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
        toolbar={(
          <div className="page-hero-action-row">
            <button type="button" className="btn-primary" onClick={() => setEditingMember({ mode: 'new' })}>
              + New Member
            </button>
          </div>
        )}
      />

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : members.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">
            <AppIcon name="household" />
          </div>
          <h2>No household details yet</h2>
          <p>Add each adult or child whose income, benefits, or retirement inputs should influence future planning.</p>
          <button type="button" className="btn-primary" onClick={() => setEditingMember({ mode: 'new' })}>
            + New Member
          </button>
        </div>
      ) : (
        <div className="household-grid">
          <section className="dashboard-card household-members-card">
            <header className="dashboard-card-header">
              <h3>Members</h3>
              <button type="button" className="dashboard-card-link button-link" onClick={() => setEditingMember({ mode: 'new' })}>
                Add
              </button>
            </header>
            <div className="dashboard-card-body">
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
            </div>
          </section>

          <section className="dashboard-card household-summary-card">
            <header className="dashboard-card-header">
              <h3>Planning Signals</h3>
            </header>
            <div className="dashboard-card-body">
              <SignalRow label="Take-home pay" value={formatMoney(summary.net_pay_annual)} detail="Annualized from pay cadence" />
              <SignalRow label="Retirement savings" value={formatMoney((summary.employee_retirement_annual || 0) + (summary.employer_retirement_annual || 0))} detail={`${formatMoney(summary.employer_retirement_annual)} employer`} />
              <SignalRow label="Benefits value" value={formatMoney(summary.total_benefits_annual)} detail="Match, HSA, FSA, other" />
              <SignalRow label="Largest contributor" value={topMember?.name || 'None'} detail={topMember ? formatMoney(topMember.household_value_annual) : formatMoney(0)} />
            </div>
          </section>

          <section className="dashboard-card household-history-card">
            <header className="dashboard-card-header">
              <h3>Income History</h3>
            </header>
            <div className="dashboard-card-body">
              {records.length === 0 ? (
                <p className="subtle" style={{ margin: 0 }}>Save a member profile to record the first dated income snapshot.</p>
              ) : (
                <div className="household-history-list">
                  {records.slice(0, 10).map((record) => (
                    <div key={record.id} className="household-history-row">
                      <div>
                        <strong>{record.member_name}</strong>
                        <span>{new Date(`${record.effective_date}T00:00:00`).toLocaleDateString()}</span>
                      </div>
                      <div>
                        <strong>{formatMoney(record.gross_income_annual)}</strong>
                        <span>{formatMoney(record.net_pay_annual)} net</span>
                      </div>
                      <div>
                        <strong>{formatMoney(record.employer_retirement_annual)}</strong>
                        <span>Employer match</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        </div>
      )}

      {editingMember && (
        <MemberModal
          member={editingMember.mode === 'new' ? null : editingMember}
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

function SignalRow({ label, value, detail }) {
  return (
    <div className="networth-signal-row">
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <em>{detail}</em>
    </div>
  );
}

function MemberCard({ member, onEdit, onDelete }) {
  const age = ageFromBirthDate(member.birth_date);
  const retirementRate =
    Number(member.gross_income_annual || 0) > 0
      ? ((Number(member.employee_retirement_annual || 0) + Number(member.employer_retirement_annual || 0)) / Number(member.gross_income_annual)) * 100
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
        <Metric label="Gross" value={formatMoney(member.gross_income_annual)} />
        <Metric label="Net Pay" value={formatMoney(member.net_pay_annual)} />
        <Metric label="Employer Match" value={formatMoney(member.employer_retirement_annual)} />
        <Metric label="Retirement Rate" value={formatPercent(retirementRate)} />
      </div>

      <div className="household-member-meta">
        <span>{labelFor(ROLE_OPTIONS, member.role)}</span>
        <span>{labelFor(RETIREMENT_OPTIONS, member.retirement_account_type)}</span>
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

function MemberModal({ member, onClose, onSaved }) {
  const [draft, setDraft] = useState(() => toDraft(member));
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
            <div className="goal-form-grid">
              <label className="field">
                <span>Name</span>
                <input value={draft.name} onChange={(e) => update('name', e.target.value)} placeholder="Alex" />
              </label>
              <label className="field">
                <span>Role</span>
                <select value={draft.role} onChange={(e) => update('role', e.target.value)}>
                  {ROLE_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Birth Date</span>
                <input type="date" value={draft.birth_date || ''} onChange={(e) => update('birth_date', e.target.value)} />
              </label>
              <label className="field">
                <span>Status</span>
                <select value={draft.employment_status} onChange={(e) => update('employment_status', e.target.value)}>
                  {EMPLOYMENT_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Employer</span>
                <input value={draft.employer || ''} onChange={(e) => update('employer', e.target.value)} placeholder="Employer name" />
              </label>
              <label className="field">
                <span>Job Title</span>
                <input value={draft.job_title || ''} onChange={(e) => update('job_title', e.target.value)} placeholder="Role or title" />
              </label>
            </div>
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
                <select value={draft.pay_frequency} onChange={(e) => update('pay_frequency', e.target.value)}>
                  {PAY_FREQUENCY_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
            </div>
          </div>

          <div className="household-form-section">
            <h4>Retirement</h4>
            <div className="goal-form-grid">
              <label className="field">
                <span>Account Type</span>
                <select value={draft.retirement_account_type} onChange={(e) => update('retirement_account_type', e.target.value)}>
                  {RETIREMENT_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
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
          </div>

          <div className="household-form-section">
            <h4>Benefits</h4>
            <div className="goal-form-grid">
              <label className="field">
                <span>Health Premium</span>
                <CurrencyInput value={draft.health_premium_per_month} onChange={(value) => update('health_premium_per_month', value)} placeholder="$0" />
              </label>
              <label className="field">
                <span>HSA Contribution</span>
                <CurrencyInput value={draft.hsa_contribution_annual} onChange={(value) => update('hsa_contribution_annual', value)} placeholder="$0" />
              </label>
              <label className="field">
                <span>Dependent Care FSA</span>
                <CurrencyInput value={draft.dependent_care_fsa_annual} onChange={(value) => update('dependent_care_fsa_annual', value)} placeholder="$0" />
              </label>
              <label className="field">
                <span>Other Benefits</span>
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

function PercentInput({ value, onChange, ...props }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="decimal"
      value={value}
      onChange={(event) => onChange(formatPercentInput(event.target.value))}
    />
  );
}
