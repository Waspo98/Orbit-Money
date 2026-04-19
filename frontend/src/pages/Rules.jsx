import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';

// ============================================================================
// Field / operator / action vocabularies (must stay in sync with ruleMatcher.js)
// ============================================================================

const FIELD_OPTIONS = [
  { value: 'merchant',              label: 'Merchant name' },
  { value: 'original_description',  label: 'Original description' },
  { value: 'amount',                label: 'Amount' },
  { value: 'account_id',            label: 'Account' },
  { value: 'category_id',           label: 'Category' }
];

const OPERATORS_BY_FIELD_TYPE = {
  text:    ['contains', 'equals', 'starts_with', 'regex'],
  number:  ['equals', 'greater_than', 'less_than', 'between'],
  id:      ['is', 'is_not']
};

const OPERATOR_LABELS = {
  contains:      'contains',
  equals:        'equals',
  starts_with:   'starts with',
  regex:         'matches regex',
  greater_than:  'is greater than',
  less_than:     'is less than',
  between:       'is between',
  is:            'is',
  is_not:        'is not'
};

function fieldType(field) {
  if (field === 'amount') return 'number';
  if (field === 'account_id' || field === 'category_id') return 'id';
  return 'text';
}

const ACTION_TYPES = [
  { value: 'rename',         label: 'Rename merchant to' },
  { value: 'categorize',     label: 'Set category to' },
  { value: 'mark_transfer',  label: 'Mark as transfer' },
  { value: 'mark_ignored',   label: 'Ignore from budget' }
];

// ============================================================================
// Human-readable summaries for the list view
// ============================================================================

function summarizeCondition(cond, accounts, categories) {
  const fieldLabel = FIELD_OPTIONS.find((f) => f.value === cond.field)?.label || cond.field;
  const opLabel = OPERATOR_LABELS[cond.operator] || cond.operator;

  let value = cond.value;
  if (cond.field === 'account_id') {
    const a = accounts.find((x) => x.id === Number(cond.value));
    value = a ? a.name : `account #${cond.value}`;
  } else if (cond.field === 'category_id') {
    const c = categories.find((x) => x.id === Number(cond.value));
    value = c ? c.name : `category #${cond.value}`;
  } else if (Array.isArray(cond.value)) {
    value = cond.value.join(' and ');
  }

  return `${fieldLabel} ${opLabel} "${value}"`;
}

function summarizeAction(action, categories) {
  switch (action.type) {
    case 'rename':
      return `Rename to "${action.value}"`;
    case 'categorize': {
      const c = categories.find((x) => x.id === Number(action.value));
      return `Set category to ${c ? c.name : `#${action.value}`}`;
    }
    case 'mark_transfer':
      return 'Mark as transfer';
    case 'mark_ignored':
      return 'Ignore from budget';
    default:
      return action.type;
  }
}

// ============================================================================
// Main component
// ============================================================================

export default function Rules() {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState(null);       // rule being edited (or {} for new)
  const [wipeOpen, setWipeOpen] = useState(false);
  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);

  async function loadAll() {
    setLoading(true);
    setError('');
    try {
      const [rulesData, accountsData, categoriesData] = await Promise.all([
        api.get('/api/rules?withCounts=1'),
        api.get('/api/accounts'),
        api.get('/api/categories')
      ]);
      setRules(rulesData.items);
      setAccounts(accountsData.items);
      setCategories(categoriesData.items);
    } catch (err) {
      setError(err.message || 'Failed to load rules');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadAll(); }, []);

  async function handleToggle(rule) {
    // Optimistic update of the enabled flag for snappy feedback.
    setRules((prev) =>
      prev.map((r) => (r.id === rule.id ? { ...r, enabled: !r.enabled } : r))
    );
    try {
      await api.patch(`/api/rules/${rule.id}/enabled`, { enabled: !rule.enabled });
      // Server reapplies synchronously. Refetch so match counts reflect the
      // new enabled-set (disabled rules still show match counts because the
      // count is condition-driven, but other rules may now claim different
      // fields and their counts can shift).
      loadAll();
    } catch (err) {
      setRules((prev) =>
        prev.map((r) => (r.id === rule.id ? { ...r, enabled: rule.enabled } : r))
      );
      alert(err.message || 'Failed to toggle rule');
    }
  }

  async function handleDelete(rule) {
    if (!confirm(`Delete rule "${rule.name}"? This can't be undone.`)) return;
    try {
      await api.del(`/api/rules/${rule.id}`);
      loadAll();
    } catch (err) {
      alert(err.message || 'Delete failed');
    }
  }

  const filtered = useMemo(() => {
    if (!search.trim()) return rules;
    const q = search.toLowerCase();
    return rules.filter((r) => {
      if (r.name.toLowerCase().includes(q)) return true;
      const condText = r.conditions.map((c) => String(c.value || '')).join(' ').toLowerCase();
      const actionText = r.actions.map((a) => String(a.value || '')).join(' ').toLowerCase();
      return condText.includes(q) || actionText.includes(q);
    });
  }, [rules, search]);

  return (
    <div className="rules-view">
      <div className="view-header">
        <div>
          <h2>Rules</h2>
          <p className="muted">
            {rules.length.toLocaleString()} rule{rules.length === 1 ? '' : 's'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {rules.length > 0 && (
            <button
              type="button"
              className="btn-danger"
              onClick={() => setWipeOpen(true)}
            >
              Wipe all
            </button>
          )}
          <button
            type="button"
            className="btn-primary"
            onClick={() => setEditing({})}
          >
            + New rule
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="rules-toolbar">
        <input
          type="search"
          className="rules-search"
          placeholder="Search rules by name, merchant, or category…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {loading ? (
        <div className="center-loading">
          <div className="spinner" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">⊙</div>
          <h2>{search ? 'No matching rules' : 'No rules yet'}</h2>
          <p>
            {search
              ? 'Try a different search term.'
              : 'Create a rule to automatically rename, categorize, or ignore transactions.'}
          </p>
          {!search && (
            <button
              type="button"
              className="btn-primary"
              onClick={() => setEditing({})}
            >
              + New rule
            </button>
          )}
        </div>
      ) : (
        <ul className="rules-list">
          {filtered.map((rule) => (
            <li
              key={rule.id}
              className={`rule-row ${rule.enabled ? '' : 'disabled'}`}
            >
              <div className="rule-main">
                <div className="rule-name-row">
                  <span className="rule-name">{rule.name}</span>
                  {typeof rule.match_count === 'number' && (
                    <span
                      className={`rule-match-count ${rule.match_count > 0 ? 'has-matches' : ''}`}
                    >
                      {rule.match_count.toLocaleString()} match
                      {rule.match_count === 1 ? '' : 'es'}
                    </span>
                  )}
                </div>
                <div className="rule-summary">
                  <span className="rule-if">If </span>
                  {rule.conditions.map((c, i) => (
                    <span key={i}>
                      {i > 0 && <span className="rule-and"> and </span>}
                      <span className="rule-condition">
                        {summarizeCondition(c, accounts, categories)}
                      </span>
                    </span>
                  ))}
                  <span className="rule-arrow"> → </span>
                  {rule.actions.map((a, i) => (
                    <span key={i}>
                      {i > 0 && <span className="rule-and"> and </span>}
                      <span className="rule-action">
                        {summarizeAction(a, categories)}
                      </span>
                    </span>
                  ))}
                </div>
              </div>

              <div className="rule-actions">
                <label
                  className="switch"
                  title={rule.enabled ? 'Enabled' : 'Disabled'}
                >
                  <input
                    type="checkbox"
                    checked={rule.enabled}
                    onChange={() => handleToggle(rule)}
                  />
                  <span className="switch-track" />
                </label>
                <button
                  type="button"
                  className="btn-secondary btn-compact"
                  onClick={() => setEditing(rule)}
                >
                  Edit
                </button>
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={() => handleDelete(rule)}
                  aria-label="Delete rule"
                >
                  ✕
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {editing && (
        <RuleEditor
          rule={editing}
          accounts={accounts}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            loadAll();
          }}
        />
      )}

      {wipeOpen && (
        <WipeRulesModal
          count={rules.length}
          onClose={() => setWipeOpen(false)}
          onWiped={() => {
            setWipeOpen(false);
            loadAll();
          }}
        />
      )}
    </div>
  );
}

// ============================================================================
// Wipe-all modal — requires typing "DELETE" to confirm
// ============================================================================

function WipeRulesModal({ count, onClose, onWiped }) {
  const [confirmText, setConfirmText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const canConfirm = confirmText === 'DELETE';

  async function handleWipe(close) {
    if (!canConfirm) return;
    setBusy(true);
    setError('');
    try {
      await api.del('/api/rules/all');
      close();
      setTimeout(onWiped, 180);
    } catch (err) {
      setError(err.message || 'Wipe failed');
      setBusy(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>Wipe all rules?</h3>
          <p>
            This permanently deletes all{' '}
            <strong style={{ color: 'var(--text)' }}>
              {count.toLocaleString()}
            </strong>{' '}
            rules. Transactions that were previously renamed or recategorized
            by these rules are not affected — their current values stay.
            Future transactions won't be auto-processed until you create new
            rules.
          </p>

          <div className="warning-banner">
            <strong>This cannot be undone.</strong>
          </div>

          <label className="field">
            <span>Type DELETE to confirm</span>
            <input
              type="text"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder="DELETE"
              autoComplete="off"
              autoCapitalize="characters"
            />
          </label>

          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
            <button
              type="button"
              className="btn-danger"
              onClick={() => handleWipe(close)}
              disabled={!canConfirm || busy}
            >
              {busy ? 'Wiping…' : `Wipe all ${count.toLocaleString()} rules`}
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Rule editor modal
// ============================================================================

export function RuleEditor({ rule, accounts, categories, onClose, onSaved }) {
  const isNew = !rule.id;

  const [name, setName] = useState(rule.name || '');
  const [conditions, setConditions] = useState(
    rule.conditions?.length
      ? rule.conditions
      : [{ field: 'merchant', operator: 'contains', value: '' }]
  );
  const [actions, setActions] = useState(
    rule.actions?.length ? rule.actions : [{ type: 'rename', value: '' }]
  );
  const [enabled, setEnabled] = useState(rule.enabled !== false);
  const [previewCount, setPreviewCount] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Debounced preview — runs ~400ms after the last change.
  useEffect(() => {
    const isValid = conditions.every((c) => c.value !== '' && c.value !== undefined);
    if (!isValid) {
      setPreviewCount(null);
      return;
    }
    setPreviewing(true);
    const id = setTimeout(async () => {
      try {
        const data = await api.post('/api/rules/preview', { conditions, actions });
        setPreviewCount(data.count);
      } catch {
        setPreviewCount(null);
      } finally {
        setPreviewing(false);
      }
    }, 400);
    return () => clearTimeout(id);
  }, [conditions, actions]);

  function updateCondition(i, patch) {
    setConditions((prev) => prev.map((c, idx) => (idx === i ? { ...c, ...patch } : c)));
  }

  function changeField(i, newField) {
    const newType = fieldType(newField);
    const validOps = OPERATORS_BY_FIELD_TYPE[newType];
    const currentOp = conditions[i].operator;
    const operator = validOps.includes(currentOp) ? currentOp : validOps[0];
    updateCondition(i, { field: newField, operator, value: newField === 'account_id' ? (accounts[0]?.id ?? '') : newField === 'category_id' ? (categories[0]?.id ?? '') : '' });
  }

  function addCondition() {
    setConditions((prev) => [
      ...prev,
      { field: 'merchant', operator: 'contains', value: '' }
    ]);
  }

  function removeCondition(i) {
    if (conditions.length === 1) return;
    setConditions((prev) => prev.filter((_, idx) => idx !== i));
  }

  function updateAction(i, patch) {
    setActions((prev) => prev.map((a, idx) => (idx === i ? { ...a, ...patch } : a)));
  }

  function changeActionType(i, newType) {
    let value = '';
    if (newType === 'categorize') value = categories[0]?.id ?? '';
    if (newType === 'mark_transfer' || newType === 'mark_ignored') value = true;
    updateAction(i, { type: newType, value });
  }

  function addAction() {
    setActions((prev) => [...prev, { type: 'rename', value: '' }]);
  }

  function removeAction(i) {
    if (actions.length === 1) return;
    setActions((prev) => prev.filter((_, idx) => idx !== i));
  }

  async function handleSave(e, close) {
    e.preventDefault();
    setError('');
    setSaving(true);

    // Auto-generate a name if empty.
    let finalName = name.trim();
    if (!finalName) {
      const firstCond = summarizeCondition(conditions[0], accounts, categories);
      finalName = `Rule: ${firstCond}`.slice(0, 80);
    }

    const body = {
      name: finalName,
      conditions,
      actions,
      enabled
    };

    try {
      if (isNew) {
        await api.post('/api/rules', body);
      } else {
        await api.put(`/api/rules/${rule.id}`, body);
      }
      close();
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>{isNew ? 'New rule' : 'Edit rule'}</h3>

          <form onSubmit={(e) => handleSave(e, close)}>
          <label className="field">
            <span>Name (optional)</span>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Auto-generated if left empty"
            />
          </label>

          {/* ----- IF (conditions) ----- */}
          <div className="rule-section">
            <div className="rule-section-label">If the transaction…</div>
            {conditions.map((c, i) => (
              <ConditionRow
                key={i}
                index={i}
                condition={c}
                accounts={accounts}
                categories={categories}
                onFieldChange={(field) => changeField(i, field)}
                onUpdate={(patch) => updateCondition(i, patch)}
                onRemove={conditions.length > 1 ? () => removeCondition(i) : null}
              />
            ))}
            <button type="button" className="btn-ghost rule-add-btn" onClick={addCondition}>
              + Add condition
            </button>
          </div>

          {/* ----- THEN (actions) ----- */}
          <div className="rule-section">
            <div className="rule-section-label">Then…</div>
            {actions.map((a, i) => (
              <ActionRow
                key={i}
                action={a}
                categories={categories}
                onTypeChange={(type) => changeActionType(i, type)}
                onUpdate={(patch) => updateAction(i, patch)}
                onRemove={actions.length > 1 ? () => removeAction(i) : null}
              />
            ))}
            <button type="button" className="btn-ghost rule-add-btn" onClick={addAction}>
              + Add action
            </button>
          </div>

          {/* ----- Preview ----- */}
          <div className="rule-preview">
            {previewing ? (
              <>
                <span className="spinner-inline" />
                <span>Previewing…</span>
              </>
            ) : previewCount !== null ? (
              <span>
                This rule would match{' '}
                <strong>{previewCount.toLocaleString()}</strong> existing
                transaction{previewCount === 1 ? '' : 's'}.
              </span>
            ) : (
              <span className="subtle">Fill in conditions to see a preview.</span>
            )}
          </div>

          <label className="toggle-row" style={{ marginBottom: 16 }}>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            <span>Rule enabled</span>
          </label>

          {error && <div className="error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={close}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={saving}>
              {saving ? 'Saving…' : isNew ? 'Create rule' : 'Save'}
            </button>
          </div>
        </form>
        </>
      )}
    </AnimatedModal>
  );
}

// ============================================================================
// Condition row
// ============================================================================

function ConditionRow({ index, condition, accounts, categories, onFieldChange, onUpdate, onRemove }) {
  const type = fieldType(condition.field);
  const operators = OPERATORS_BY_FIELD_TYPE[type];

  return (
    <div className="rule-row-builder">
      {index > 0 && <div className="rule-and-label">and</div>}
      <div className="rule-builder-grid">
        <select
          value={condition.field}
          onChange={(e) => onFieldChange(e.target.value)}
        >
          {FIELD_OPTIONS.map((f) => (
            <option key={f.value} value={f.value}>{f.label}</option>
          ))}
        </select>

        <select
          value={condition.operator}
          onChange={(e) => onUpdate({ operator: e.target.value })}
        >
          {operators.map((op) => (
            <option key={op} value={op}>{OPERATOR_LABELS[op] || op}</option>
          ))}
        </select>

        {condition.field === 'account_id' ? (
          <select
            value={condition.value || ''}
            onChange={(e) => onUpdate({ value: Number(e.target.value) })}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>
        ) : condition.field === 'category_id' ? (
          <select
            value={condition.value || ''}
            onChange={(e) => onUpdate({ value: Number(e.target.value) })}
          >
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        ) : condition.operator === 'between' ? (
          <div className="rule-between">
            <input
              type="number"
              step="0.01"
              value={Array.isArray(condition.value) ? condition.value[0] ?? '' : ''}
              onChange={(e) => {
                const arr = Array.isArray(condition.value) ? [...condition.value] : ['', ''];
                arr[0] = parseFloat(e.target.value);
                onUpdate({ value: arr });
              }}
              placeholder="Low"
            />
            <span>and</span>
            <input
              type="number"
              step="0.01"
              value={Array.isArray(condition.value) ? condition.value[1] ?? '' : ''}
              onChange={(e) => {
                const arr = Array.isArray(condition.value) ? [...condition.value] : ['', ''];
                arr[1] = parseFloat(e.target.value);
                onUpdate({ value: arr });
              }}
              placeholder="High"
            />
          </div>
        ) : type === 'number' ? (
          <input
            type="number"
            step="0.01"
            value={condition.value ?? ''}
            onChange={(e) => onUpdate({ value: parseFloat(e.target.value) })}
            placeholder="0.00"
          />
        ) : (
          <input
            type="text"
            value={condition.value ?? ''}
            onChange={(e) => onUpdate({ value: e.target.value })}
            placeholder="Value"
          />
        )}

        {onRemove && (
          <button
            type="button"
            className="btn-ghost rule-remove-btn"
            onClick={onRemove}
            aria-label="Remove condition"
          >
            ✕
          </button>
        )}
      </div>
    </div>
  );
}

// ============================================================================
// Action row
// ============================================================================

function ActionRow({ action, categories, onTypeChange, onUpdate, onRemove }) {
  const needsValue = action.type === 'rename' || action.type === 'categorize';

  return (
    <div className="rule-row-builder">
      <div className="rule-builder-grid">
        <select
          value={action.type}
          onChange={(e) => onTypeChange(e.target.value)}
        >
          {ACTION_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>

        {needsValue ? (
          action.type === 'categorize' ? (
            <select
              value={action.value || ''}
              onChange={(e) => onUpdate({ value: Number(e.target.value) })}
              style={{ gridColumn: 'span 2' }}
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              value={action.value || ''}
              onChange={(e) => onUpdate({ value: e.target.value })}
              placeholder="New merchant name"
              style={{ gridColumn: 'span 2' }}
            />
          )
        ) : (
          <span className="subtle" style={{ gridColumn: 'span 2', alignSelf: 'center' }}>
            (no value needed)
          </span>
        )}

        {onRemove && (
          <button
            type="button"
            className="btn-ghost rule-remove-btn"
            onClick={onRemove}
            aria-label="Remove action"
          >
            ✕
          </button>
        )}
      </div>
    </div>
  );
}
