import { memo, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import AppSelect from '../AppSelect.jsx';
import AnimatedModal from '../AnimatedModal.jsx';
import CollapseIndicator from '../CollapseIndicator.jsx';
import DropdownMenu from '../DropdownMenu.jsx';
import ImageUrlFinderModal from '../ImageUrlFinderModal.jsx';
import { formatSignedCurrency } from '../../lib/formatters.js';
import { formatMediumDate, formatMonthDay, formatMonthKeyLabel } from '../../lib/localDate.js';
import { useSwipeableRow } from '../../hooks/useSwipeableRow.js';
import { hapticSelection, hapticTap, triggerHaptic } from '../../lib/haptics.js';

const COLLAPSE_UNMOUNT_MS = 260;
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

export const TransactionRow = memo(function TransactionRow({
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
  const [renderDetail, setRenderDetail] = useState(expanded);
  useEffect(() => {
    if (expanded) {
      setRenderDetail(true);
      return undefined;
    }
    const timer = setTimeout(() => setRenderDetail(false), COLLAPSE_UNMOUNT_MS);
    return () => clearTimeout(timer);
  }, [expanded]);

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

  const maxSwipe = (onEdit && onDelete) ? 128 : (onEdit || onDelete) ? 68 : 0;
  const swipeDisabled = hideActions || expanded || maxSwipe === 0;

  const {
    swipeOffset,
    isSwiping,
    isOpen,
    suppressClickRef,
    closeSwipe,
    rowProps
  } = useSwipeableRow({
    disabled: swipeDisabled,
    maxSwipe
  });

  function handleSwipeEdit(e) {
    e.stopPropagation();
    hapticTap();
    closeSwipe();
    onEdit?.();
  }

  function handleSwipeDelete(e) {
    e.stopPropagation();
    triggerHaptic('medium');
    closeSwipe();
    onDelete?.();
  }

  function handleRowClick(event) {
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      return;
    }
    if (isOpen) {
      closeSwipe();
      return;
    }
    if (event.target.closest('button, a, input, select, textarea, [role=button]')) return;
    hapticSelection();
    onExpand();
  }

  return (
    <li
      className={`txn-row ${hideMerchantLogo ? 'no-logo' : ''} ${txn.is_ignored ? 'ignored' : ''} ${isTransfer ? 'transfer' : ''} ${expanded ? 'expanded' : ''} ${txn.has_edits ? 'has-edits' : ''} ${isOpen ? 'swiped-open' : ''}`}
      onClick={handleRowClick}
    >
      <div className="txn-row-swipe-wrapper" {...rowProps}>
        <div
          className={`txn-row-swipe-content ${isSwiping ? 'swiping' : ''}`}
          style={{
            transform: swipeOffset !== 0 ? `translate3d(${swipeOffset}px, 0, 0)` : undefined
          }}
        >
          <div className="txn-row-summary">
            {!hideMerchantLogo && (
              <TransactionMerchantMark txn={txn} category={category} onLogoChanged={onLogoChanged} />
            )}
            <div className="txn-main">
              <div className="txn-merchant">{txn.merchant}</div>
              <div className="txn-meta">
                <span className="txn-date-short">
                  {expanded ? formatMediumDate(txn.date) : formatMonthDay(txn.date)}
                </span>
                {category && (
                  <>
                    <span className="dot" />
                    <span className="txn-category-chip" style={{ color: category.color }}>
                      <span>{category.icon}</span>
                      <span>{category.name}</span>
                    </span>
                  </>
                )}
                {account && (!hideAccountInMeta || expanded) && (
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
            {!hideActions && (
              <div className="txn-row-trailing">
                {expanded ? (
                  <CollapseIndicator
                    expanded
                    className="txn-row-collapse-indicator"
                    label={`Collapse ${txn.merchant}`}
                  />
                ) : (
                  <div className="txn-row-menu">
                    <DropdownMenu items={actionItems} ariaLabel={`Actions for ${txn.merchant}`} />
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {!hideActions && !expanded && maxSwipe > 0 && (
          <div
            className="txn-swipe-actions"
            style={{
              visibility: (swipeOffset !== 0 || isOpen) ? 'visible' : 'hidden'
            }}
            aria-hidden={!isOpen && swipeOffset === 0}
          >
            {onEdit && (
              <button
                type="button"
                className="txn-swipe-action-btn edit"
                onClick={handleSwipeEdit}
                aria-label={`Edit ${txn.merchant}`}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                </svg>
                <span>Edit</span>
              </button>
            )}
            {onDelete && (
              <button
                type="button"
                className="txn-swipe-action-btn delete"
                onClick={handleSwipeDelete}
                aria-label={`Delete ${txn.merchant}`}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  <line x1="10" y1="11" x2="10" y2="17" />
                  <line x1="14" y1="11" x2="14" y2="17" />
                </svg>
                <span>Delete</span>
              </button>
            )}
          </div>
        )}
      </div>

      <div className="txn-detail-wrapper" aria-hidden={!expanded}>
        {(renderDetail || expanded) ? (
          <TransactionDetail
            txn={txn}
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
        ) : (
          <div className="txn-detail" />
        )}
      </div>
    </li>
  );
});

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
  const budgetMonth = (txn.date || '').slice(0, 7);
  const budgetLabel = budgetMonth ? `${formatMonthKeyLabel(budgetMonth)} Budget` : 'Monthly Budget';

  return (
    <div className="txn-detail">
      <dl className="txn-detail-grid txn-bento-grid">
        <div className={`txn-bento-tile ${merchantEdited ? 'is-edited' : ''}`}>
          <dt className="txn-bento-label">
            <span>Merchant</span>
            {merchantEdited && (
              <span className="edit-badge">{merchantSourceLabel}</span>
            )}
          </dt>
          <dd className="txn-bento-value">
            <div className="txn-bento-primary">{txn.merchant}</div>
            {merchantEdited && (
              <div className="txn-provenance-footer">
                <span className="subtle txn-original">
                  Originally: <code>{txn.original_merchant}</code>
                </span>
                <div className="txn-provenance-actions">
                  <button
                    type="button"
                    className="txn-provenance-pill m3-tactile"
                    onClick={() => {
                      hapticTap();
                      onResetField('merchant');
                    }}
                    title="Revert to the original bank value"
                    {...tabProps}
                  >
                    Reset
                  </button>
                  {merchantRuleId && onViewRule && (
                    <button
                      type="button"
                      className="txn-provenance-pill m3-tactile"
                      onClick={() => {
                        hapticTap();
                        onViewRule(merchantRuleId);
                      }}
                      title="Open this rule in the rule editor"
                      {...tabProps}
                    >
                      View Rule
                    </button>
                  )}
                </div>
              </div>
            )}
          </dd>
        </div>

        <div className={`txn-bento-tile ${categoryEdited ? 'is-edited' : ''}`}>
          <dt className="txn-bento-label">
            <span>Category</span>
            {categoryEdited && (
              <span className="edit-badge">{categorySourceLabel}</span>
            )}
          </dt>
          <dd className="txn-bento-value">
            {category ? (
              <span
                className="txn-bento-category-pill"
                style={category.color ? { '--cat-color': category.color, color: category.color } : undefined}
              >
                <span aria-hidden="true">{category.icon}</span>
                <span>{category.name}</span>
              </span>
            ) : (
              <span className="subtle">Uncategorized</span>
            )}
            {categoryEdited && (
              <div className="txn-provenance-footer">
                {originalCategory && (
                  <span className="subtle txn-original">
                    Originally: {originalCategory.icon} {originalCategory.name}
                  </span>
                )}
                <div className="txn-provenance-actions">
                  <button
                    type="button"
                    className="txn-provenance-pill m3-tactile"
                    onClick={() => {
                      hapticTap();
                      onResetField('category_id');
                    }}
                    title="Revert to the original category"
                    {...tabProps}
                  >
                    Reset
                  </button>
                  {categoryRuleId && onViewRule && (
                    <button
                      type="button"
                      className="txn-provenance-pill m3-tactile"
                      onClick={() => {
                        hapticTap();
                        onViewRule(categoryRuleId);
                      }}
                      title="Open this rule in the rule editor"
                      {...tabProps}
                    >
                      View Rule
                    </button>
                  )}
                </div>
              </div>
            )}
          </dd>
        </div>

        <div className="txn-bento-tile">
          <dt className="txn-bento-label">Budget</dt>
          <dd className="txn-bento-value">
            <Link
              to={`/budgets?month=${encodeURIComponent(budgetMonth)}`}
              className="txn-bento-budget-link m3-tactile"
              onClick={() => hapticTap()}
              {...tabProps}
            >
              <span className="txn-bento-budget-text">
                <span>{budgetLabel}</span>
                {category && <span className="subtle txn-bento-budget-cat"> · {category.name}</span>}
              </span>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M7 17L17 7" />
                <path d="M7 7h10v10" />
              </svg>
            </Link>
          </dd>
        </div>

        <div className="txn-bento-tile">
          <dt className="txn-bento-label">Original Description</dt>
          <dd className="txn-bento-value txn-detail-mono">
            {txn.original_description || <span className="subtle">-</span>}
          </dd>
        </div>

        {txn.notes && (
          <div className="txn-bento-tile txn-detail-full">
            <dt className="txn-bento-label">Notes</dt>
            <dd className="txn-bento-value">{txn.notes}</dd>
          </div>
        )}
      </dl>

      <div className="txn-control-deck">
        <div className="txn-status-chips" role="group" aria-label="Transaction status flags">
          <button
            type="button"
            className={`txn-status-chip m3-tactile ${txn.is_transfer ? 'btn-active active' : ''} ${transferEdited ? 'edited' : ''}`}
            onClick={() => {
              hapticSelection();
              onToggleTransfer?.();
            }}
            aria-pressed={!!txn.is_transfer}
            title={txn.is_transfer ? 'Unmark as transfer' : 'Mark as transfer'}
            {...tabProps}
          >
            {txn.is_transfer ? (
              <svg className="txn-chip-check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="17 1 21 5 17 9" />
                <path d="M3 11V9a4 4 0 0 1 4-4h14" />
                <polyline points="7 23 3 19 7 15" />
                <path d="M21 13v2a4 4 0 0 1-4 4H3" />
              </svg>
            )}
            <span>Transfer</span>
          </button>

          <button
            type="button"
            className={`txn-status-chip m3-tactile ${txn.is_ignored ? 'btn-active active' : ''} ${ignoredEdited ? 'edited' : ''}`}
            onClick={() => {
              hapticSelection();
              onToggleIgnored?.();
            }}
            aria-pressed={!!txn.is_ignored}
            title={txn.is_ignored ? 'Unignore transaction' : 'Ignore transaction'}
            {...tabProps}
          >
            {txn.is_ignored ? (
              <svg className="txn-chip-check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            )}
            <span>Ignored</span>
          </button>

          {mhaTrackerEnabled && (
            <button
              type="button"
              className={`txn-status-chip m3-tactile ${txn.mha_eligible ? 'btn-active active' : ''}`}
              onClick={() => {
                hapticSelection();
                onToggleMhaEligible?.();
              }}
              aria-pressed={!!txn.mha_eligible}
              title={txn.mha_eligible ? 'Mark MHA ineligible' : 'Mark MHA eligible'}
              {...tabProps}
            >
              {txn.mha_eligible ? (
                <svg className="txn-chip-check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              ) : (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
              )}
              <span>MHA Eligible</span>
            </button>
          )}
        </div>

        <div className="txn-detail-actions">
          <button
            type="button"
            className="btn-secondary btn-compact txn-action-pill txn-action-primary m3-tactile"
            onClick={() => {
              hapticTap();
              onEdit?.();
            }}
            {...tabProps}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
            </svg>
            <span>Edit</span>
          </button>

          <button
            type="button"
            className="btn-secondary btn-compact txn-action-pill m3-tactile"
            onClick={() => {
              hapticTap();
              onCreateRule?.();
            }}
            {...tabProps}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="4" y1="21" x2="4" y2="14" />
              <line x1="4" y1="10" x2="4" y2="3" />
              <line x1="12" y1="21" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12" y2="3" />
              <line x1="20" y1="21" x2="20" y2="16" />
              <line x1="20" y1="12" x2="20" y2="3" />
              <line x1="1" y1="14" x2="7" y2="14" />
              <line x1="9" y1="8" x2="15" y2="8" />
              <line x1="17" y1="16" x2="23" y2="16" />
            </svg>
            <span>Create Rule</span>
          </button>

          {onMarkRecurring && (
            <button
              type="button"
              className="btn-secondary btn-compact txn-action-pill m3-tactile"
              onClick={() => {
                hapticTap();
                onMarkRecurring();
              }}
              {...tabProps}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                <path d="M3 3v5h5" />
                <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
                <path d="M16 16h5v5" />
              </svg>
              <span>Recurring</span>
            </button>
          )}

          <button
            type="button"
            className="btn-danger btn-compact txn-action-pill txn-action-delete m3-tactile"
            onClick={() => {
              triggerHaptic('medium');
              onDelete?.();
            }}
            {...tabProps}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="3 6 5 6 21 6" />
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            </svg>
            <span>Delete</span>
          </button>
        </div>
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
