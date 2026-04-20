import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
  useSortable,
  arrayMove
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

import { api } from '../api.js';
import DropdownMenu from '../components/DropdownMenu.jsx';
import AnimatedModal from '../components/AnimatedModal.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';

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
  if (amount === null || amount === undefined) return '—';
  return Number(amount).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
}

function parseOptionalCurrency(value) {
  const cleaned = String(value || '').replace(/[$,]/g, '').trim();
  if (!cleaned) return null;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : NaN;
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

  // Reorder mode: when true, rows become drag handles and ALL other
  // interactions (expand, action buttons, dropdown, See transactions) are
  // hidden. The only thing you can do is drag-and-drop. Entered via the
  // Reorder button, exited via Done.
  const [reorderMode, setReorderMode] = useState(false);

  // Sensors are only used while in reorder mode. No activation threshold —
  // any movement starts a drag immediately because there are no competing
  // interactions to distinguish from.
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

  // Exiting reorder mode: nothing to save explicitly — each drop has
  // already persisted. But if the user archived/edited before entering
  // reorder mode, we want to make sure we're synced.
  function toggleReorderMode() {
    setReorderMode((v) => !v);
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
  const cashBalance = activeAccounts
    .filter((account) => account.type === 'checking' || account.type === 'savings')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
  const creditCardsDue = activeAccounts
    .filter((account) => account.type === 'credit')
    .reduce((total, account) => total + Math.abs(Number(account.current_balance) || 0), 0);
  const investmentsBalance = activeAccounts
    .filter((account) => account.type === 'investment')
    .reduce((total, account) => total + (Number(account.current_balance) || 0), 0);
  const accountsSubtitle = `${accountTypeSummary(accounts) || 'No accounts'}${
    showArchived ? ' including archived' : ''
  }${
    reorderMode ? ' · Drag a row to reorder' : ''
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

  async function handleDragEnd(event) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const oldIndex = accounts.findIndex((a) => a.id === active.id);
    const newIndex = accounts.findIndex((a) => a.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = arrayMove(accounts, oldIndex, newIndex);
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
    <div className={`accounts-view ${reorderMode ? 'reorder-mode' : ''}`}>
      <PageHero
        id="accounts-title"
        variant="accounts"
        kicker="Your Money's Home Base"
        title="Accounts"
        subtitle={accountsSubtitle}
        stats={accountHeroStats}
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
            onClick={toggleReorderMode}
          >
            {reorderMode ? 'Done' : 'Reorder'}
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
          <p>Import from Rocket Money or connect SimpleFIN to add accounts.</p>
        </div>
      ) : reorderMode ? (
        // Reorder mode: wrapped in DndContext, all rows draggable, no actions
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={accounts.map((a) => a.id)}
            strategy={verticalListSortingStrategy}
          >
            <ul className="account-list reorder-active">
              {accounts.map((a) => (
                <DraggableReorderRow key={a.id} account={a} />
              ))}
            </ul>
          </SortableContext>
        </DndContext>
      ) : (
        // Normal mode: no drag, all actions available
        <ul className="account-list">
          {accounts.map((a) => (
            <StaticAccountRow
              key={a.id}
              account={a}
              allCount={accounts.length}
              onEdit={() => setEditing(a)}
              onMerge={() => setMerging(a)}
              onArchive={() => handleArchive(a.id, !!a.is_archived)}
              onDelete={() => handleDelete(a)}
              onViewTransactions={() => viewTransactions(a.id)}
            />
          ))}
        </ul>
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

      <Dialog />
    </div>
  );
}

// ============================================================================
// Row used in reorder mode — entire row is a drag handle, no buttons
// ============================================================================

function DraggableReorderRow({ account }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: account.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  };

  const badges = [];
  badges.push(
    <span key="type" className={`type-pill type-${account.type}`}>
      {TYPE_LABELS[account.type] || account.type}
    </span>
  );
  if (account.is_archived) {
    badges.push(
      <span key="arch" className="badge-archived">
        Archived
      </span>
    );
  }

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={`account-row draggable ${account.is_archived ? 'archived' : ''} ${isDragging ? 'dragging' : ''}`}
      {...attributes}
      {...listeners}
    >
      <span className="drag-grip" aria-hidden="true">⋮⋮</span>
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
    </li>
  );
}

// ============================================================================
// Row used in normal mode — full interactions, no drag
// ============================================================================

function StaticAccountRow({
  account,
  allCount,
  onEdit,
  onMerge,
  onArchive,
  onDelete,
  onViewTransactions
}) {
  const canDelete = account.transaction_count === 0;
  const menuItems = [
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
// Edit modal
// ============================================================================

function EditAccountModal({ account, onClose, onSaved }) {
  const [name, setName] = useState(account.name);
  const [type, setType] = useState(account.type);
  const [institution, setInstitution] = useState(account.institution || '');
  const [estimatedValue, setEstimatedValue] = useState(
    account.estimated_value == null ? '' : String(account.estimated_value)
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
      close();
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
              <select value={type} onChange={(e) => setType(e.target.value)}>
                {TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
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
                <input
                  type="text"
                  inputMode="decimal"
                  value={estimatedValue}
                  onChange={(e) => setEstimatedValue(e.target.value)}
                  placeholder="e.g. 300000"
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
      close();
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
            <select
              value={targetId}
              onChange={(e) => setTargetId(e.target.value)}
            >
              {candidates.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {a.institution ? ` — ${a.institution}` : ''}
                  {a.account_number_last4
                    ? ` ···${a.account_number_last4}`
                    : ''}
                </option>
              ))}
            </select>
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
