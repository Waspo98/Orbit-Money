import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import FilterSheet from '../components/FilterSheet.jsx';
import { RuleEditor } from './Rules.jsx';

const DEFAULT_PAGE_SIZE = 50;
const PAGE_SIZE_OPTIONS = [25, 50, 100];

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

// CSS animation durations (kept in sync with index.css).
const EXPAND_ANIMATION_MS = 220;
const EXPAND_ANIMATION_BUFFER_MS = 40;

const SORT_OPTIONS = [
  { value: 'date_desc',        label: 'Newest first' },
  { value: 'date_asc',         label: 'Oldest first' },
  { value: 'abs_amount_desc',  label: 'Biggest first' },
  { value: 'abs_amount_asc',   label: 'Smallest first' },
  { value: 'amount_desc',      label: 'Most positive' },
  { value: 'amount_asc',       label: 'Most negative' },
  { value: 'merchant_asc',     label: 'Merchant A–Z' }
];

// ============================================================================
// Formatting helpers
// ============================================================================

function parseLocalDate(iso) {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function formatDayMonth(iso) {
  const dt = parseLocalDate(iso);
  if (!dt) return '';
  return dt.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function formatFullDate(iso) {
  const dt = parseLocalDate(iso);
  if (!dt) return '';
  return dt.toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  });
}

function monthKey(iso) {
  return iso ? iso.slice(0, 7) : '';
}

function monthLabel(key) {
  const [y, m] = key.split('-').map(Number);
  return `${MONTH_NAMES[m - 1]} ${y}`;
}

function formatAmount(amount) {
  const abs = Math.abs(amount).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD'
  });
  return amount < 0 ? `−${abs}` : `+${abs}`;
}

function formatShortAmount(amount) {
  return Number(amount).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  });
}

function groupByMonth(items) {
  const groups = [];
  let current = null;
  for (const item of items) {
    const key = monthKey(item.date);
    if (!current || current.key !== key) {
      current = { key, label: monthLabel(key), items: [] };
      groups.push(current);
    }
    current.items.push(item);
  }
  return groups;
}

function describeEditSource(source) {
  if (!source) return null;
  if (source === 'user') return 'edited manually';
  if (source.startsWith('rule:')) return 'applied by rule';
  return 'edited';
}

function useMorphingHero() {
  const [state, setState] = useState({ progress: 0, height: 612 });
  const frameRef = useRef(null);

  useEffect(() => {
    function expandedHeight() {
      return window.innerWidth <= 560 ? 612 : 520;
    }

    function update() {
      if (frameRef.current) return;

      frameRef.current = window.requestAnimationFrame(() => {
        frameRef.current = null;
        const expanded = expandedHeight();
        const collapsed = 56;
        const collapseDistance = expanded - collapsed;
        const progress = Math.min(1, Math.max(0, window.scrollY / collapseDistance));
        const height = Math.round(expanded - (expanded - collapsed) * progress);

        setState((prev) =>
          Math.abs(prev.progress - progress) > 0.01 || prev.height !== height
            ? { progress, height }
            : prev
        );
      });
    }

    update();
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
      if (frameRef.current) window.cancelAnimationFrame(frameRef.current);
    };
  }, []);

  return state;
}

// ============================================================================
// URL <-> filter state
// ============================================================================

function parseIntCsv(v) {
  if (!v) return [];
  return v.split(',').map((x) => parseInt(x.trim(), 10)).filter(Number.isFinite);
}

function parseFiltersFromUrl(params) {
  // Back-compat: if `accounts` is absent but `account_id` (legacy single) is
  // set, treat it as a single-item list. User actions rewrite the URL to
  // the new param so this migration happens on first filter apply.
  const explicit = parseIntCsv(params.get('accounts'));
  const legacy = params.get('account_id')
    ? [parseInt(params.get('account_id'), 10)].filter(Number.isFinite)
    : [];
  const accountIds = explicit.length > 0 ? explicit : legacy;

  const categoryIds = parseIntCsv(params.get('categories'));

  const amountMin = params.get('amount_min');
  const amountMax = params.get('amount_max');

  return {
    q: params.get('q') || '',
    accountIds,
    categoryIds,
    dateFrom: params.get('date_from') || '',
    dateTo: params.get('date_to') || '',
    amountMin: amountMin !== null && amountMin !== '' ? Number(amountMin) : null,
    amountMax: amountMax !== null && amountMax !== '' ? Number(amountMax) : null,
    type: params.get('type') || 'all',
    includeIgnored: params.get('include_ignored') !== '0',
    hasEdits: params.get('has_edits') || 'any',
    sort: params.get('sort') || 'date_desc',
    pageSize: PAGE_SIZE_OPTIONS.includes(parseInt(params.get('page_size'), 10))
      ? parseInt(params.get('page_size'), 10)
      : DEFAULT_PAGE_SIZE,
    page: Math.max(1, parseInt(params.get('page'), 10) || 1)
  };
}

function filtersToSearchParams(f) {
  const p = new URLSearchParams();
  if (f.q) p.set('q', f.q);
  if (f.accountIds && f.accountIds.length) p.set('accounts', f.accountIds.join(','));
  if (f.categoryIds && f.categoryIds.length) p.set('categories', f.categoryIds.join(','));
  if (f.dateFrom) p.set('date_from', f.dateFrom);
  if (f.dateTo) p.set('date_to', f.dateTo);
  if (f.amountMin != null && f.amountMin !== '') p.set('amount_min', String(f.amountMin));
  if (f.amountMax != null && f.amountMax !== '') p.set('amount_max', String(f.amountMax));
  if (f.type && f.type !== 'all') p.set('type', f.type);
  if (f.includeIgnored === false) p.set('include_ignored', '0');
  if (f.hasEdits && f.hasEdits !== 'any') p.set('has_edits', f.hasEdits);
  if (f.sort && f.sort !== 'date_desc') p.set('sort', f.sort);
  if (f.pageSize && f.pageSize !== DEFAULT_PAGE_SIZE) p.set('page_size', String(f.pageSize));
  if (f.page && f.page !== 1) p.set('page', String(f.page));
  return p;
}

function countActiveFilters(f) {
  let n = 0;
  if (f.q) n++;
  if (f.accountIds.length) n++;
  if (f.categoryIds.length) n++;
  if (f.dateFrom || f.dateTo) n++;
  if (f.amountMin != null || f.amountMax != null) n++;
  if (f.type !== 'all') n++;
  if (f.includeIgnored === false) n++;
  if (f.hasEdits !== 'any') n++;
  return n;
}

// ============================================================================
// Main page
// ============================================================================

export default function Transactions({ accounts, categories, onOpenMenu }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const hero = useMorphingHero();

  // URL-derived filter state (single source of truth for the data query).
  const filters = useMemo(
    () => parseFiltersFromUrl(searchParams),
    [searchParams]
  );

  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [grandTotal, setGrandTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [expandedId, setExpandedId] = useState(null);
  const [editingTxn, setEditingTxn] = useState(null);
  const [newRuleFromTxn, setNewRuleFromTxn] = useState(null);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);

  // Local (debounced) search input — keeps typing snappy, writes to URL
  // after a short idle window so the server request doesn't fire per
  // keystroke.
  const [searchLocal, setSearchLocal] = useState(filters.q);
  const searchDebounceRef = useRef(null);

  // Keep the local input in sync if the URL changes from elsewhere
  // (e.g., a filter pill dismissal clears q).
  useEffect(() => {
    setSearchLocal(filters.q);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.q]);

  // ---------- Data loading ----------
  //
  // `silent: true` skips the loading-spinner swap. Useful when refreshing
  // after a mutation (e.g., rule created from a transaction) where we
  // don't want the page to collapse to a spinner — the browser would
  // clamp scrollY to the new document height, losing the user's scroll
  // position on long lists.
  async function load({ silent = false } = {}) {
    if (!silent) setLoading(true);
    setError('');
    try {
      // Reuse the same URL params; just drop `page` handling — the server
      // reads it from the query too but we'll send limit explicitly.
      const p = new URLSearchParams(searchParams);
      p.set('limit', String(filters.pageSize || DEFAULT_PAGE_SIZE));
      if (!p.has('page')) p.set('page', '1');

      const data = await api.get(`/api/transactions?${p.toString()}`);
      setItems(data.items);
      setTotal(data.total);
      setGrandTotal(data.grandTotal ?? data.total);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err.message || 'Failed to load transactions');
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // ---------- URL writers ----------
  function commitFilters(next, opts = {}) {
    // Always reset page to 1 when filters change — otherwise the user can
    // land on an empty page 5 after narrowing results.
    const withPage = { ...next, page: opts.keepPage ? next.page : 1 };
    setSearchParams(filtersToSearchParams(withPage));
    setExpandedId(null);
    if (opts.scrollTop !== false) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  function setSearch(value) {
    setSearchLocal(value);
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      commitFilters({ ...filters, q: value.trim() }, { scrollTop: false });
    }, 350);
  }

  function setSort(value) {
    commitFilters({ ...filters, sort: value }, { scrollTop: false });
  }

  function setPageSize(value) {
    commitFilters({ ...filters, pageSize: Number(value) }, { scrollTop: false });
  }

  function clearFilter(key) {
    const next = { ...filters };
    switch (key) {
      case 'q': next.q = ''; setSearchLocal(''); break;
      case 'accounts': next.accountIds = []; break;
      case 'categories': next.categoryIds = []; break;
      case 'date': next.dateFrom = ''; next.dateTo = ''; break;
      case 'amount': next.amountMin = null; next.amountMax = null; break;
      case 'type': next.type = 'all'; break;
      case 'ignored': next.includeIgnored = true; break;
      case 'edits': next.hasEdits = 'any'; break;
      default: break;
    }
    commitFilters(next);
  }

  function clearAllFilters() {
    setSearchLocal('');
    commitFilters({
      q: '',
      accountIds: [],
      categoryIds: [],
      dateFrom: '',
      dateTo: '',
      amountMin: null,
      amountMax: null,
      type: 'all',
      includeIgnored: true,
      hasEdits: 'any',
      pageSize: filters.pageSize,
      sort: filters.sort
    });
  }

  function applyFromSheet(draft) {
    commitFilters({ ...filters, ...draft });
  }

  function goToPage(p) {
    const next = { ...filters, page: p };
    setSearchParams(filtersToSearchParams(next));
    setExpandedId(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---------- Local mutation helpers ----------
  function applyLocalPatch(id, patch) {
    setItems((prev) => prev.map((t) => (t.id === id ? { ...t, ...patch } : t)));
  }
  function replaceLocal(id, full) {
    setItems((prev) => prev.map((t) => (t.id === id ? full : t)));
  }
  function removeLocal(id) {
    setItems((prev) => prev.filter((t) => t.id !== id));
    setTotal((t) => Math.max(0, t - 1));
    setExpandedId(null);
  }

  async function handleToggle(txn, field) {
    const nextValue = !txn[field];
    applyLocalPatch(txn.id, { [field]: nextValue ? 1 : 0 });
    try {
      const result = await api.patch(`/api/transactions/${txn.id}`, {
        [field]: nextValue
      });
      if (result?.transaction) replaceLocal(txn.id, result.transaction);
    } catch (err) {
      applyLocalPatch(txn.id, { [field]: txn[field] });
      alert(err.message || 'Toggle failed');
    }
  }

  async function handleDelete(txn) {
    if (!confirm(`Delete this transaction? "${txn.merchant}" for ${formatAmount(txn.amount)}`)) {
      return;
    }
    try {
      await api.del(`/api/transactions/${txn.id}`);
      removeLocal(txn.id);
    } catch (err) {
      alert(err.message || 'Delete failed');
    }
  }

  async function handleResetField(txn, field) {
    try {
      const result = await api.post(`/api/transactions/${txn.id}/reset`, {
        fields: [field]
      });
      if (result?.transaction) replaceLocal(txn.id, result.transaction);
    } catch (err) {
      alert(err.message || 'Reset failed');
    }
  }

  const accountById = new Map(accounts.map((a) => [a.id, a]));
  const categoryById = new Map(categories.map((c) => [c.id, c]));

  const activeCount = countActiveFilters(filters);
  const isFiltered = activeCount > 0;
  const groups = groupByMonth(items);
  const monthlyTransactionCount = groups[0]?.items.length || total;
  const visibleIncome = items
    .filter((t) => t.amount > 0 && !t.is_transfer && !t.is_ignored)
    .reduce((sum, t) => sum + Number(t.amount || 0), 0);
  const visibleOutflow = items
    .filter((t) => t.amount < 0 && !t.is_transfer && !t.is_ignored)
    .reduce((sum, t) => sum + Math.abs(Number(t.amount || 0)), 0);
  const visibleNet = visibleIncome - visibleOutflow;

  // Onboarding empty state — only when no filters AND nothing exists at all.
  if (!loading && grandTotal === 0 && !isFiltered) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">§</div>
        <h2>No transactions yet</h2>
        <p>
          Import your Rocket Money export to bring over your full history,
          preserve your custom merchant names, and generate rename rules.
        </p>
        <button
          type="button"
          className="btn-primary"
          onClick={() => navigate('/import')}
        >
          Import from Rocket Money
        </button>
      </div>
    );
  }

  return (
    <div className="transactions-view">
      <section
        className="page-hero page-hero-transactions"
        aria-labelledby="transactions-title"
        style={{
          '--hero-progress': hero.progress,
          '--hero-content-opacity': Math.max(0, 1 - hero.progress * 1.35),
          height: `${hero.height}px`
        }}
      >
        <div className="page-hero-inner">
        <div className="page-hero-chrome">
          <button
            type="button"
            className="hero-brand brand-home"
            onClick={() => navigate('/dashboard')}
            aria-label="Go to dashboard"
          >
            <span className="brand-mark">$</span>
            <span className="brand-name">Orbit Money</span>
          </button>
          <button
            type="button"
            className="btn-icon hero-menu-button"
            onClick={onOpenMenu}
            aria-label="Open menu"
          >
            ☰
          </button>
        </div>
        <div className="page-hero-content">
        <div className="page-hero-topline">
          <div className="page-hero-main">
            <div className="page-kicker">Money movement</div>
            <h2 id="transactions-title">Transactions</h2>
            <p>
              {isFiltered
                ? `${monthlyTransactionCount.toLocaleString()} monthly transactions`
                : `${monthlyTransactionCount.toLocaleString()} monthly transactions`}
            </p>
          </div>

          <div className="page-hero-stats" aria-label="Transaction summary">
            <div className="page-hero-stat good">
              <span>Monthly income</span>
              <strong>{formatShortAmount(visibleIncome)}</strong>
            </div>
            <div className="page-hero-stat caution">
              <span>Monthly expenses</span>
              <strong>{formatShortAmount(visibleOutflow)}</strong>
            </div>
            <div className={`page-hero-stat ${visibleNet >= 0 ? 'good' : 'caution'}`}>
              <span>Monthly net</span>
              <strong>{formatAmount(visibleNet)}</strong>
            </div>
            <div className="page-hero-stat">
              <span>Filters</span>
              <strong>{activeCount || 'None'}</strong>
            </div>
          </div>
        </div>

      {/* ---------- Search + toolbar ---------- */}
      <div className="txn-toolbar txn-toolbar-hero">
        <div className="txn-search-wrap">
          <span className="txn-search-icon" aria-hidden="true">⌕</span>
          <input
            type="search"
            className="txn-search-input"
            placeholder="Search merchant, description, notes…"
            value={searchLocal}
            onChange={(e) => setSearch(e.target.value)}
          />
          {searchLocal && (
            <button
              type="button"
              className="txn-search-clear"
              onClick={() => setSearch('')}
              aria-label="Clear search"
            >
              ✕
            </button>
          )}
        </div>
        <div className="txn-toolbar-actions">
          <button
            type="button"
            className={`btn-secondary ${activeCount > 0 ? 'btn-active' : ''}`}
            onClick={() => setFilterSheetOpen(true)}
          >
            ⚙ Filter{activeCount > 0 ? ` (${activeCount})` : ''}
          </button>
          <label className="txn-sort">
            <span className="visually-hidden">Sort</span>
            <select
              value={filters.sort}
              onChange={(e) => setSort(e.target.value)}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="txn-page-size">
            <span className="visually-hidden">Transactions per page</span>
            <select
              value={filters.pageSize}
              onChange={(e) => setPageSize(e.target.value)}
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size} / page
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
        </div>
        </div>
      </section>
      <div className="page-hero-spacer page-hero-transactions-spacer" aria-hidden="true" />

      {/* ---------- Active filter pills ---------- */}
      {activeCount > 0 && (
        <ActiveFilterPills
          filters={filters}
          accountById={accountById}
          categoryById={categoryById}
          onClear={clearFilter}
          onClearAll={clearAllFilters}
        />
      )}

      {error && <div className="error">{error}</div>}

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : total === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">§</div>
          <h2>No transactions match your filters</h2>
          <p>Try widening the date range or removing some criteria.</p>
          <button type="button" className="btn-secondary" onClick={clearAllFilters}>
            Clear all filters
          </button>
        </div>
      ) : (
        <>
          {groups.map((group) => (
            <section key={group.key} className="txn-month-group">
              <header className="txn-month-header">
                <span className="txn-month-label">{group.label}</span>
                <span className="txn-month-count">
                  {group.items.length}{' '}
                  {group.items.length === 1 ? 'transaction' : 'transactions'}
                </span>
              </header>

              <ul className="txn-list">
                {group.items.map((t) => (
                  <TransactionRow
                    key={t.id}
                    txn={t}
                    expanded={expandedId === t.id}
                    onExpand={() =>
                      setExpandedId(expandedId === t.id ? null : t.id)
                    }
                    account={accountById.get(t.account_id)}
                    category={categoryById.get(t.category_id)}
                    originalCategory={categoryById.get(t.original_category_id)}
                    hideAccountInMeta={filters.accountIds.length === 1}
                    onEdit={() => setEditingTxn(t)}
                    onCreateRule={() => setNewRuleFromTxn(t)}
                    onToggleTransfer={() => handleToggle(t, 'is_transfer')}
                    onToggleIgnored={() => handleToggle(t, 'is_ignored')}
                    onDelete={() => handleDelete(t)}
                    onResetField={(field) => handleResetField(t, field)}
                  />
                ))}
              </ul>
            </section>
          ))}

          <div className="pagination">
            <button
              type="button"
              className="btn-secondary"
              disabled={filters.page <= 1}
              onClick={() => goToPage(filters.page - 1)}
            >
              ← Previous
            </button>
            <span className="page-info">
              Page {filters.page} of {totalPages}
            </span>
            <button
              type="button"
              className="btn-secondary"
              disabled={filters.page >= totalPages}
              onClick={() => goToPage(filters.page + 1)}
            >
              Next →
            </button>
          </div>
        </>
      )}

      {editingTxn && (
        <EditTransactionModal
          txn={editingTxn}
          categories={categories}
          onClose={() => setEditingTxn(null)}
          onSaved={(updated) => {
            if (updated) replaceLocal(editingTxn.id, updated);
            setEditingTxn(null);
          }}
          onReset={(field) => handleResetField(editingTxn, field)}
        />
      )}

      {newRuleFromTxn && (
        <RuleEditor
          rule={{
            // Seed the full rule editor with the obvious starting point
            // for a "make a rule like this transaction" flow. The user
            // gets the full condition/action builder — multiple conditions,
            // amount filters, categorize, mark transfer, etc.
            conditions: [
              {
                field: 'merchant',
                operator: 'contains',
                value: (newRuleFromTxn.original_merchant || newRuleFromTxn.merchant || '').trim()
              }
            ],
            actions: [
              { type: 'rename', value: newRuleFromTxn.merchant || '' }
            ],
            enabled: true
          }}
          accounts={accounts}
          categories={categories}
          onClose={() => setNewRuleFromTxn(null)}
          onSaved={() => {
            setNewRuleFromTxn(null);
            // Silent reload — avoids the spinner swap that would clamp
            // scrollY and jump the user to the top of the list.
            load({ silent: true });
          }}
        />
      )}

      {filterSheetOpen && (
        <FilterSheet
          initial={filters}
          accounts={accounts}
          categories={categories}
          onApply={applyFromSheet}
          onClose={() => setFilterSheetOpen(false)}
        />
      )}
    </div>
  );
}

// ============================================================================
// Active filter pills — one per active filter, dismissible
// ============================================================================

function ActiveFilterPills({
  filters,
  accountById,
  categoryById,
  onClear,
  onClearAll
}) {
  const pills = [];

  if (filters.q) {
    pills.push({ key: 'q', label: `“${filters.q}”` });
  }

  if (filters.accountIds.length) {
    const names = filters.accountIds
      .map((id) => accountById.get(id)?.name || `#${id}`)
      .join(', ');
    pills.push({
      key: 'accounts',
      label: filters.accountIds.length === 1 ? names : `${filters.accountIds.length} accounts`,
      title: names
    });
  }

  if (filters.categoryIds.length) {
    const names = filters.categoryIds
      .map((id) => categoryById.get(id)?.name || `#${id}`)
      .join(', ');
    pills.push({
      key: 'categories',
      label: filters.categoryIds.length === 1
        ? names
        : `${filters.categoryIds.length} categories`,
      title: names
    });
  }

  if (filters.dateFrom || filters.dateTo) {
    const from = filters.dateFrom ? formatDayMonth(filters.dateFrom) : '—';
    const to = filters.dateTo ? formatDayMonth(filters.dateTo) : 'today';
    pills.push({ key: 'date', label: `${from} → ${to}` });
  }

  if (filters.amountMin != null || filters.amountMax != null) {
    const lo = filters.amountMin != null ? formatShortAmount(filters.amountMin) : '0';
    const hi = filters.amountMax != null ? formatShortAmount(filters.amountMax) : '∞';
    pills.push({ key: 'amount', label: `${lo} – ${hi}` });
  }

  if (filters.type !== 'all') {
    const label =
      filters.type === 'income'
        ? 'Income only'
        : filters.type === 'expense'
          ? 'Expenses only'
          : 'Transfers only';
    pills.push({ key: 'type', label });
  }

  if (filters.includeIgnored === false) {
    pills.push({ key: 'ignored', label: 'Excl. ignored' });
  }

  if (filters.hasEdits !== 'any') {
    pills.push({
      key: 'edits',
      label: filters.hasEdits === 'yes' ? 'Has edits' : 'No edits'
    });
  }

  return (
    <div className="active-pills-row">
      {pills.map((p) => (
        <button
          key={p.key}
          type="button"
          className="active-pill"
          onClick={() => onClear(p.key)}
          title={p.title || 'Remove filter'}
        >
          <span>{p.label}</span>
          <span className="active-pill-x" aria-hidden="true">✕</span>
        </button>
      ))}
      {pills.length > 1 && (
        <button
          type="button"
          className="active-pill active-pill-clearall"
          onClick={onClearAll}
        >
          Clear all
        </button>
      )}
    </div>
  );
}

// ============================================================================
// Transaction row — expand/contract animation via grid-template-rows,
// scroll-into-view accounts for bottom tabs
// ============================================================================

export function TransactionRow({
  txn,
  expanded,
  onExpand,
  account,
  category,
  originalCategory,
  hideAccountInMeta,
  onEdit,
  onCreateRule,
  onToggleTransfer,
  onToggleIgnored,
  onDelete,
  onResetField
}) {
  const isIncome = txn.amount > 0 && !txn.is_transfer;
  const isTransfer = !!txn.is_transfer;

  const rowRef = useRef(null);
  const prevExpandedRef = useRef(expanded);

  // Scroll-into-view after the expand animation settles. Uses a manual
  // scrollBy (rather than element.scrollIntoView) so we can account for the
  // bottom-tabs bar overlapping the viewport — scrollIntoView treats the
  // whole window.innerHeight as usable area and leaves the bottom of the
  // expanded card hidden under the tabs.
  useEffect(() => {
    const wasExpanded = prevExpandedRef.current;
    prevExpandedRef.current = expanded;

    if (!expanded || wasExpanded) return;

    const timer = setTimeout(() => {
      const el = rowRef.current;
      if (!el) return;

      const rect = el.getBoundingClientRect();
      const viewportHeight = window.innerHeight;

      // Mobile/tablet: bottom-tabs is 80px tall and fixed at bottom.
      // Desktop (>=1080px): tabs are display:none so offset is 0.
      const tabsEl = document.querySelector('.bottom-tabs');
      let tabsHeight = 0;
      if (tabsEl) {
        const style = window.getComputedStyle(tabsEl);
        if (style.display !== 'none') {
          tabsHeight = tabsEl.getBoundingClientRect().height;
        }
      }
      const bottomPadding = 16;
      const effectiveBottom = viewportHeight - tabsHeight - bottomPadding;

      if (rect.bottom > effectiveBottom) {
        const overflow = Math.ceil(rect.bottom - effectiveBottom);
        // If the whole card is taller than the viewport, at least align
        // the top just below the header — otherwise scroll just enough.
        const maxScroll = Math.max(0, rect.top - 16);
        const scrollBy = Math.min(overflow, Math.max(overflow, 0) + maxScroll);
        window.scrollBy({ top: scrollBy, behavior: 'smooth' });
      }
    }, EXPAND_ANIMATION_MS + EXPAND_ANIMATION_BUFFER_MS);

    return () => clearTimeout(timer);
  }, [expanded]);

  function handleRowClick(e) {
    // Ignore clicks on interactive elements (buttons, links, inputs).
    // Also ignore clicks that happened inside the expanded detail area —
    // otherwise tapping the expanded content would collapse the card.
    if (e.target.closest('button, a, input, select, textarea, [role=button]')) return;
    if (expanded && e.target.closest('.txn-detail-wrapper')) return;
    onExpand();
  }

  return (
    <li
      ref={rowRef}
      className={`txn-row ${txn.is_ignored ? 'ignored' : ''} ${isTransfer ? 'transfer' : ''} ${expanded ? 'expanded' : ''} ${txn.has_edits ? 'has-edits' : ''}`}
      onClick={handleRowClick}
    >
      <div className="txn-row-summary">
        <div className="txn-main">
          <div className="txn-merchant">{txn.merchant}</div>
          <div className="txn-meta">
            <span className="txn-date-short">{formatDayMonth(txn.date)}</span>
            {category && (
              <>
                <span className="dot" />
                <span
                  className="txn-category-chip"
                  style={{ color: category.color }}
                >
                  <span>{category.icon}</span>
                  <span>{category.name}</span>
                </span>
              </>
            )}
            {account && !hideAccountInMeta && (
              <>
                <span className="dot" />
                <span>
                  {account.name}
                  {account.account_number_last4 && ` ···${account.account_number_last4}`}
                </span>
              </>
            )}
          </div>
        </div>

        <div className={`txn-amount ${isIncome ? 'income' : ''}`}>
          {formatAmount(txn.amount)}
        </div>
      </div>

      {/* Always-mounted detail wrapper — the expand/contract is animated
          via grid-template-rows (0fr ↔ 1fr) on this container. Content
          inside is overflow: hidden so it clips during the animation. */}
      <div className="txn-detail-wrapper" aria-hidden={!expanded}>
        <TransactionDetail
          txn={txn}
          account={account}
          category={category}
          originalCategory={originalCategory}
          interactive={expanded}
          onEdit={onEdit}
          onCreateRule={onCreateRule}
          onToggleTransfer={onToggleTransfer}
          onToggleIgnored={onToggleIgnored}
          onDelete={onDelete}
          onResetField={onResetField}
        />
      </div>
    </li>
  );
}

// ============================================================================
// Transaction detail (expanded content)
// ============================================================================

function TransactionDetail({
  txn,
  account,
  category,
  originalCategory,
  interactive,
  onEdit,
  onCreateRule,
  onToggleTransfer,
  onToggleIgnored,
  onDelete,
  onResetField
}) {
  const merchantEdited = txn.edited_merchant_source !== null;
  const categoryEdited = txn.edited_category_id_source !== null;
  const transferEdited = txn.edited_is_transfer_source !== null;
  const ignoredEdited = txn.edited_is_ignored_source !== null;

  const merchantSourceLabel = describeEditSource(txn.edited_merchant_source);
  const categorySourceLabel = describeEditSource(txn.edited_category_id_source);

  // When collapsed, the buttons are visually hidden but still in the DOM.
  // tabIndex=-1 removes them from tab order so keyboard users don't land
  // on invisible controls.
  const tabProps = interactive ? {} : { tabIndex: -1 };

  return (
    <div className="txn-detail">
      <dl className="txn-detail-grid">
        <div>
          <dt>Date</dt>
          <dd>{formatFullDate(txn.date)}</dd>
        </div>
        <div>
          <dt>Account</dt>
          <dd>
            {account ? (
              <>
                {account.name}
                {account.institution && (
                  <span className="subtle">  ·  {account.institution}</span>
                )}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>

        <div className="txn-detail-full">
          <dt>
            Merchant
            {merchantEdited && (
              <span className="edit-badge">
                {merchantSourceLabel}
                <button
                  type="button"
                  className="linkish"
                  onClick={() => onResetField('merchant')}
                  title="Revert to the original bank value"
                  {...tabProps}
                >
                  reset
                </button>
              </span>
            )}
          </dt>
          <dd>
            {txn.merchant}
            {merchantEdited && (
              <span className="subtle txn-original">
                Originally: <code>{txn.original_merchant}</code>
              </span>
            )}
          </dd>
        </div>

        <div>
          <dt>
            Category
            {categoryEdited && (
              <span className="edit-badge">
                {categorySourceLabel}
                <button
                  type="button"
                  className="linkish"
                  onClick={() => onResetField('category_id')}
                  title="Revert to the original category"
                  {...tabProps}
                >
                  reset
                </button>
              </span>
            )}
          </dt>
          <dd>
            {category ? (
              <span style={{ color: category.color }}>
                {category.icon} {category.name}
              </span>
            ) : (
              <span className="subtle">Uncategorized</span>
            )}
            {categoryEdited && originalCategory && (
              <span className="subtle txn-original">
                Originally: {originalCategory.icon} {originalCategory.name}
              </span>
            )}
          </dd>
        </div>

        <div>
          <dt>Original description</dt>
          <dd className="txn-detail-mono">
            {txn.original_description || <span className="subtle">—</span>}
          </dd>
        </div>

        {txn.notes && (
          <div className="txn-detail-full">
            <dt>Notes</dt>
            <dd>{txn.notes}</dd>
          </div>
        )}

        <div className="txn-detail-full">
          <dt>Budget</dt>
          <dd className="subtle">
            <a
              href={`/budgets?month=${encodeURIComponent(
                (txn.date || '').slice(0, 7)
              )}`}
              className="linkish"
            >
              View budgets for {(txn.date || '').slice(0, 7) || 'this month'}
            </a>
            {category && <> · {category.name}</>}
          </dd>
        </div>
      </dl>

      <div className="txn-detail-actions">
        <button type="button" className="btn-secondary btn-compact" onClick={onEdit} {...tabProps}>
          ✎ Edit
        </button>
        <button
          type="button"
          className="btn-secondary btn-compact"
          onClick={onCreateRule}
          {...tabProps}
        >
          ⊙ Create rule
        </button>
        <button
          type="button"
          className={`btn-secondary btn-compact ${txn.is_transfer ? 'btn-active' : ''} ${transferEdited ? 'edited' : ''}`}
          onClick={onToggleTransfer}
          {...tabProps}
        >
          ⇌ {txn.is_transfer ? 'Unmark transfer' : 'Mark transfer'}
        </button>
        <button
          type="button"
          className={`btn-secondary btn-compact ${txn.is_ignored ? 'btn-active' : ''} ${ignoredEdited ? 'edited' : ''}`}
          onClick={onToggleIgnored}
          {...tabProps}
        >
          ⊘ {txn.is_ignored ? 'Unignore' : 'Ignore'}
        </button>
        <button
          type="button"
          className="btn-danger btn-compact"
          onClick={onDelete}
          {...tabProps}
        >
          ✕ Delete
        </button>
      </div>
    </div>
  );
}

// ============================================================================
// Edit transaction modal
// ============================================================================

export function EditTransactionModal({ txn, categories, onClose, onSaved, onReset }) {
  const [merchant, setMerchant] = useState(txn.merchant || '');
  const [categoryId, setCategoryId] = useState(txn.category_id || '');
  const [notes, setNotes] = useState(txn.notes || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const merchantEdited = txn.edited_merchant_source !== null;
  const categoryEdited = txn.edited_category_id_source !== null;

  async function handleSave(e, close) {
    e.preventDefault();
    setSaving(true);
    setError('');

    const patch = {
      merchant: merchant.trim(),
      category_id: categoryId ? Number(categoryId) : null,
      notes
    };

    try {
      const result = await api.patch(`/api/transactions/${txn.id}`, patch);
      close();
      setTimeout(() => onSaved(result?.transaction), 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Edit transaction</h3>

          <form onSubmit={(e) => handleSave(e, close)}>
            <label className="field">
              <span>
                Merchant
                {merchantEdited && (
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => {
                      onReset('merchant');
                      close();
                    }}
                    title="Clear the edit and let any matching rule re-apply"
                  >
                    reset to original ({txn.original_merchant})
                  </button>
                )}
              </span>
              <input
                type="text"
                value={merchant}
                onChange={(e) => setMerchant(e.target.value)}
                required
                autoFocus
              />
            </label>

            <label className="field">
              <span>
                Category
                {categoryEdited && (
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => {
                      onReset('category_id');
                      close();
                    }}
                    title="Clear the edit and let any matching rule re-apply"
                  >
                    reset to original
                  </button>
                )}
              </span>
              <select
                value={categoryId || ''}
                onChange={(e) => setCategoryId(e.target.value)}
              >
                <option value="">(Uncategorized)</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.icon} {c.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Notes</span>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Optional"
                rows={3}
                style={{ fontFamily: 'var(--font-sans)', fontSize: '0.92rem' }}
              />
            </label>

            <p className="subtle" style={{ marginTop: -10, marginBottom: 12 }}>
              Date ({formatFullDate(txn.date)}) and amount ({formatAmount(txn.amount)})
              are not editable — they're ground truth from the bank.
            </p>

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
