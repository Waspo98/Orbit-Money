import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { sortCategoriesByName } from '../../lib/categorySort.js';
import AppSelect from '../AppSelect.jsx';
import AnimatedModal from '../AnimatedModal.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from '../CurrencyInput.jsx';
import { TransactionRow } from '../transactions/TransactionRow.jsx';
import {
  ACTION_TYPES,
  FIELD_OPTIONS,
  OPERATOR_LABELS,
  OPERATORS_BY_FIELD_TYPE,
  fieldType,
  summarizeCondition
} from './ruleDefinitions.js';

export function RuleEditor({ rule, accounts, categories, onClose, onSaved }) {
  const [activeRule, setActiveRule] = useState(rule);
  const isNew = !activeRule.id;
  const sortedCategories = useMemo(() => sortCategoriesByName(categories), [categories]);
  const accountById = useMemo(
    () => new Map(accounts.map((account) => [account.id, account])),
    [accounts]
  );
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.id, category])),
    [categories]
  );

  const [name, setName] = useState(rule.name || '');
  const [conditions, setConditions] = useState(
    rule.conditions?.length
      ? rule.conditions
      : [{ field: 'merchant', operator: 'contains', value: '' }]
  );
  const [actions, setActions] = useState(
    rule.actions?.length ? rule.actions : [{ type: 'rename', value: '' }]
  );
  const [preview, setPreview] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [openingRuleId, setOpeningRuleId] = useState(null);
  const [error, setError] = useState('');

  function resetDraft(nextRule) {
    setActiveRule(nextRule);
    setName(nextRule.name || '');
    setConditions(
      nextRule.conditions?.length
        ? nextRule.conditions
        : [{ field: 'merchant', operator: 'contains', value: '' }]
    );
    setActions(nextRule.actions?.length ? nextRule.actions : [{ type: 'rename', value: '' }]);
    setPreview(null);
    setSaving(false);
    setError('');
  }

  useEffect(() => {
    resetDraft(rule);
  }, [rule.id]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const isValid = conditions.every((condition) => (
      condition.value !== '' && condition.value !== undefined
    ));
    if (!isValid) {
      setPreview(null);
      return undefined;
    }

    setPreviewing(true);
    const timeoutId = setTimeout(async () => {
      try {
        const data = await api.post('/api/rules/preview', {
          ruleId: activeRule.id || null,
          conditions,
          actions,
          enabled: true
        });
        setPreview(data);
      } catch {
        setPreview(null);
      } finally {
        setPreviewing(false);
      }
    }, 400);

    return () => clearTimeout(timeoutId);
  }, [activeRule.id, conditions, actions]);

  function updateCondition(index, patch) {
    setConditions((current) => current.map((condition, currentIndex) => (
      currentIndex === index ? { ...condition, ...patch } : condition
    )));
  }

  function changeField(index, nextField) {
    const nextType = fieldType(nextField);
    const validOperators = OPERATORS_BY_FIELD_TYPE[nextType];
    const currentOperator = conditions[index].operator;
    const operator = validOperators.includes(currentOperator) ? currentOperator : validOperators[0];
    updateCondition(index, {
      field: nextField,
      operator,
      value: nextField === 'account_id'
        ? (accounts[0]?.id ?? '')
        : nextField === 'category_id'
          ? (sortedCategories[0]?.id ?? '')
          : ''
    });
  }

  function addCondition() {
    setConditions((current) => ([
      ...current,
      { field: 'merchant', operator: 'contains', value: '' }
    ]));
  }

  function removeCondition(index) {
    if (conditions.length === 1) return;
    setConditions((current) => current.filter((_, currentIndex) => currentIndex !== index));
  }

  function updateAction(index, patch) {
    setActions((current) => current.map((action, currentIndex) => (
      currentIndex === index ? { ...action, ...patch } : action
    )));
  }

  function changeActionType(index, nextType) {
    let value = '';
    if (nextType === 'categorize') value = sortedCategories[0]?.id ?? '';
    if (nextType === 'mark_transfer' || nextType === 'mark_ignored') value = true;
    updateAction(index, { type: nextType, value });
  }

  function addAction() {
    setActions((current) => [...current, { type: 'rename', value: '' }]);
  }

  function removeAction(index) {
    if (actions.length === 1) return;
    setActions((current) => current.filter((_, currentIndex) => currentIndex !== index));
  }

  async function handleSave(event, close) {
    event.preventDefault();
    setError('');
    setSaving(true);

    let finalName = name.trim();
    if (!finalName) {
      const renameAction = actions.find((action) => action.type === 'rename');
      finalName = String(
        renameAction?.value || summarizeCondition(conditions[0], accounts, categories)
      ).trim().slice(0, 80);
    }

    const body = {
      name: finalName,
      conditions,
      actions,
      enabled: true
    };

    try {
      if (isNew) {
        await api.post('/api/rules', body);
      } else {
        await api.put(`/api/rules/${activeRule.id}`, body);
      }
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  async function openConflictRule(ruleId) {
    setOpeningRuleId(ruleId);
    setError('');
    try {
      const data = await api.get(`/api/rules/${ruleId}`);
      if (data.rule) {
        resetDraft(data.rule);
      }
    } catch (err) {
      setError(err.message || 'Could not open that rule');
    } finally {
      setOpeningRuleId(null);
    }
  }

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <h3>{isNew ? 'New rule' : 'Edit rule'}</h3>

          <form onSubmit={(event) => handleSave(event, close)}>
            <label className="field">
              <span>Name (optional)</span>
              <input
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Auto-generated if left empty"
              />
            </label>

            <div className="rule-section">
              <div className="rule-section-label">If the transaction…</div>
              {conditions.map((condition, index) => (
                <ConditionRow
                  key={index}
                  index={index}
                  condition={condition}
                  accounts={accounts}
                  categories={sortedCategories}
                  onFieldChange={(field) => changeField(index, field)}
                  onUpdate={(patch) => updateCondition(index, patch)}
                  onRemove={conditions.length > 1 ? () => removeCondition(index) : null}
                />
              ))}
              <button type="button" className="btn-ghost rule-add-btn" onClick={addCondition}>
                + Add condition
              </button>
            </div>

            <div className="rule-section">
              <div className="rule-section-label">Then…</div>
              {actions.map((action, index) => (
                <ActionRow
                  key={index}
                  action={action}
                  categories={sortedCategories}
                  onTypeChange={(type) => changeActionType(index, type)}
                  onUpdate={(patch) => updateAction(index, patch)}
                  onRemove={actions.length > 1 ? () => removeAction(index) : null}
                />
              ))}
              <button type="button" className="btn-ghost rule-add-btn" onClick={addAction}>
                + Add action
              </button>
            </div>

            <RulePreview
              preview={preview}
              previewing={previewing}
              editing={!!activeRule.id}
              accountById={accountById}
              categoryById={categoryById}
              onEditRule={openConflictRule}
              openingRuleId={openingRuleId}
            />

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

function RulePreview({
  preview,
  previewing,
  editing,
  accountById,
  categoryById,
  onEditRule,
  openingRuleId
}) {
  if (previewing) {
    return (
      <div className="rule-preview">
        <span className="spinner-inline" />
        <span>Previewing...</span>
      </div>
    );
  }

  if (!preview) {
    return (
      <div className="rule-preview">
        <span className="subtle">Fill in conditions to see a preview.</span>
      </div>
    );
  }

  const matchCount = Number(preview.count || 0);
  const willChange = preview.willChange || [];
  const conflicts = preview.conflicts || [];
  const hasDetails = willChange.length > 0 || conflicts.length > 0;

  return (
    <div className={`rule-preview ${hasDetails ? 'rule-preview-rich' : ''}`}>
      <div className="rule-preview-summary">
        <span>
          <strong>{matchCount.toLocaleString()}</strong> matching transaction
          {matchCount === 1 ? '' : 's'}.
        </span>
      </div>

      {preview.willChangeCount > 0 && (
        <RulePreviewSection
          title={editing ? 'Affected' : 'Will Change'}
          count={preview.willChangeCount}
          items={willChange}
          limit={preview.limit}
          accountById={accountById}
          categoryById={categoryById}
        />
      )}

      {preview.conflictCount > 0 && (
        <RulePreviewSection
          title="Conflicts"
          count={preview.conflictCount}
          items={conflicts}
          limit={preview.limit}
          accountById={accountById}
          categoryById={categoryById}
          onEditRule={onEditRule}
          openingRuleId={openingRuleId}
          conflict
        />
      )}
    </div>
  );
}

function RulePreviewSection({
  title,
  count,
  items,
  limit,
  accountById,
  categoryById,
  onEditRule,
  openingRuleId,
  conflict = false
}) {
  return (
    <section className="rule-preview-section">
      <div className="rule-preview-section-header">
        <span>{title}</span>
        <span>{count.toLocaleString()}</span>
      </div>
      <div className="rule-preview-list">
        {items.map((item) => (
          <RulePreviewTransaction
            key={item.transaction.id}
            item={item}
            accountById={accountById}
            categoryById={categoryById}
            onEditRule={onEditRule}
            openingRuleId={openingRuleId}
            conflict={conflict}
          />
        ))}
      </div>
      {count > items.length && (
        <div className="rule-preview-more">
          Showing first {Math.min(limit || items.length, items.length).toLocaleString()}.
        </div>
      )}
    </section>
  );
}

function RulePreviewTransaction({
  item,
  accountById,
  categoryById,
  onEditRule,
  openingRuleId,
  conflict
}) {
  const txn = item.transaction;

  return (
    <div className={`rule-preview-transaction ${conflict ? 'has-conflict' : ''}`}>
      <ul className="txn-list dash-recent-txn-list rule-preview-transaction-row">
        <TransactionRow
          txn={txn}
          expanded={false}
          onExpand={() => {}}
          account={accountById.get(txn.account_id)}
          category={categoryById.get(txn.category_id)}
          originalCategory={categoryById.get(txn.original_category_id)}
          hideAccountInMeta={false}
          onEdit={() => {}}
          onCreateRule={() => {}}
          onToggleTransfer={() => {}}
          onToggleIgnored={() => {}}
          onToggleMhaEligible={() => {}}
          onDelete={() => {}}
          onResetField={() => {}}
          hideMerchantLogo
          hideActions
        />
      </ul>

      <div className="rule-preview-fields">
        {item.fields.map((field, index) => (
          <span key={`${field.field}-${field.ruleId || ''}-${index}`} className="rule-preview-field">
            {conflict
              ? `${field.label} handled by ${field.ruleName}`
              : formatPreviewChange(field, categoryById)}
          </span>
        ))}
      </div>

      {conflict && item.rules?.length > 0 && (
        <div className="rule-preview-conflict-actions">
          {item.rules.map((rule) => (
            <button
              key={rule.id}
              type="button"
              className="btn-secondary btn-compact"
              onClick={() => onEditRule(rule.id)}
              disabled={openingRuleId === rule.id}
            >
              {openingRuleId === rule.id ? 'Opening...' : `Edit ${rule.name}`}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function formatPreviewChange(field, categoryById) {
  if (field.applied) {
    return `${field.label} handled by this rule`;
  }
  return `${field.label}: ${formatPreviewValue(field.field, field.from, categoryById)} to ${formatPreviewValue(field.field, field.to, categoryById)}`;
}

function formatPreviewValue(field, value, categoryById) {
  if (field === 'category') {
    if (value === null || value === undefined || value === '') return 'Uncategorized';
    return categoryById.get(Number(value))?.name || 'Unknown category';
  }
  if (field === 'transfer' || field === 'ignored') {
    return value ? 'Yes' : 'No';
  }
  return value || 'Blank';
}

function ConditionRow({
  index,
  condition,
  accounts,
  categories,
  onFieldChange,
  onUpdate,
  onRemove
}) {
  const type = fieldType(condition.field);
  const operators = OPERATORS_BY_FIELD_TYPE[type];

  return (
    <div className="rule-row-builder">
      {index > 0 && <div className="rule-and-label">and</div>}
      <div className="rule-builder-grid">
        <AppSelect
          value={condition.field}
          options={FIELD_OPTIONS}
          onChange={onFieldChange}
          ariaLabel="Condition field"
        />

        <AppSelect
          value={condition.operator}
          options={operators.map((operator) => ({
            value: operator,
            label: OPERATOR_LABELS[operator] || operator
          }))}
          onChange={(operator) => onUpdate({ operator })}
          ariaLabel="Condition operator"
        />

        {condition.field === 'account_id' ? (
          <AppSelect
            value={condition.value || ''}
            options={accounts.map((account) => ({
              value: account.id,
              label: account.name
            }))}
            onChange={(nextValue) => onUpdate({ value: Number(nextValue) })}
            ariaLabel="Condition account"
          />
        ) : condition.field === 'category_id' ? (
          <AppSelect
            value={condition.value || ''}
            options={categories.map((category) => ({
              value: category.id,
              label: category.name
            }))}
            onChange={(nextValue) => onUpdate({ value: Number(nextValue) })}
            ariaLabel="Condition category"
          />
        ) : condition.operator === 'between' ? (
          <div className="rule-between">
            <CurrencyInput
              allowNegative
              value={Array.isArray(condition.value) ? formatCurrencyInput(condition.value[0] ?? '', { allowNegative: true }) : ''}
              onChange={(value) => {
                const nextValue = Array.isArray(condition.value) ? [...condition.value] : ['', ''];
                nextValue[0] = value ? parseCurrencyInput(value, 0) : '';
                onUpdate({ value: nextValue });
              }}
              placeholder="Low"
            />
            <span>and</span>
            <CurrencyInput
              allowNegative
              value={Array.isArray(condition.value) ? formatCurrencyInput(condition.value[1] ?? '', { allowNegative: true }) : ''}
              onChange={(value) => {
                const nextValue = Array.isArray(condition.value) ? [...condition.value] : ['', ''];
                nextValue[1] = value ? parseCurrencyInput(value, 0) : '';
                onUpdate({ value: nextValue });
              }}
              placeholder="High"
            />
          </div>
        ) : type === 'number' ? (
          <CurrencyInput
            allowNegative
            value={formatCurrencyInput(condition.value ?? '', { allowNegative: true })}
            onChange={(value) => onUpdate({ value: value ? parseCurrencyInput(value, 0) : '' })}
            placeholder="$0"
          />
        ) : (
          <input
            type="text"
            value={condition.value ?? ''}
            onChange={(event) => onUpdate({ value: event.target.value })}
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
            ×
          </button>
        )}
      </div>
    </div>
  );
}

function ActionRow({ action, categories, onTypeChange, onUpdate, onRemove }) {
  const needsValue = action.type === 'rename' || action.type === 'categorize';

  return (
    <div className="rule-row-builder">
      <div className="rule-builder-grid">
        <AppSelect
          value={action.type}
          options={ACTION_TYPES}
          onChange={onTypeChange}
          ariaLabel="Rule action"
        />

        {needsValue ? (
          action.type === 'categorize' ? (
            <AppSelect
              className="rule-select-span-2"
              value={action.value || ''}
              options={categories.map((category) => ({
                value: category.id,
                label: category.name
              }))}
              onChange={(nextValue) => onUpdate({ value: Number(nextValue) })}
              ariaLabel="Rule category"
            />
          ) : (
            <input
              type="text"
              value={action.value || ''}
              onChange={(event) => onUpdate({ value: event.target.value })}
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
            ×
          </button>
        )}
      </div>
    </div>
  );
}
