import { useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';
import CurrencyInput, {
  formatCurrencyInput,
  parseCurrencyInput
} from './CurrencyInput.jsx';
import { formatCurrency } from '../lib/formatters.js';

function formatMoney(value) {
  return formatCurrency(value);
}

export default function BudgetAmountModal({
  item,
  onClose,
  onSaved,
  onDelete,
  spendingDetail
}) {
  const [amount, setAmount] = useState(
    item.amount != null ? formatCurrencyInput(item.amount) : ''
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const isNew = !item.budget_id;

  async function handleSave(event, close) {
    event.preventDefault();
    setError('');

    const parsedAmount = parseCurrencyInput(amount, NaN);
    if (!Number.isFinite(parsedAmount) || parsedAmount < 0) {
      setError('Enter a valid non-negative amount.');
      return;
    }

    setSaving(true);
    try {
      await onSaved({ category_id: item.category.id, amount: parsedAmount });
      close({ animate: true });
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>
            {isNew ? 'Add' : 'Edit'} Budget - {item.category.icon} {item.category.name}
          </h3>
          <p className="subtle">
            Applies to every month.
            {spendingDetail && <> {spendingDetail}</>}
            {!spendingDetail && !isNew && item.spent > 0 && (
              <> Spent {formatMoney(item.spent)} this month so far.</>
            )}
          </p>

          <form onSubmit={(event) => handleSave(event, close)}>
            <label className="field">
              <span>Default Monthly Budget</span>
              <CurrencyInput
                placeholder="$0"
                value={amount}
                onChange={setAmount}
                required
              />
            </label>

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              {!isNew && onDelete && (
                <button
                  type="button"
                  className="btn-danger"
                  onClick={onDelete}
                  style={{ marginRight: 'auto' }}
                >
                  Delete
                </button>
              )}
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving...' : isNew ? 'Add Budget' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
