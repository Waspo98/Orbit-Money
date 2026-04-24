import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import DropdownMenu from '../components/DropdownMenu.jsx';
import InlinePopover from '../components/InlinePopover.jsx';
import PageHero from '../components/PageHero.jsx';
import SearchField from '../components/SearchField.jsx';
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
const EMOJI_OPTIONS = [
  '🚗', '👶', '💡', '💵', '💝', '💳', '🍽️', '🎓',
  '🎬', '👨‍👩‍👧', '💸', '🎉', '🎁', '🛒', '💪', '🏠',
  '💰', '🔄', '📈', '⚖️', '🏦', '🏥', '💆', '🐾',
  '💹', '🛍️', '💻', '🧾', '✈️', '❓', '💱', '💼'
];

function displayIcon(category) {
  return category.icon || '#';
}

function describeFlags(category) {
  const flags = [];
  if (category.is_income) flags.push('Income');
  if (category.is_transfer) flags.push('Transfer');
  if (category.mha_default_eligible) flags.push('MHA eligible');
  if (category.mha_default_ignored) flags.push('MHA ignored');
  return flags;
}

function deleteSummary(category) {
  const transactionCount = Number(category.transaction_count || 0);
  const budgetCount = Number(category.budget_count || 0);
  return [
    transactionCount > 0
      ? `${transactionCount.toLocaleString()} transaction${
          transactionCount === 1 ? '' : 's'
        } will become uncategorized`
      : null,
    budgetCount > 0
      ? `${budgetCount.toLocaleString()} budget${
          budgetCount === 1 ? '' : 's'
        } will be removed`
      : null
  ]
    .filter(Boolean)
    .join(', ');
}

function sortCategoriesByName(items) {
  return [...items].sort((a, b) =>
    String(a.name || '').localeCompare(String(b.name || ''), undefined, {
      sensitivity: 'base',
      numeric: true
    })
  );
}

export default function Categories({
  mhaTrackerEnabled = false,
  onChange
}) {
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
      setCategories(sortCategoriesByName(data.items || []));
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
    const sorted = sortCategoriesByName(categories);
    if (!q) return sorted;
    return sorted.filter((category) =>
      `${category.name} ${category.icon || ''}`.toLowerCase().includes(q)
    );
  }, [categories, search]);

  function viewTransactions(category) {
    navigate(`/transactions?categories=${category.id}`);
  }

  async function deleteCategory(category) {
    if (category.rule_count > 0) {
      alert(
        `"${category.name}" is used by ${category.rule_count.toLocaleString()} rule${
          category.rule_count === 1 ? '' : 's'
        }. Update or delete those rules before removing the category.`,
        { title: 'Category used by rules' }
      );
      return;
    }

    const detail = deleteSummary(category);
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

  return (
    <div className="categories-view">
      <PageHero
        id="categories-title"
        variant="transactions"
        kicker="Spending Clusters"
        title="Category Manager"
        subtitle={`${categories.length.toLocaleString()} categor${
          categories.length === 1 ? 'y' : 'ies'
        }`}
        initialHeight={420}
        toolbar={(
          <div className="page-hero-action-row">
            <button
              type="button"
              className="btn-primary"
              onClick={() => setEditing({})}
            >
              + New Category
            </button>
          </div>
        )}
      />

      <div className="rules-toolbar categories-toolbar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search categories..."
          className="categories-search"
        />
      </div>

      <div className="categories-content">
        {error && <div className="error">{error}</div>}

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
                + New Category
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
                onViewTransactions={() => viewTransactions(category)}
                onDelete={() => deleteCategory(category)}
              />
            ))}
          </ul>
        )}
      </div>

      {editing && (
        <CategoryEditor
          category={editing}
          mhaTrackerEnabled={mhaTrackerEnabled}
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

function CategoryRow({ category, onEdit, onViewTransactions, onDelete }) {
  const flags = describeFlags(category);
  const transactionCount = Number(category.transaction_count || 0);
  const actionItems = [
    { label: 'Edit', onClick: onEdit },
    {
      label: 'See Transactions',
      onClick: onViewTransactions,
      disabled: transactionCount === 0
    },
    { divider: true },
    { label: 'Delete', onClick: onDelete, destructive: true }
  ];

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
        <div className="category-row-line category-row-line-title">
          <div className="category-name-row">
            <span className="category-name">{category.name}</span>
            {flags.map((flag) => (
              <span key={flag} className="type-pill type-other">
                {flag}
              </span>
            ))}
          </div>
          <DropdownMenu items={actionItems} ariaLabel={`Actions for ${category.name}`} />
        </div>

        <div className="category-row-line category-row-line-meta">
          <div className="category-meta">
            <span>
              {transactionCount.toLocaleString()} transaction
              {transactionCount === 1 ? '' : 's'}
            </span>
            {category.rule_count > 0 && (
              <span>
                {category.rule_count.toLocaleString()} rule
                {category.rule_count === 1 ? '' : 's'}
              </span>
            )}
          </div>
        </div>
      </div>
    </li>
  );
}

function CategoryEditor({
  category,
  mhaTrackerEnabled = false,
  onClose,
  onSaved
}) {
  const { alert, confirm, Dialog } = useAppDialog();
  const isNew = !category.id;
  const [name, setName] = useState(category.name || '');
  const [icon, setIcon] = useState(category.icon || '');
  const [color, setColor] = useState(category.color || COLOR_PRESETS[0]);
  const [mhaDefaultEligible, setMhaDefaultEligible] = useState(
    !!category.mha_default_eligible
  );
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const previewColor = HEX_COLOR_RE.test(color) ? color : '#888888';

  async function handleDelete(close) {
    if (isNew) return;
    if (category.rule_count > 0) {
      alert(
        `"${category.name}" is used by ${category.rule_count.toLocaleString()} rule${
          category.rule_count === 1 ? '' : 's'
        }. Update or delete those rules before removing the category.`,
        { title: 'Category used by rules' }
      );
      return;
    }

    const detail = deleteSummary(category);
    const ok = await confirm(
      `Delete "${category.name}"?${detail ? ` ${detail}.` : ''} This can't be undone.`,
      {
        title: 'Delete category',
        confirmLabel: 'Delete',
        destructive: true
      }
    );
    if (!ok) return;

    setDeleting(true);
    setError('');
    try {
      await api.del(`/api/categories/${category.id}`);
      close({ animate: true });
      setTimeout(onSaved, 180);
    } catch (err) {
      setError(err.message || 'Delete failed');
      setDeleting(false);
    }
  }

  async function handleSave(e, close) {
    e.preventDefault();
    const body = {
      name: name.trim(),
      icon: icon.trim(),
      color
    };

    if (mhaTrackerEnabled) {
      body.mha_default_eligible = mhaDefaultEligible;
    }

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
      close({ animate: true });
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

            <div className="category-editor-name-row">
              <label className="field category-emoji-field">
                <span>Emoji</span>
                <div className="category-emoji-picker-wrap">
                  <button
                    type="button"
                    className="category-emoji-button"
                    onClick={() => setEmojiPickerOpen((open) => !open)}
                    aria-label="Choose category emoji"
                    aria-expanded={emojiPickerOpen}
                  >
                    {icon || '#'}
                  </button>
                  <InlinePopover open={emojiPickerOpen} className="category-emoji-popover" role="listbox">
                      {EMOJI_OPTIONS.map((emoji) => (
                        <button
                          key={emoji}
                          type="button"
                          className={`category-emoji-option ${
                            icon === emoji ? 'active' : ''
                          }`}
                          onClick={() => {
                            setIcon(emoji);
                            setEmojiPickerOpen(false);
                          }}
                          aria-label={`Use ${emoji}`}
                        >
                          {emoji}
                        </button>
                      ))}
                      <label className="category-emoji-custom">
                        <span>Custom</span>
                        <input
                          type="text"
                          value={icon}
                          onChange={(e) => setIcon(e.target.value)}
                          placeholder="Paste emoji"
                          maxLength={24}
                          aria-label="Custom category emoji"
                        />
                      </label>
                  </InlinePopover>
                </div>
              </label>

              <label className="field category-name-field">
                <span>Name</span>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Groceries"
                  required
                />
              </label>
            </div>

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

            {mhaTrackerEnabled && (
              <label className="category-editor-toggle">
                <span>
                  <strong>MHA Eligible</strong>
                  <small>Automatically include transactions in this category.</small>
                </span>
                <input
                  type="checkbox"
                  checked={mhaDefaultEligible}
                  onChange={(e) => setMhaDefaultEligible(e.target.checked)}
                />
              </label>
            )}

            {!isNew && (category.is_income || category.is_transfer) && (
              <div className="warning-banner">
                Income and transfer behavior stays unchanged here. This editor only
                updates the name, emoji, color, and MHA default.
              </div>
            )}

            {error && <div className="error">{error}</div>}

            <div className="modal-actions category-editor-actions">
              {!isNew && (
                <button
                  type="button"
                  className="btn-danger"
                  onClick={() => handleDelete(close)}
                  disabled={saving || deleting}
                >
                  {deleting ? 'Deleting...' : 'Delete Category'}
                </button>
              )}
              <button type="button" className="btn-secondary" onClick={close}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={saving || deleting}>
                {saving ? 'Saving...' : isNew ? 'Create Category' : 'Save'}
              </button>
            </div>
          </form>
          <Dialog />
        </>
      )}
    </AnimatedModal>
  );
}
