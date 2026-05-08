import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import PageHero from '../components/PageHero.jsx';
import SearchField from '../components/SearchField.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { RuleEditor } from '../components/rules/RuleEditor.jsx';
import { summarizeAction, summarizeCondition } from '../components/rules/ruleDefinitions.js';

export default function Rules() {
  const { alert, confirm, Dialog } = useAppDialog();
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState(null);
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

  useEffect(() => {
    loadAll();
  }, []);

  async function handleToggle(rule) {
    setRules((current) => current.map((item) => (
      item.id === rule.id ? { ...item, enabled: !item.enabled } : item
    )));
    try {
      await api.patch(`/api/rules/${rule.id}/enabled`, { enabled: !rule.enabled });
      loadAll();
    } catch (err) {
      setRules((current) => current.map((item) => (
        item.id === rule.id ? { ...item, enabled: rule.enabled } : item
      )));
      alert(err.message || 'Failed to toggle rule', { title: 'Could not update rule' });
    }
  }

  async function handleDelete(rule) {
    const ok = await confirm(`Delete rule "${rule.name}"? This can't be undone.`, {
      title: 'Delete rule',
      confirmLabel: 'Delete',
      destructive: true
    });
    if (!ok) return;

    try {
      await api.del(`/api/rules/${rule.id}`);
      loadAll();
    } catch (err) {
      alert(err.message || 'Delete failed', { title: 'Delete failed' });
    }
  }

  const filtered = useMemo(() => {
    if (!search.trim()) return rules;
    const query = search.toLowerCase();
    return rules.filter((rule) => {
      if (rule.name.toLowerCase().includes(query)) return true;
      const conditionText = rule.conditions.map((condition) => String(condition.value || '')).join(' ').toLowerCase();
      const actionText = rule.actions.map((action) => String(action.value || '')).join(' ').toLowerCase();
      return conditionText.includes(query) || actionText.includes(query);
    });
  }, [rules, search]);

  return (
    <div className="rules-view">
      <PageHero
        id="rules-title"
        variant="rules"
        kicker="Automation"
        title="Rules"
        subtitle={`${rules.length.toLocaleString()} rule${rules.length === 1 ? '' : 's'}`}
      />

      <div className="page-action-row rules-page-actions" role="group" aria-label="Rule actions">
        <button
          type="button"
          className="btn-primary"
          onClick={() => setEditing({})}
        >
          + New Rule
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="rules-toolbar">
        <SearchField
          value={search}
          onChange={setSearch}
          placeholder="Search rules by name, merchant, or category..."
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
              + New Rule
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
                  {rule.conditions.map((condition, index) => (
                    <span key={index}>
                      {index > 0 && <span className="rule-and"> and </span>}
                      <span className="rule-condition">
                        {summarizeCondition(condition, accounts, categories)}
                      </span>
                    </span>
                  ))}
                  <span className="rule-arrow"> → </span>
                  {rule.actions.map((action, index) => (
                    <span key={index}>
                      {index > 0 && <span className="rule-and"> and </span>}
                      <span className="rule-action">
                        {summarizeAction(action, categories)}
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
                  ×
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {rules.length > 0 && (
        <div className="rules-danger-zone">
          <button
            type="button"
            className="btn-danger"
            onClick={() => setWipeOpen(true)}
          >
            Wipe all
          </button>
        </div>
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

      <Dialog />
    </div>
  );
}

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
      close({ animate: true });
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
            rules. Rule-applied transaction edits will be cleared, so those
            transactions fall back to their original bank/import values. Manual
            edits stay in place. Future transactions won't be auto-processed
            until you create new rules.
          </p>

          <div className="warning-banner">
            <strong>This cannot be undone.</strong>
          </div>

          <label className="field">
            <span>Type DELETE to confirm</span>
            <input
              type="text"
              value={confirmText}
              onChange={(event) => setConfirmText(event.target.value)}
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
              {busy ? 'Wiping...' : `Wipe all ${count.toLocaleString()} rules`}
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

export { RuleEditor };
