import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DndContext,
  PointerSensor,
  TouchSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  closestCenter
} from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
  sortableKeyboardCoordinates,
  arrayMove
} from '@dnd-kit/sortable';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppRangeSlider from '../components/AppRangeSlider.jsx';
import PageHero from '../components/PageHero.jsx';
import ReorderListItem from '../components/ReorderListItem.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import {
  formatCurrency,
  formatSignedCurrency,
  formatPercent,
  formatPercentInput,
  parsePercentInput
} from '../lib/formatters.js';
import { addMonthsToLocalDate } from '../lib/localDate.js';

const GOAL_PRESETS = [
  { kind: 'retirement', label: 'Retirement', icon: '🏖️' },
  { kind: 'college', label: 'Kids College', icon: '🎓' },
  { kind: 'car', label: 'New Car', icon: '🚗' },
  { kind: 'home', label: 'Home', icon: '🏠' },
  { kind: 'emergency', label: 'Emergency Fund', icon: '🛟' },
  { kind: 'travel', label: 'Travel', icon: '✈️' },
  { kind: 'custom', label: 'Something Else', icon: '✨' }
];

const ACCOUNT_TYPE_LABELS = {
  checking: 'Checking',
  savings: 'Savings',
  investment: 'Investment',
  cash: 'Cash',
  other: 'Other'
};

const RETIREMENT_ACCOUNT_KIND_LABELS = {
  '401k': '401(k)',
  '403b': '403(b)',
  '457b': '457(b)',
  ira: 'IRA',
  roth_ira: 'Roth IRA',
  sep_ira: 'SEP IRA',
  simple_ira: 'SIMPLE IRA',
  hsa: 'HSA',
  pension: 'Pension',
  other: 'Other'
};

const GOAL_COLORS = [
  '#10b981',
  '#38bdf8',
  '#a78bfa',
  '#f59e0b',
  '#f472b6',
  '#22c55e',
  '#fb7185',
  '#60a5fa'
];

function formatMoney(amount, digits = 0) {
  return formatCurrency(amount, { maximumFractionDigits: digits });
}

function formatSignedMoney(amount) {
  return formatSignedCurrency(amount);
}

const parseMoney = parseCurrencyInput;

function colorForGoal(goalKey) {
  const text = String(goalKey || 'goal');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 9973;
  }
  return GOAL_COLORS[hash % GOAL_COLORS.length];
}

function formatMonth(key) {
  if (!key) return '';
  const [year, month] = key.split('-').map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString(undefined, {
    month: 'short',
    year: 'numeric'
  });
}

function formatDate(date) {
  if (!date) return 'No ETA yet';
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    month: 'short',
    year: 'numeric'
  });
}

function formatFullMonthDate(date) {
  if (!date) return 'No ETA yet';
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric'
  });
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

function formatEta(eta) {
  if (!eta) return 'No ETA yet';
  if (eta.status === 'complete') return 'Reached';
  if (!eta.date) return 'Needs history';
  return formatDate(eta.date);
}

function monthsUntil(date) {
  if (!date) return null;
  const target = new Date(`${date}T00:00:00`);
  if (Number.isNaN(target.getTime())) return null;
  const now = new Date();
  const months = (target.getFullYear() - now.getFullYear()) * 12 + (target.getMonth() - now.getMonth());
  return Math.max(1, months);
}

function desiredEta(goal) {
  return goal?.target_date ? formatFullMonthDate(goal.target_date) : 'ASAP';
}

function neededExtraForDesiredEta(goal) {
  if (!goal?.target_date) return null;
  const remaining = Math.max(0, Number(goal.target_amount || 0) - Number(goal.current_amount || 0));
  if (remaining <= 0) return 0;
  const months = monthsUntil(goal.target_date);
  if (!months) return null;
  const requiredMonthly = remaining / months;
  return Math.max(0, requiredMonthly - (Number(goal.monthly_pace) || 0));
}

function addMonthsToDate(date, amount) {
  return addMonthsToLocalDate(date, amount);
}

function estimateEta(goal, extraMonthly) {
  const current = Number(goal?.current_amount) || 0;
  const target = Number(goal?.target_amount) || 0;
  if (current >= target) return { date: null, months: 0, status: 'complete' };
  const monthly = (Number(goal?.monthly_pace) || 0) + Number(extraMonthly || 0);
  if (monthly <= 0) return { date: null, months: null, status: 'stalled' };
  const months = Math.ceil((target - current) / monthly);
  return { date: addMonthsToDate(new Date(), months), months, status: 'projected' };
}

function isRetirementGoal(goal) {
  return String(goal?.name || '').trim().toLowerCase() === 'retirement';
}

function effectiveMonthlyRate(annualReturn) {
  const rate = Number(annualReturn) || 0;
  if (rate <= -1) return -1;
  return (1 + rate) ** (1 / 12) - 1;
}

function futureValueSeries(current, monthly, annualReturn, months) {
  const principal = Number(current) || 0;
  const contribution = Number(monthly) || 0;
  const monthlyReturn = effectiveMonthlyRate(annualReturn);
  if (months <= 0) return principal;
  if (monthlyReturn <= 0) return principal + contribution * months;
  return principal * ((1 + monthlyReturn) ** months) +
    contribution * ((((1 + monthlyReturn) ** months) - 1) / monthlyReturn);
}

function projectRetirementByYear(current, annualSavings, annualReturn, years) {
  const items = [{ yearOffset: 0, amount: Number(current) || 0 }];
  const principal = Number(current) || 0;
  const monthlyContribution = (Number(annualSavings) || 0) / 12;
  for (let year = 1; year <= years; year += 1) {
    items.push({
      yearOffset: year,
      amount: futureValueSeries(principal, monthlyContribution, annualReturn, year * 12)
    });
  }
  return items;
}

function classifyRetirementAccount(name) {
  const text = String(name || '').toLowerCase();
  if (text.includes('hsa')) return 'HSA';
  if (text.includes('roth')) return 'Roth';
  if (text.includes('403')) return '403(b)';
  if (text.includes('401')) return '401(k)';
  if (text.includes('ira')) return 'IRA';
  return 'Other';
}

function chartRange(history) {
  if (!history?.length) return { min: 0, max: 1 };
  const values = history.map((point) => Number(point.amount) || 0);
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    min -= Math.max(100, Math.abs(min) * 0.1);
    max += Math.max(100, Math.abs(max) * 0.1);
  }
  const pad = (max - min) * 0.16;
  return { min: min - pad, max: max + pad };
}

function chartPath(history, width, height) {
  if (!history?.length) return '';
  const { min, max } = chartRange(history);
  return history
    .map((point, index) => {
      const x = history.length === 1 ? width / 2 : (index / (history.length - 1)) * width;
      const y = height - (((Number(point.amount) || 0) - min) / (max - min)) * height;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(' ');
}

function chartPathWithRange(history, width, height, min, max) {
  if (!history?.length) return '';
  const safeMin = Number.isFinite(min) ? min : 0;
  const safeMax = Number.isFinite(max) && max > safeMin ? max : safeMin + 1;
  return history
    .map((point, index) => {
      const x = history.length === 1 ? width / 2 : (index / (history.length - 1)) * width;
      const y = height - (((Number(point.amount) || 0) - safeMin) / (safeMax - safeMin)) * height;
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${Math.max(0, Math.min(height, y)).toFixed(2)}`;
    })
    .join(' ');
}

function areaPath(history, width, height) {
  const line = chartPath(history, width, height);
  if (!line) return '';
  return `${line} L ${width} ${height} L 0 ${height} Z`;
}

function areaPathWithRange(history, width, height, min, max) {
  const line = chartPathWithRange(history, width, height, min, max);
  if (!line) return '';
  return `${line} L ${width} ${height} L 0 ${height} Z`;
}

function allocationBasis(account, reserveAmount) {
  return Math.max(0, (Number(account?.current_balance) || 0) - Number(reserveAmount || 0));
}

function accountOpenAmount(account, goalId) {
  const ownAmount = (account?.allocations || [])
    .filter((row) => row.goal_id === goalId)
    .reduce((sum, row) => sum + Number(row.current_amount || 0), 0);
  return Math.max(0, (Number(account?.remaining_amount) || 0) + ownAmount);
}

function openSpaceAllocationAmount(allocation, openAmount) {
  const value = allocationValueNumber(allocation);
  if (allocation.allocation_type === 'percent') {
    return openAmount * (Math.min(100, Math.max(0, value)) / 100);
  }
  return Math.min(Math.max(0, value), openAmount);
}

function draftAllocationValue(allocation, accounts, goalId) {
  if (allocation.allocation_type === 'fixed') {
    return formatCurrencyInput(allocation.current_amount || allocation.allocation_value);
  }
  const account = accounts.find((row) => row.id === allocation.account_id);
  const openAmount = accountOpenAmount(account, goalId);
  if (openAmount <= 0) return '0';
  const percentOfOpen = (Number(allocation.current_amount || 0) / openAmount) * 100;
  return String(Math.round(Math.min(100, Math.max(0, percentOfOpen))));
}

function toSavedAllocation(allocation, account, goalId) {
  const basis = allocationBasis(account, 0);
  const openAmount = accountOpenAmount(account, goalId);
  const fixedCanSteal = allocation.allocation_type === 'fixed' && allocation.stealFromOthers;
  const currentAmount = fixedCanSteal
    ? Math.min(Math.max(0, allocationValueNumber(allocation)), basis)
    : openSpaceAllocationAmount(allocation, openAmount);
  return {
    account_id: allocation.account_id,
    allocation_type: allocation.allocation_type,
    allocation_value:
      allocation.allocation_type === 'percent'
        ? basis > 0
          ? Math.min(100, (currentAmount / basis) * 100)
          : 0
        : currentAmount,
    reserve_amount: 0
  };
}

export default function Goals() {
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [wizardGoal, setWizardGoal] = useState(null);
  const [household, setHousehold] = useState(null);
  const [imagineMonthly, setImagineMonthly] = useState(50);
  const [focusCollapsed, setFocusCollapsed] = useState(true);
  const [reorderMode, setReorderMode] = useState(false);
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 0 }
    }),
    useSensor(TouchSensor, {
      activationConstraint: { distance: 0 }
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates
    })
  );

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [next, householdNext] = await Promise.all([
        api.get('/api/goals?months=24'),
        api.get('/api/household').catch(() => null)
      ]);
      setData(next);
      setHousehold(householdNext);
      setSelectedId((current) => {
        if (current && next.goals.some((goal) => goal.id === current)) return current;
        return next.goals[0]?.id || null;
      });
    } catch (err) {
      setError(err.message || 'Failed to load goals');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const goals = data?.goals || [];
  const accounts = data?.accounts || [];
  const selectedGoal = goals.find((goal) => goal.id === selectedId) || goals[0] || null;
  const showingRetirementPlanner = isRetirementGoal(selectedGoal);
  const imaginedEta = selectedGoal ? estimateEta(selectedGoal, imagineMonthly) : null;
  async function handleDelete(goal) {
    const ok = await confirm(`Delete "${goal.name}"? Its account allocations will be removed.`, {
      title: 'Delete goal',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/goals/${goal.id}`);
      await load();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  function openNewGoal() {
    setWizardGoal({ mode: 'new' });
  }

  function openEditGoal(goal) {
    setWizardGoal(goal);
  }

  async function handleGoalDragEnd(event) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const oldIndex = goals.findIndex((goal) => goal.id === active.id);
    const newIndex = goals.findIndex((goal) => goal.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const nextGoals = arrayMove(goals, oldIndex, newIndex);
    const optimisticData = { ...data, goals: nextGoals };
    setData(optimisticData);

    try {
      const result = await api.put('/api/goals/reorder', {
        ids: nextGoals.map((goal) => goal.id)
      });
      setData(result);
    } catch (err) {
      setData(data);
      alert(err.message || 'Reorder failed', { title: 'Reorder failed' });
    }
  }

  function toggleFocusCard(e) {
    if (
      e.target.closest('button, a, input, select, textarea') ||
      e.target.closest('.goals-focus-detail')
    ) {
      return;
    }
    setFocusCollapsed((value) => !value);
  }

  function handleFocusCardKeyDown(e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    if (
      e.target.closest('button, a, input, select, textarea') ||
      e.target.closest('.goals-focus-detail')
    ) {
      return;
    }
    e.preventDefault();
    setFocusCollapsed((value) => !value);
  }

  return (
    <div className="goals-view">
      <PageHero
        id="goals-title"
        variant="goals"
        kicker="Saving Targets"
        title="Goals"
        subtitle="Connect asset accounts, allocate savings, and project when each target lands."
        toolbar={(
          <div className="page-hero-action-row">
            <button type="button" className="btn-primary" onClick={openNewGoal}>
              + New Goal
            </button>
          </div>
        )}
      />

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : accounts.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">$</div>
          <h2>No asset accounts yet</h2>
          <p>Add a checking, savings, investment, cash, or other asset account before creating goals.</p>
          <Link to="/accounts" className="btn-primary">Manage accounts</Link>
        </div>
      ) : goals.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">G</div>
          <h2>No goals yet</h2>
          <p>Create a saving target and connect the accounts that should count toward it.</p>
          <button type="button" className="btn-primary" onClick={openNewGoal}>+ New Goal</button>
        </div>
      ) : (
        <div className="goals-grid">
          {showingRetirementPlanner ? (
            <RetirementPlanner goal={selectedGoal} household={household} />
          ) : (
            <section
              className={`dashboard-card goals-focus-card ${focusCollapsed ? 'collapsed' : ''}`}
              onClick={toggleFocusCard}
              onKeyDown={handleFocusCardKeyDown}
              tabIndex={0}
              aria-expanded={!focusCollapsed}
            >
              <header className="dashboard-card-header">
                <h3>{selectedGoal?.name || 'Goal'}</h3>
                <div className="goals-card-actions">
                  <button type="button" className="dashboard-card-link button-link" onClick={() => openEditGoal(selectedGoal)}>
                    Edit
                  </button>
                </div>
              </header>
              <div className="dashboard-card-body">
                <GoalProgress goal={selectedGoal} />
                <div className="goals-focus-detail" aria-hidden={focusCollapsed}>
                  <div className="goals-focus-detail-inner">
                    <GoalChart goal={selectedGoal} />
                    <ImaginePanel
                      goal={selectedGoal}
                      imagineMonthly={imagineMonthly}
                      imaginedEta={imaginedEta}
                      onChange={setImagineMonthly}
                    />
                    <div className="goals-focus-actions">
                      <button type="button" className="btn-secondary" onClick={() => openEditGoal(selectedGoal)}>
                        Edit goal
                      </button>
                      <button type="button" className="btn-danger" onClick={() => handleDelete(selectedGoal)}>
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          )}

          <section className="dashboard-card goals-list-card">
            <header className="dashboard-card-header">
              <h3>Goals</h3>
              {goals.length > 1 && (
                <button
                  type="button"
                  className="dashboard-card-link button-link"
                  onClick={() => setReorderMode((value) => !value)}
                >
                  {reorderMode ? 'Done' : 'Reorder'}
                </button>
              )}
            </header>
            <div className="goal-list">
              {reorderMode ? (
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragEnd={handleGoalDragEnd}
                >
                  <SortableContext
                    items={goals.map((goal) => goal.id)}
                    strategy={verticalListSortingStrategy}
                  >
                    <div className="goal-list reorder-active">
                      {goals.map((goal) => (
                        <DraggableGoalRow key={goal.id} goal={goal} />
                      ))}
                    </div>
                  </SortableContext>
                </DndContext>
              ) : goals.map((goal) => {
                const leading = <span className="goal-list-icon">{goal.icon || presetFor(goal.kind).icon}</span>;
                const subtitle = `${formatMoney(goal.current_amount)} of ${formatMoney(goal.target_amount)}`;
                const progress = `${Math.round(goal.progress_percent || 0)}%`;
                const eta = formatEta(goal.eta);

                return (
                  <SelectableListItem
                    key={goal.id}
                    className="goal-list-row"
                    active={selectedGoal?.id === goal.id}
                    onClick={() => setSelectedId(goal.id)}
                    leading={leading}
                    title={goal.name}
                    subtitle={subtitle}
                    sidePrimary={progress}
                    sideSecondary={eta}
                    ariaLabel={`Select ${goal.name}`}
                  />
                );
              })}
            </div>
          </section>

          <section className="dashboard-card goals-account-card">
            <header className="dashboard-card-header">
              <h3>Account allocation</h3>
              <Link to="/accounts" className="dashboard-card-link">View Accounts</Link>
            </header>
            <div className="dashboard-card-body">
              <div className="goal-account-list">
                {accounts.map((account) => (
                  <AccountAllocationRow key={account.id} account={account} />
                ))}
              </div>
            </div>
          </section>
        </div>
      )}

      {wizardGoal && (
        <GoalWizard
          goal={wizardGoal.mode === 'new' ? null : wizardGoal}
          accounts={accounts}
          confirm={confirm}
          onClose={() => setWizardGoal(null)}
          onSaved={async (savedId) => {
            setWizardGoal(null);
            await load();
            setSelectedId(savedId);
          }}
        />
      )}

      <Dialog />
    </div>
  );
}

function presetFor(kind) {
  return GOAL_PRESETS.find((preset) => preset.kind === kind) || GOAL_PRESETS[GOAL_PRESETS.length - 1];
}

function DraggableGoalRow({ goal }) {
  return (
    <ReorderListItem
      id={goal.id}
      className="goal-list-row"
      handleLabel={`Reorder ${goal.name}`}
      leading={<span className="goal-list-icon">{goal.icon || presetFor(goal.kind).icon}</span>}
      title={goal.name}
      subtitle={`${formatMoney(goal.current_amount)} of ${formatMoney(goal.target_amount)}`}
      sidePrimary={`${Math.round(goal.progress_percent || 0)}%`}
      sideSecondary={formatEta(goal.eta)}
    />
  );
}

function GoalProgress({ goal }) {
  const progress = Math.max(0, Math.min(100, Number(goal?.progress_percent) || 0));
  const remaining = Math.max(0, Number(goal?.target_amount || 0) - Number(goal?.current_amount || 0));
  const extraNeeded = neededExtraForDesiredEta(goal);

  return (
    <div className="goal-progress-panel">
      <div className="goal-progress-main">
        <span>{formatMoney(goal?.current_amount)} saved</span>
        <strong className="goal-progress-amount">{formatMoney(goal?.target_amount)}</strong>
        <em>{formatMoney(remaining)} remaining</em>
      </div>
      <div className="goal-progress-ring" style={{ '--goal-progress': `${progress}%` }}>
        <span>{Math.round(progress)}%</span>
      </div>
      <div className="goal-progress-meta">
        <SignalRow className="goal-eta-row goal-desired-row" label="Desired ETA" value={desiredEta(goal)} />
        <SignalRow className="goal-eta-row goal-projected-row" label="Projected ETA" value={formatEta(goal?.eta)} />
        <SignalRow className="goal-monthly-row" label="Monthly pace" value={formatSignedMoney(goal?.monthly_pace)} />
      </div>
    </div>
  );
}

function RetirementPlanner({ goal, household }) {
  const [editingAssumptions, setEditingAssumptions] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [scenarioMonthlySavings, setScenarioMonthlySavings] = useState('');
  const [assumptions, setAssumptions] = useState({
    currentAge: '',
    retirementAge: '67',
    annualReturn: '7%',
    inflation: '2.5%',
    replacementRate: '80%',
    withdrawalRate: '4%'
  });

  const members = household?.members || [];
  const summary = household?.summary || {};
  const allocations = goal?.allocations || [];
  const linkedAccounts = members.flatMap((member) => (
    (member.retirement_accounts || []).map((account) => ({
      ...account,
      member_name: member.name,
      balance: Number(account.balance ?? account.estimated_value ?? account.current_balance) || 0
    }))
  ));
  const usesLinkedAccounts = linkedAccounts.length > 0;
  const accountSources = usesLinkedAccounts
    ? linkedAccounts.map((account) => ({
      label: RETIREMENT_ACCOUNT_KIND_LABELS[account.account_kind] || classifyRetirementAccount(account.account_name),
      amount: account.balance,
      name: account.account_name,
      owner: account.member_name,
      kind: account.account_kind
    }))
    : allocations.map((allocation) => ({
      label: classifyRetirementAccount(allocation.account_name),
      amount: Number(allocation.current_amount) || 0,
      name: allocation.account_name,
      owner: null,
      kind: classifyRetirementAccount(allocation.account_name).toLowerCase()
    }));
  const linkedCurrentBalance = accountSources.reduce((sum, source) => sum + source.amount, 0);
  const inferredHsaCurrent = accountSources
    .filter((source) => source.kind === 'hsa' || source.label === 'HSA')
    .reduce((sum, source) => sum + source.amount, 0);
  const householdHsaAnnual = members.reduce(
    (sum, member) => sum + (Number(member.hsa_contribution_annual) || 0),
    0
  );
  const inferredAge =
    members
      .map((member) => ageFromBirthDate(member.birth_date))
      .find((age) => age !== null) ?? 35;
  const currentAge = Number(assumptions.currentAge) || inferredAge;
  const retirementAge = Math.max(currentAge, Number(assumptions.retirementAge) || 67);
  const hsaAccessAge = Math.max(currentAge, 65);
  const years = Math.max(0, retirementAge - currentAge);
  const months = Math.round(years * 12);
  const monthsToHsaAccess = Math.max(0, Math.round((hsaAccessAge - currentAge) * 12));
  const lockedHsaYears = Math.max(0, hsaAccessAge - retirementAge);
  const annualReturn = parsePercentInput(assumptions.annualReturn) / 100;
  const inflation = parsePercentInput(assumptions.inflation) / 100;
  const realReturn = ((1 + annualReturn) / (1 + inflation)) - 1;
  const currentBalance = usesLinkedAccounts ? linkedCurrentBalance : Number(goal?.current_amount) || 0;
  const employeeAnnual = Number(summary.employee_retirement_annual) || 0;
  const employerAnnual = Number(summary.employer_retirement_annual) || 0;
  const annualSavings = employeeAnnual + employerAnnual;
  const plannedMonthly = annualSavings / 12;
  const annualIncome = Number(summary.net_pay_annual || summary.gross_income_annual) || 0;
  const annualNeed = annualIncome * (parsePercentInput(assumptions.replacementRate) / 100);
  const withdrawalRate = Math.max(0.1, parsePercentInput(assumptions.withdrawalRate) || 4) / 100;
  const hsaCurrent = inferredHsaCurrent;
  const hsaAnnual = householdHsaAnnual;
  const hsaMonthly = hsaAnnual / 12;
  const nonHsaCurrent = Math.max(0, currentBalance - hsaCurrent);
  const nonHsaMonthly = Math.max(0, plannedMonthly - hsaMonthly);
  const targetNestEgg = annualNeed / withdrawalRate;
  const projectedNonHsa = futureValueSeries(nonHsaCurrent, nonHsaMonthly, annualReturn, months);
  const projectedHsaAtRetirement = futureValueSeries(hsaCurrent, hsaMonthly, annualReturn, months);
  const projectedHsaAtAccess = futureValueSeries(hsaCurrent, hsaMonthly, annualReturn, monthsToHsaAccess);
  const projectedBalance = projectedNonHsa + projectedHsaAtRetirement;
  const bridgeNeed = retirementAge < hsaAccessAge ? annualNeed * lockedHsaYears : 0;
  const bridgeSurplus = projectedNonHsa - bridgeNeed;
  const gap = projectedBalance - targetNestEgg;
  const savingsRate = Number(summary.gross_income_annual) > 0
    ? (annualSavings / Number(summary.gross_income_annual)) * 100
    : 0;
  const readiness = Math.min(100, targetNestEgg > 0 ? (projectedBalance / targetNestEgg) * 100 : 0);
  const hsaBridgeText = retirementAge >= hsaAccessAge
    ? `HSA available by age ${Math.round(retirementAge)}`
    : bridgeSurplus >= 0
      ? `${formatMoney(bridgeSurplus)} bridge surplus`
      : `${formatMoney(Math.abs(bridgeSurplus))} bridge gap`;
  const defaultMonthlySavings = annualSavings / 12;
  const scenarioMonthlyValue = String(scenarioMonthlySavings).trim() === ''
    ? defaultMonthlySavings
    : parseMoney(scenarioMonthlySavings);
  const scenarioAnnualValue = scenarioMonthlyValue * 12;
  const scenarioRangeMaxMonthly = Math.max(1000, Math.ceil((Math.max(defaultMonthlySavings, targetNestEgg / Math.max(1, months)) * 1.75) / 250) * 250);
  const maxRangeAnnualValue = scenarioRangeMaxMonthly * 12;
  const defaultPoints = projectRetirementByYear(currentBalance, annualSavings, annualReturn, Math.max(1, Math.round(years)));
  const scenarioPoints = projectRetirementByYear(currentBalance, scenarioAnnualValue, annualReturn, Math.max(1, Math.round(years)));
  const maxRangePoints = projectRetirementByYear(currentBalance, maxRangeAnnualValue, annualReturn, Math.max(1, Math.round(years)));
  const defaultProjection = defaultPoints[defaultPoints.length - 1]?.amount || currentBalance;
  const scenarioProjection = scenarioPoints[scenarioPoints.length - 1]?.amount || currentBalance;
  const scenarioGap = scenarioProjection - targetNestEgg;

  function update(key, value) {
    setAssumptions((prev) => ({ ...prev, [key]: value }));
  }

  function toggleCollapsed(e) {
    if (
      e.target.closest('button, a, input, select, textarea')
    ) {
      return;
    }
    setCollapsed((value) => !value);
  }

  function handleCardKeyDown(e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    if (
      e.target.closest('button, a, input, select, textarea')
    ) {
      return;
    }
    e.preventDefault();
    setCollapsed((value) => !value);
  }

  return (
    <section
      className={`dashboard-card retirement-planner-card ${collapsed ? 'collapsed' : ''}`}
      onClick={toggleCollapsed}
      onKeyDown={handleCardKeyDown}
      tabIndex={0}
      aria-expanded={!collapsed}
    >
      <header className="dashboard-card-header">
        <h3>Retirement Calculator</h3>
        <div className="dashboard-card-actions">
          <button type="button" className="dashboard-card-link button-link" onClick={() => setEditingAssumptions(true)}>
            Edit Assumptions
          </button>
        </div>
      </header>
      <div className="dashboard-card-body">
        <div className="retirement-planner-hero">
          <div>
            <span>Projected at {Math.round(retirementAge)}</span>
            <strong>{formatMoney(projectedBalance)}</strong>
            <em className={gap >= 0 ? 'income' : 'expense'}>
              {gap >= 0 ? `${formatMoney(gap)} surplus` : `${formatMoney(Math.abs(gap))} short`}
            </em>
            <label className="field retirement-hero-age-field">
              <span>Retirement Age</span>
              <div className="retirement-age-stepper">
                <input
                  type="number"
                  min="1"
                  max="99"
                  value={assumptions.retirementAge}
                  onChange={(e) => update('retirementAge', e.target.value)}
                />
                <div className="retirement-age-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    aria-label="Decrease retirement age"
                    onClick={() => update('retirementAge', String(Math.max(currentAge, retirementAge - 1)))}
                  >
                    Down
                  </button>
                  <button
                    type="button"
                    className="btn-secondary"
                    aria-label="Increase retirement age"
                    onClick={() => update('retirementAge', String(Math.min(99, retirementAge + 1)))}
                  >
                    Up
                  </button>
                </div>
              </div>
            </label>
          </div>
          <div className="retirement-readiness-ring" style={{ '--retirement-progress': `${readiness}%` }}>
            <span>{Math.round(readiness)}%</span>
          </div>
        </div>

        <div className="retirement-planner-detail" aria-hidden={collapsed}>
          <div className="retirement-planner-detail-inner">
            <div className="retirement-metric-grid">
              <RetirementMetric label="Target Nest Egg" value={formatMoney(targetNestEgg)} detail={`${formatMoney(annualNeed)}/yr need`} />
              <RetirementMetric label="Household Savings" value={`${formatMoney(plannedMonthly)}/mo`} detail={`${formatMoney(employerAnnual)} employer/yr`} />
              <RetirementMetric label="Savings Rate" value={formatPercent(savingsRate)} detail={`${formatMoney(employeeAnnual)} employee/yr`} />
              <RetirementMetric label="Real Return" value={formatPercent(realReturn * 100)} detail={`${formatPercent(annualReturn * 100)} market minus ${formatPercent(inflation * 100)} inflation`} />
              <RetirementMetric label="Time to Retirement" value={`${Math.round(years)} years`} detail={`${Math.round(months).toLocaleString()} months`} />
            </div>

            <RetirementProjectionPanel
              currentAge={currentAge}
              retirementAge={retirementAge}
              targetNestEgg={targetNestEgg}
              defaultProjection={defaultProjection}
              monthlySavings={scenarioMonthlyValue}
              scenarioProjection={scenarioProjection}
              scenarioGap={scenarioGap}
              defaultPoints={defaultPoints}
              points={scenarioPoints}
              maxRangePoints={maxRangePoints}
              rangeMax={scenarioRangeMaxMonthly}
              onMonthlySavingsChange={setScenarioMonthlySavings}
            />

            <div className="retirement-member-card">
              {members.length === 0 ? (
                <p className="subtle">Add household income and match details to unlock employer contribution projections.</p>
              ) : members.map((member) => (
                <div key={member.id} className="retirement-member-pane">
                  <div>
                    <strong>{member.name}</strong>
                    <span>{member.retirement_accounts?.length || 0} accounts | {formatPercent(member.employee_contribution_percent)} employee | {formatPercent(member.employer_match_percent)} match</span>
                  </div>
                  <em>{formatMoney((Number(member.employee_retirement_annual) || 0) + (Number(member.employer_retirement_annual) || 0))}/yr</em>
                </div>
              ))}
            </div>

            <div className="retirement-hsa-panel">
              <div>
                <span>HSA bridge check</span>
                <strong className={bridgeSurplus >= 0 ? 'income' : 'expense'}>
                  {hsaBridgeText}
                </strong>
                <em>
                  {retirementAge >= hsaAccessAge
                    ? `HSA is available by age ${Math.round(retirementAge)}.`
                    : `${formatMoney(bridgeNeed)} needed from retirement to age ${Math.round(hsaAccessAge)} before HSA behaves like a retirement account.`}
                </em>
              </div>
              <div className="retirement-hsa-stats">
                <RetirementMetric label="HSA at Retirement" value={formatMoney(projectedHsaAtRetirement)} detail={`${formatMoney(hsaAnnual)}/yr contributions`} />
                <RetirementMetric label={`HSA at ${Math.round(hsaAccessAge)}`} value={formatMoney(projectedHsaAtAccess)} detail={`${Math.round(lockedHsaYears)} locked years after retirement`} />
              </div>
            </div>
          </div>
        </div>
      </div>

      {editingAssumptions && (
        <AnimatedModal onClose={() => setEditingAssumptions(false)} size="lg" animation="zoom">
          {({ close }) => (
            <div className="retirement-assumption-modal">
              <h3>Retirement Assumptions</h3>
              <p className="subtle">
                Linked household accounts supply current balances. These assumptions only adjust the projection math.
              </p>
              <div className="retirement-assumption-grid">
                <label className="field">
                  <span>Current Age</span>
                  <input type="number" min="0" value={assumptions.currentAge} onChange={(e) => update('currentAge', e.target.value)} placeholder={String(inferredAge)} />
                </label>
                <label className="field">
                  <span>Market Return</span>
                  <PercentInput value={assumptions.annualReturn} onChange={(value) => update('annualReturn', value)} placeholder="7%" />
                </label>
                <label className="field">
                  <span>Inflation</span>
                  <PercentInput value={assumptions.inflation} onChange={(value) => update('inflation', value)} placeholder="2.5%" />
                </label>
                <label className="field">
                  <span>Income Replacement</span>
                  <PercentInput value={assumptions.replacementRate} onChange={(value) => update('replacementRate', value)} placeholder="80%" />
                </label>
                <label className="field">
                  <span>Withdrawal Rate</span>
                  <PercentInput value={assumptions.withdrawalRate} onChange={(value) => update('withdrawalRate', value)} placeholder="4%" />
                </label>
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-primary" onClick={close}>Done</button>
              </div>
            </div>
          )}
        </AnimatedModal>
      )}
    </section>
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

function RetirementMetric({ label, value, detail }) {
  return (
    <div className="retirement-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <em>{detail}</em>
    </div>
  );
}

function RetirementProjectionPanel({
  currentAge,
  retirementAge,
  targetNestEgg,
  defaultProjection,
  monthlySavings,
  scenarioProjection,
  scenarioGap,
  defaultPoints,
  points,
  maxRangePoints,
  rangeMax,
  onMonthlySavingsChange
}) {
  const width = 640;
  const height = 220;
  const chartPoints = points.map((point) => ({
    month: String(point.yearOffset),
    amount: point.amount
  }));
  const computedYMax = Math.max(
    targetNestEgg,
    defaultProjection,
    ...maxRangePoints.map((point) => point.amount),
    ...defaultPoints.map((point) => point.amount),
    1
  ) * 1.08;
  const yMax = Math.max(targetNestEgg, Math.min(10000000, computedYMax));
  const yMin = 0;
  const line = chartPathWithRange(chartPoints, width, height, yMin, yMax);
  const area = areaPathWithRange(chartPoints, width, height, yMin, yMax);
  const targetY = height - ((targetNestEgg - yMin) / (yMax - yMin)) * height;
  const yTicks = [1, 0.5, 0].map((ratio) => ({
    value: yMax * ratio,
    y: height - (height * ratio)
  }));

  function handleSlider(value) {
    onMonthlySavingsChange(formatCurrencyInput(value));
  }

  return (
    <div className="retirement-projection-panel">
      <div className="retirement-projection-copy">
        <span>Projection Curve</span>
        <strong className={scenarioGap >= 0 ? 'income' : 'expense'}>
          {scenarioGap >= 0 ? 'On Pace' : 'Below Target'} | {formatMoney(scenarioProjection)}
        </strong>
        <em>
          {scenarioGap >= 0
            ? `${formatMoney(scenarioGap)} above target at retirement`
            : `${formatMoney(Math.abs(scenarioGap))} below target at retirement`}
        </em>
      </div>
      <div className="goal-chart-wrap">
        <div className="retirement-chart-axis-title">Projected Balance</div>
        <svg className="goal-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Retirement projection">
          <defs>
            <linearGradient id="retirementProjectionArea" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.24" />
              <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
            </linearGradient>
          </defs>
          {yTicks.map((tick) => (
            <g key={tick.y}>
              <line x1="0" y1={tick.y} x2={width} y2={tick.y} className="retirement-chart-gridline" />
              <text x="6" y={Math.max(14, tick.y - 6)} className="retirement-chart-axis-label">
                {formatMoney(tick.value)}
              </text>
            </g>
          ))}
          <line x1="0" y1={targetY} x2={width} y2={targetY} className="retirement-chart-target-line" />
          <text x={width - 8} y={Math.max(14, targetY - 6)} textAnchor="end" className="retirement-chart-target-label">
            Target {formatMoney(targetNestEgg)}
          </text>
          <path d={area} fill="url(#retirementProjectionArea)" />
          <path d={line} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <div className="retirement-chart-age-labels">
          <span>Age {Math.round(currentAge)}</span>
          <span>Age {Math.round(retirementAge)}</span>
        </div>
        <div className="retirement-chart-x-label">Age</div>
      </div>
      <div className="goal-imagine-controls retirement-projection-controls">
        <AppRangeSlider
          min="0"
          max={rangeMax}
          step="25"
          hapticStep="25"
          value={Math.min(rangeMax, Math.max(0, monthlySavings))}
          onChange={(e) => handleSlider(e.target.value)}
          aria-label="Retirement savings per month"
        />
        <input
          type="text"
          value={formatCurrencyInput(monthlySavings)}
          onChange={(e) => onMonthlySavingsChange(formatCurrencyInput(e.target.value))}
          inputMode="numeric"
          aria-label="Retirement savings per month"
        />
      </div>
      <div className="retirement-projection-input-label">Retirement Savings Per Month</div>
    </div>
  );
}

function GoalChart({ goal }) {
  const width = 640;
  const height = 180;
  const history = goal?.history || [];
  const line = chartPath(history, width, height);
  const area = areaPath(history, width, height);
  const first = history[0];
  const latest = history[history.length - 1];

  return (
    <div className="goal-chart-wrap">
      {history.length ? (
        <>
          <svg className="goal-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${goal.name} savings trend`}>
            <defs>
              <linearGradient id={`goalArea${goal.id}`} x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.24" />
                <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
              </linearGradient>
            </defs>
            <path d={area} fill={`url(#goalArea${goal.id})`} />
            <path d={line} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <div className="networth-chart-labels">
            <span>{formatMonth(first?.month)}</span>
            <strong>{formatMoney(latest?.amount)}</strong>
            <span>{formatMonth(latest?.month)}</span>
          </div>
        </>
      ) : (
        <p className="subtle">No trend to chart yet.</p>
      )}
    </div>
  );
}

function ImaginePanel({ goal, imagineMonthly, imaginedEta, onChange }) {
  const imaginedText = imaginedEta?.date
    ? `Goal will be reached in ${formatFullMonthDate(imaginedEta.date)}`
    : 'Goal needs more monthly savings history to project a date';

  return (
    <div className="goal-imagine-panel">
      <div className="goal-imagine-copy">
        <span>Want to reach your goal faster?</span>
        <strong>{formatMoney(imagineMonthly)}/mo</strong>
        <em>{imaginedText}</em>
      </div>
      <div className="goal-imagine-controls">
        <AppRangeSlider
          min="0"
          max="1000"
          step="25"
          hapticStep="25"
          value={imagineMonthly}
          onChange={(e) => onChange(Number(e.target.value))}
          aria-label={`Extra monthly savings for ${goal.name}`}
        />
        <input
          type="text"
          inputMode="numeric"
          value={formatMoney(imagineMonthly)}
          onChange={(e) => onChange(parseMoney(e.target.value))}
          aria-label="Extra monthly savings amount"
        />
      </div>
    </div>
  );
}

function SignalRow({ label, value, detail, compactDetail = null, className = '' }) {
  return (
    <div className={`networth-signal-row ${className}`}>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      {detail && <em data-compact-detail={compactDetail ?? detail}>{detail}</em>}
    </div>
  );
}

function AccountAllocationRow({ account }) {
  const allocatable = Math.max(0, Number(account.allocatable_amount || account.current_balance || 0));
  const basis = allocatable > 0 ? allocatable : 1;

  return (
    <div className="goal-account-row">
      <div className="goal-account-row-top">
        <div>
          <strong>{account.name}</strong>
          <span>
            <span className={`type-pill type-${account.type}`}>{ACCOUNT_TYPE_LABELS[account.type] || account.type}</span>
            {account.institution && <em>{account.institution}</em>}
          </span>
        </div>
        <div className="goal-account-values">
          <strong>{formatMoney(account.allocated_amount)}</strong>
          <span>{formatMoney(account.remaining_amount)} open</span>
        </div>
      </div>
      <div className="goal-account-meter goal-account-meter-chunked" aria-label={`${account.name} allocation`}>
        {(account.allocations || []).map((allocation) => {
          const amount = Math.max(0, Number(allocation.current_amount || 0));
          const width = Math.min(100, Math.max(0, (amount / basis) * 100));
          if (width <= 0) return null;
          return (
            <span
              key={allocation.id}
              style={{
                width: `${width}%`,
                '--goal-color': colorForGoal(allocation.goal_id || allocation.goal_name)
              }}
              title={`${allocation.goal_name}: ${formatMoney(amount)}`}
            />
          );
        })}
      </div>
      <div className="goal-account-row-bottom">
        <span>Balance {formatMoney(account.current_balance)}</span>
        <span>Allocatable {formatMoney(account.allocatable_amount)}</span>
      </div>
      {account.allocations.length > 0 && (
        <div className="goal-account-chips">
          {account.allocations.map((allocation) => (
            <span
              key={allocation.id}
              style={{ '--goal-color': colorForGoal(allocation.goal_id || allocation.goal_name) }}
            >
              {allocation.goal_name}: {formatMoney(allocation.current_amount)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function GoalWizard({ goal, accounts, confirm, onClose, onSaved }) {
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [targetDateMode, setTargetDateMode] = useState(goal?.target_date ? 'date' : 'asap');
  const [draft, setDraft] = useState(() => ({
    name: goal?.name || '',
    kind: goal?.kind || 'custom',
    icon: goal?.icon || presetFor(goal?.kind || 'custom').icon,
    target_amount: goal ? formatCurrencyInput(goal.target_amount) : '',
    target_date: goal?.target_date || '',
    allocations: (goal?.allocations || []).map((allocation) => ({
      account_id: allocation.account_id,
      allocation_type: allocation.allocation_type,
      allocation_value: draftAllocationValue(allocation, accounts, goal?.id),
      reserve_amount: '',
      stealFromOthers: false
    }))
  }));

  const selectedIds = new Set(draft.allocations.map((allocation) => allocation.account_id));
  const selectedAccounts = accounts.filter((account) => selectedIds.has(account.id));
  const title = goal ? 'Edit goal' : 'Create goal';

  function update(key, value) {
    setDraft((prev) => ({ ...prev, [key]: value }));
  }

  function chooseTargetDateMode(mode) {
    setTargetDateMode(mode);
    if (mode === 'asap') update('target_date', '');
  }

  function chooseKind(kind) {
    const preset = presetFor(kind);
    setDraft((prev) => ({
      ...prev,
      kind,
      icon: kind === 'custom' ? prev.icon || preset.icon : preset.icon,
      name: preset.label
    }));
  }

  function toggleAccount(accountId) {
    setDraft((prev) => {
      const exists = prev.allocations.some((allocation) => allocation.account_id === accountId);
      return {
        ...prev,
        allocations: exists
          ? prev.allocations.filter((allocation) => allocation.account_id !== accountId)
          : [
              ...prev.allocations,
              {
                account_id: accountId,
                allocation_type: 'percent',
                allocation_value: '25',
                reserve_amount: '',
                stealFromOthers: false
              }
            ]
      };
    });
  }

  function updateAllocation(accountId, key, value) {
    setDraft((prev) => ({
      ...prev,
      allocations: prev.allocations.map((allocation) =>
        allocation.account_id === accountId ? { ...allocation, [key]: value } : allocation
      )
    }));
  }

  function validateStep(nextStep = step) {
    if (nextStep === 0) {
      if (!draft.name.trim()) return 'Goal name is required.';
      if (parseMoney(draft.target_amount) <= 0) return 'Target amount is required.';
    }
    if (nextStep === 1 && draft.allocations.length === 0) {
      return 'Connect at least one account.';
    }
    if (nextStep === 2) {
      if (draft.allocations.some((allocation) => allocationValueNumber(allocation) <= 0)) {
        return 'Each selected account needs an allocation greater than zero.';
      }
      const overOpenSpace = draft.allocations.some((allocation) => {
        const account = accounts.find((row) => row.id === allocation.account_id);
        if (!account) return true;
        const openAmount = accountOpenAmount(account, goal?.id);
        if (allocation.allocation_type === 'fixed' && allocation.stealFromOthers) {
          return allocationValueNumber(allocation) > allocationBasis(account, 0) + 0.01;
        }
        return openAmount <= 0 || openSpaceAllocationAmount(allocation, openAmount) > openAmount + 0.01;
      });
      if (overOpenSpace) {
        return 'Allocation cannot exceed open account space.';
      }
    }
    return '';
  }

  function goNext() {
    const message = validateStep(step);
    if (message) {
      setError(message);
      return;
    }
    setError('');
    setStep((value) => Math.min(2, value + 1));
  }

  async function save(close) {
    const message = validateStep(2);
    if (message) {
      setError(message);
      return;
    }

    setSaving(true);
    setError('');
    const stealRequests = draft.allocations
      .map((allocation) => {
        const account = accounts.find((row) => row.id === allocation.account_id);
        if (!account || allocation.allocation_type !== 'fixed' || !allocation.stealFromOthers) return null;
        const requested = allocationValueNumber(allocation);
        const openAmount = accountOpenAmount(account, goal?.id);
        if (requested <= openAmount + 0.01) return null;
        return { account, requested, openAmount };
      })
      .filter(Boolean);
    if (stealRequests.length > 0) {
      const ok = await confirm(
        `This fixed allocation will take space from existing goal percentages on ${stealRequests.map((row) => row.account.name).join(', ')}. Continue?`,
        {
          title: 'Steal allocation?',
          confirmLabel: 'Steal allocation'
        }
      );
      if (!ok) {
        setSaving(false);
        return;
      }
    }

    const payload = {
      name: draft.name.trim(),
      kind: draft.kind,
      icon: draft.icon,
      target_amount: parseMoney(draft.target_amount),
      target_date: draft.target_date || null,
      stealFromOthers: stealRequests.length > 0,
      allocations: draft.allocations.map((allocation) =>
        toSavedAllocation(
          allocation,
          accounts.find((account) => account.id === allocation.account_id),
          goal?.id
        )
      )
    };

    try {
      const result = goal
        ? await api.put(`/api/goals/${goal.id}`, payload)
        : await api.post('/api/goals', payload);
      close({ animation: 'zoom' });
      setTimeout(() => onSaved(result.id), 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg" animation="zoom">
      {({ close }) => (
        <div className="goal-wizard">
          <h3>{title}</h3>
          <div className="goal-wizard-steps" aria-label="Goal setup progress">
            {['Purpose', 'Accounts', 'Allocation'].map((label, index) => (
              <button
                type="button"
                key={label}
                className={step === index ? 'active' : ''}
                onClick={() => {
                  if (index < step || !validateStep(step)) {
                    setError('');
                    setStep(index);
                  }
                }}
              >
                {label}
              </button>
            ))}
          </div>

          {step === 0 && (
            <div className="goal-wizard-panel">
              <div className="goal-preset-grid">
                {GOAL_PRESETS.map((preset) => (
                  <button
                    type="button"
                    key={preset.kind}
                    className={draft.kind === preset.kind ? 'active' : ''}
                    onClick={() => chooseKind(preset.kind)}
                  >
                    <span>{preset.icon}</span>
                    <strong>{preset.label}</strong>
                  </button>
                ))}
              </div>
              {draft.kind === 'custom' && (
                <label className="field goal-emoji-field">
                  <span>Goal emoji</span>
                  <input
                    value={draft.icon}
                    onChange={(e) => update('icon', e.target.value.slice(0, 16))}
                    placeholder="Choose an emoji"
                    aria-label="Goal emoji"
                  />
                </label>
              )}
              <label className="field">
                <span>What are you saving for?</span>
                <input value={draft.name} onChange={(e) => update('name', e.target.value)} placeholder="New car" />
              </label>
              <div className="goal-form-grid">
                <label className="field">
                  <span>Target amount</span>
                  <CurrencyInput
                    value={draft.target_amount}
                    onChange={(value) => update('target_amount', value)}
                    placeholder="$25,000"
                  />
                </label>
                <label className="field">
                  <span>Target date</span>
                  <div className="goal-date-choice" role="group" aria-label="Target date choice">
                    <button
                      type="button"
                      className={targetDateMode === 'asap' ? 'active' : ''}
                      onClick={() => chooseTargetDateMode('asap')}
                    >
                      ASAP
                    </button>
                    <button
                      type="button"
                      className={targetDateMode === 'date' ? 'active' : ''}
                      onClick={() => chooseTargetDateMode('date')}
                    >
                      Date
                    </button>
                  </div>
                  {targetDateMode === 'date' && (
                    <input type="date" value={draft.target_date} onChange={(e) => update('target_date', e.target.value)} />
                  )}
                </label>
              </div>
            </div>
          )}

          {step === 1 && (
            <div className="goal-wizard-panel">
              <div className="goal-account-picker">
                {accounts.map((account) => {
                  const otherAllocated = account.allocations
                    .filter((allocation) => allocation.goal_id !== goal?.id)
                    .reduce((sum, allocation) => sum + Number(allocation.current_amount || 0), 0);
                  return (
                    <button
                      type="button"
                      key={account.id}
                      className={selectedIds.has(account.id) ? 'active' : ''}
                      aria-pressed={selectedIds.has(account.id)}
                      onClick={() => toggleAccount(account.id)}
                    >
                      <span className="goal-picker-check" aria-hidden />
                      <span className="goal-picker-main">
                        <strong>{account.name}</strong>
                        <em>{ACCOUNT_TYPE_LABELS[account.type] || account.type} | {formatMoney(account.current_balance)}</em>
                      </span>
                      <span className="goal-picker-side">
                        <strong>{formatMoney(account.remaining_amount)} open</strong>
                        <em>{formatMoney(otherAllocated)} allocated</em>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="goal-wizard-panel">
              <div className="goal-allocation-editor">
                {selectedAccounts.map((account) => {
                  const allocation = draft.allocations.find((row) => row.account_id === account.id);
                  return (
                    <AllocationEditor
                      key={account.id}
                      account={account}
                      allocation={allocation}
                      goalId={goal?.id}
                      onChange={(key, value) => updateAllocation(account.id, key, value)}
                    />
                  );
                })}
              </div>
            </div>
          )}

          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={step === 0 ? close : () => setStep((value) => value - 1)}>
              {step === 0 ? 'Cancel' : 'Back'}
            </button>
            {step < 2 ? (
              <button type="button" className="btn-primary" onClick={goNext}>
                Next
              </button>
            ) : (
              <button type="button" className="btn-primary" onClick={() => save(close)} disabled={saving}>
                {saving ? 'Saving...' : goal ? 'Save goal' : 'Create goal'}
              </button>
            )}
          </div>
        </div>
      )}
    </AnimatedModal>
  );
}

function allocationValueNumber(allocation) {
  if (allocation.allocation_type === 'fixed') return parseMoney(allocation.allocation_value);
  const parsed = Number(String(allocation.allocation_value || '').replace(/[^0-9.]/g, ''));
  return Number.isFinite(parsed) ? parsed : 0;
}

function AllocationEditor({ account, allocation, goalId, onChange }) {
  const otherAllocations = account.allocations.filter((row) => row.goal_id !== goalId);
  const otherAmount = otherAllocations.reduce((sum, row) => sum + Number(row.current_amount || 0), 0);
  const openAmount = accountOpenAmount(account, goalId);
  const basis = allocationBasis(account, 0);
  const fixedCanSteal = allocation.allocation_type === 'fixed' && allocation.stealFromOthers;
  const maxAmount = fixedCanSteal ? basis : openAmount;
  const currentAmount = fixedCanSteal
    ? Math.min(Math.max(0, allocationValueNumber(allocation)), basis)
    : openSpaceAllocationAmount(allocation, openAmount);

  function changeType(type) {
    const nextValue = type === 'fixed'
      ? formatCurrencyInput(currentAmount)
      : openAmount > 0
        ? String(Math.round((currentAmount / openAmount) * 100))
        : '0';
    onChange('allocation_type', type);
    onChange('allocation_value', nextValue);
    if (type === 'percent') onChange('stealFromOthers', false);
  }

  function changeValue(value) {
    if (allocation.allocation_type === 'fixed') {
      onChange('allocation_value', formatCurrencyInput(Math.min(maxAmount, parseMoney(value))));
      return;
    }
    const parsed = Number(String(value).replace(/[^0-9.]/g, '')) || 0;
    onChange('allocation_value', String(Math.min(100, Math.max(0, parsed))));
  }

  return (
    <div className="goal-allocation-row">
      <div className="goal-allocation-header">
        <div>
          <strong>{account.name}</strong>
          <span>{formatMoney(account.current_balance)} balance | {formatMoney(otherAmount)} allocated | {formatMoney(openAmount)} open</span>
        </div>
        <div className="goal-allocation-mode" role="group" aria-label="Allocation type">
          <button
            type="button"
            className={allocation.allocation_type === 'percent' ? 'active' : ''}
            onClick={() => changeType('percent')}
          >
            Percent
          </button>
          <button
            type="button"
            className={allocation.allocation_type === 'fixed' ? 'active' : ''}
            onClick={() => changeType('fixed')}
          >
            Fixed
          </button>
        </div>
      </div>

      <div className="goal-form-grid goal-form-grid-single">
        <label className="field">
          <span>{allocation.allocation_type === 'fixed' ? 'Amount Allocated Towards Goal' : 'Percent of Account Allocated Towards Goal'}</span>
          {allocation.allocation_type === 'fixed' ? (
            <CurrencyInput
              value={allocation.allocation_value}
              onChange={changeValue}
              placeholder="$500"
            />
          ) : (
            <input
              type="text"
              inputMode="decimal"
              value={allocation.allocation_value}
              onChange={(e) => changeValue(e.target.value)}
              placeholder="25"
            />
          )}
        </label>
      </div>

      <AllocationMeter
        allocation={allocation}
        maxAmount={maxAmount}
        currentAmount={currentAmount}
        otherAmount={otherAmount}
        fixedCanSteal={fixedCanSteal}
        onChange={onChange}
      />

      {allocation.allocation_type === 'fixed' && openAmount < basis - 0.01 && (
        <label className="goal-fixed-steal-row">
          <input
            type="checkbox"
            checked={!!allocation.stealFromOthers}
            onChange={(e) => {
              onChange('stealFromOthers', e.target.checked);
              if (!e.target.checked) {
                onChange('allocation_value', formatCurrencyInput(Math.min(openAmount, allocationValueNumber(allocation))));
              }
            }}
          />
          <span>Allow this fixed amount to reduce other goal percentages</span>
        </label>
      )}

      {otherAllocations.length > 0 && (
        <div className="goal-existing-allocations">
          {otherAllocations.map((row) => (
            <span key={row.id}>
              {row.goal_name}: {formatMoney(row.current_amount)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function AllocationMeter({ allocation, maxAmount, currentAmount, otherAmount, fixedCanSteal, onChange }) {
  const rangeValue =
    allocation.allocation_type === 'fixed'
      ? Math.min(maxAmount, allocationValueNumber(allocation))
      : allocationValueNumber(allocation);

  function handleRange(value) {
    if (allocation.allocation_type === 'fixed') {
      onChange('allocation_value', formatCurrencyInput(value));
    } else {
      onChange('allocation_value', String(value));
    }
  }

  return (
    <div className="goal-meter-wrap">
      <AppRangeSlider
        min="0"
        max={allocation.allocation_type === 'fixed' ? Math.max(0, Math.round(maxAmount)) : 100}
        step={allocation.allocation_type === 'fixed' ? 50 : 1}
        hapticStep={allocation.allocation_type === 'fixed' ? 100 : 5}
        value={rangeValue}
        onChange={(e) => handleRange(Number(e.target.value))}
        aria-label="Goal allocation meter"
      />
      <div className="goal-meter-labels">
        <span>Already allocated {formatMoney(otherAmount)}</span>
        <span>This goal {formatMoney(currentAmount)}</span>
        <span>{fixedCanSteal ? 'Pie' : 'Open'} {formatMoney(maxAmount)}</span>
      </div>
    </div>
  );
}
