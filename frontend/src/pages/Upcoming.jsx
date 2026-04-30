import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import PageHero from '../components/PageHero.jsx';
import RecurringItemEditor, {
  formFromRecurringItem,
  formFromSuggestion,
  kindLabel,
  newRecurringForm,
  recurringFrequencyLabel
} from '../components/upcoming/RecurringItemEditor.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { formatCurrency, formatSignedCurrency } from '../lib/formatters.js';
import { formatFullDate, formatMonthDay } from '../lib/localDate.js';

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'bill', label: 'Bills' },
  { value: 'subscription', label: 'Subscriptions' },
  { value: 'income', label: 'Income' }
];

const KIND_TITLES = {
  bill: 'Bills',
  subscription: 'Subscriptions',
  income: 'Income'
};

function todayIso() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function dueLabel(value) {
  const date = new Date(`${value}T00:00:00`);
  const today = new Date(`${todayIso()}T00:00:00`);
  if (Number.isNaN(date.getTime())) return 'Scheduled';
  const diff = Math.round((date.getTime() - today.getTime()) / 86400000);
  if (diff < 0) return `${Math.abs(diff)} Days Overdue`;
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  return `${diff} Days`;
}

function amountClass(direction) {
  return direction === 'income' ? 'income' : 'expense';
}

function signedAmount(direction, amount) {
  const value = Number(amount || 0);
  return direction === 'income'
    ? formatSignedCurrency(Math.abs(value))
    : `-${formatCurrency(Math.abs(value))}`;
}

function projectedLabel(item) {
  if (item.amount_strategy !== 'history_average') return null;
  const months = item.projection?.sample_months;
  if (months) return `Projected from ${months} month${months === 1 ? '' : 's'}`;
  return `Projected from ${item.amount_lookback_months || 6} months`;
}

function itemDisplayAmount(item) {
  return Number(item.projected_amount ?? item.amount ?? 0);
}

function kindTotal(items, kind) {
  return items
    .filter((item) => item.kind === kind)
    .reduce((sum, item) => sum + itemDisplayAmount(item), 0);
}

function daysBetweenDates(startDate, endDate) {
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return 0;
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86400000));
}

function groupByDate(occurrences) {
  const groups = [];
  let current = null;
  for (const occurrence of occurrences) {
    const key = occurrence.next_date || occurrence.date;
    if (!current || current.key !== key) {
      current = { key, items: [] };
      groups.push(current);
    }
    current.items.push(occurrence);
  }
  return groups;
}

export default function Upcoming({ accounts = [], categories = [] }) {
  const { alert, confirm, Dialog } = useAppDialog();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');
  const [editor, setEditor] = useState(null);

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
  const restOfMonth = data?.rest_of_month || [];
  const cashFlow = data?.cash_flow || {
    income: 0,
    expenses: 0,
    net: 0,
    occurrence_count: 0
  };

  const groupedItems = useMemo(() => ({
    bill: items.filter((item) => item.kind === 'bill'),
    subscription: items.filter((item) => item.kind === 'subscription'),
    income: items.filter((item) => item.kind === 'income')
  }), [items]);

  const upcomingTotals = useMemo(() => ({
    bill: kindTotal(items, 'bill'),
    subscription: kindTotal(items, 'subscription'),
    income: kindTotal(items, 'income')
  }), [items]);

  function openNew(kind = 'bill') {
    setEditor({
      mode: 'create',
      title: 'Add Recurring',
      form: newRecurringForm({ kind, direction: kind === 'income' ? 'income' : 'expense' })
    });
  }

  function openEdit(item) {
    setEditor({
      mode: 'edit',
      id: item.id,
      title: 'Edit Recurring',
      form: formFromRecurringItem(item)
    });
  }

  function openSuggestion(suggestion) {
    setEditor({
      mode: 'suggestion',
      key: suggestion.key,
      title: 'Add Suggested Recurring',
      form: formFromSuggestion(suggestion)
    });
  }

  async function saveRecurring(payload) {
    if (editor?.mode === 'edit') {
      await api.put(`/api/upcoming/${editor.id}`, payload);
    } else if (editor?.mode === 'suggestion') {
      await api.post('/api/upcoming/suggestions/accept', {
        ...payload,
        key: editor.key
      });
    } else {
      await api.post('/api/upcoming', payload);
    }
    await load({ silent: true });
  }

  async function deleteItem(item) {
    const ok = await confirm(`Delete "${item.name}" from Upcoming?`, {
      title: 'Delete Recurring Item',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;
    try {
      await api.del(`/api/upcoming/${item.id}`);
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not delete recurring item.', { title: 'Delete Failed' });
    }
  }

  async function dismissSuggestion(suggestion) {
    try {
      await api.post('/api/upcoming/suggestions/dismiss', { key: suggestion.key });
      await load({ silent: true });
    } catch (err) {
      alert(err.message || 'Could not dismiss suggestion.', { title: 'Dismiss Failed' });
    }
  }

  const heroStats = [
    {
      label: 'Rest Of Month Net',
      value: formatSignedCurrency(cashFlow.net || 0),
      tone: Number(cashFlow.net || 0) >= 0 ? 'good' : 'caution'
    },
    { label: 'Upcoming Income', value: formatCurrency(upcomingTotals.income), tone: 'good' },
    { label: 'Upcoming Bills', value: formatCurrency(upcomingTotals.bill), tone: 'caution' },
    { label: 'Upcoming Subscriptions', value: formatCurrency(upcomingTotals.subscription), tone: 'caution' }
  ];

  return (
    <div className="upcoming-view">
      <PageHero
        id="upcoming-title"
        variant="upcoming"
        kicker="Money Calendar"
        title="Upcoming"
        subtitle="Projected bills, subscriptions, income, and cash flow."
        stats={heroStats}
        statLabel="Upcoming summary"
        initialHeight={420}
        toolbar={
          <div className="page-hero-action-row">
            <button type="button" className="btn-secondary" onClick={() => setFilter('suggested')}>
              Suggestions
            </button>
            <button type="button" className="btn-primary" onClick={() => openNew()}>
              + Add Recurring
            </button>
          </div>
        }
      />

      {error && <div className="error">{error}</div>}

      <div className={`upcoming-content ${refreshing ? 'refreshing' : ''}`}>
        {loading ? (
          <div className="center-loading"><div className="spinner" /></div>
        ) : (
          <>
            <CashFlowPanel
              cashFlow={cashFlow}
              restOfMonth={restOfMonth}
            />

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

            <UpcomingPlan
              filter={filter}
              groupedItems={groupedItems}
              items={items}
              suggestions={suggestions}
              onAdd={openNew}
              onEdit={openEdit}
              onDelete={deleteItem}
              onReviewSuggestion={openSuggestion}
              onDismissSuggestion={dismissSuggestion}
              onViewSuggestions={() => setFilter('suggested')}
            />
          </>
        )}
      </div>

      {editor && (
        <RecurringItemEditor
          item={{ form: editor.form }}
          title={editor.title}
          accounts={accounts}
          categories={categories}
          saveLabel={editor.mode === 'suggestion' ? 'Add Recurring' : 'Save'}
          onSave={saveRecurring}
          onClose={() => setEditor(null)}
        />
      )}

      <Dialog />
    </div>
  );
}

function CashFlowPanel({ cashFlow, restOfMonth }) {
  const maxFlow = Math.max(Number(cashFlow.income || 0), Number(cashFlow.expenses || 0), 1);
  const grouped = groupByDate(restOfMonth);
  const daysRemaining = daysBetweenDates(cashFlow.start_date || todayIso(), cashFlow.end_date || todayIso());
  const transactionCount = Number(cashFlow.occurrence_count || 0);
  return (
    <section className="upcoming-cashflow-panel" aria-label="Projected cash flow">
      <div className="upcoming-cashflow-summary">
        <span>Rest Of Month</span>
        <strong className={Number(cashFlow.net || 0) >= 0 ? 'income' : 'expense'}>
          {formatSignedCurrency(cashFlow.net || 0)}
        </strong>
        <em>{cashFlow.occurrence_count || 0} projected transaction{cashFlow.occurrence_count === 1 ? '' : 's'}</em>
        <div className="upcoming-cashflow-bars" aria-hidden="true">
          <span className="income" style={{ width: `${Math.max(8, (Number(cashFlow.income || 0) / maxFlow) * 100)}%` }} />
          <span className="expense" style={{ width: `${Math.max(8, (Number(cashFlow.expenses || 0) / maxFlow) * 100)}%` }} />
        </div>
        <div className="upcoming-cashflow-split">
          <span>In {formatCurrency(cashFlow.income || 0)}</span>
          <span>Out {formatCurrency(cashFlow.expenses || 0)}</span>
        </div>
      </div>

      <div className="upcoming-cashflow-timeline">
        <header>
          <span>
            {transactionCount} transaction{transactionCount === 1 ? '' : 's'} remaining in the next {daysRemaining} day{daysRemaining === 1 ? '' : 's'}
          </span>
        </header>
        {grouped.length === 0 ? (
          <div className="upcoming-mini-empty">No scheduled cash flow remains this month.</div>
        ) : (
          <ol>
            {grouped.slice(0, 6).map((group) => (
              <li key={group.key}>
                <time dateTime={group.key}>
                  <strong>{formatMonthDay(group.key)}</strong>
                  <span>{dueLabel(group.key)}</span>
                </time>
                <div>
                  {group.items.map((occurrence) => (
                    <div key={occurrence.id} className="upcoming-timeline-item">
                      <span>{occurrence.name}</span>
                      <strong className={amountClass(occurrence.direction)}>
                        {signedAmount(occurrence.direction, occurrence.amount)}
                      </strong>
                    </div>
                  ))}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  );
}

function UpcomingPlan({
  filter,
  groupedItems,
  items,
  suggestions,
  onAdd,
  onEdit,
  onDelete,
  onReviewSuggestion,
  onDismissSuggestion,
  onViewSuggestions
}) {
  if (filter === 'suggested') {
    return (
      <SuggestionsPanel
        suggestions={suggestions}
        onReview={onReviewSuggestion}
        onDismiss={onDismissSuggestion}
      />
    );
  }

  if (items.length === 0) {
    return (
      <div className="empty-state">
        <h2>No Recurring Items</h2>
        <p>Add recurring bills, subscriptions, or income to start projecting cash flow.</p>
        <button type="button" className="btn-primary" onClick={onViewSuggestions}>
          View Suggestions
        </button>
      </div>
    );
  }

  if (filter !== 'all') {
    return (
      <UpcomingLane
        kind={filter}
        items={groupedItems[filter] || []}
        total={kindTotal(items, filter)}
        onAdd={() => onAdd(filter)}
        onEdit={onEdit}
        onDelete={onDelete}
      />
    );
  }

  return (
    <div className="upcoming-lanes">
      {['bill', 'subscription', 'income'].map((kind) => (
        <UpcomingLane
          key={kind}
          kind={kind}
          items={groupedItems[kind] || []}
          total={kindTotal(items, kind)}
          onAdd={() => onAdd(kind)}
          onEdit={onEdit}
          onDelete={onDelete}
        />
      ))}
      {suggestions.length > 0 && (
        <section className="upcoming-lane upcoming-lane-suggestions">
          <header>
            <div>
              <span>Suggested</span>
              <strong>Potential recurring</strong>
            </div>
            <em>{suggestions.length}</em>
          </header>
          <SuggestionsList
            suggestions={suggestions.slice(0, 4)}
            onReview={onReviewSuggestion}
            onDismiss={onDismissSuggestion}
            compact
          />
        </section>
      )}
    </div>
  );
}

function UpcomingLane({ kind, items, total, onAdd, onEdit, onDelete }) {
  return (
    <section className={`upcoming-lane upcoming-lane-${kind}`}>
      <header>
        <div>
          <strong>{KIND_TITLES[kind]}</strong>
        </div>
        <em>{formatCurrency(total)}</em>
      </header>

      {items.length === 0 ? (
        <div className="upcoming-mini-empty">
          <span>No {KIND_TITLES[kind].toLowerCase()} saved.</span>
          <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={onAdd}>
            Add
          </button>
        </div>
      ) : (
        <ul className="upcoming-plan-list">
          {items.map((item) => (
            <RecurringRow
              key={item.id}
              item={item}
              onEdit={() => onEdit(item)}
              onDelete={() => onDelete(item)}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function RecurringRow({ item, onEdit, onDelete }) {
  const projection = projectedLabel(item);
  return (
    <li className={`upcoming-plan-row upcoming-item-${item.kind}`}>
      <button type="button" className="upcoming-plan-row-main" onClick={onEdit}>
        <span className="upcoming-row-date">
          <strong>{formatMonthDay(item.next_date)}</strong>
          <em>{dueLabel(item.next_date)}</em>
        </span>
        <span className="upcoming-row-copy">
          <strong>{item.name}</strong>
          <em>
            {item.category_name || 'Uncategorized'} - {recurringFrequencyLabel(item)}
          </em>
          {projection && <span className="upcoming-projection-pill">{projection}</span>}
        </span>
        <span className="upcoming-row-amount">
          <strong className={amountClass(item.direction)}>
            {signedAmount(item.direction, itemDisplayAmount(item))}
          </strong>
          {item.account_name && <em>{item.account_name}</em>}
        </span>
      </button>
      <div className="upcoming-actions">
        <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={onEdit}>
          Edit
        </button>
        <button type="button" className="dashboard-card-link dashboard-card-action-button danger-link" onClick={onDelete}>
          Delete
        </button>
      </div>
    </li>
  );
}

function SuggestionsPanel({ suggestions, onReview, onDismiss }) {
  return (
    <section className="upcoming-lane upcoming-suggestions-panel">
      <header>
        <div>
          <span>Suggested</span>
          <strong>Suggested Recurring</strong>
        </div>
        <em>{suggestions.length}</em>
      </header>
      <SuggestionsList suggestions={suggestions} onReview={onReview} onDismiss={onDismiss} />
    </section>
  );
}

function SuggestionsList({ suggestions, onReview, onDismiss, compact = false }) {
  if (suggestions.length === 0) {
    return (
      <div className="upcoming-mini-empty">
        <span>No suggestions right now.</span>
      </div>
    );
  }

  return (
    <ul className="upcoming-suggestion-list">
      {suggestions.map((suggestion) => (
        <li key={suggestion.key} className={`upcoming-suggestion-row upcoming-item-${suggestion.kind}`}>
          <button type="button" className="upcoming-suggestion-main" onClick={() => onReview(suggestion)}>
            <span className="upcoming-row-date">
              <strong>{formatMonthDay(suggestion.next_date)}</strong>
              <em>{suggestion.confidence}% Match</em>
            </span>
            <span className="upcoming-row-copy">
              <strong>{suggestion.name}</strong>
              <em>
                {kindLabel(suggestion.kind)} - {suggestion.category_name || 'Uncategorized'} - {recurringFrequencyLabel(suggestion)}
              </em>
              {!compact && (
                <span className="upcoming-projection-pill">
                  {suggestion.transaction_count} transaction hit{suggestion.transaction_count === 1 ? '' : 's'}
                </span>
              )}
            </span>
            <span className="upcoming-row-amount">
              <strong className={amountClass(suggestion.direction)}>
                {signedAmount(suggestion.direction, suggestion.amount)}
              </strong>
              <em>{formatFullDate(suggestion.next_date)}</em>
            </span>
          </button>
          <div className="upcoming-actions">
            <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={() => onReview(suggestion)}>
              Review
            </button>
            <button type="button" className="dashboard-card-link dashboard-card-action-button" onClick={() => onDismiss(suggestion)}>
              Dismiss
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
