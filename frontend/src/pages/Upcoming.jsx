import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import { OVERLAY_ANIM_MS } from '../components/overlayBehavior.js';
import PageHero from '../components/PageHero.jsx';
import SearchField from '../components/SearchField.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import RecurringItemEditor, {
  formFromRecurringItem,
  formFromSuggestion,
  formFromTransaction,
  kindLabel,
  newRecurringForm,
  recurringFrequencyLabel
} from '../components/upcoming/RecurringItemEditor.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { formatCurrency, formatSignedCurrency } from '../lib/formatters.js';
import { formatFullDate, formatMonthDay } from '../lib/localDate.js';

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'income', label: 'Income' },
  { value: 'giving', label: 'Giving' },
  { value: 'subscription', label: 'Subscriptions' },
  { value: 'bill', label: 'Bills' },
  { value: 'suggested', label: 'Suggestions' }
];

const LANE_ORDER = ['income', 'giving', 'subscription', 'bill'];

const KIND_TITLES = {
  bill: 'Bills',
  subscription: 'Subscriptions',
  income: 'Income',
  giving: 'Giving'
};

const KIND_LABELS = {
  bill: 'Bill',
  subscription: 'Subscription',
  income: 'Income',
  giving: 'Giving'
};

const GIVING_HINTS = [
  'charitable',
  'charity',
  'church',
  'donation',
  'donations',
  'giving',
  'nonprofit',
  'non-profit',
  'tithe'
];

const MODAL_HANDOFF_DELAY_MS = OVERLAY_ANIM_MS + 80;

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
  const transactions = item.projection?.sample_count;
  if (transactions) return `Projected from ${transactions} transaction${transactions === 1 ? '' : 's'}`;
  const months = item.projection?.sample_months;
  if (months) return `Projected from ${months} month${months === 1 ? '' : 's'}`;
  return `Projected from ${item.amount_lookback_months || 6} months`;
}

function itemDisplayAmount(item) {
  return Number(item.projected_amount ?? item.amount ?? 0);
}

function isGivingItem(item) {
  if (item?.kind === 'giving') return true;
  if (item?.kind === 'income' || item?.direction === 'income') return false;
  const text = [
    item?.category_name,
    item?.name,
    item?.merchant,
    item?.notes
  ].join(' ').toLowerCase();
  return GIVING_HINTS.some((hint) => text.includes(hint));
}

function itemsForKind(items, kind) {
  if (kind === 'giving') return items.filter(isGivingItem);
  if (kind === 'bill' || kind === 'subscription') {
    return items.filter((item) => item.kind === kind && !isGivingItem(item));
  }
  return items.filter((item) => item.kind === kind);
}

function kindTotal(items, kind) {
  return itemsForKind(items, kind).reduce((sum, item) => sum + itemDisplayAmount(item), 0);
}

function upcomingKindLabel(kind) {
  return KIND_LABELS[kind] || kindLabel(kind);
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
  const [transactionPicker, setTransactionPicker] = useState(null);
  const [historyItem, setHistoryItem] = useState(null);

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
    bill: itemsForKind(items, 'bill'),
    giving: itemsForKind(items, 'giving'),
    subscription: itemsForKind(items, 'subscription'),
    income: itemsForKind(items, 'income')
  }), [items]);

  const upcomingTotals = useMemo(() => ({
    bill: kindTotal(items, 'bill'),
    giving: kindTotal(items, 'giving'),
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

  function openTransactionPicker(kind = 'bill') {
    setTransactionPicker({ kind });
  }

  function openTransactionReview(txn, kind = 'bill') {
    const category = categories.find((categoryOption) => categoryOption.id === txn.category_id);
    const reviewKind = kind;
    setTransactionPicker(null);
    setEditor({
      mode: 'create',
      title: `Review ${upcomingKindLabel(kind)}`,
      form: {
        ...formFromTransaction(txn, category),
        kind: reviewKind,
        direction: reviewKind === 'income' ? 'income' : 'expense'
      }
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
    { label: 'Upcoming Income', value: formatCurrency(upcomingTotals.income), tone: 'good' },
    { label: 'Upcoming Giving', value: formatCurrency(upcomingTotals.giving), tone: 'caution' },
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
        toolbar={
          <div className="page-hero-action-row">
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

            <div className="housing-segmented upcoming-tabs" role="tablist" aria-label="Upcoming filters">
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
              onAddFromTransaction={openTransactionPicker}
              onEdit={openEdit}
              onDelete={deleteItem}
              onViewHistory={setHistoryItem}
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

      {transactionPicker && (
        <TransactionPickerModal
          kind={transactionPicker.kind}
          accounts={accounts}
          categories={categories}
          onPick={(txn) => openTransactionReview(txn, transactionPicker.kind)}
          onClose={() => setTransactionPicker(null)}
        />
      )}

      {historyItem && (
        <RecurringHistoryModal
          item={historyItem}
          accounts={accounts}
          categories={categories}
          onEdit={() => {
            const item = historyItem;
            setHistoryItem(null);
            openEdit(item);
          }}
          actionLabel={historyItem.key ? 'Review Suggestion' : 'Edit Recurring'}
          onReviewSuggestion={historyItem.key ? () => {
            const suggestion = historyItem;
            setHistoryItem(null);
            openSuggestion(suggestion);
          } : undefined}
          onClose={() => setHistoryItem(null)}
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
    <section className="dashboard-card upcoming-cashflow-panel" aria-label="Projected cash flow">
      <header className="dashboard-card-header upcoming-card-header">
        <h3>Rest Of Month</h3>
        <span className="muted upcoming-card-subtitle">
          {transactionCount} transaction{transactionCount === 1 ? '' : 's'} remaining in the next {daysRemaining} day{daysRemaining === 1 ? '' : 's'}
        </span>
      </header>

      <div className="dashboard-card-body upcoming-cashflow-body">
        <div className="upcoming-cashflow-summary">
          <span>Projected Net</span>
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
      </div>
    </section>
  );
}

function UpcomingPlan({
  filter,
  groupedItems,
  items,
  suggestions,
  onAddFromTransaction,
  onEdit,
  onDelete,
  onViewHistory,
  onReviewSuggestion,
  onDismissSuggestion,
  onViewSuggestions
}) {
  if (filter === 'suggested') {
    return (
      <SuggestionsPanel
        suggestions={suggestions}
        onViewHistory={onViewHistory}
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
        onAdd={() => onAddFromTransaction(filter)}
        onEdit={onEdit}
        onDelete={onDelete}
        onViewHistory={onViewHistory}
      />
    );
  }

  return (
    <div className="upcoming-lanes">
      {LANE_ORDER.map((kind) => (
        <UpcomingLane
          key={kind}
          kind={kind}
          items={groupedItems[kind] || []}
          total={kindTotal(items, kind)}
          onAdd={() => onAddFromTransaction(kind)}
          onEdit={onEdit}
          onDelete={onDelete}
          onViewHistory={onViewHistory}
        />
      ))}
      {suggestions.length > 0 && (
        <section className="dashboard-card upcoming-lane upcoming-lane-suggestions">
          <header className="dashboard-card-header upcoming-card-header">
            <h3>Suggested Recurring</h3>
            <span className="pill accent upcoming-count-pill">{suggestions.length}</span>
          </header>
          <div className="dashboard-card-body upcoming-lane-body">
            <SuggestionsList
              suggestions={suggestions.slice(0, 4)}
              onViewHistory={onViewHistory}
              onReview={onReviewSuggestion}
              onDismiss={onDismissSuggestion}
              compact
            />
          </div>
        </section>
      )}
    </div>
  );
}

function UpcomingLane({ kind, items, total, onAdd, onEdit, onDelete, onViewHistory }) {
  return (
    <section className={`dashboard-card upcoming-lane upcoming-lane-${kind}`}>
      <header className="dashboard-card-header upcoming-card-header">
        <h3>{KIND_TITLES[kind]}</h3>
        <span className="upcoming-card-total">{formatCurrency(total)}</span>
      </header>

      <div className="dashboard-card-body upcoming-lane-body">
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
                onViewHistory={() => onViewHistory(item)}
                onEdit={() => onEdit(item)}
                onDelete={() => onDelete(item)}
              />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function RecurringRow({ item, onViewHistory, onEdit, onDelete }) {
  const projection = projectedLabel(item);
  return (
    <li className={`selectable-list-item upcoming-plan-row upcoming-item-${item.kind}`}>
      <button
        type="button"
        className="upcoming-plan-row-main"
        onClick={onViewHistory}
        aria-label={`View previous transactions for ${item.name}`}
      >
        <span className="selectable-list-leading upcoming-row-date">
          <strong>{formatMonthDay(item.next_date)}</strong>
          <em>{dueLabel(item.next_date)}</em>
        </span>
        <span className="selectable-list-main upcoming-row-copy">
          <strong>{item.name}</strong>
          <em>
            {item.category_name || 'Uncategorized'} - {recurringFrequencyLabel(item)}
          </em>
          {projection && <span className="pill accent upcoming-projection-pill">{projection}</span>}
        </span>
        <span className="selectable-list-side upcoming-row-amount">
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

function TransactionPickerModal({ kind, accounts, categories, onPick, onClose }) {
  const [query, setQuery] = useState('');
  const [transactions, setTransactions] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const accountById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);
  const categoryById = useMemo(() => new Map(categories.map((category) => [category.id, category])), [categories]);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(async () => {
      setLoading(true);
      setError('');
      try {
        const params = new URLSearchParams({
          page: '1',
          limit: '100',
          sort: 'date_desc',
          include_ignored: '0',
          include_transfers: '0'
        });
        if (kind === 'income') params.set('type', 'income');
        if (query.trim()) params.set('q', query.trim());
        const result = await api.get(`/api/transactions?${params.toString()}`);
        if (!active) return;
        setTransactions(result.items || []);
        setTotal(Number(result.total || 0));
      } catch (err) {
        if (!active) return;
        setError(err.message || 'Could not load transactions.');
      } finally {
        if (active) setLoading(false);
      }
    }, query.trim() ? 250 : 0);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [kind, query]);

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>Add {upcomingKindLabel(kind)} From Transaction</h3>
          <p className="modal-copy">
            {total > transactions.length
              ? `Showing ${transactions.length} of ${total} transactions.`
              : `${total} transaction${total === 1 ? '' : 's'} available.`}
          </p>

          <SearchField
            value={query}
            onChange={setQuery}
            placeholder="Search transactions"
            className="upcoming-transaction-search"
          />

          <div className="upcoming-transaction-results">
            {loading ? (
              <div className="upcoming-transaction-state"><div className="spinner" /></div>
            ) : error ? (
              <div className="error">{error}</div>
            ) : transactions.length === 0 ? (
              <div className="upcoming-mini-empty">
                <span>No transactions found.</span>
              </div>
            ) : (
              transactions.map((txn) => (
                <TransactionPickRow
                  key={txn.id}
                  txn={txn}
                  account={accountById.get(txn.account_id)}
                  category={categoryById.get(txn.category_id)}
                  onClick={() => {
                    close({ animate: true });
                    setTimeout(() => onPick(txn), MODAL_HANDOFF_DELAY_MS);
                  }}
                />
              ))
            )}
          </div>

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

function TransactionPickRow({ txn, account, category, onClick }) {
  const subtitle = [
    category ? `${category.icon} ${category.name}` : 'Uncategorized',
    account?.name
  ].filter(Boolean).join(' - ');

  return (
    <SelectableListItem
      className="upcoming-transaction-row"
      leading={formatMonthDay(txn.date)}
      title={txn.merchant || 'Transaction'}
      subtitle={subtitle}
      sidePrimary={formatSignedCurrency(txn.amount)}
      sideSecondary={formatFullDate(txn.date)}
      onClick={onClick}
      ariaLabel={`Use ${txn.merchant || 'transaction'} from ${formatFullDate(txn.date)}`}
    />
  );
}

function RecurringHistoryModal({
  item,
  accounts,
  categories,
  actionLabel = 'Edit Recurring',
  onEdit,
  onReviewSuggestion,
  onClose
}) {
  const [transactions, setTransactions] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const accountById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);
  const categoryById = useMemo(() => new Map(categories.map((category) => [category.id, category])), [categories]);
  const searchTerm = String(item.merchant || item.name || '').trim();

  useEffect(() => {
    let active = true;
    async function loadMatches() {
      setLoading(true);
      setError('');
      try {
        const params = new URLSearchParams({
          page: '1',
          limit: '50',
          sort: 'date_desc',
          include_ignored: '0',
          include_transfers: '0',
          date_to: todayIso(),
          type: item.direction === 'income' ? 'income' : 'expense'
        });
        if (searchTerm) params.set('q', searchTerm);
        else if (item.category_id) params.set('categories', String(item.category_id));
        const result = await api.get(`/api/transactions?${params.toString()}`);
        if (!active) return;
        setTransactions(result.items || []);
        setTotal(Number(result.total || 0));
      } catch (err) {
        if (!active) return;
        setError(err.message || 'Could not load matching transactions.');
      } finally {
        if (active) setLoading(false);
      }
    }

    loadMatches();
    return () => {
      active = false;
    };
  }, [item.id, item.key, item.category_id, item.direction, searchTerm]);

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>{item.name} History</h3>
          <p className="modal-copy">
            {total > transactions.length
              ? `Showing ${transactions.length} of ${total} previous matches.`
              : `${total} previous match${total === 1 ? '' : 'es'}.`}
          </p>

          <div className="upcoming-history-summary">
            <span>{recurringFrequencyLabel(item)}</span>
            <strong className={amountClass(item.direction)}>
              {signedAmount(item.direction, itemDisplayAmount(item))}
            </strong>
          </div>

          <div className="upcoming-history-list">
            {loading ? (
              <div className="upcoming-transaction-state"><div className="spinner" /></div>
            ) : error ? (
              <div className="error">{error}</div>
            ) : transactions.length === 0 ? (
              <div className="upcoming-mini-empty">
                <span>No previous transactions matched.</span>
              </div>
            ) : (
              transactions.map((txn) => (
                <HistoryTransactionRow
                  key={txn.id}
                  txn={txn}
                  account={accountById.get(txn.account_id)}
                  category={categoryById.get(txn.category_id)}
                />
              ))
            )}
          </div>

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Close
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                close({ animate: true });
                setTimeout(onReviewSuggestion || onEdit, MODAL_HANDOFF_DELAY_MS);
              }}
            >
              {actionLabel}
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

function HistoryTransactionRow({ txn, account, category }) {
  const subtitle = [
    category ? `${category.icon} ${category.name}` : 'Uncategorized',
    account?.name
  ].filter(Boolean).join(' - ');

  return (
    <div className="selectable-list-item upcoming-history-row">
      <span className="selectable-list-leading upcoming-history-date">
        {formatMonthDay(txn.date)}
      </span>
      <span className="selectable-list-main">
        <strong>{txn.merchant || 'Transaction'}</strong>
        <em>{subtitle}</em>
      </span>
      <span className="selectable-list-side">
        <strong className={txn.amount >= 0 ? 'income' : 'expense'}>{formatSignedCurrency(txn.amount)}</strong>
        <em>{formatFullDate(txn.date)}</em>
      </span>
    </div>
  );
}

function SuggestionsPanel({ suggestions, onViewHistory, onReview, onDismiss }) {
  return (
    <section className="dashboard-card upcoming-lane upcoming-suggestions-panel">
      <header className="dashboard-card-header upcoming-card-header">
        <h3>Suggested Recurring</h3>
        <span className="pill accent upcoming-count-pill">{suggestions.length}</span>
      </header>
      <div className="dashboard-card-body upcoming-lane-body">
        <SuggestionsList
          suggestions={suggestions}
          onViewHistory={onViewHistory}
          onReview={onReview}
          onDismiss={onDismiss}
        />
      </div>
    </section>
  );
}

function SuggestionsList({ suggestions, onViewHistory, onReview, onDismiss, compact = false }) {
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
        <li key={suggestion.key} className={`selectable-list-item upcoming-suggestion-row upcoming-item-${suggestion.kind}`}>
          <button
            type="button"
            className="upcoming-suggestion-main"
            onClick={() => onViewHistory(suggestion)}
            aria-label={`View previous transactions for ${suggestion.name}`}
          >
            <span className="selectable-list-leading upcoming-row-date">
              <strong>{formatMonthDay(suggestion.next_date)}</strong>
              <em>{suggestion.confidence}% Match</em>
            </span>
            <span className="selectable-list-main upcoming-row-copy">
              <strong>{suggestion.name}</strong>
              <em>
                {kindLabel(suggestion.kind)} - {suggestion.category_name || 'Uncategorized'} - {recurringFrequencyLabel(suggestion)}
              </em>
              {!compact && (
                <span className="pill accent upcoming-projection-pill">
                  {suggestion.transaction_count} transaction hit{suggestion.transaction_count === 1 ? '' : 's'}
                </span>
              )}
            </span>
            <span className="selectable-list-side upcoming-row-amount">
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
