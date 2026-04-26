import { useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import AnimatedModal from '../AnimatedModal.jsx';
import DropdownMenu from '../DropdownMenu.jsx';
import { formatCurrency, formatCurrencyOr, formatSignedCurrency } from '../../lib/formatters.js';
import { formatFullDate, formatMonthDay } from '../../lib/localDate.js';

const EXPAND_ANIMATION_MS = 220;
const EXPAND_ANIMATION_BUFFER_MS = 40;
const reportedLogoStatuses = new Set();

function describeEditSource(source) {
  if (!source) return null;
  if (source === 'user') return 'edited manually';
  if (source.startsWith('rule:')) return 'applied by rule';
  return 'edited';
}

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
  onMarkRecurring,
  onToggleTransfer,
  onToggleIgnored,
  onToggleMhaEligible,
  onDelete,
  onResetField,
  onLogoChanged,
  hideMerchantLogo = false,
  mhaTrackerEnabled = false
}) {
  const isIncome = txn.amount > 0 && !txn.is_transfer;
  const isTransfer = !!txn.is_transfer;
  const actionItems = [
    { label: 'Edit', onClick: onEdit },
    { label: 'Create rule', onClick: onCreateRule },
    ...(onMarkRecurring ? [{ label: 'Mark as recurring', onClick: onMarkRecurring }] : []),
    {
      label: txn.is_transfer ? 'Unmark as transfer' : 'Mark as transfer',
      onClick: onToggleTransfer
    },
    {
      label: txn.mha_eligible ? 'MHA ineligible' : 'MHA eligible',
      onClick: onToggleMhaEligible,
      hidden: !mhaTrackerEnabled
    },
    {
      label: txn.is_ignored ? 'Unignore' : 'Ignore',
      onClick: onToggleIgnored
    },
    { divider: true },
    { label: 'Delete', onClick: onDelete, destructive: true }
  ];

  const rowRef = useRef(null);
  const prevExpandedRef = useRef(expanded);

  useEffect(() => {
    const wasExpanded = prevExpandedRef.current;
    prevExpandedRef.current = expanded;

    if (!expanded || wasExpanded) return undefined;

    const timer = setTimeout(() => {
      const element = rowRef.current;
      if (!element) return;

      const rect = element.getBoundingClientRect();
      const viewportHeight = window.innerHeight;
      const tabsElement = document.querySelector('.bottom-tabs');
      let tabsHeight = 0;
      if (tabsElement) {
        const style = window.getComputedStyle(tabsElement);
        if (style.display !== 'none') {
          tabsHeight = tabsElement.getBoundingClientRect().height;
        }
      }

      const effectiveBottom = viewportHeight - tabsHeight - 16;
      if (rect.bottom > effectiveBottom) {
        const overflow = Math.ceil(rect.bottom - effectiveBottom);
        const maxScroll = Math.max(0, rect.top - 16);
        const scrollBy = Math.min(overflow, Math.max(overflow, 0) + maxScroll);
        window.scrollBy({ top: scrollBy, behavior: 'smooth' });
      }
    }, EXPAND_ANIMATION_MS + EXPAND_ANIMATION_BUFFER_MS);

    return () => clearTimeout(timer);
  }, [expanded]);

  function handleRowClick(event) {
    if (event.target.closest('button, a, input, select, textarea, [role=button]')) return;
    onExpand();
  }

  return (
    <li
      ref={rowRef}
      className={`txn-row ${hideMerchantLogo ? 'no-logo' : ''} ${txn.is_ignored ? 'ignored' : ''} ${isTransfer ? 'transfer' : ''} ${expanded ? 'expanded' : ''} ${txn.has_edits ? 'has-edits' : ''}`}
      onClick={handleRowClick}
    >
      <div className="txn-row-summary">
        {!hideMerchantLogo && (
          <TransactionMerchantMark txn={txn} category={category} onLogoChanged={onLogoChanged} />
        )}
        <div className="txn-main">
          <div className="txn-merchant">{txn.merchant}</div>
          <div className="txn-meta">
            <span className="txn-date-short">{formatMonthDay(txn.date)}</span>
            {category && (
              <>
                <span className="dot" />
                <span className="txn-category-chip" style={{ color: category.color }}>
                  <span>{category.icon}</span>
                  <span>{category.name}</span>
                </span>
              </>
            )}
            {account && !hideAccountInMeta && (
              <span>
                {account.name}
                {account.account_number_last4 && ` ...${account.account_number_last4}`}
              </span>
            )}
          </div>
        </div>

        <div className={`txn-amount ${isIncome ? 'income' : ''}`}>
          {formatSignedCurrency(txn.amount)}
        </div>
        {!expanded && (
          <div className="txn-row-menu">
            <DropdownMenu items={actionItems} ariaLabel={`Actions for ${txn.merchant}`} />
          </div>
        )}
      </div>

      <div className="txn-detail-wrapper" aria-hidden={!expanded}>
        <TransactionDetail
          txn={txn}
          account={account}
          category={category}
          originalCategory={originalCategory}
          interactive={expanded}
          onEdit={onEdit}
          onCreateRule={onCreateRule}
          onMarkRecurring={onMarkRecurring}
          onToggleTransfer={onToggleTransfer}
          onToggleIgnored={onToggleIgnored}
          onToggleMhaEligible={onToggleMhaEligible}
          onDelete={onDelete}
          onResetField={onResetField}
          mhaTrackerEnabled={mhaTrackerEnabled}
        />
      </div>
    </li>
  );
}

function TransactionMerchantMark({ txn, category, onLogoChanged }) {
  const logo = txn.merchant_logo;
  const [showLogo, setShowLogo] = useState(Boolean(logo?.url));
  const [finderOpen, setFinderOpen] = useState(false);

  useEffect(() => {
    setShowLogo(Boolean(logo?.url));
  }, [logo?.url]);

  function report(status) {
    if (!logo?.merchant_key) return;
    const reportKey = `${logo.merchant_key}:${status}`;
    if (reportedLogoStatuses.has(reportKey)) return;
    reportedLogoStatuses.add(reportKey);

    api.post('/api/merchant-logos/report', {
      merchant_key: logo.merchant_key,
      status
    }).catch(() => {
      reportedLogoStatuses.delete(reportKey);
    });
  }

  function applyLogoState(nextLogo) {
    setShowLogo(Boolean(nextLogo?.url));
    onLogoChanged?.({
      ...txn,
      merchant_logo: nextLogo || null
    });
  }

  async function ensureLogoEntry() {
    if (logo?.merchant_key) return logo;
    const result = await api.post('/api/merchant-logos/ensure', {
      transaction_id: txn.id
    });
    return result.merchant_logo;
  }

  async function useCategoryIcon() {
    try {
      const ensuredLogo = await ensureLogoEntry();
      if (!ensuredLogo?.merchant_key) return;
      applyLogoState({
        ...ensuredLogo,
        url: null,
        status: 'hidden'
      });
      const result = await api.post('/api/merchant-logos/override', {
        merchant_key: ensuredLogo.merchant_key,
        use_category_icon: true
      });
      applyLogoState(result.merchant_logo);
    } catch {
      setShowLogo(Boolean(logo?.url));
      onLogoChanged?.(txn);
    }
  }

  function markContent() {
    if (showLogo && logo?.url) {
      return (
        <img
          src={logo.url}
          alt=""
          loading="lazy"
          width="32"
          height="32"
          onLoad={() => {
            if (logo.status === 'candidate') report('loaded');
          }}
          onError={() => {
            setShowLogo(false);
            report('failed');
          }}
        />
      );
    }

    return (
      <span
        className="txn-merchant-mark-fallback"
        style={category?.color ? { color: category.color } : undefined}
        aria-hidden="true"
      >
        {category?.icon || '$'}
      </span>
    );
  }

  const actionItems = [
    { label: 'Find Logo', onClick: () => setFinderOpen(true) },
    { label: 'Use Category Icon', onClick: () => useCategoryIcon() }
  ];

  return (
    <span className="txn-logo-menu-wrap">
      <DropdownMenu
        items={actionItems}
        ariaLabel={`Logo options for ${txn.merchant}`}
        triggerClassName="txn-merchant-mark"
        menuClassName="txn-logo-menu"
        align="start"
        renderTrigger={() => markContent()}
      />
      {finderOpen && (
        <LogoFinderModal
          txn={txn}
          logo={logo}
          onClose={() => setFinderOpen(false)}
          onSaved={(nextLogo) => {
            setFinderOpen(false);
            applyLogoState(nextLogo);
          }}
        />
      )}
    </span>
  );
}

function LogoFinderModal({ txn, logo, onClose, onSaved }) {
  const [merchantLogo, setMerchantLogo] = useState(logo || null);
  const [query, setQuery] = useState(txn.merchant || logo?.merchant_name || '');
  const [logoUrl, setLogoUrl] = useState(logo?.url || '');
  const [candidates, setCandidates] = useState([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [hasSearched, setHasSearched] = useState(false);

  useEffect(() => {
    let active = true;
    searchBrands(query, { activeRef: () => active });
    return () => {
      active = false;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function ensureLogoEntry() {
    if (merchantLogo?.merchant_key) return merchantLogo;
    const result = await api.post('/api/merchant-logos/ensure', {
      transaction_id: txn.id
    });
    setMerchantLogo(result.merchant_logo);
    return result.merchant_logo;
  }

  async function searchBrands(nextQuery = query, options = {}) {
    const trimmed = nextQuery.trim();
    if (!trimmed) return;
    const isActive = options.activeRef || (() => true);
    setSearching(true);
    setError('');
    try {
      const result = await api.post('/api/merchant-logos/search', {
        transaction_id: txn.id,
        query: trimmed
      });
      if (!isActive()) return;
      setMerchantLogo(result.merchant_logo);
      setCandidates(result.candidates || []);
      setHasSearched(true);
      if (result.configured === false) {
        setError('Logo search is not configured on this server yet. Paste a direct image URL below.');
      }
    } catch (err) {
      if (!isActive()) return;
      setError(err.message || 'Logo search failed');
      setHasSearched(true);
    } finally {
      if (isActive()) setSearching(false);
    }
  }

  function openDuckDuckGo() {
    const searchQuery = encodeURIComponent(`${query || txn.merchant || 'merchant'} logo`);
    window.open(`https://duckduckgo.com/?q=${searchQuery}&iar=images&iax=images&ia=images`, '_blank', 'noopener,noreferrer');
  }

  async function handleSubmit(event, close) {
    event.preventDefault();
    const ensuredLogo = await ensureLogoEntry();
    if (!ensuredLogo?.merchant_key) {
      setError('This merchant does not have a logo entry to update yet.');
      return;
    }
    if (!logoUrl.trim()) {
      setError('Choose a result or paste a direct image URL.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const result = await api.post('/api/merchant-logos/override', {
        merchant_key: ensuredLogo.merchant_key,
        logo_url: logoUrl.trim()
      });
      close({ animate: true });
      setTimeout(() => onSaved(result.merchant_logo), 180);
    } catch (err) {
      setError(err.message || 'Logo override failed');
      setSaving(false);
    }
  }

  async function useCategoryIcon(close) {
    setSaving(true);
    setError('');
    try {
      const ensuredLogo = await ensureLogoEntry();
      if (!ensuredLogo?.merchant_key) {
        setError('This merchant does not have a logo entry to update yet.');
        setSaving(false);
        return;
      }
      const result = await api.post('/api/merchant-logos/override', {
        merchant_key: ensuredLogo.merchant_key,
        use_category_icon: true
      });
      close({ animate: true });
      setTimeout(() => onSaved(result.merchant_logo), 180);
    } catch (err) {
      setError(err.message || 'Logo override failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>Find Logo</h3>
          <p className="modal-copy">
            Search Logo.dev results for {txn.merchant}, or paste a direct image URL.
          </p>
          <form
            className="logo-finder-search"
            onSubmit={(event) => {
              event.preventDefault();
              searchBrands(query);
            }}
          >
            <label className="field">
              <span>Merchant Search</span>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Merchant name"
                required
              />
            </label>
            <button type="submit" className="btn-secondary" disabled={searching}>
              {searching ? 'Searching...' : 'Search'}
            </button>
          </form>

          <div className="logo-finder-results">
            {candidates.map((candidate) => (
              <button
                key={candidate.domain}
                type="button"
                className={`logo-candidate ${logoUrl === candidate.logo_url ? 'selected' : ''}`}
                onClick={() => setLogoUrl(candidate.logo_url || '')}
              >
                <span className="logo-candidate-image">
                  <img src={candidate.logo_url} alt="" loading="lazy" />
                </span>
                <span className="logo-candidate-text">
                  <strong>{candidate.name}</strong>
                  <span>{candidate.domain}</span>
                </span>
              </button>
            ))}
            {!searching && hasSearched && candidates.length === 0 && (
              <div className="logo-finder-empty">
                No Logo.dev matches found. Try a simpler merchant name or paste a direct image URL.
              </div>
            )}
          </div>

          <form onSubmit={(event) => handleSubmit(event, close)}>
            <label className="field">
              <span>Logo Image URL</span>
              <input
                type="url"
                value={logoUrl}
                onChange={(event) => setLogoUrl(event.target.value)}
                placeholder="https://example.com/logo.png"
                required
              />
            </label>
            <div className={`logo-finder-bottom-tools ${logoUrl ? 'has-preview' : ''}`}>
              {logoUrl && (
                <div className="logo-finder-preview" aria-label="Selected logo preview">
                  <img src={logoUrl} alt="" />
                </div>
              )}
              <div className="logo-finder-secondary-actions">
                <button type="button" className="btn-secondary" onClick={openDuckDuckGo}>
                  DuckDuckGo Images
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => useCategoryIcon(close)}
                  disabled={saving}
                >
                  Use Category Icon
                </button>
              </div>
            </div>
            {error && <div className="error">{error}</div>}
            <div className="modal-actions logo-finder-actions">
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

function TransactionDetail({
  txn,
  account,
  category,
  originalCategory,
  interactive,
  onEdit,
  onCreateRule,
  onMarkRecurring,
  onToggleTransfer,
  onToggleIgnored,
  onToggleMhaEligible,
  onDelete,
  onResetField,
  mhaTrackerEnabled = false
}) {
  const merchantEdited = txn.edited_merchant_source !== null;
  const categoryEdited = txn.edited_category_id_source !== null;
  const transferEdited = txn.edited_is_transfer_source !== null;
  const ignoredEdited = txn.edited_is_ignored_source !== null;
  const merchantSourceLabel = describeEditSource(txn.edited_merchant_source);
  const categorySourceLabel = describeEditSource(txn.edited_category_id_source);
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
                {account.institution && <span className="subtle">  .  {account.institution}</span>}
              </>
            ) : '-'}
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
            {txn.original_description || <span className="subtle">-</span>}
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
              href={`/budgets?month=${encodeURIComponent((txn.date || '').slice(0, 7))}`}
              className="linkish"
            >
              View budgets for {(txn.date || '').slice(0, 7) || 'this month'}
            </a>
            {category && <> . {category.name}</>}
          </dd>
        </div>
      </dl>

      <div className="txn-detail-actions">
        <button type="button" className="btn-secondary btn-compact" onClick={onEdit} {...tabProps}>
          Edit
        </button>
        <button
          type="button"
          className="btn-secondary btn-compact"
          onClick={onCreateRule}
          {...tabProps}
        >
          Create rule
        </button>
        {onMarkRecurring && (
          <button
            type="button"
            className="btn-secondary btn-compact"
            onClick={onMarkRecurring}
            {...tabProps}
          >
            Mark as recurring
          </button>
        )}
        <button
          type="button"
          className={`btn-secondary btn-compact ${txn.is_transfer ? 'btn-active' : ''} ${transferEdited ? 'edited' : ''}`}
          onClick={onToggleTransfer}
          {...tabProps}
        >
          {txn.is_transfer ? 'Unmark as transfer' : 'Mark as transfer'}
        </button>
        {mhaTrackerEnabled && (
          <button
            type="button"
            className={`btn-secondary btn-compact ${txn.mha_eligible ? 'btn-active' : ''}`}
            onClick={onToggleMhaEligible}
            aria-pressed={!!txn.mha_eligible}
            {...tabProps}
          >
            MHA Eligible
          </button>
        )}
        <button
          type="button"
          className={`btn-secondary btn-compact ${txn.is_ignored ? 'btn-active' : ''} ${ignoredEdited ? 'edited' : ''}`}
          onClick={onToggleIgnored}
          {...tabProps}
        >
          {txn.is_ignored ? 'Unignore' : 'Ignore'}
        </button>
        <button
          type="button"
          className="btn-danger btn-compact"
          onClick={onDelete}
          {...tabProps}
        >
          Delete
        </button>
      </div>
    </div>
  );
}

export function EditTransactionModal({ txn, categories, onClose, onSaved, onReset }) {
  const [merchant, setMerchant] = useState(txn.merchant || '');
  const [categoryId, setCategoryId] = useState(txn.category_id || '');
  const [notes, setNotes] = useState(txn.notes || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const merchantEdited = txn.edited_merchant_source !== null;
  const categoryEdited = txn.edited_category_id_source !== null;

  async function handleSave(event, close) {
    event.preventDefault();
    setSaving(true);
    setError('');

    const patch = {
      merchant: merchant.trim(),
      category_id: categoryId ? Number(categoryId) : null,
      notes
    };

    try {
      const result = await api.patch(`/api/transactions/${txn.id}`, patch);
      close({ animate: true });
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

          <form onSubmit={(event) => handleSave(event, close)}>
            <label className="field">
              <span>
                Merchant
                {merchantEdited && (
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => {
                      onReset('merchant');
                      close({ animate: true });
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
                onChange={(event) => setMerchant(event.target.value)}
                required
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
                      close({ animate: true });
                    }}
                    title="Clear the edit and let any matching rule re-apply"
                  >
                    reset to original
                  </button>
                )}
              </span>
              <select
                value={categoryId || ''}
                onChange={(event) => setCategoryId(event.target.value)}
              >
                <option value="">(Uncategorized)</option>
                {categories.map((categoryOption) => (
                  <option key={categoryOption.id} value={categoryOption.id}>
                    {categoryOption.icon} {categoryOption.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Notes</span>
              <textarea
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                placeholder="Optional"
                rows={3}
                style={{ fontFamily: 'var(--font-sans)', fontSize: '0.92rem' }}
              />
            </label>

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
