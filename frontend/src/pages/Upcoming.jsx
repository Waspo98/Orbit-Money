import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../components/CurrencyInput.jsx';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { formatCurrency } from '../lib/formatters.js';
import { addMonthsToLocalDate, formatLocalDate } from '../lib/localDate.js';

const KIND_OPTIONS = [
  { value: 'bill', label: 'Bill' },
  { value: 'subscription', label: 'Subscription' },
  { value: 'income', label: 'Income' }
];

const FREQUENCY_OPTIONS = [
  { value: 'weekly', label: 'Weekly' },
  { value: 'biweekly', label: 'Biweekly' },
  { value: 'semimonthly', label: 'Twice Monthly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'bimonthly', label: 'Bimonthly' },
  { value: 'yearly', label: 'Yearly' },
  { value: 'custom', label: 'Custom' }
];

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'bill', label: 'Bills' },
  { value: 'subscription', label: 'Subscriptions' },
  { value: 'income', label: 'Income' },
  { value: 'suggestions', label: 'Suggestions' }
];

function todayIso() {
  return formatLocalDate();
}

function addOneMonthIso() {
  return addMonthsToLocalDate(formatLocalDate(), 1);
}

function formatShortDate(value) {
  if (!value) return '';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function dueLabel(value) {
  const date = new Date(`${value}T00:00:00`);
  const today = new Date(`${todayIso()}T00:00:00`);
  if (Number.isNaN(date.getTime())) return 'Scheduled';
  const diff = Math.round((date.getTime() - today.getTime()) / 86400000);
  if (diff < 0) return `${Math.abs(diff)} days overdue`;
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  return `${diff} days`;
}

function kindLabel(kind) {
  return KIND_OPTIONS.find((option) => option.value === kind)?.label || 'Item';
}

function frequencyLabel(item) {
  const type = item?.frequency_type || 'monthly';
  if (type === 'weekly') return 'Weekly';
  if (type === 'biweekly') return 'Biweekly';
  if (type === 'semimonthly') return 'Twice monthly';
  if (type === 'bimonthly') return 'Bimonthly';
  if (type === 'yearly') return 'Yearly';
  if (type === 'custom') {
    const interval = Number(item.frequency_interval) || 1;
    const unit = String(item.frequency_unit || 'days').replace(/s$/, '');
    return `Every ${interval} ${unit}${interval === 1 ? '' : 's'}`;
  }
  return 'Monthly';
}

function newForm() {
  return {
    name: '',
    kind: 'bill',
    amount: '',
    direction: 'expense',
    frequency_type: 'monthly',
    frequency_interval: '1',
    frequency_unit: 'months',
    next_date: addOneMonthIso(),
    category_id: '',
    account_id: '',
    notes: ''
  };
}

function formFromItem(item) {
  return {
    name: item.name || '',
    kind: item.kind || 'bill',
    amount: formatCurrencyInput(item.amount || 0),
    direction: item.direction || (item.kind === 'income' ? 'income' : 'expense'),
    frequency_type: item.frequency_type || 'monthly',
    frequency_interval: String(item.frequency_interval || 1),
    frequency_unit: item.frequency_unit || 'months',
    next_date: item.next_date || addOneMonthIso(),
    category_id: item.category_id ? String(item.category_id) : '',
    account_id: item.account_id ? String(item.account_id) : '',
    notes: item.notes || ''
  };
}

function payloadFromForm(form) {
  const kind = form.kind;
  return {
    name: form.name.trim(),
    kind,
    amount: parseCurrencyInput(form.amount, 0),
    direction: kind === 'income' ? 'income' : form.direction,
    frequency_type: form.frequency_type,
    frequency_interval: Number(form.frequency_interval) || 1,
    frequency_unit: form.frequency_unit,
    next_date: form.next_date,
    category_id: form.category_id ? Number(form.category_id) : null,
    account_id: form.account_id ? Number(form.account_id) : null,
    notes: form.notes.trim() || null
  };
}

export default function Upcoming({ accounts = [], categories = [] }) {
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');
  const [editingItem, setEditingItem] = useState(null);
  const [formOpen, setFormOpen] = useState(false);

  async function load({ silent = false } = {}) {
    if (!silent && data == null) setLoading(true);
    else setRefreshing(true);
    setError('');
    try {
      setData(await api.get('/api/upcoming'));
    } catch (err) {
      setError(err.message || 'Failed to load upcoming items');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const items = data?.items || [];
  const suggestions = data?.suggestions || [];
  const filteredItems = useMemo(() => {
    if (filter === 'suggestions') return [];
    return items.filter((item) => filter === 'all' || item.kind === filter);
  }, [items, filter]);

  function openNew(kind = 'bill') {
    setEditingItem({ draft: true, form: { ...newForm(), kind, direction: kind === 'income' ? 'income' : 'expense' } });
    setFormOpen(true);
  }

  function openEdit(item) {
    setEditingItem({ ...item, form: formFromItem(item) });
    setFormOpen(true);
  }

  async function saveItem(form) {
    const payload = payloadFromForm(form);
    if (!payload.name) {
      alert('Name is required.', { title: 'Missing name' });
      return;
    }
    if (!payload.next_date) {
      alert('Next date is required.', { title: 'Missing date' });
      return;
    }
    try {
      if (editingItem?.draft) await api.post('/api/upcoming', payload);
      else await api.put(`/api/upcoming/${editingItem.id}`, payload);
      setFormOpen(false);
      setEditingItem(null);
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not save upcoming item.', { title: 'Save failed' });
    }
  }

  async function deleteItem(item) {
    const ok = await confirm(`Delete "${item.name}" from Upcoming?`, {
      title: 'Delete Upcoming Item',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/upcoming/${item.id}`);
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not delete upcoming item.', { title: 'Delete failed' });
    }
  }

  async function acceptSuggestion(suggestion) {
    try {
      await api.post('/api/upcoming/suggestions/accept', suggestion);
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not accept suggestion.', { title: 'Suggestion failed' });
    }
  }

  async function dismissSuggestion(suggestion) {
    try {
      await api.post('/api/upcoming/suggestions/dismiss', { key: suggestion.key });
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not dismiss suggestion.', { title: 'Dismiss failed' });
    }
  }

  return (
    <div className="upcoming-view">
      <PageHero
        id="upcoming-title"
        variant="upcoming"
        kicker="Money Calendar"
        title="Upcoming"
        subtitle="Bills, subscriptions, and income in one place."
        initialHeight={300}
        toolbar={
          <div className="page-hero-action-row">
            <button type="button" className="btn-primary" onClick={() => openNew()}>
              + Add Upcoming
            </button>
          </div>
        }
      />

      {error && <div className="error">{error}</div>}

      <div className={`upcoming-content ${refreshing ? 'refreshing' : ''}`}>
        <div className="upcoming-tabs" role="tablist" aria-label="Upcoming filters">
          {FILTERS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={filter === option.value ? 'active' : ''}
              onClick={() => setFilter(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="center-loading"><div className="spinner" /></div>
        ) : filter === 'suggestions' ? (
          <SuggestionsList
            suggestions={suggestions}
            onAccept={acceptSuggestion}
            onDismiss={dismissSuggestion}
          />
        ) : filteredItems.length === 0 ? (
          <div className="empty-state">
            <h2>No Upcoming Items</h2>
            <p>Add a bill, subscription, or income item manually, or review suggestions found from transactions.</p>
            <button type="button" className="btn-primary" onClick={() => setFilter('suggestions')}>
              View Suggestions
            </button>
          </div>
        ) : (
          <ul className="upcoming-list">
            {filteredItems.map((item) => (
              <UpcomingItem
                key={item.id}
                item={item}
                onEdit={() => openEdit(item)}
                onDelete={() => deleteItem(item)}
              />
            ))}
          </ul>
        )}
      </div>

      {formOpen && (
        <UpcomingEditor
          item={editingItem}
          accounts={accounts}
          categories={categories}
          onSave={saveItem}
          onClose={() => {
            setFormOpen(false);
            setEditingItem(null);
          }}
        />
      )}

      <Dialog />
    </div>
  );
}

function UpcomingItem({ item, onEdit, onDelete }) {
  return (
    <li className={`upcoming-item upcoming-item-${item.kind}`}>
      <div className="upcoming-date">
        <strong>{formatShortDate(item.next_date)}</strong>
        <span>{dueLabel(item.next_date)}</span>
      </div>
      <div className="upcoming-main">
        <div>
          <strong>{item.name}</strong>
          <span>{kindLabel(item.kind)} - {item.category_name || 'Uncategorized'} - {frequencyLabel(item)}</span>
        </div>
        {item.account_name && <em>{item.account_name}</em>}
      </div>
      <div className="upcoming-side">
        <strong className={item.direction === 'income' ? 'income' : 'expense'}>
          {item.direction === 'income' ? '+' : '-'}{formatCurrency(item.amount)}
        </strong>
        <div className="upcoming-actions">
          <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={onEdit}>
            Edit
          </button>
          <button type="button" className="dashboard-card-link dashboard-card-action-button danger-link" onClick={onDelete}>
            Delete
          </button>
        </div>
      </div>
    </li>
  );
}

function SuggestionsList({ suggestions, onAccept, onDismiss }) {
  if (suggestions.length === 0) {
    return (
      <div className="empty-state">
        <h2>No Suggestions</h2>
        <p>Suggestions appear when income, bills, or repeating merchants show up across transaction history.</p>
      </div>
    );
  }

  return (
    <ul className="upcoming-list">
      {suggestions.map((suggestion) => (
        <li key={suggestion.key} className={`upcoming-item upcoming-suggestion upcoming-item-${suggestion.kind}`}>
          <div className="upcoming-date">
            <strong>{formatShortDate(suggestion.next_date)}</strong>
            <span>{suggestion.confidence}% match</span>
          </div>
          <div className="upcoming-main">
            <div>
              <strong>{suggestion.name}</strong>
              <span>{kindLabel(suggestion.kind)} - {suggestion.category_name || 'Uncategorized'} - {frequencyLabel(suggestion)}</span>
            </div>
            <em>{suggestion.transaction_count} transaction hits</em>
          </div>
          <div className="upcoming-side">
            <strong className={suggestion.direction === 'income' ? 'income' : 'expense'}>
              {suggestion.direction === 'income' ? '+' : '-'}{formatCurrency(suggestion.amount)}
            </strong>
            <div className="upcoming-actions">
              <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={() => onAccept(suggestion)}>
                Add
              </button>
              <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={() => onDismiss(suggestion)}>
                Dismiss
              </button>
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function UpcomingEditor({ item, accounts, categories, onSave, onClose }) {
  const [form, setForm] = useState(item?.form || newForm());
  const isIncome = form.kind === 'income';

  function patch(patchValue) {
    setForm((prev) => {
      const next = { ...prev, ...patchValue };
      if (patchValue.kind === 'income') next.direction = 'income';
      if (patchValue.kind && patchValue.kind !== 'income') next.direction = 'expense';
      return next;
    });
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>{item?.draft ? 'Add Upcoming' : 'Edit Upcoming'}</h3>
          <p>Save bills, subscriptions, or income manually and tune their schedule.</p>

          <div className="upcoming-editor-grid">
            <label className="field">
              <span>Name</span>
              <input
                value={form.name}
                onChange={(event) => patch({ name: event.target.value })}
                placeholder="Mortgage, paycheck, Netflix"
              />
            </label>

            <label className="field">
              <span>Type</span>
              <select value={form.kind} onChange={(event) => patch({ kind: event.target.value })}>
                {KIND_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Amount</span>
              <CurrencyInput
                value={form.amount}
                onChange={(value) => patch({ amount: value })}
                placeholder="$0.00"
              />
            </label>

            <label className="field">
              <span>Next Date</span>
              <input
                type="date"
                value={form.next_date}
                onChange={(event) => patch({ next_date: event.target.value })}
              />
            </label>

            <label className="field">
              <span>Frequency</span>
              <select value={form.frequency_type} onChange={(event) => patch({ frequency_type: event.target.value })}>
                {FREQUENCY_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>

            {form.frequency_type === 'custom' && (
              <div className="upcoming-custom-frequency">
                <label className="field">
                  <span>Every</span>
                  <input
                    type="number"
                    min="1"
                    value={form.frequency_interval}
                    onChange={(event) => patch({ frequency_interval: event.target.value })}
                  />
                </label>
                <label className="field">
                  <span>Unit</span>
                  <select value={form.frequency_unit} onChange={(event) => patch({ frequency_unit: event.target.value })}>
                    <option value="days">Days</option>
                    <option value="weeks">Weeks</option>
                    <option value="months">Months</option>
                  </select>
                </label>
              </div>
            )}

            {!isIncome && (
              <label className="field">
                <span>Direction</span>
                <select value={form.direction} onChange={(event) => patch({ direction: event.target.value })}>
                  <option value="expense">Expense</option>
                  <option value="income">Income</option>
                </select>
              </label>
            )}

            <label className="field">
              <span>Category</span>
              <select value={form.category_id} onChange={(event) => patch({ category_id: event.target.value })}>
                <option value="">Uncategorized</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Account</span>
              <select value={form.account_id} onChange={(event) => patch({ account_id: event.target.value })}>
                <option value="">Any Account</option>
                {accounts.filter((account) => !account.is_archived).map((account) => (
                  <option key={account.id} value={account.id}>{account.name}</option>
                ))}
              </select>
            </label>

            <label className="field upcoming-notes-field">
              <span>Notes</span>
              <textarea
                value={form.notes}
                onChange={(event) => patch({ notes: event.target.value })}
                placeholder="Optional note"
              />
            </label>
          </div>

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
            <button type="button" className="btn-primary" onClick={() => onSave(form)}>
              Save
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}
