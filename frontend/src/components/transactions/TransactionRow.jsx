import { useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import AppSelect from '../AppSelect.jsx';
import AnimatedModal from '../AnimatedModal.jsx';
import DropdownMenu from '../DropdownMenu.jsx';
import ImageUrlFinderModal from '../ImageUrlFinderModal.jsx';
import { formatCurrency, formatCurrencyOr, formatSignedCurrency } from '../../lib/formatters.js';
import { formatFullDate, formatMonthDay } from '../../lib/localDate.js';

const EXPAND_ANIMATION_MS = 220;
const EXPAND_ANIMATION_BUFFER_MS = 40;
const reportedLogoStatuses = new Set();

function normalizeNullableId(value) {
  if (value === '' || value === null || value === undefined) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function describeEditSource(source) {
  if (!source) return null;
  if (source === 'user') return 'edited manually';
  if (source.startsWith('rule:')) return 'applied by rule';
  return 'edited';
}

function ruleIdFromEditSource(source) {
  if (typeof source !== 'string' || !source.startsWith('rule:')) return null;
  const id = Number(source.slice('rule:'.length));
  return Number.isInteger(id) && id > 0 ? id : null;
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
  onViewRule,
  onLogoChanged,
  hideMerchantLogo = false,
  hideActions = false,
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
        {!expanded && !hideActions && (
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
          onViewRule={onViewRule}
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

  async function ensureLogoEntry() {
    if (merchantLogo?.merchant_key) return merchantLogo;
    const result = await api.post('/api/merchant-logos/ensure', {
      transaction_id: txn.id
    });
    setMerchantLogo(result.merchant_logo);
    return result.merchant_logo;
  }

  async function searchBrands(searchQuery, options = {}) {
    const result = await api.post('/api/merchant-logos/search', {
      transaction_id: txn.id,
      query: searchQuery
    });
    if (options.activeRef && !options.activeRef()) return { candidates: [] };
    setMerchantLogo(result.merchant_logo);
    return {
      candidates: (result.candidates || []).map((candidate) => ({
        key: candidate.domain,
        imageUrl: candidate.logo_url,
        title: candidate.name,
        subtitle: candidate.domain
      })),
      message: result.configured === false
        ? 'Logo search is not configured on this server yet. Paste a direct image URL below.'
        : ''
    };
  }

  async function saveLogo(logoUrl, { close }) {
    const ensuredLogo = await ensureLogoEntry();
    if (!ensuredLogo?.merchant_key) {
      throw new Error('This merchant does not have a logo entry to update yet.');
    }
    const result = await api.post('/api/merchant-logos/override', {
      merchant_key: ensuredLogo.merchant_key,
      logo_url: logoUrl.trim()
    });
    close({ animate: true });
    setTimeout(() => onSaved(result.merchant_logo), 180);
  }

  async function useCategoryIcon({ close }) {
    const ensuredLogo = await ensureLogoEntry();
    if (!ensuredLogo?.merchant_key) {
      throw new Error('This merchant does not have a logo entry to update yet.');
    }
    const result = await api.post('/api/merchant-logos/override', {
      merchant_key: ensuredLogo.merchant_key,
      use_category_icon: true
    });
    close({ animate: true });
    setTimeout(() => onSaved(result.merchant_logo), 180);
  }

  return (
    <ImageUrlFinderModal
      title="Find Logo"
      copy={`Search Logo.dev results for ${txn.merchant}, or paste a direct image URL.`}
      onClose={onClose}
      initialQuery={txn.merchant || logo?.merchant_name || ''}
      initialUrl={logo?.url || ''}
      autoSearch
      searchLabel="Merchant Search"
      searchPlaceholder="Merchant name"
      urlLabel="Logo Image URL"
      urlPlaceholder="https://example.com/logo.png"
      emptyMessage="No Logo.dev matches found. Try a simpler merchant name or paste a direct image URL."
      externalSearchQuery={(currentQuery) => `${currentQuery || txn.merchant || 'merchant'} logo`}
      onSearch={searchBrands}
      onSave={saveLogo}
      secondaryActions={[
        {
          label: 'Use Category Icon',
          onClick: useCategoryIcon,
          keepSaving: true
        }
      ]}
      imageVariant="logo"
    />
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
  onViewRule,
  mhaTrackerEnabled = false
}) {
  const merchantEdited = txn.edited_merchant_source !== null;
  const categoryEdited = txn.edited_category_id_source !== null;
  const transferEdited = txn.edited_is_transfer_source !== null;
  const ignoredEdited = txn.edited_is_ignored_source !== null;
  const merchantSourceLabel = describeEditSource(txn.edited_merchant_source);
  const categorySourceLabel = describeEditSource(txn.edited_category_id_source);
  const merchantRuleId = ruleIdFromEditSource(txn.edited_merchant_source);
  const categoryRuleId = ruleIdFromEditSource(txn.edited_category_id_source);
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
                {merchantRuleId && onViewRule && (
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => onViewRule(merchantRuleId)}
                    title="Open this rule in the rule editor"
                    {...tabProps}
                  >
                    view rule
                  </button>
                )}
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
                {categoryRuleId && onViewRule && (
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => onViewRule(categoryRuleId)}
                    title="Open this rule in the rule editor"
                    {...tabProps}
                  >
                    view rule
                  </button>
                )}
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
  const trimmedMerchant = merchant.trim();
  const initialMerchant = (txn.merchant || '').trim();
  const normalizedCategoryId = normalizeNullableId(categoryId);
  const initialCategoryId = normalizeNullableId(txn.category_id);
  const hasChanges =
    trimmedMerchant !== initialMerchant ||
    normalizedCategoryId !== initialCategoryId ||
    notes !== (txn.notes || '');

  async function handleSave(event, close) {
    event.preventDefault();
    if (!hasChanges) return;
    setSaving(true);
    setError('');

    const patch = {};
    if (trimmedMerchant !== initialMerchant) patch.merchant = trimmedMerchant;
    if (normalizedCategoryId !== initialCategoryId) {
      patch.category_id = normalizedCategoryId;
    }
    if (notes !== (txn.notes || '')) patch.notes = notes;

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
              <AppSelect
                value={categoryId || ''}
                onChange={setCategoryId}
                ariaLabel="Transaction category"
                options={[
                  { value: '', label: '(Uncategorized)' },
                  ...categories.map((categoryOption) => ({
                    value: categoryOption.id,
                    label: `${categoryOption.icon} ${categoryOption.name}`
                  }))
                ]}
              />
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
              <button type="submit" className="btn-primary" disabled={saving || !hasChanges}>
                {saving ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
