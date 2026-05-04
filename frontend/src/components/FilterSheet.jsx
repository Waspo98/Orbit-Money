import { useEffect, useMemo, useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from './CurrencyInput.jsx';
import { formatLocalDate } from '../lib/localDate.js';

// ============================================================================
// FilterSheet — comprehensive filter UI for the Transactions page.
//
// Draft-state pattern: the user's in-flight choices live here, NOT in URL
// state. Changes don't commit until the user hits "Apply" — then we pass
// the whole filter object back to the parent which writes it into URL
// params (the source of truth for the list view).
//
// Reset inside the sheet clears the draft; Cancel/backdrop discards it.
// ============================================================================

const TYPE_OPTIONS = [
  { value: 'all',      label: 'All' },
  { value: 'income',   label: 'Income' },
  { value: 'expense',  label: 'Expenses' },
  { value: 'transfer', label: 'Transfers' }
];

const HAS_EDITS_OPTIONS = [
  { value: 'any', label: 'Any' },
  { value: 'yes', label: 'Has edits' },
  { value: 'no',  label: 'No edits' }
];

// Date preset helpers — all return { from, to } as YYYY-MM-DD strings.
function todayIso() {
  return formatLocalDate();
}
function monthStart(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`;
}
function monthEnd(date) {
  // Last day of the given month = day 0 of the next month
  const d = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  return formatLocalDate(d);
}
function daysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return formatLocalDate(d);
}

const DATE_PRESETS = [
  {
    key: 'this_month',
    label: 'This month',
    compute: () => {
      const now = new Date();
      return { from: monthStart(now), to: todayIso() };
    }
  },
  {
    key: 'last_month',
    label: 'Last month',
    compute: () => {
      const lm = new Date();
      lm.setMonth(lm.getMonth() - 1);
      return { from: monthStart(lm), to: monthEnd(lm) };
    }
  },
  {
    key: 'last_30',
    label: 'Last 30 days',
    compute: () => ({ from: daysAgo(30), to: todayIso() })
  },
  {
    key: 'last_90',
    label: 'Last 90 days',
    compute: () => ({ from: daysAgo(90), to: todayIso() })
  },
  {
    key: 'ytd',
    label: 'Year to date',
    compute: () => {
      const y = new Date().getFullYear();
      return { from: `${y}-01-01`, to: todayIso() };
    }
  },
  {
    key: 'all',
    label: 'All time',
    compute: () => ({ from: '', to: '' })
  }
];

export default function FilterSheet({
  initial,
  accounts,
  categories,
  onApply,
  onClose
}) {
  // Draft state — seeded from URL-derived initial filters.
  const [accountIds, setAccountIds] = useState(initial.accountIds || []);
  const [categoryIds, setCategoryIds] = useState(initial.categoryIds || []);
  const [includeUncategorizedCategory, setIncludeUncategorizedCategory] = useState(
    initial.includeUncategorizedCategory === true
  );
  const [dateFrom, setDateFrom] = useState(initial.dateFrom || '');
  const [dateTo, setDateTo] = useState(initial.dateTo || '');
  const [amountMin, setAmountMin] = useState(
    initial.amountMin != null ? formatCurrencyInput(initial.amountMin) : ''
  );
  const [amountMax, setAmountMax] = useState(
    initial.amountMax != null ? formatCurrencyInput(initial.amountMax) : ''
  );
  const [type, setType] = useState(initial.type || 'all');
  const [includeIgnored, setIncludeIgnored] = useState(
    initial.includeIgnored !== false
  );
  const [includeTransfers, setIncludeTransfers] = useState(
    initial.includeTransfers !== false
  );
  const [hasEdits, setHasEdits] = useState(initial.hasEdits || 'any');

  // Match active preset (if any) by comparing the computed from/to strings.
  const activePreset = useMemo(() => {
    for (const p of DATE_PRESETS) {
      const r = p.compute();
      if (r.from === dateFrom && r.to === dateTo) return p.key;
    }
    return null;
  }, [dateFrom, dateTo]);

  function applyPreset(p) {
    const { from, to } = p.compute();
    setDateFrom(from);
    setDateTo(to);
  }

  function toggleInList(list, setList, value) {
    setList((prev) =>
      prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]
    );
  }

  function resetDraft() {
    setAccountIds([]);
    setCategoryIds([]);
    setIncludeUncategorizedCategory(false);
    setDateFrom('');
    setDateTo('');
    setAmountMin('');
    setAmountMax('');
    setType('all');
    setIncludeIgnored(true);
    setIncludeTransfers(true);
    setHasEdits('any');
  }

  function handleApply(close) {
    const min = amountMin.trim() ? parseCurrencyInput(amountMin, NaN) : null;
    const max = amountMax.trim() ? parseCurrencyInput(amountMax, NaN) : null;

    onApply({
      accountIds,
      categoryIds,
      includeUncategorizedCategory,
      dateFrom,
      dateTo,
      amountMin: Number.isFinite(min) && min >= 0 ? min : null,
      amountMax: Number.isFinite(max) && max >= 0 ? max : null,
      type,
      includeIgnored,
      includeTransfers,
      hasEdits
    });
    close();
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <div className="filter-sheet">
          <div className="filter-sheet-header">
            <h3>Filter transactions</h3>
            <button
              type="button"
              className="linkish"
              onClick={resetDraft}
              title="Clear all filters"
            >
              Reset
            </button>
          </div>

          <div className="filter-sheet-body">
            {/* ---- Date range ---- */}
            <section className="filter-section">
              <h4>Date</h4>
              <div className="chip-row">
                {DATE_PRESETS.map((p) => (
                  <button
                    key={p.key}
                    type="button"
                    className={`chip ${activePreset === p.key ? 'chip-active' : ''}`}
                    onClick={() => applyPreset(p)}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
              <div className="filter-row">
                <label className="filter-field">
                  <span>From</span>
                  <input
                    type="date"
                    value={dateFrom}
                    onChange={(e) => setDateFrom(e.target.value)}
                  />
                </label>
                <label className="filter-field">
                  <span>To</span>
                  <input
                    type="date"
                    value={dateTo}
                    onChange={(e) => setDateTo(e.target.value)}
                  />
                </label>
              </div>
            </section>

            {/* ---- Transaction type ---- */}
            <section className="filter-section">
              <h4>Type</h4>
              <div className="chip-row">
                {TYPE_OPTIONS.map((o) => (
                  <button
                    key={o.value}
                    type="button"
                    className={`chip ${type === o.value ? 'chip-active' : ''}`}
                    onClick={() => setType(o.value)}
                  >
                    {o.label}
                  </button>
                ))}
              </div>
            </section>

            {/* ---- Amount range ---- */}
            <section className="filter-section">
              <h4>Amount</h4>
              <p className="subtle filter-hint">
                Matches absolute value — e.g. 50 matches both −$50 expenses and
                +$50 income.
              </p>
              <div className="filter-row">
                <label className="filter-field">
                  <span>Min</span>
                  <CurrencyInput
                    placeholder="$0"
                    value={amountMin}
                    onChange={setAmountMin}
                  />
                </label>
                <label className="filter-field">
                  <span>Max</span>
                  <CurrencyInput
                    placeholder="No max"
                    value={amountMax}
                    onChange={setAmountMax}
                  />
                </label>
              </div>
            </section>

            {/* ---- Accounts ---- */}
            <section className="filter-section">
              <h4>
                Accounts
                {accountIds.length > 0 && (
                  <span className="filter-count">{accountIds.length}</span>
                )}
              </h4>
              <div className="filter-checkbox-list">
                {accounts.length === 0 ? (
                  <p className="subtle">No accounts yet.</p>
                ) : (
                  accounts.map((a) => (
                    <label key={a.id} className="filter-checkbox">
                      <input
                        type="checkbox"
                        checked={accountIds.includes(a.id)}
                        onChange={() =>
                          toggleInList(accountIds, setAccountIds, a.id)
                        }
                      />
                      <span className="filter-checkbox-label">
                        {a.name}
                        {a.account_number_last4 && (
                          <span className="subtle"> ···{a.account_number_last4}</span>
                        )}
                      </span>
                    </label>
                  ))
                )}
              </div>
            </section>

            {/* ---- Categories ---- */}
            <section className="filter-section">
              <h4>
                Categories
                {(categoryIds.length > 0 || includeUncategorizedCategory) && (
                  <span className="filter-count">
                    {categoryIds.length + (includeUncategorizedCategory ? 1 : 0)}
                  </span>
                )}
              </h4>
              <div className="filter-checkbox-list filter-checkbox-grid">
                <label className="filter-checkbox">
                  <input
                    type="checkbox"
                    checked={includeUncategorizedCategory}
                    onChange={() => setIncludeUncategorizedCategory((prev) => !prev)}
                  />
                  <span className="filter-checkbox-label">Uncategorized</span>
                </label>
                {categories.map((c) => (
                  <label key={c.id} className="filter-checkbox">
                    <input
                      type="checkbox"
                      checked={categoryIds.includes(c.id)}
                      onChange={() =>
                        toggleInList(categoryIds, setCategoryIds, c.id)
                      }
                    />
                    <span
                      className="filter-checkbox-label"
                      style={{ color: c.color }}
                    >
                      {c.icon} {c.name}
                    </span>
                  </label>
                ))}
              </div>
            </section>

            {/* ---- Edits ---- */}
            <section className="filter-section">
              <h4>Edit state</h4>
              <div className="chip-row">
                {HAS_EDITS_OPTIONS.map((o) => (
                  <button
                    key={o.value}
                    type="button"
                    className={`chip ${hasEdits === o.value ? 'chip-active' : ''}`}
                    onClick={() => setHasEdits(o.value)}
                  >
                    {o.label}
                  </button>
                ))}
              </div>
            </section>

            {/* ---- Flags ---- */}
            <section className="filter-section">
              <label className="toggle-row">
                <input
                  type="checkbox"
                  checked={includeIgnored}
                  onChange={(e) => setIncludeIgnored(e.target.checked)}
                />
                <span>Include ignored transactions</span>
              </label>
              <label className="toggle-row">
                <input
                  type="checkbox"
                  checked={includeTransfers}
                  onChange={(e) => setIncludeTransfers(e.target.checked)}
                />
                <span>Include transfer transactions</span>
              </label>
            </section>
          </div>

          <div className="modal-actions filter-sheet-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
            <button
              type="button"
              className="btn-primary"
              onClick={() => handleApply(close)}
            >
              Apply
            </button>
          </div>
        </div>
      )}
    </AnimatedModal>
  );
}
