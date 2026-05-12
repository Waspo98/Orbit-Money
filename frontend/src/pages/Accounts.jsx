import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
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
import DropdownMenu from '../components/DropdownMenu.jsx';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppSelect from '../components/AppSelect.jsx';
import CollapseIndicator from '../components/CollapseIndicator.jsx';
import DashboardCard from '../components/dashboard/DashboardCard.jsx';
import DateInput from '../components/DateInput.jsx';
import EmptyState from '../components/EmptyState.jsx';
import ExpandingSection from '../components/ExpandingSection.jsx';
import { ImageUrlFinderPanel } from '../components/ImageUrlFinderModal.jsx';
import PageActionRow from '../components/PageActionRow.jsx';
import PageHero from '../components/PageHero.jsx';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import CurrencyInput, { formatCurrencyInput, parseCurrencyInput } from '../components/CurrencyInput.jsx';
import {
  formatCurrency as formatUsd,
  formatPercent
} from '../lib/formatters.js';
import {
  formatMonthDay,
  todayLocalDate
} from '../lib/localDate.js';

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

const TYPE_OPTIONS = Object.entries(TYPE_LABELS).map(([value, label]) => ({
  value,
  label
}));

function formatCurrency(amount) {
  return amount === null || amount === undefined ? '—' : formatUsd(amount);
}

function parseOptionalCurrency(value) {
  if (String(value ?? '').replace(/[$,\s]/g, '').trim() === '') return null;
  const parsed = parseCurrencyInput(value, NaN);
  return Number.isFinite(parsed) ? parsed : NaN;
}

function formatRewardRateLabel(item) {
  if (!item) return '';
  if (typeof item === 'string') return item;
  const label = item.label || item.category || 'Other';
  const rate = Number(item.rate);
  if (!Number.isFinite(rate)) return label;
  const type = String(item.type || '').toLowerCase();
  if (type.includes('cashback') || type.includes('percent')) return `${label} ${rate}%`;
  return `${label} ${rate}x`;
}

function splitCommaList(value) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function splitLineList(value) {
  return String(value || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function joinProfileList(value, formatter = (item) => item) {
  if (!Array.isArray(value)) return '';
  return value.map(formatter).filter(Boolean).join(', ');
}

function formatPercentChange(next, previous) {
  if (!Number.isFinite(previous) || previous === 0) return null;
  const change = ((next - previous) / Math.abs(previous)) * 100;
  return `${formatPercent(Math.abs(change))} ${change >= 0 ? 'more' : 'less'}`;
}

function pluralTypeLabel(type, count) {
  const label = TYPE_LABELS[type] || type;
  if (type === 'cash') return 'Cash';
  if (type === 'checking') return 'Checking';
  if (type === 'savings') return 'Savings';
  if (type === 'credit') return count === 1 ? 'Credit Card' : 'Credit Cards';
  return `${label}${count === 1 ? '' : 's'}`;
}

function buildAccountGroups(accounts) {
  const groupsByType = new Map();
  const orderedTypes = [];

  for (const account of accounts) {
    const type = account.type || 'other';
    if (!groupsByType.has(type)) {
      groupsByType.set(type, []);
      orderedTypes.push(type);
    }
    groupsByType.get(type).push(account);
  }

  return orderedTypes.map((type) => {
    const items = groupsByType.get(type) || [];
    const total = items.reduce(
      (sum, account) => sum + (Number(account.current_balance) || 0),
      0
    );
    return { type, items, total };
  });
}

function normalizeAccountGroupLayoutPreference(saved, groups) {
  const savedByType = new Map();
  if (Array.isArray(saved)) {
    for (const item of saved) {
      const type = String(item?.type || '').trim();
      if (!type || savedByType.has(type)) continue;
      savedByType.set(type, {
        type,
        wideSpan: Number(item?.wideSpan) === 2 ? 2 : 1
      });
    }
  }

  return groups.map((group) => ({
    type: group.type,
    wideSpan: savedByType.get(group.type)?.wideSpan || 1
  }));
}

function getAccountGroupStandardPlacementByType(layout) {
  const placements = new Map();
  let filledColumns = 0;

  for (const item of layout) {
    const standardPlacement = filledColumns === 0 ? 'left' : 'right';
    placements.set(item.type, standardPlacement);

    if (item.wideSpan === 2) {
      filledColumns = 0;
    } else {
      filledColumns = standardPlacement === 'left' ? 1 : 0;
    }
  }

  return placements;
}

function flattenGroups(groups) {
  return groups.flatMap((group) => group.items);
}

function accountSortableId(accountId) {
  return `account-${accountId}`;
}

function groupSortableId(type) {
  return `group-${type}`;
}

function accountIdFromSortable(id) {
  const text = String(id || '');
  return text.startsWith('account-') ? Number(text.slice('account-'.length)) : null;
}

function groupTypeFromSortable(id) {
  const text = String(id || '');
  return text.startsWith('group-') ? text.slice('group-'.length) : null;
}

function accountNetWorthContribution(account) {
  const balance = Number(account.current_balance) || 0;
  if (account.type === 'mortgage') {
    const estimatedValue = Number(account.estimated_value) || 0;
    return estimatedValue - Math.abs(balance);
  }
  return balance;
}

function findAccountGroup(groups, accountId) {
  return groups.find((group) => group.items.some((account) => account.id === accountId));
}

// ============================================================================
// Main page
// ============================================================================

export default function Accounts({
  onChange,
  accountGroupLayoutPreference = [],
  onAccountGroupLayoutPreferenceChange
}) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { alert, confirm, Dialog } = useAppDialog();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [editing, setEditing] = useState(null);
  const [merging, setMerging] = useState(null);
  const [recording, setRecording] = useState(null);
  const [cardDetailsAccount, setCardDetailsAccount] = useState(null);
  const [addingManual, setAddingManual] = useState(false);
  const [collapsedGroups, setCollapsedGroups] = useState(() => new Set(Object.keys(TYPE_LABELS)));
  const [activeDragId, setActiveDragId] = useState(null);
  const [overDragId, setOverDragId] = useState(null);
  const [accountReorderGroupType, setAccountReorderGroupType] = useState(null);

  // Reorder mode: when true, rows become drag handles and ALL other
  // interactions (expand, action buttons, dropdown, See transactions) are
  // hidden. The only thing you can do is drag-and-drop. Entered via the
  // Reorder button, exited via Done.
  const [reorderMode, setReorderMode] = useState(false);

  const sensors = useReorderSensors();

  async function load() {
    setLoading(true);
    setError('');
    try {
      const data = await api.get(
        `/api/accounts?includeArchived=${showArchived ? '1' : '0'}`
      );
      setAccounts(data.items);
    } catch (err) {
      setError(err.message || 'Failed to load accounts');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showArchived]);

  useEffect(() => {
    if (loading) return;
    if (searchParams.get('action') !== 'add-snapshot') return;
    const accountId = Number(searchParams.get('accountId'));
    const account = accounts.find((item) => item.id === accountId);
    if (!account) return;
    setRecording(account);
    const next = new URLSearchParams(searchParams);
    next.delete('action');
    next.delete('accountId');
    setSearchParams(next, { replace: true });
  }, [accounts, loading, searchParams, setSearchParams]);

  useDragInteractionLock(Boolean(activeDragId));

  // Exiting reorder mode: nothing to save explicitly — each drop has
  // already persisted. But if the user archived/edited before entering
  // reorder mode, we want to make sure we're synced.
  function toggleReorderMode() {
    setReorderMode((value) => {
      if (value) resetDragState();
      if (!value) setAccountReorderGroupType(null);
      return !value;
    });
  }

  function toggleAccountReorderGroup(type) {
    resetDragState();
    setReorderMode(false);
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      next.delete(type);
      return next;
    });
    setAccountReorderGroupType((current) => (current === type ? null : type));
  }

  function toggleAccountGroup(type) {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(type)) {
        next.delete(type);
      } else {
        next.add(type);
      }
      return next;
    });
  }

  async function handleArchive(id, currentlyArchived) {
    const endpoint = currentlyArchived ? 'unarchive' : 'archive';
    try {
      await api.post(`/api/accounts/${id}/${endpoint}`);
      await load();
      onChange?.();
    } catch (err) {
      alert(err.message || 'Action failed', { title: 'Account action failed' });
    }
  }

  async function handleDelete(account) {
    if (account.transaction_count > 0) {
      alert(
        `This account has ${account.transaction_count.toLocaleString()} transactions. Merge it into another account first.`,
        { title: 'Cannot delete account' }
      );
      return;
    }
    const ok = await confirm(`Delete "${account.name}"? This can't be undone.`, {
      title: 'Delete account',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/accounts/${account.id}`);
      await load();
      onChange?.();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  function viewTransactions(accountId) {
    navigate(`/transactions?account_id=${accountId}`);
  }

  function manageSnapshotReminder(accountId) {
    navigate(`/settings/preferences?notificationPanel=snapshot-reminders&accountId=${accountId}`);
  }

  const activeAccounts = accounts.filter((account) => !account.is_archived);
  const accountGroups = useMemo(() => buildAccountGroups(accounts), [accounts]);
  const accountGroupLayout = useMemo(
    () => normalizeAccountGroupLayoutPreference(accountGroupLayoutPreference, accountGroups),
    [accountGroupLayoutPreference, accountGroups]
  );
  const accountGroupLayoutByType = useMemo(
    () => new Map(accountGroupLayout.map((item) => [item.type, item])),
    [accountGroupLayout]
  );
  const accountGroupStandardPlacementByType = useMemo(
    () => getAccountGroupStandardPlacementByType(accountGroupLayout),
    [accountGroupLayout]
  );
  const cashBalance = activeAccounts
    .filter((account) => account.type === 'checking' || account.type === 'savings')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
  const creditCardsDue = activeAccounts
    .filter((account) => account.type === 'credit')
    .reduce((total, account) => total + Math.abs(Number(account.current_balance) || 0), 0);
  const investmentsBalance = activeAccounts
    .filter((account) => account.type === 'investment')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
  const netWorth = activeAccounts.reduce(
    (total, account) => total + accountNetWorthContribution(account),
    0
  );
  const accountCountLabel = `${activeAccounts.length.toLocaleString()} ${
    activeAccounts.length === 1 ? 'Account' : 'Accounts'
  }`;
  const accountHeroStats = [
    {
      label: 'Cash',
      value: loading ? '—' : formatCurrency(cashBalance),
      tone: cashBalance >= 0 ? 'good' : 'caution'
    },
    {
      label: 'Credit Cards',
      value: loading ? '—' : formatCurrency(creditCardsDue),
      tone: creditCardsDue > 0 ? 'caution' : 'good'
    },
    {
      label: 'Investments',
      value: loading ? '—' : formatCurrency(investmentsBalance),
      tone: investmentsBalance >= 0 ? 'good' : 'caution'
    },
    {
      label: 'Total Accounts',
      value: activeAccounts.length.toLocaleString()
    }
  ];

  function resetDragState() {
    setActiveDragId(null);
    setOverDragId(null);
  }

  function updateAccountGroupWideSpan(type, wideSpan) {
    const next = accountGroupLayout.map((item) =>
      item.type === type ? { ...item, wideSpan } : item
    );
    onAccountGroupLayoutPreferenceChange?.(next);
  }

  function handleGroupDragStart(event) {
    setActiveDragId(event.active?.id ?? null);
    setOverDragId(null);
  }

  function handleGroupDragOver(event) {
    setOverDragId(event.over?.id ?? null);
  }

  async function handleGroupDragEnd(event) {
    const { active, over } = event;
    resetDragState();
    if (!over || active.id === over.id) return;

    const activeGroupType = groupTypeFromSortable(active.id);
    const overGroupType = groupTypeFromSortable(over.id);
    if (!activeGroupType || !overGroupType) return;

    const oldIndex = accountGroups.findIndex((group) => group.type === activeGroupType);
    const newIndex = accountGroups.findIndex((group) => group.type === overGroupType);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = flattenGroups(arrayMove(accountGroups, oldIndex, newIndex));

    setAccounts(reordered);

    try {
      await api.post('/api/accounts/reorder', {
        orderedIds: reordered.map((a) => a.id)
      });
      onChange?.();
    } catch (err) {
      setError(err.message || 'Reorder failed; reloading…');
      load();
    }
  }

  return (
    <div className={`accounts-view app-page-width ${reorderMode ? 'reorder-mode' : ''} ${activeDragId ? 'dragging-active' : ''}`}>
      <PageHero
        id="accounts-title"
        variant="accounts"
        kicker="Your Money's Home Base"
        title="Accounts"
        subtitle={
          loading
            ? 'Loading accounts...'
            : `${formatCurrency(netWorth)} Net Worth across ${accountCountLabel}`
        }
        stats={accountHeroStats}
      />

      <PageActionRow className="accounts-toolbar accounts-page-toolbar" label="Account actions">
        {!reorderMode && (
          <label className="toggle-row">
            <input
              type="checkbox"
              checked={showArchived}
              onChange={(e) => setShowArchived(e.target.checked)}
            />
            <span>Show archived</span>
          </label>
        )}
        {accounts.length > 1 && (
          <button
            type="button"
            className={reorderMode ? 'btn-primary' : 'btn-secondary'}
            disabled={Boolean(accountReorderGroupType)}
            onClick={toggleReorderMode}
          >
            {reorderMode ? 'Done' : 'Reorder Groups'}
          </button>
        )}
        {!reorderMode && (
          <button
            type="button"
            className="btn-primary"
            onClick={() => setAddingManual(true)}
          >
            + Manual Account
          </button>
        )}
      </PageActionRow>

      <div className="view-header" hidden>
        <div>
          <h2>Accounts</h2>
          <p className="muted">
            {accounts.length.toLocaleString()} account
            {accounts.length === 1 ? '' : 's'}
            {showArchived ? ' (including archived)' : ''}
            {reorderMode && (
              <span className="subtle"> · Drag a row to reorder</span>
            )}
          </p>
        </div>

        <div className="accounts-toolbar">
          {!reorderMode && (
            <label className="toggle-row">
              <input
                type="checkbox"
                checked={showArchived}
                onChange={(e) => setShowArchived(e.target.checked)}
              />
              <span>Show archived</span>
            </label>
          )}
          {accounts.length > 1 && (
            <button
              type="button"
              className={reorderMode ? 'btn-primary' : 'btn-secondary'}
              onClick={toggleReorderMode}
            >
              {reorderMode ? 'Done' : 'Reorder'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : accounts.length === 0 ? (
        <EmptyState
          icon="$"
          title="No accounts yet"
          description="Import from Rocket Money, connect SimpleFIN, or add a manual account."
          action={(
            <button type="button" className="btn-secondary" onClick={() => setAddingManual(true)}>
              Add Manual Account
            </button>
          )}
        />
      ) : reorderMode ? (
        // Group reorder mode intentionally mirrors the simpler Goals list.
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleGroupDragStart}
          onDragOver={handleGroupDragOver}
          onDragEnd={handleGroupDragEnd}
          onDragCancel={resetDragState}
        >
          <SortableContext
            items={accountGroups.map((group) => groupSortableId(group.type))}
            strategy={verticalListSortingStrategy}
          >
            <div className="reorder-list reorder-active reorder-drag-scope">
              {accountGroups.map((group) => (
                <DraggableAccountGroupRow
                  key={group.type}
                  group={group}
                  wideSpan={accountGroupLayoutByType.get(group.type)?.wideSpan || 1}
                  standardPlacement={accountGroupStandardPlacementByType.get(group.type) || 'left'}
                  onWideSpanChange={(wideSpan) => updateAccountGroupWideSpan(group.type, wideSpan)}
                  previewDisplaced={
                    groupSortableId(group.type) === overDragId &&
                    groupSortableId(group.type) !== activeDragId
                  }
                />
              ))}
            </div>
          </SortableContext>
          {activeDragId && <div className="drag-screen-blocker" aria-hidden="true" />}
        </DndContext>
      ) : (
        // Normal mode: grouped cards with all account actions available.
        <div className="account-group-grid">
          {accountGroups.map((group) => (
            <AccountGroup
              key={group.type}
              group={group}
              wideSpan={accountGroupLayoutByType.get(group.type)?.wideSpan || 1}
              collapsed={collapsedGroups.has(group.type)}
              disabled={accountReorderGroupType === group.type}
              onToggle={() => toggleAccountGroup(group.type)}
            >
              <div className="account-group-tools">
                {group.items.length > 1 && (
                  <button
                    type="button"
                    className={accountReorderGroupType === group.type ? 'btn-primary btn-compact' : 'btn-secondary btn-compact'}
                    onClick={() => toggleAccountReorderGroup(group.type)}
                  >
                    {accountReorderGroupType === group.type ? 'Done' : 'Reorder Accounts'}
                  </button>
                )}
              </div>
              {accountReorderGroupType === group.type ? (
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragStart={(event) => {
                    setActiveDragId(event.active?.id ?? null);
                    setOverDragId(null);
                  }}
                  onDragOver={(event) => {
                    setOverDragId(event.over?.id ?? null);
                  }}
                  onDragEnd={async (event) => {
                    const { active, over } = event;
                    resetDragState();
                    if (!over || active.id === over.id) return;

                    const activeAccountId = accountIdFromSortable(active.id);
                    const overAccountId = accountIdFromSortable(over.id);
                    if (!activeAccountId || !overAccountId) return;

                    const sourceGroup = accountGroups.find((candidate) => candidate.type === group.type);
                    if (!sourceGroup) return;

                    const oldIndex = sourceGroup.items.findIndex((account) => account.id === activeAccountId);
                    const newIndex = sourceGroup.items.findIndex((account) => account.id === overAccountId);
                    if (oldIndex === -1 || newIndex === -1) return;

                    const nextGroups = accountGroups.map((candidate) =>
                      candidate.type === sourceGroup.type
                        ? { ...candidate, items: arrayMove(candidate.items, oldIndex, newIndex) }
                        : candidate
                    );
                    const reordered = flattenGroups(nextGroups);

                    setAccounts(reordered);

                    try {
                      await api.post('/api/accounts/reorder', {
                        orderedIds: reordered.map((account) => account.id)
                      });
                      onChange?.();
                    } catch (err) {
                      setError(err.message || 'Reorder failed; reloading...');
                      load();
                    }
                  }}
                  onDragCancel={resetDragState}
                >
                  <SortableContext
                    items={group.items.map((account) => accountSortableId(account.id))}
                    strategy={verticalListSortingStrategy}
                  >
                    <ul className="account-list reorder-active reorder-drag-scope">
                      {group.items.map((a) => (
                        <DraggableReorderRow
                          key={a.id}
                          account={a}
                          previewDisplaced={
                            accountSortableId(a.id) === overDragId &&
                            accountSortableId(a.id) !== activeDragId
                          }
                        />
                      ))}
                    </ul>
                  </SortableContext>
                  {activeDragId && <div className="drag-screen-blocker" aria-hidden="true" />}
                </DndContext>
              ) : (
                <ul className="account-list account-group-list">
                  {group.items.map((a) => (
                    <StaticAccountRow
                      key={a.id}
                      account={a}
                      allCount={accounts.length}
                      onAddRecord={() => setRecording(a)}
                      onEdit={() => setEditing(a)}
                      onMerge={() => setMerging(a)}
                      onArchive={() => handleArchive(a.id, !!a.is_archived)}
                      onDelete={() => handleDelete(a)}
                      onViewTransactions={() => viewTransactions(a.id)}
                      onManageSnapshotReminder={() => manageSnapshotReminder(a.id)}
                      onCardDetails={a.type === 'credit' ? () => setCardDetailsAccount(a) : null}
                    />
                  ))}
                </ul>
              )}
            </AccountGroup>
          ))}
        </div>
      )}

      {editing && (
        <EditAccountModal
          account={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
            onChange?.();
          }}
        />
      )}

      {merging && (
        <MergeAccountModal
          source={merging}
          candidates={accounts.filter(
            (a) => a.id !== merging.id && !a.is_archived
          )}
          onClose={() => setMerging(null)}
          onMerged={() => {
            setMerging(null);
            load();
            onChange?.();
          }}
        />
      )}

      {addingManual && (
        <ManualAccountModal
          onClose={() => setAddingManual(false)}
          onCreated={(account) => {
            setAddingManual(false);
            setRecording(account);
            load();
            onChange?.();
          }}
        />
      )}

      {recording && (
        <AccountRecordModal
          account={recording}
          confirm={confirm}
          onClose={() => setRecording(null)}
          onSaved={() => {
            setRecording(null);
            load();
            onChange?.();
          }}
        />
      )}

      {cardDetailsAccount && (
        <CreditCardDetailsModal
          account={cardDetailsAccount}
          onClose={() => setCardDetailsAccount(null)}
          onSaved={() => {
            setCardDetailsAccount(null);
            load();
            onChange?.();
          }}
        />
      )}

      <Dialog />
    </div>
  );
}

// ============================================================================
// Row used in reorder mode — entire row is a drag handle, no buttons
// ============================================================================

function AccountGroup({ group, children, collapsed, disabled = false, wideSpan = 1, onToggle }) {
  const totalTone = group.total < 0 ? 'negative' : 'positive';

  return (
    <DashboardCard
      title={pluralTypeLabel(group.type, group.items.length)}
      subtitle={(
        <span className="muted">
          {group.items.length.toLocaleString()} {group.items.length === 1 ? 'account' : 'accounts'}
        </span>
      )}
      className={`account-group-card ${wideSpan === 2 ? 'account-group-span-wide' : ''} ${collapsed ? 'collapsed' : ''}`}
      headerAs="button"
      headerClassName="account-group-header account-group-toggle"
      headerProps={{
        type: 'button',
        'aria-expanded': !collapsed,
        disabled,
        onClick: onToggle
      }}
      action={(
        <span className="account-group-header-side">
          <strong className={`account-group-total ${totalTone}`}>{formatCurrency(group.total)}</strong>
          <CollapseIndicator expanded={!collapsed} className="account-group-caret" />
        </span>
      )}
      body={false}
    >
      <ExpandingSection
        expanded={!collapsed}
        className="account-group-body"
        innerClassName="account-group-body-inner"
      >
        {children}
      </ExpandingSection>
    </DashboardCard>
  );
}

function AccountGroupWidthPicker({ type, wideSpan, standardPlacement, onWideSpanChange }) {
  const title = pluralTypeLabel(type, 2);
  const placementLabel = standardPlacement === 'right' ? 'right side' : 'left side';

  return (
    <span
      className="dashboard-column-picker account-group-column-picker"
      role="group"
      aria-label={`${title} width on wider screens`}
    >
      <button
        type="button"
        className={wideSpan === 1 ? 'active' : ''}
        aria-label={`Use standard width for ${title}, previewed on the ${placementLabel}`}
        aria-pressed={wideSpan === 1}
        title={`Standard Width (${placementLabel})`}
        onClick={() => onWideSpanChange(1)}
      >
        <span
          className={`dashboard-width-icon dashboard-width-icon-standard dashboard-width-icon-${standardPlacement}`}
          aria-hidden="true"
        />
      </button>
      <button
        type="button"
        className={wideSpan === 2 ? 'active' : ''}
        aria-label={`Use two-column width for ${title}`}
        aria-pressed={wideSpan === 2}
        title="Two-Column Width"
        onClick={() => onWideSpanChange(2)}
      >
        <span
          className="dashboard-width-icon dashboard-width-icon-wide"
          aria-hidden="true"
        />
      </button>
    </span>
  );
}

function DraggableAccountGroupRow({
  group,
  wideSpan = 1,
  standardPlacement = 'left',
  onWideSpanChange,
  previewDisplaced = false
}) {
  const totalTone = group.total < 0 ? 'negative' : 'positive';

  return (
    <ReorderListItem
      id={groupSortableId(group.type)}
      className="account-group-reorder-row"
      handleLabel={`Reorder ${pluralTypeLabel(group.type, group.items.length)}`}
      title={pluralTypeLabel(group.type, group.items.length)}
      subtitle={`${group.items.length.toLocaleString()} ${group.items.length === 1 ? 'account' : 'accounts'}`}
      side={(
        <span className="account-group-reorder-side">
          <AccountGroupWidthPicker
            type={group.type}
            wideSpan={wideSpan}
            standardPlacement={standardPlacement}
            onWideSpanChange={onWideSpanChange}
          />
          <span className="account-group-reorder-values">
            <strong>{formatCurrency(group.total)}</strong>
            <em>{totalTone === 'negative' ? 'Liability' : 'Asset'}</em>
          </span>
        </span>
      )}
      previewDisplaced={previewDisplaced}
    />
  );
}

function DraggableReorderRow({ account, previewDisplaced = false }) {
  const metaParts = [
    TYPE_LABELS[account.type] || account.type,
    account.institution,
    account.account_number_last4 ? `...${account.account_number_last4}` : '',
    account.is_archived ? 'Archived' : ''
  ].filter(Boolean);

  return (
    <ReorderListItem
      id={accountSortableId(account.id)}
      as="li"
      className={`account-row ${account.is_archived ? 'archived' : ''}`}
      handleLabel={`Reorder ${account.name}`}
      previewDisplaced={previewDisplaced}
      title={account.name}
      subtitle={metaParts.join(' | ')}
      sidePrimary={formatCurrency(account.current_balance)}
    />
  );
}

// ============================================================================
// Row used in normal mode — full interactions, no drag
// ============================================================================

function StaticAccountRow({
  account,
  allCount,
  onAddRecord,
  onEdit,
  onMerge,
  onArchive,
  onDelete,
  onViewTransactions,
  onManageSnapshotReminder,
  onCardDetails
}) {
  const canDelete = account.transaction_count === 0;
  const isCreditCard = account.type === 'credit' && typeof onCardDetails === 'function';
  const profile = account.credit_card_profile || null;
  const rewardLabels = Array.isArray(profile?.reward_categories)
    ? profile.reward_categories.map(formatRewardRateLabel).filter(Boolean).slice(0, 3)
    : [];
  const authorizedUsers = Array.isArray(profile?.authorized_users)
    ? profile.authorized_users
    : [];
  const creditLimit = Number(profile?.credit_limit);
  const balance = Math.abs(Number(account.current_balance) || 0);
  const utilization = Number.isFinite(creditLimit) && creditLimit > 0
    ? Math.round((balance / creditLimit) * 100)
    : null;
  const glanceItems = [];
  if (Number.isFinite(creditLimit) && creditLimit > 0) {
    glanceItems.push(`Limit ${formatCurrency(creditLimit)}`);
    if (utilization !== null) glanceItems.push(`${utilization}% used`);
  }
  if (profile?.annual_fee_post_date) {
    const fee = Number(profile.annual_fee);
    glanceItems.push(
      `${Number.isFinite(fee) ? formatCurrency(fee) : 'Fee'} ${formatMonthDay(profile.annual_fee_post_date)}`
    );
  } else if (profile && Number(profile.annual_fee) === 0) {
    glanceItems.push('No annual fee');
  }
  if (authorizedUsers.length > 0) {
    glanceItems.push(authorizedUsers.length === 1 ? authorizedUsers[0] : `${authorizedUsers[0]} + ${authorizedUsers.length - 1}`);
  }
  const menuItems = [
    {
      label: 'Edit Card',
      icon: '✎',
      onClick: onCardDetails,
      hidden: !isCreditCard
    },
    { label: 'Add Record', icon: '+', onClick: onAddRecord },
    { label: 'Snapshot Reminder', icon: '!', onClick: onManageSnapshotReminder },
    { label: 'Edit', icon: '✎', onClick: onEdit, hidden: isCreditCard },
    {
      label: 'Merge',
      icon: '⇌',
      onClick: onMerge,
      hidden: allCount < 2
    },
    {
      label: account.is_archived ? 'Unarchive' : 'Archive',
      icon: account.is_archived ? '↻' : '⊘',
      onClick: onArchive
    },
    { divider: true, hidden: !canDelete },
    {
      label: 'Delete',
      icon: '✕',
      destructive: true,
      onClick: onDelete,
      hidden: !canDelete
    }
  ];

  const badges = [];
  badges.push(
    <span key="type" className={`type-pill type-${account.type}`}>
      {TYPE_LABELS[account.type] || account.type}
    </span>
  );
  if (account.is_manual && !account.simplefin_account_id) {
    badges.push(
      <span key="manual" className="badge-manual">
        Manual
      </span>
    );
  }
  if (account.is_archived) {
    badges.push(
      <span key="arch" className="badge-archived">
        Archived
      </span>
    );
  }

  function handleRowClick() {
    if (isCreditCard) onCardDetails();
  }

  function handleRowKeyDown(event) {
    if (!isCreditCard) return;
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onCardDetails();
  }

  return (
    <li
      className={`account-row ${isCreditCard ? 'credit-card-row' : ''} ${profile?.image_url ? 'has-card-image' : ''} ${account.is_archived ? 'archived' : ''}`.trim()}
      role={isCreditCard ? 'button' : undefined}
      tabIndex={isCreditCard ? 0 : undefined}
      onClick={handleRowClick}
      onKeyDown={handleRowKeyDown}
    >
      <div className="account-main">
        <div className="account-title-line">
          {profile?.image_url && (
            <span className="account-card-thumb">
              <img src={profile.image_url} alt="" loading="lazy" />
            </span>
          )}
          <div className="account-title-copy">
            <div className="account-name">{account.name}</div>
            {account.institution && (
              <div className="account-title-institution">{account.institution}</div>
            )}
          </div>
        </div>
        <div className="account-badges">{badges}</div>
        <div className="account-meta">
          {account.account_number_last4 && (
            <span>···{account.account_number_last4}</span>
          )}
          {account.type === 'mortgage' && account.estimated_value != null && (
            <span>Estimated value {formatCurrency(account.estimated_value)}</span>
          )}
        </div>
        {isCreditCard && (
          <div className="account-credit-glance">
            {glanceItems.length > 0 || rewardLabels.length > 0 ? (
              <>
                {glanceItems.map((item) => (
                  <span key={item}>{item}</span>
                ))}
                {rewardLabels.map((item) => (
                  <span key={item}>{item}</span>
                ))}
              </>
            ) : (
              <span>Tap to add card details</span>
            )}
          </div>
        )}
      </div>

      <div className="account-balance">
        {formatCurrency(account.current_balance)}
      </div>

      <div className="account-row-actions" onClick={(event) => event.stopPropagation()}>
        <button
          type="button"
          className="btn-secondary btn-compact"
          onClick={onViewTransactions}
          disabled={account.transaction_count === 0}
        >
          See transactions
        </button>
        <DropdownMenu
          items={menuItems}
          ariaLabel={`Actions for ${account.name}`}
        />
      </div>
    </li>
  );
}

function formFromCreditCardProfile(account) {
  const profile = account.credit_card_profile || {};
  return {
    account_name: account.name || '',
    account_institution: account.institution || '',
    card_name: profile.card_name || '',
    issuer_name: profile.issuer_name || account.institution || '',
    network: profile.network || '',
    image_url: profile.image_url || '',
    annual_fee: profile.annual_fee == null ? '' : formatCurrencyInput(profile.annual_fee),
    annual_fee_post_date: profile.annual_fee_post_date || '',
    credit_limit: profile.credit_limit == null ? '' : formatCurrencyInput(profile.credit_limit),
    authorized_users: joinProfileList(profile.authorized_users),
    reward_categories: joinProfileList(profile.reward_categories, formatRewardRateLabel),
    benefits: joinProfileList(profile.benefits, (item) => item?.name || item).replace(/, /g, '\n'),
    notes: profile.notes || ''
  };
}

function CreditCardDetailsModal({ account, onClose, onSaved }) {
  const [form, setForm] = useState(() => formFromCreditCardProfile(account));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function handleSave(event, close) {
    event.preventDefault();
    setError('');

    const annualFee = parseOptionalCurrency(form.annual_fee);
    if (Number.isNaN(annualFee)) {
      setError('Annual fee must be a valid amount.');
      return;
    }
    const creditLimit = parseOptionalCurrency(form.credit_limit);
    if (Number.isNaN(creditLimit)) {
      setError('Credit limit must be a valid amount.');
      return;
    }

    setSaving(true);
    try {
      await api.put(`/api/accounts/${account.id}/credit-card-profile`, {
        account_name: form.account_name,
        account_institution: form.account_institution,
        card_name: form.card_name,
        issuer_name: form.issuer_name,
        network: form.network,
        image_url: form.image_url,
        annual_fee: annualFee,
        annual_fee_post_date: form.annual_fee_post_date,
        credit_limit: creditLimit,
        authorized_users: splitCommaList(form.authorized_users),
        reward_categories: splitCommaList(form.reward_categories).map((label) => ({ label })),
        benefits: splitLineList(form.benefits).map((name) => ({ name })),
        notes: form.notes
      });
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Save failed.');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <div className="modal-header">
            <div>
              <h3>Edit Card</h3>
              <p className="modal-copy">{form.account_name || account.name}</p>
            </div>
            <button type="button" className="modal-close" onClick={close} aria-label="Close">
              x
            </button>
          </div>

          <form className="credit-card-profile-form" onSubmit={(event) => handleSave(event, close)}>
            {form.image_url && (
              <div className="credit-card-modal-photo">
                <img src={form.image_url} alt="" />
              </div>
            )}

            <div className="credit-card-profile-grid">
              <label className="field">
                <span>Account Name</span>
                <input
                  type="text"
                  value={form.account_name}
                  onChange={(event) => updateField('account_name', event.target.value)}
                  placeholder="Chase Freedom Unlimited (2457)"
                  required
                />
              </label>
              <label className="field">
                <span>Institution</span>
                <input
                  type="text"
                  value={form.account_institution}
                  onChange={(event) => updateField('account_institution', event.target.value)}
                  placeholder="Chase Bank"
                />
              </label>
              <label className="field">
                <span>Card Name</span>
                <input
                  type="text"
                  value={form.card_name}
                  onChange={(event) => updateField('card_name', event.target.value)}
                  placeholder="Chase Freedom Unlimited"
                />
              </label>
              <label className="field">
                <span>Issuer</span>
                <input
                  type="text"
                  value={form.issuer_name}
                  onChange={(event) => updateField('issuer_name', event.target.value)}
                  placeholder="Chase"
                />
              </label>
              <label className="field">
                <span>Network</span>
                <input
                  type="text"
                  value={form.network}
                  onChange={(event) => updateField('network', event.target.value)}
                  placeholder="Visa"
                />
              </label>
              <label className="field">
                <span>Credit Limit</span>
                <CurrencyInput
                  value={form.credit_limit}
                  onChange={(value) => updateField('credit_limit', value)}
                  placeholder="$15,000"
                />
              </label>
              <label className="field">
                <span>Annual Fee</span>
                <CurrencyInput
                  value={form.annual_fee}
                  onChange={(value) => updateField('annual_fee', value)}
                  placeholder="$95"
                />
              </label>
              <label className="field">
                <span>Fee Posts</span>
                <DateInput
                  value={form.annual_fee_post_date}
                  onChange={(value) => updateField('annual_fee_post_date', value)}
                />
              </label>
            </div>

            <label className="field">
              <span>Authorized Users (separate with commas)</span>
              <input
                type="text"
                value={form.authorized_users}
                onChange={(event) => updateField('authorized_users', event.target.value)}
                placeholder="You, Alex"
              />
            </label>

            <label className="field">
              <span>Best Categories (separate with commas)</span>
              <input
                type="text"
                value={form.reward_categories}
                onChange={(event) => updateField('reward_categories', event.target.value)}
                placeholder="Dining 3x, Travel 2x, Everything 1.5x"
              />
            </label>

            <label className="field">
              <span>Benefits</span>
              <textarea
                value={form.benefits}
                onChange={(event) => updateField('benefits', event.target.value)}
                rows={3}
                placeholder="One benefit per line"
              />
            </label>

            <label className="field">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => updateField('notes', event.target.value)}
                rows={3}
                placeholder="Downgrade plan, retention offer, reminders"
              />
            </label>

            <section className="credit-card-image-panel" aria-label="Card image lookup">
              <ImageUrlFinderPanel
                initialQuery={form.card_name || account.name || ''}
                initialUrl={form.image_url}
                searchLabel="Card Image Search"
                searchPlaceholder="Chase Freedom Unlimited"
                urlLabel="Card Image URL"
                urlPlaceholder="https://example.com/card.png"
                externalSearchQuery={(currentQuery) => `${currentQuery || form.card_name || account.name || 'credit card'} credit card image`}
                onSelectedUrlChange={(value) => updateField('image_url', value)}
                showExternalSearchInTools={false}
                imageVariant="card"
              />
            </section>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Add manual account modal
// ============================================================================

function ManualAccountModal({ onClose, onCreated }) {
  const [name, setName] = useState('');
  const [type, setType] = useState('checking');
  const [institution, setInstitution] = useState('');
  const [last4, setLast4] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave(event, close) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const result = await api.post('/api/accounts', {
        name,
        type,
        institution,
        account_number_last4: last4
      });
      close({ animate: true });
      setTimeout(() => onCreated(result.account), 180);
    } catch (err) {
      setError(err.message || 'Create account failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Add Manual Account</h3>
          <p className="modal-copy">
            Create the account first, then Orbit will open the existing account snapshot popup for its starting balance.
          </p>

          <form onSubmit={(event) => handleSave(event, close)}>
            <label className="field">
              <span>Name</span>
              <input
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Cash Envelope"
                required
              />
            </label>

            <label className="field">
              <span>Type</span>
              <AppSelect
                value={type}
                onChange={setType}
                ariaLabel="Manual account type"
                options={TYPE_OPTIONS}
              />
            </label>

            <label className="field">
              <span>Institution</span>
              <input
                type="text"
                value={institution}
                onChange={(event) => setInstitution(event.target.value)}
                placeholder="Optional"
              />
            </label>

            <label className="field">
              <span>Last Four</span>
              <input
                type="text"
                value={last4}
                onChange={(event) => setLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
                inputMode="numeric"
                placeholder="Optional"
              />
            </label>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Creating...' : 'Create Account'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Add account balance record modal
// ============================================================================

function AccountRecordModal({ account, confirm, onClose, onSaved }) {
  const [date, setDate] = useState(todayLocalDate());
  const [balance, setBalance] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const previousBalance = Number(account.current_balance) || 0;

  async function handleSave(e, close) {
    e.preventDefault();
    setError('');

    const nextBalance = parseOptionalCurrency(balance);
    if (Number.isNaN(nextBalance) || nextBalance === null) {
      setError('Account total must be a valid number.');
      return;
    }

    const changeText = formatPercentChange(nextBalance, previousBalance);
    const confirmMessage = changeText
      ? `${formatCurrency(nextBalance)} is ${changeText} than the previous amount of ${formatCurrency(previousBalance)}. Does that sound right?`
      : `${formatCurrency(nextBalance)} will replace the previous amount of ${formatCurrency(previousBalance)}. Does that sound right?`;

    const ok = await confirm(confirmMessage, {
      title: 'Confirm Account Record',
      confirmLabel: 'Add Record'
    });
    if (!ok) return;

    setSaving(true);
    try {
      await api.post(`/api/accounts/${account.id}/records`, {
        date,
        balance: nextBalance,
        previousBalance
      });
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Add record failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Add Record</h3>

          <p className="modal-copy">
            Add a dated balance snapshot for{' '}
            <strong style={{ color: 'var(--text)' }}>{account.name}</strong>.
          </p>

          <form onSubmit={(e) => handleSave(e, close)}>
            <label className="field">
              <span>Date</span>
              <DateInput value={date} onChange={setDate} required />
            </label>

            <label className="field">
              <span>Account Total</span>
              <CurrencyInput
                value={balance}
                onChange={setBalance}
                placeholder="$125,000"
                required
              />
            </label>

            <div className="warning-banner">
              Previous amount: <strong>{formatCurrency(previousBalance)}</strong>
            </div>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Adding...' : 'Add Record'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Edit modal
// ============================================================================

function EditAccountModal({ account, onClose, onSaved }) {
  const [name, setName] = useState(account.name);
  const [type, setType] = useState(account.type);
  const [institution, setInstitution] = useState(account.institution || '');
  const [estimatedValue, setEstimatedValue] = useState(
    account.estimated_value == null ? '' : formatCurrencyInput(account.estimated_value)
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave(e, close) {
    e.preventDefault();
    setSaving(true);
    setError('');

    const nextEstimatedValue =
      type === 'mortgage' ? parseOptionalCurrency(estimatedValue) : null;

    if (Number.isNaN(nextEstimatedValue)) {
      setError('Estimated value must be a valid number.');
      setSaving(false);
      return;
    }

    const patch = {
      name: name.trim(),
      type,
      institution: institution.trim(),
      estimated_value: nextEstimatedValue
    };

    try {
      await api.put(`/api/accounts/${account.id}`, patch);
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Edit account</h3>

          <form onSubmit={(e) => handleSave(e, close)}>
            <label className="field">
              <span>Name</span>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </label>

            <label className="field">
              <span>Type</span>
              <AppSelect
                value={type}
                options={TYPE_OPTIONS}
                onChange={setType}
                ariaLabel="Account type"
              />
            </label>

            <label className="field">
              <span>Institution</span>
              <input
                type="text"
                value={institution}
                onChange={(e) => setInstitution(e.target.value)}
                placeholder="e.g. Chase"
              />
            </label>

            {type === 'mortgage' && (
              <label className="field">
                <span>Estimated Value</span>
                <CurrencyInput
                  value={estimatedValue}
                  onChange={setEstimatedValue}
                  placeholder="$300,000"
                />
              </label>
            )}

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Merge modal
// ============================================================================

function MergeAccountModal({ source, candidates, onClose, onMerged }) {
  const [targetId, setTargetId] = useState(candidates[0]?.id || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const target = candidates.find((a) => a.id === Number(targetId));

  async function handleMerge(close) {
    if (!targetId) return;
    setBusy(true);
    setError('');
    try {
      await api.post(`/api/accounts/${source.id}/merge`, {
        targetId: Number(targetId)
      });
      close({ animate: true });
      setTimeout(onMerged, 180);
    } catch (err) {
      setError(err.message || 'Merge failed');
      setBusy(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Merge account</h3>

          <p>
            Move all {source.transaction_count.toLocaleString()} transaction
            {source.transaction_count === 1 ? '' : 's'} from{' '}
            <strong style={{ color: 'var(--text)' }}>{source.name}</strong> into
            another account. {source.name} will be deleted afterward.
          </p>

          <label className="field">
            <span>Merge into</span>
            <AppSelect
              value={targetId}
              onChange={setTargetId}
              ariaLabel="Merge target account"
              options={candidates.map((a) => ({
                value: a.id,
                label: `${a.name}${a.institution ? ` - ${a.institution}` : ''}${a.account_number_last4 ? ` ...${a.account_number_last4}` : ''}`
              }))}
            />
          </label>

          {target && (
            <p className="subtle">
              After merge:{' '}
              <strong style={{ color: 'var(--text)' }}>{target.name}</strong>{' '}
              will have{' '}
              {(target.transaction_count +
                source.transaction_count).toLocaleString()}{' '}
              transactions.
            </p>
          )}

          {source.simplefin_account_id && (
            <div className="warning-banner">
              <strong>Heads up:</strong> {source.name} is SimpleFIN-linked. After
              merging, SimpleFIN will likely recreate it on the next sync.
            </div>
          )}

          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
            <button
              type="button"
              className="btn-danger"
              onClick={() => handleMerge(close)}
              disabled={busy || !targetId}
            >
              {busy ? 'Merging…' : `Merge into ${target?.name || ''}`}
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}


