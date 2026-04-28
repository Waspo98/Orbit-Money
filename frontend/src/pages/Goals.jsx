import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DndContext,
  closestCenter
} from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
  arrayMove
} from '@dnd-kit/sortable';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppRangeSlider from '../components/AppRangeSlider.jsx';
import PageHero from '../components/PageHero.jsx';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import {
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import { addMonthsToLocalDate } from '../lib/localDate.js';

const GOAL_PRESETS = [
  { kind: 'college', label: 'Kids College', icon: '??' },
  { kind: 'car', label: 'New Car', icon: '??' },
  { kind: 'home', label: 'Home', icon: '??' },
  { kind: 'emergency', label: 'Emergency Fund', icon: '??' },
  { kind: 'travel', label: 'Travel', icon: '??' },
  { kind: 'custom', label: 'Something Else', icon: '?' }
];

const ACCOUNT_TYPE_LABELS = {
  checking: 'Checking',
  savings: 'Savings',
  investment: 'Investment',
  cash: 'Cash',
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
  const [activeDragId, setActiveDragId] = useState(null);
  const [overDragId, setOverDragId] = useState(null);
  const sensors = useReorderSensors();

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

  useDragInteractionLock(Boolean(activeDragId));

  const goals = data?.goals || [];
  const accounts = data?.accounts || [];
  const selectedGoal = goals.find((goal) => goal.id === selectedId) || goals[0] || null;
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

  function resetDragState() {
    setActiveDragId(null);
    setOverDragId(null);
  }

  function handleGoalDragStart(event) {
    setActiveDragId(event.active?.id ?? null);
    setOverDragId(null);
  }

  function handleGoalDragOver(event) {
    setOverDragId(event.over?.id ?? null);
  }

  async function handleGoalDragEnd(event) {
    const { active, over } = event;
    resetDragState();
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

  function toggleReorderMode() {
    setReorderMode((value) => {
      if (value) resetDragState();
      return !value;
    });
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
    <div className={`goals-view ${activeDragId ? 'dragging-active' : ''}`}>
      <PageHero
        id="goals-title"
        variant="goals"
        kicker="Saving Targets"
        title="Savings Goals"
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
          <h2>No savings goals yet</h2>
          <p>Create a saving target and connect the accounts that should count toward it.</p>
          <button type="button" className="btn-primary" onClick={openNewGoal}>+ New Goal</button>
        </div>
      ) : (
        <div className="goals-grid">
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

          <section className="dashboard-card goals-list-card">
            <header className="dashboard-card-header">
              <h3>Savings Goals</h3>
              {goals.length > 1 && (
                <button
                  type="button"
                  className="dashboard-card-link button-link"
                  onClick={toggleReorderMode}
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
                  onDragStart={handleGoalDragStart}
                  onDragOver={handleGoalDragOver}
                  onDragEnd={handleGoalDragEnd}
                  onDragCancel={resetDragState}
                >
                  <SortableContext
                    items={goals.map((goal) => goal.id)}
                    strategy={verticalListSortingStrategy}
                  >
                    <div className="goal-list reorder-active reorder-drag-scope">
                      {goals.map((goal) => (
                        <DraggableGoalRow
                          key={goal.id}
                          goal={goal}
                          previewDisplaced={goal.id === overDragId && goal.id !== activeDragId}
                        />
                      ))}
                    </div>
                  </SortableContext>
                  {activeDragId && <div className="drag-screen-blocker" aria-hidden="true" />}
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
          onDeleted={async () => {
            setWizardGoal(null);
            await load();
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

function DraggableGoalRow({ goal, previewDisplaced = false }) {
  return (
    <ReorderListItem
      id={goal.id}
      className="goal-list-row"
      handleLabel={`Reorder ${goal.name}`}
      previewDisplaced={previewDisplaced}
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
            <path d={area} fill={`url(#goalArea${goal.id})`}>
              <title>{`${goal.name}: ${formatMoney(latest?.amount || 0)} saved`}</title>
            </path>
            <path d={line} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
              <title>{`${goal.name} trend, latest ${formatMoney(latest?.amount || 0)} in ${formatMonth(latest?.month)}`}</title>
            </path>
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

function GoalWizard({ goal, accounts, confirm, onClose, onSaved, onDeleted }) {
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

  async function deleteGoal(close) {
    if (!goal) return;
    const ok = await confirm(`Delete "${goal.name}"? Its account allocations will be removed.`, {
      title: 'Delete goal',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;

    setSaving(true);
    setError('');
    try {
      await api.del(`/api/goals/${goal.id}`);
      close({ animation: 'zoom' });
      setTimeout(() => onDeleted(), 180);
    } catch (err) {
      setError(err.message || 'Delete failed');
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

          <div className={`modal-actions goal-wizard-actions ${goal ? 'has-danger' : ''}`}>
            {goal && (
              <button type="button" className="btn-danger" onClick={() => deleteGoal(close)} disabled={saving}>
                Delete Goal
              </button>
            )}
            <div className="goal-wizard-primary-actions">
              <button type="button" className="btn-secondary" onClick={step === 0 ? close : () => setStep((value) => value - 1)} disabled={saving}>
                {step === 0 ? 'Cancel' : 'Back'}
              </button>
              {step < 2 ? (
                <button type="button" className="btn-primary" onClick={goNext} disabled={saving}>
                  Next
                </button>
              ) : (
                <button type="button" className="btn-primary" onClick={() => save(close)} disabled={saving}>
                  {saving ? 'Saving...' : goal ? 'Save goal' : 'Create goal'}
                </button>
              )}
            </div>
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
