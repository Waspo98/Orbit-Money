import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';

const COLOR_PRESETS = [
  '#5B8DEF',
  '#66BB6A',
  '#FF7043',
  '#AB47BC',
  '#F6C454',
  '#26A69A',
  '#EC407A',
  '#78909C'
];
const HEX_COLOR_RE = /^#[0-9a-fA-F]{6}$/;

function displayIcon(category) {
  return category.icon || '#';
}

function describeFlags(category) {
  const flags = [];
  if (category.is_income) flags.push('Income');
  if (category.is_transfer) flags.push('Transfer');
  if (category.mha_default_eligible) flags.push('MHA include');
  if (category.mha_default_ignored) flags.push('MHA ignore');
  return flags;
}

export default function Categories({ onChange }) {
  const navigate = useNavigate();
  const { alert, confirm, Dialog } = useAppDialog();
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState(null);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const data = await api.get('/api/categories');
      setCategories(data.items);
    } catch (err) {
      setError(err.message || 'Failed to load categories');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return categories;
    return categories.filter((category) =>
      `${category.name} ${category.icon || ''}`.toLowerCase().includes(q)
    );
  }, [categories, search]);

  async function handleDelete(category) {
    if (category.rule_count > 0) {
      alert(
        `"${category.name}" is used by ${category.rule_count.toLocaleString()} rule${
          category.rule_count === 1 ? '' : 's'
        }. Update or delete those rules before removing the category.`,
        { title: 'Category used by rules' }
      );
      return;
    }

    const transactionCount = Number(category.transaction_count || 0);
    const budgetCount = Number(category.budget_count || 0);
    const detail = [
      transactionCount > 0
        ? `${transactionCount.toLocaleString()} transaction${
            transactionCount === 1 ? '' : 's'
          } will become uncategorized`
        : null,
      budgetCount > 0
        ? `${budgetCount.toLocaleString()} budget${budgetCount === 1 ? '' : 's'} will be removed`
        : null
    ]
      .filter(Boolean)
      .join(', ');

    const ok = await confirm(
      `Delete "${category.name}"?${detail ? ` ${detail}.` : ''} This can't be undone.`,
      {
        title: 'Delete category',
        confirmLabel: 'Delete',
        destructive: true
      }
    );
    if (!ok) return;

    try {
      await api.del(`/api/categories/${category.id}`);
      await load();
      onChange?.();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  function viewTransactions(category) {
    navigate(`/transactions?categories=${category.id}`);
  }

  return (
    <div className="categories-view">
      <div className="view-header">
        <div>
          <h2>Category Manager</h2>
          <p className="muted">
            {categories.length.toLocaleString()} categor{categories.length === 1 ? 'y' : 'ies'}
          </p>
        </div>
        <button
          type="button"
          className="btn-primary"
          onClick={() => setEditing({})}
        >
          + New category
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="categories-toolbar">
        <input
          type="search"
          className="rules-search"
          placeholder="Search categories..."
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
          <div className="empty-state-icon">#</div>
          <h2>{search ? 'No matching categories' : 'No categories yet'}</h2>
          <p>
            {search
              ? 'Try a different search term.'
              : 'Add categories to organize transactions, budgets, and rules.'}
          </p>
          {!search && (
            <button
              type="button"
              className="btn-primary"
              onClick={() => setEditing({})}
            >
              + New category
            </button>
          )}
        </div>
      ) : (
        <ul className="category-list">
          {filtered.map((category) => (
            <CategoryRow
              key={category.id}
              category={category}
              onEdit={() => setEditing(category)}
              onDelete={() => handleDelete(category)}
              onViewTransactions={() => viewTransactions(category)}
            />
          ))}
        </ul>
      )}

      {editing && (
        <CategoryEditor
          category={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
            onChange?.();
          }}
        />
      )}

      <Dialog />
    </div>
  );
}

function CategoryRow({ category, onEdit, onDelete, onViewTransactions }) {
  const flags = describeFlags(category);
  const transactionCount = Number(category.transaction_count || 0);

  return (
    <li className="category-row">
      <div className="category-swatch-wrap">
        <span
          className="category-swatch"
          style={{ '--category-color': category.color }}
          aria-hidden="true"
        >
          {displayIcon(category)}
        </span>
      </div>

      <div className="category-main">
        <div className="category-name-row">
          <span className="category-name">{category.name}</span>
          {flags.map((flag) => (
            <span key={flag} className="type-pill type-other">
              {flag}
            </span>
          ))}
        </div>
        <div className="category-meta">
          <span>{transactionCount.toLocaleString()} transaction{transactionCount === 1 ? '' : 's'}</span>
          {category.budget_count > 0 && (
            <span>{category.budget_count.toLocaleString()} budget{category.budget_count === 1 ? '' : 's'}</span>
          )}
          {category.rule_count > 0 && (
            <span>{category.rule_count.toLocaleString()} rule{category.rule_count === 1 ? '' : 's'}</span>
          )}
        </div>
      </div>

      <div className="category-actions">
        <button
          type="button"
          className="btn-secondary btn-compact"
          onClick={onViewTransactions}
          disabled={transactionCount === 0}
        >
          See transactions
        </button>
        <button type="button" className="btn-secondary btn-compact" onClick={onEdit}>
          Edit
        </button>
        <button
          type="button"
          className="btn-ghost"
          onClick={onDelete}
          aria-label={`Delete ${category.name}`}
        >
          {'\u2715'}
        </button>
      </div>
    </li>
  );
}

function CategoryEditor({ category, onClose, onSaved }) {
  const isNew = !category.id;
  const [name, setName] = useState(category.name || '');
  const [icon, setIcon] = useState(category.icon || '');
  const [color, setColor] = useState(category.color || COLOR_PRESETS[0]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const previewColor = HEX_COLOR_RE.test(color) ? color : '#888888';

  async function handleSave(e, close) {
    e.preventDefault();
    const body = {
      name: name.trim(),
      icon: icon.trim(),
      color
    };

    if (!body.name) {
      setError('Name cannot be empty.');
      return;
    }
    if (!HEX_COLOR_RE.test(body.color)) {
      setError('Color must be a 6-digit hex value, like #5B8DEF.');
      return;
    }

    setSaving(true);
    setError('');
    try {
      if (isNew) {
        await api.post('/api/categories', body);
      } else {
        await api.put(`/api/categories/${category.id}`, body);
      }
      close();
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Save failed');
      setSaving(false);
    }
  }

  return (
    <AnimatedModal onClose={onClose}>
      {({ close }) => (
        <>
          <h3>{isNew ? 'New category' : 'Edit category'}</h3>

          <form onSubmit={(e) => handleSave(e, close)}>
            <div className="category-editor-preview">
              <span
                className="category-swatch category-swatch-large"
                style={{ '--category-color': previewColor }}
                aria-hidden="true"
              >
                {icon || '#'}
              </span>
              <div>
                <strong>{name.trim() || 'Category name'}</strong>
                <p>Preview</p>
              </div>
            </div>

            <label className="field">
              <span>Name</span>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Groceries"
                required
              />
            </label>

            <label className="field">
              <span>Emoji</span>
              <input
                type="text"
                value={icon}
                onChange={(e) => setIcon(e.target.value)}
                placeholder="🍽"
                maxLength={24}
              />
            </label>

            <label className="field">
              <span>Color</span>
              <div className="category-color-control">
                <input
                  type="color"
                  value={previewColor}
                  onChange={(e) => setColor(e.target.value)}
                  aria-label="Pick category color"
                />
                <input
                  type="text"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  pattern="#[0-9a-fA-F]{6}"
                  placeholder="#5B8DEF"
                  spellCheck={false}
                />
              </div>
            </label>

            <div className="category-color-presets" aria-label="Color presets">
              {COLOR_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  className={`category-color-preset ${color.toLowerCase() === preset.toLowerCase() ? 'active' : ''}`}
                  style={{ '--category-color': preset }}
                  onClick={() => setColor(preset)}
                  aria-label={`Use ${preset}`}
                />
              ))}
            </div>

            {!isNew && (category.is_income || category.is_transfer) && (
              <div className="warning-banner">
                Income and transfer behavior stays unchanged here. This editor only
                updates the name, emoji, and color.
              </div>
            )}

            {error && <div className="error">{error}</div>}

            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Saving...' : isNew ? 'Create category' : 'Save'}
              </button>
            </div>
          </form>
        </>
      )}
    </AnimatedModal>
  );
}
