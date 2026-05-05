import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import FilterSheet from '../components/FilterSheet.jsx';
import AppSelect from '../components/AppSelect.jsx';
import AnimatedModal from '../components/AnimatedModal.jsx';
import BrandLogo from '../components/BrandLogo.jsx';
import PageHero from '../components/PageHero.jsx';
import SearchField from '../components/SearchField.jsx';
import CurrencyInput, { parseCurrencyInput } from '../components/CurrencyInput.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { APP_ICON_192 } from '../brandAssets.js';
import { RuleEditor } from '../components/rules/RuleEditor.jsx';
import {
  EditTransactionModal,
  TransactionRow
} from '../components/transactions/TransactionRow.jsx';
import RecurringItemEditor, {
  formFromTransaction
} from '../components/upcoming/RecurringItemEditor.jsx';
import {
  formatCurrency,
  formatSignedCurrency
} from '../lib/formatters.js';
import {
  formatMonthDay,
  formatMonthKeyLabel
} from '../lib/localDate.js';

const DEFAULT_PAGE_SIZE = 50;
const PAGE_SIZE_OPTIONS = [25, 50, 100];

const SORT_OPTIONS = [
  { value: 'date_desc',        label: 'Newest first' },
  { value: 'date_asc',         label: 'Oldest first' },
  { value: 'abs_amount_desc',  label: 'Biggest first' },
  { value: 'abs_amount_asc',   label: 'Smallest first' },
  { value: 'amount_desc',      label: 'Most positive' },
  { value: 'amount_asc',       label: 'Most negative' },
  { value: 'merchant_asc',     label: 'Merchant A-Z' }
];

function TransactionToolbarIcon({ type }) {
  if (type === 'sort') {
    return (
      <svg className="txn-control-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M6 7h12M8 12h8M10 17h4" />
      </svg>
    );
  }

  return (
    <svg className="txn-control-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M5 6.5h14M5 12h14M5 17.5h14" />
      <path d="M9 4.5v4M15 10v4M11 15.5v4" />
    </svg>
  );
}

function monthKey(iso) {
  return iso ? iso.slice(0, 7) : '';
}
function monthLabel(key) {
  return formatMonthKeyLabel(key);
}

function formatAmount(amount) {
  return formatSignedCurrency(amount);
}

function formatShortAmount(amount) {
  return formatCurrency(amount, { maximumFractionDigits: 0 });
}

function todayLocalDate() {
  const now = new Date();
  const offsetDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return offsetDate.toISOString().slice(0, 10);
}

function groupByMonth(items, countsByMonth = {}) {
  const groups = [];
  let current = null;
  for (const item of items) {
    const key = monthKey(item.date);
    if (!current || current.key !== key) {
      current = {
        id: `${key}-${groups.length}`,
        key,
        label: monthLabel(key),
        count: countsByMonth[key],
        items: []
      };
      groups.push(current);
    }
    current.items.push(item);
  }
  for (const group of groups) {
    if (!Number.isFinite(group.count)) {
      group.count = group.items.length;
    }
  }
  return groups;
}

// ============================================================================
// URL <-> filter state
// ============================================================================

function parseIntCsv(v) {
  if (!v) return [];
  return v.split(',').map((x) => parseInt(x.trim(), 10)).filter(Number.isFinite);
}

function parseCategoryCsv(v) {
  if (!v) return { ids: [], includeUncategorized: false };
  const tokens = v.split(',').map((x) => x.trim()).filter(Boolean);
  return {
    ids: tokens
      .filter((token) => token !== 'uncategorized')
      .map((token) => parseInt(token, 10))
      .filter(Number.isFinite),
    includeUncategorized: tokens.includes('uncategorized')
  };
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

  const parsedCategories = parseCategoryCsv(params.get('categories'));

  const amountMin = params.get('amount_min');
  const amountMax = params.get('amount_max');

  return {
    q: params.get('q') || '',
    accountIds,
    categoryIds: parsedCategories.ids,
    includeUncategorizedCategory: parsedCategories.includeUncategorized,
    dateFrom: params.get('date_from') || '',
    dateTo: params.get('date_to') || '',
    amountMin: amountMin !== null && amountMin !== '' ? Number(amountMin) : null,
    amountMax: amountMax !== null && amountMax !== '' ? Number(amountMax) : null,
    type: params.get('type') || 'all',
    includeIgnored: params.get('include_ignored') !== '0',
    includeTransfers: params.get('include_transfers') !== '0',
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
  const categoryTokens = [
    ...((f.categoryIds && f.categoryIds.length) ? f.categoryIds.map(String) : []),
    ...(f.includeUncategorizedCategory ? ['uncategorized'] : [])
  ];
  if (categoryTokens.length) p.set('categories', categoryTokens.join(','));
  if (f.dateFrom) p.set('date_from', f.dateFrom);
  if (f.dateTo) p.set('date_to', f.dateTo);
  if (f.amountMin != null && f.amountMin !== '') p.set('amount_min', String(f.amountMin));
  if (f.amountMax != null && f.amountMax !== '') p.set('amount_max', String(f.amountMax));
  if (f.type && f.type !== 'all') p.set('type', f.type);
  if (f.includeIgnored === false) p.set('include_ignored', '0');
  if (f.includeTransfers === false) p.set('include_transfers', '0');
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
  if (f.categoryIds.length || f.includeUncategorizedCategory) n++;
  if (f.dateFrom || f.dateTo) n++;
  if (f.amountMin != null || f.amountMax != null) n++;
  if (f.type !== 'all') n++;
  if (f.includeIgnored === false) n++;
  if (f.includeTransfers === false) n++;
  if (f.hasEdits !== 'any') n++;
  return n;
}

// ============================================================================
// Main page
// ============================================================================

export default function Transactions({ accounts, categories, mhaTrackerEnabled = false }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { alert, confirm, Dialog } = useAppDialog();

  // URL-derived filter state (single source of truth for the data query).
  const filters = useMemo(
    () => parseFiltersFromUrl(searchParams),
    [searchParams]
  );

  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [grandTotal, setGrandTotal] = useState(0);
  const [monthlyTotal, setMonthlyTotal] = useState(0);
  const [monthCounts, setMonthCounts] = useState({});
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [expandedId, setExpandedId] = useState(null);
  const [editingTxn, setEditingTxn] = useState(null);
  const [manualTxnOpen, setManualTxnOpen] = useState(false);
  const [newRuleFromTxn, setNewRuleFromTxn] = useState(null);
  const [editingRule, setEditingRule] = useState(null);
  const [recurringFromTxn, setRecurringFromTxn] = useState(null);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [simpleFinSyncState, setSimpleFinSyncState] = useState('idle');
  const groupRefs = useRef(new Map());
  const floatingHeaderRef = useRef(null);
  const [pinnedMonth, setPinnedMonth] = useState(null);

  // Local (debounced) search input - keeps typing snappy, writes to URL
  // after a short idle window so the server request doesn't fire per
  // keystroke.
  const [searchLocal, setSearchLocal] = useState(filters.q);
  const searchDebounceRef = useRef(null);
  const syncResetRef = useRef(null);

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
  // don't want the page to collapse to a spinner - the browser would
  // clamp scrollY to the new document height, losing the user's scroll
  // position on long lists.
  async function load({ silent = false } = {}) {
    if (!silent) setLoading(true);
    setError('');
    try {
      // Reuse the same URL params; just drop `page` handling - the server
      // reads it from the query too but we'll send limit explicitly.
      const p = new URLSearchParams(searchParams);
      p.set('limit', String(filters.pageSize || DEFAULT_PAGE_SIZE));
      if (!p.has('page')) p.set('page', '1');

      const data = await api.get(`/api/transactions?${p.toString()}`);
      setItems(data.items);
      setTotal(data.total);
      setGrandTotal(data.grandTotal ?? data.total);
      setMonthlyTotal(data.monthlyTotal ?? 0);
      setMonthCounts(
        Object.fromEntries(
          (data.monthCounts || []).map((row) => [row.month, Number(row.count) || 0])
        )
      );
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

  useEffect(() => () => {
    if (syncResetRef.current) window.clearTimeout(syncResetRef.current);
  }, []);

  // ---------- URL writers ----------
  function commitFilters(next, opts = {}) {
    // Always reset page to 1 when filters change - otherwise the user can
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
      case 'categories':
        next.categoryIds = [];
        next.includeUncategorizedCategory = false;
        break;
      case 'date': next.dateFrom = ''; next.dateTo = ''; break;
      case 'amount': next.amountMin = null; next.amountMax = null; break;
      case 'type': next.type = 'all'; break;
      case 'ignored': next.includeIgnored = true; break;
      case 'transfers': next.includeTransfers = true; break;
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
      includeUncategorizedCategory: false,
      dateFrom: '',
      dateTo: '',
      amountMin: null,
      amountMax: null,
      type: 'all',
      includeIgnored: true,
      includeTransfers: true,
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
    const removed = items.find((t) => t.id === id);
    setItems((prev) => prev.filter((t) => t.id !== id));
    setTotal((t) => Math.max(0, t - 1));
    if (removed?.date) {
      const key = monthKey(removed.date);
      setMonthCounts((prev) => ({
        ...prev,
        [key]: Math.max(0, (prev[key] || 1) - 1)
      }));
    }
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
      load({ silent: true });
    } catch (err) {
      applyLocalPatch(txn.id, { [field]: txn[field] });
      alert(err.message || 'Toggle failed', { title: 'Could not update transaction' });
    }
  }

  async function handleDelete(txn) {
    const ok = await confirm(
      `Delete this transaction? "${txn.merchant}" for ${formatAmount(txn.amount)}`,
      {
        title: 'Delete transaction',
        confirmLabel: 'Delete',
        destructive: true
      }
    );
    if (!ok) {
      return;
    }
    try {
      await api.del(`/api/transactions/${txn.id}`);
      removeLocal(txn.id);
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  async function handleResetField(txn, field) {
    try {
      const result = await api.post(`/api/transactions/${txn.id}/reset`, {
        fields: [field]
      });
      if (result?.transaction) replaceLocal(txn.id, result.transaction);
      load({ silent: true });
    } catch (err) {
      alert(err.message || 'Reset failed', { title: 'Reset failed' });
    }
  }

  async function handleViewRule(ruleId) {
    try {
      const data = await api.get(`/api/rules/${ruleId}`);
      if (!data?.rule) throw new Error('Rule not found.');
      setEditingRule(data.rule);
    } catch (err) {
      alert(err.message || 'Could not open rule', { title: 'Could Not Open Rule' });
    }
  }

  function handleMarkRecurring(txn) {
    setRecurringFromTxn({
      txn,
      form: formFromTransaction(txn, categoryById.get(txn.category_id))
    });
  }

  async function saveRecurringFromTxn(payload) {
    await api.post('/api/upcoming', payload);
  }

  async function handleSimpleFinSync() {
    if (simpleFinSyncState === 'syncing') return;
    if (syncResetRef.current) window.clearTimeout(syncResetRef.current);

    setSimpleFinSyncState('syncing');
    try {
      await api.post('/api/simplefin/sync');
      await load({ silent: true });
      setSimpleFinSyncState('success');
      syncResetRef.current = window.setTimeout(() => {
        setSimpleFinSyncState('idle');
        syncResetRef.current = null;
      }, 2200);
    } catch (err) {
      setSimpleFinSyncState('idle');
      alert(err.message || 'SimpleFIN sync failed', { title: 'SimpleFIN Sync Failed' });
    }
  }

  const accountById = new Map(accounts.map((a) => [a.id, a]));
  const categoryById = new Map(categories.map((c) => [c.id, c]));
  const hasSimpleFinAccount = accounts.some((account) => account.simplefin_account_id);

  const activeCount = countActiveFilters(filters);
  const isFiltered = activeCount > 0;
  const groups = useMemo(
    () => groupByMonth(items, monthCounts),
    [items, monthCounts]
  );

  useEffect(() => {
    if (!groups.length) {
      setPinnedMonth(null);
      return undefined;
    }

    let frameId = null;

    function measurePinnedMonth() {
      frameId = null;
      const topOffset = 0;
      const headerHeight =
        floatingHeaderRef.current?.getBoundingClientRect().height || 52;
      let activeIndex = -1;

      for (let index = 0; index < groups.length; index += 1) {
        const node = groupRefs.current.get(groups[index].id);
        if (!node) continue;
        const rect = node.getBoundingClientRect();
        if (rect.top <= topOffset && rect.bottom > topOffset + headerHeight) {
          activeIndex = index;
        }
      }

      if (activeIndex < 0) {
        setPinnedMonth(null);
        return;
      }

      const group = groups[activeIndex];
      const node = groupRefs.current.get(group.id);
      if (!node) {
        setPinnedMonth(null);
        return;
      }

      const rect = node.getBoundingClientRect();
      const nextGroup = groups[activeIndex + 1];
      const nextNode = nextGroup ? groupRefs.current.get(nextGroup.id) : null;
      const nextTop = nextNode?.getBoundingClientRect().top;
      const translateY =
        typeof nextTop === 'number'
          ? Math.min(0, nextTop - topOffset - headerHeight)
          : 0;

      const nextPinnedMonth = {
        key: group.id,
        label: group.label,
        count: group.count,
        left: rect.left,
        width: rect.width,
        translateY
      };

      setPinnedMonth((prev) =>
        prev &&
        prev.key === nextPinnedMonth.key &&
        prev.count === nextPinnedMonth.count &&
        Math.abs(prev.left - nextPinnedMonth.left) < 0.5 &&
        Math.abs(prev.width - nextPinnedMonth.width) < 0.5 &&
        Math.abs(prev.translateY - nextPinnedMonth.translateY) < 0.5
          ? prev
          : nextPinnedMonth
      );
    }

    function updatePinnedMonth() {
      if (frameId != null) return;
      frameId = window.requestAnimationFrame(measurePinnedMonth);
    }

    updatePinnedMonth();
    window.addEventListener('scroll', updatePinnedMonth, { passive: true });
    window.addEventListener('resize', updatePinnedMonth);
    return () => {
      if (frameId != null) window.cancelAnimationFrame(frameId);
      window.removeEventListener('scroll', updatePinnedMonth);
      window.removeEventListener('resize', updatePinnedMonth);
    };
  }, [groups]);

  // Onboarding empty state - only when no filters AND nothing exists at all.
  if (!loading && grandTotal === 0 && !isFiltered) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">$</div>
        <h2>No transactions yet</h2>
        <p>
          Import your Rocket Money export to bring over your full history,
          preserve your custom merchant names, and generate rename rules.
        </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate('/settings')}
          >
            Import from Rocket Money
        </button>
      </div>
    );
  }

  return (
    <div className="transactions-view">
      <PageHero
        id="transactions-title"
        variant="transactions"
        kicker="Money movement"
        title="Transactions"
        subtitle={`${monthlyTotal.toLocaleString()} ${monthlyTotal === 1 ? 'transaction' : 'transactions'} this month`}
        chrome={(hero) => (
          <div className="page-hero-chrome">
            <button
              type="button"
              className="hero-brand brand-home"
              onClick={() => navigate('/dashboard')}
              aria-label="Go to dashboard"
            >
              <img src={APP_ICON_192} alt="" className="brand-mark brand-mark-image" />
              <BrandLogo tone="white" />
            </button>
          </div>
        )}
        toolbar={(
          <div className="txn-toolbar txn-toolbar-hero" role="group" aria-label="Transaction tools">
            <SearchField
              value={searchLocal}
              onChange={setSearch}
              placeholder="Search merchant, description, notes..."
              className="txn-search-field"
            />
            <div className="txn-toolbar-actions">
              <button
                type="button"
                className="btn-secondary txn-filter-button txn-control-button"
                onClick={() => setFilterSheetOpen(true)}
              >
                <svg
                  className="txn-filter-icon"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path d="M4 6h16l-6 7v4.5l-4 2V13L4 6Z" />
                </svg>
                <span className="txn-control-label">Filter</span>
                {activeCount > 0 && (
                  <span className="txn-filter-count">{activeCount}</span>
                )}
              </button>
              <AppSelect
                className="txn-control-select txn-sort"
                value={filters.sort}
                options={SORT_OPTIONS}
                onChange={setSort}
                ariaLabel="Sort transactions"
                triggerLabel="Sort"
                triggerIcon={<TransactionToolbarIcon type="sort" />}
                showCaret={false}
              />
              <AppSelect
                className="txn-control-select txn-page-size"
                value={filters.pageSize}
                options={PAGE_SIZE_OPTIONS.map((size) => ({
                  value: size,
                  label: `${size} / page`
                }))}
                onChange={setPageSize}
                ariaLabel="Transactions per page"
                triggerLabel="View"
                triggerIcon={<TransactionToolbarIcon type="view" />}
                showCaret={false}
              />
            </div>
          </div>
        )}
      />
      <div className="txn-page-actions" role="group" aria-label="Transaction actions">
        {hasSimpleFinAccount && (
          <button
            type="button"
            className={`btn-secondary txn-sync-button ${simpleFinSyncState === 'success' ? 'success' : ''}`.trim()}
            onClick={handleSimpleFinSync}
            disabled={simpleFinSyncState === 'syncing'}
          >
            {simpleFinSyncState === 'syncing' && <span className="spinner-inline" />}
            {simpleFinSyncState === 'syncing'
              ? 'Syncing'
              : simpleFinSyncState === 'success'
                ? 'Synced'
                : 'SimpleFIN Sync'}
          </button>
        )}
        <button
          type="button"
          className="btn-primary"
          onClick={() => setManualTxnOpen(true)}
        >
          + Manual Transaction
        </button>
      </div>
      {pinnedMonth && typeof document !== 'undefined' && createPortal(
        <header
          ref={floatingHeaderRef}
          className="txn-pinned-month-header"
          style={{
            left: `${pinnedMonth.left}px`,
            width: `${pinnedMonth.width}px`,
            transform: `translateY(${pinnedMonth.translateY}px)`
          }}
          aria-hidden="true"
        >
          <span className="txn-month-label">{pinnedMonth.label}</span>
          <span className="txn-month-count">
            {pinnedMonth.count}{' '}
            {pinnedMonth.count === 1 ? 'transaction' : 'transactions'}
          </span>
        </header>,
        document.body
      )}
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
          <div className="empty-state-icon">$</div>
          <h2>No transactions match your filters</h2>
          <p>Try widening the date range or removing some criteria.</p>
          <button type="button" className="btn-secondary" onClick={clearAllFilters}>
            Clear all filters
          </button>
        </div>
      ) : (
        <>
          {groups.map((group) => (
            <section
              key={group.id}
              className="txn-month-group"
              ref={(node) => {
                if (node) groupRefs.current.set(group.id, node);
                else groupRefs.current.delete(group.id);
              }}
            >
              <header className="txn-month-header">
                <span className="txn-month-label">{group.label}</span>
                <span className="txn-month-count">
                  {group.count}{' '}
                  {group.count === 1 ? 'transaction' : 'transactions'}
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
                    onMarkRecurring={() => handleMarkRecurring(t)}
                    onToggleTransfer={() => handleToggle(t, 'is_transfer')}
                    onToggleIgnored={() => handleToggle(t, 'is_ignored')}
                    onToggleMhaEligible={() => handleToggle(t, 'mha_eligible')}
                    onDelete={() => handleDelete(t)}
                    onResetField={(field) => handleResetField(t, field)}
                    onViewRule={handleViewRule}
                    onLogoChanged={(updated) => replaceLocal(t.id, updated)}
                    mhaTrackerEnabled={mhaTrackerEnabled}
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
              Previous
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
              Next
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
            if (updated) load({ silent: true });
          }}
          onReset={(field) => handleResetField(editingTxn, field)}
        />
      )}

      {manualTxnOpen && (
        <ManualTransactionModal
          accounts={accounts.filter((account) => !account.is_archived)}
          categories={categories}
          onClose={() => setManualTxnOpen(false)}
          onSaved={() => {
            setManualTxnOpen(false);
            load({ silent: true });
          }}
        />
      )}

      {newRuleFromTxn && (
        <RuleEditor
          rule={{
            // Seed the full rule editor with the obvious starting point
            // for a "make a rule like this transaction" flow. The user
            // gets the full condition/action builder - multiple conditions,
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
          sourceTransaction={newRuleFromTxn}
          onClose={() => setNewRuleFromTxn(null)}
          onSaved={() => {
            setNewRuleFromTxn(null);
            // Silent reload - avoids the spinner swap that would clamp
            // scrollY and jump the user to the top of the list.
            load({ silent: true });
          }}
        />
      )}

      {editingRule && (
        <RuleEditor
          rule={editingRule}
          accounts={accounts}
          categories={categories}
          onClose={() => setEditingRule(null)}
          onSaved={() => {
            setEditingRule(null);
            load({ silent: true });
          }}
        />
      )}

      {recurringFromTxn && (
        <RecurringItemEditor
          item={{ form: recurringFromTxn.form }}
          title="Mark As Recurring"
          accounts={accounts}
          categories={categories}
          saveLabel="Add Recurring"
          onSave={saveRecurringFromTxn}
          onClose={() => setRecurringFromTxn(null)}
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

      <Dialog />
    </div>
  );
}

function ManualTransactionModal({ accounts, categories, onClose, onSaved }) {
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');
  const [date, setDate] = useState(todayLocalDate());
  const [merchant, setMerchant] = useState('');
  const [amount, setAmount] = useState('');
  const [direction, setDirection] = useState('expense');
  const [categoryId, setCategoryId] = useState('');
  const [notes, setNotes] = useState('');
  const [isTransfer, setIsTransfer] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleSave(event, close) {
    event.preventDefault();
    const parsedAmount = parseCurrencyInput(amount, NaN);
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setError('Amount must be a positive number.');
      return;
    }
    if (!accountId) {
      setError('Choose an account.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await api.post('/api/transactions', {
        account_id: Number(accountId),
        date,
        merchant,
        amount: direction === 'expense' ? -parsedAmount : parsedAmount,
        category_id: categoryId ? Number(categoryId) : null,
        notes,
        is_transfer: isTransfer
      });
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Create transaction failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Add Manual Transaction</h3>
          <p className="modal-copy">
            Use this for cash, corrections, or one-off entries that will not arrive through SimpleFIN.
          </p>

          <form onSubmit={(event) => handleSave(event, close)}>
            <label className="field">
              <span>Account</span>
              <AppSelect
                value={accountId || ''}
                onChange={setAccountId}
                ariaLabel="Transaction account"
                options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              />
            </label>

            <label className="field">
              <span>Date</span>
              <input type="date" value={date} onChange={(event) => setDate(event.target.value)} required />
            </label>

            <label className="field">
              <span>Merchant</span>
              <input
                type="text"
                value={merchant}
                onChange={(event) => setMerchant(event.target.value)}
                placeholder="Merchant or description"
                required
              />
            </label>

            <label className="field">
              <span>Amount</span>
              <CurrencyInput value={amount} onChange={setAmount} placeholder="$25.00" required />
            </label>

            <div className="settings-theme-toggle settings-theme-toggle-two transaction-direction-toggle" role="radiogroup" aria-label="Transaction direction">
              {[
                { value: 'expense', label: 'Expense' },
                { value: 'income', label: 'Income' }
              ].map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`settings-theme-option ${direction === option.value ? 'active' : ''}`}
                  onClick={() => setDirection(option.value)}
                  role="radio"
                  aria-checked={direction === option.value}
                >
                  <span className="settings-theme-text">
                    <span className="settings-theme-label">{option.label}</span>
                  </span>
                </button>
              ))}
            </div>

            <label className="field">
              <span>Category</span>
              <AppSelect
                value={categoryId || ''}
                onChange={setCategoryId}
                ariaLabel="Transaction category"
                options={[
                  { value: '', label: '(Uncategorized)' },
                  ...categories.map((category) => ({
                    value: category.id,
                    label: `${category.icon} ${category.name}`
                  }))
                ]}
              />
            </label>

            <label className="toggle-row">
              <input
                type="checkbox"
                checked={isTransfer}
                onChange={(event) => setIsTransfer(event.target.checked)}
              />
              <span>Mark As Transfer</span>
            </label>

            <label className="field">
              <span>Notes</span>
              <textarea
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                placeholder="Optional"
                rows={3}
              />
            </label>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving || accounts.length === 0}>
                {saving ? 'Adding...' : 'Add Transaction'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
// ============================================================================
// Active filter pills - one per active filter, dismissible
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
    pills.push({ key: 'q', label: `"${filters.q}"` });
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

  if (filters.categoryIds.length || filters.includeUncategorizedCategory) {
    const categoryNames = [
      ...filters.categoryIds.map((id) => categoryById.get(id)?.name || `#${id}`),
      ...(filters.includeUncategorizedCategory ? ['Uncategorized'] : [])
    ];
    const names = categoryNames.join(', ');
    pills.push({
      key: 'categories',
      label: categoryNames.length === 1
        ? names
        : `${categoryNames.length} categories`,
      title: names
    });
  }

  if (filters.dateFrom || filters.dateTo) {
    const from = filters.dateFrom ? formatMonthDay(filters.dateFrom) : '-';
    const to = filters.dateTo ? formatMonthDay(filters.dateTo) : 'today';
    pills.push({ key: 'date', label: `${from} -> ${to}` });
  }

  if (filters.amountMin != null || filters.amountMax != null) {
    const lo = filters.amountMin != null ? formatShortAmount(filters.amountMin) : '0';
    const hi = filters.amountMax != null ? formatShortAmount(filters.amountMax) : 'No max';
    pills.push({ key: 'amount', label: `${lo} - ${hi}` });
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

  if (filters.includeTransfers === false) {
    pills.push({ key: 'transfers', label: 'Excl. transfers' });
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
          <span className="active-pill-x" aria-hidden="true">x</span>
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
