import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import ExpandingSection from '../components/ExpandingSection.jsx';
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

function todayLocalDate() {
  const now = new Date();
  const offsetDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return offsetDate.toISOString().slice(0, 10);
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

function accountTypeSummary(accounts) {
  const counts = new Map();
  accounts.forEach((account) => {
    counts.set(account.type, (counts.get(account.type) || 0) + 1);
  });
  return Array.from(counts.entries())
    .map(([type, count]) => `${count} ${pluralTypeLabel(type, count)}`)
    .join(' | ');
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

function findAccountGroup(groups, accountId) {
  return groups.find((group) => group.items.some((account) => account.id === accountId));
}

// ============================================================================
// Main page
// ============================================================================

export default function Accounts({ onChange }) {
  const navigate = useNavigate();
  const { alert, confirm, Dialog } = useAppDialog();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [editing, setEditing] = useState(null);
  const [merging, setMerging] = useState(null);
  const [recording, setRecording] = useState(null);
  const [addingManual, setAddingManual] = useState(false);
  const [collapsedGroups, setCollapsedGroups] = useState(() => new Set());
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

  const activeAccounts = accounts.filter((account) => !account.is_archived);
  const accountGroups = useMemo(() => buildAccountGroups(accounts), [accounts]);
  const cashBalance = activeAccounts
    .filter((account) => account.type === 'checking' || account.type === 'savings')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
  const creditCardsDue = activeAccounts
    .filter((account) => account.type === 'credit')
    .reduce((total, account) => total + Math.abs(Number(account.current_balance) || 0), 0);
  const investmentsBalance = activeAccounts
    .filter((account) => account.type === 'investment')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
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
    <div className={`accounts-view ${reorderMode ? 'reorder-mode' : ''} ${activeDragId ? 'dragging-active' : ''}`}>
      <PageHero
        id="accounts-title"
        variant="accounts"
        kicker="Your Money's Home Base"
        title="Accounts"
        subtitle={`${accountTypeSummary(accounts) || 'No accounts'}${showArchived ? ' including archived' : ''}`}
        stats={accountHeroStats}
        toolbar={!reorderMode ? (
          <div className="page-hero-action-row">
            <button
              type="button"
              className="btn-primary"
              onClick={() => setAddingManual(true)}
            >
              + Manual Account
            </button>
          </div>
        ) : null}
      />

      <div className="accounts-toolbar accounts-page-toolbar">
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
      </div>

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
        <div className="empty-state">
          <div className="empty-state-icon">⬢</div>
          <h2>No accounts yet</h2>
          <p>Import from Rocket Money, connect SimpleFIN, or add a manual account.</p>
          <button type="button" className="btn-secondary" onClick={() => setAddingManual(true)}>
            Add Manual Account
          </button>
        </div>
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
            <div className="goal-list reorder-active reorder-drag-scope">
              {accountGroups.map((group) => (
                <DraggableAccountGroupRow
                  key={group.type}
                  group={group}
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

      <Dialog />
    </div>
  );
}

// ============================================================================
// Row used in reorder mode — entire row is a drag handle, no buttons
// ============================================================================

function AccountGroup({ group, children, collapsed, disabled = false, onToggle }) {
  const totalTone = group.total < 0 ? 'negative' : 'positive';

  return (
    <section className={`dashboard-card account-group-card ${collapsed ? 'collapsed' : ''}`}>
      <button
        type="button"
        className="dashboard-card-header account-group-header account-group-toggle"
        aria-expanded={!collapsed}
        disabled={disabled}
        onClick={onToggle}
      >
        <div>
          <h3>{pluralTypeLabel(group.type, group.items.length)}</h3>
          <span className="muted">
            {group.items.length.toLocaleString()} {group.items.length === 1 ? 'account' : 'accounts'}
          </span>
        </div>
        <span className="account-group-header-side">
          <strong className={`account-group-total ${totalTone}`}>{formatCurrency(group.total)}</strong>
          <CollapseIndicator expanded={!collapsed} className="account-group-caret" />
        </span>
      </button>
      <ExpandingSection
        expanded={!collapsed}
        className="account-group-body"
        innerClassName="account-group-body-inner"
      >
        {children}
      </ExpandingSection>
    </section>
  );
}

function DraggableAccountGroupRow({ group, previewDisplaced = false }) {
  const totalTone = group.total < 0 ? 'negative' : 'positive';

  return (
    <ReorderListItem
      id={groupSortableId(group.type)}
      className="account-group-reorder-row"
      handleLabel={`Reorder ${pluralTypeLabel(group.type, group.items.length)}`}
      title={pluralTypeLabel(group.type, group.items.length)}
      subtitle={`${group.items.length.toLocaleString()} ${group.items.length === 1 ? 'account' : 'accounts'}`}
      sidePrimary={formatCurrency(group.total)}
      sideSecondary={totalTone === 'negative' ? 'Liability' : 'Asset'}
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
  onViewTransactions
}) {
  const canDelete = account.transaction_count === 0;
  const menuItems = [
    { label: 'Add Record', icon: '+', onClick: onAddRecord },
    { label: 'Edit', icon: '✎', onClick: onEdit },
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
  if (account.simplefin_account_id) {
    badges.push(
      <span key="sf" className="badge-simplefin" title="Linked to SimpleFIN">
        SimpleFIN
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

  return (
    <li className={`account-row ${account.is_archived ? 'archived' : ''}`}>
      <div className="account-main">
        <div className="account-name">{account.name}</div>
        <div className="account-badges">{badges}</div>
        <div className="account-meta">
          {account.institution && <span>{account.institution}</span>}
          {account.account_number_last4 && (
            <span>···{account.account_number_last4}</span>
          )}
          {account.type === 'mortgage' && account.estimated_value != null && (
            <span>Estimated value {formatCurrency(account.estimated_value)}</span>
          )}
        </div>
      </div>

      <div className="account-balance">
        {formatCurrency(account.current_balance)}
      </div>

      <div className="account-row-actions">
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
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                required
              />
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


