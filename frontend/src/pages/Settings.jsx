import { useEffect, useMemo, useRef, useState } from 'react';
import { closestCenter, DndContext } from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  verticalListSortingStrategy
} from '@dnd-kit/sortable';
import { api } from '../api.js';
import AnimatedModal from '../components/AnimatedModal.jsx';
import AppIcon from '../components/AppIcon.jsx';
import BrandLogo from '../components/BrandLogo.jsx';
import CollapseIndicator from '../components/CollapseIndicator.jsx';
import PageHero from '../components/PageHero.jsx';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import {
  ROUTES,
  normalizeNavigationPreferences
} from '../navigation.js';
import { APP_VERSION_LABEL } from '../version.js';
import { APP_ICON_512 } from '../brandAssets.js';

const SIMPLEFIN_BRIDGE_URL = 'https://beta-bridge.simplefin.org/';
const SETTINGS_CARD_ORDER_STORAGE_KEY = 'orbit-money-settings-card-order';

const THEME_OPTIONS = [
  {
    value: 'light',
    label: 'Day',
    emoji: '☀️',
    description: 'A bright interface for daylight use.'
  },
  {
    value: 'dark',
    label: 'Night',
    emoji: '🌙',
    description: 'A dimmer interface for low light.'
  },
  {
    value: 'system',
    label: 'System',
    emoji: '💻',
    description: 'Match this device automatically.'
  }
];

const DARK_VARIANT_OPTIONS = [
  {
    value: 'classic',
    label: 'Soft Dark',
    emoji: '🌘',
    description: "A gray dark theme that's gentle on the eyes."
  },
  {
    value: 'amoled',
    label: 'AMOLED Black',
    emoji: '⬛',
    description: 'A pure black background for AMOLED devices.'
  }
];

const SETTINGS_CARD_DEFS = [
  {
    id: 'appearance',
    title: 'Appearance',
    description: 'Choose how the app looks on this device.'
  },
  {
    id: 'features',
    title: 'Turn App Features On/Off',
    description: 'Show, hide, and reorder navigation sections.'
  },
  {
    id: 'simplefin',
    title: 'SimpleFIN',
    description: 'Bank connection, sync status, and bridge setup.'
  },
  {
    id: 'import',
    title: 'Import Data',
    description: 'Bring in Rocket Money data when you need to reload history.'
  },
  {
    id: 'account',
    title: 'Account',
    description: 'Household access and signed-in user details.'
  },
  {
    id: 'about',
    title: 'Orbit Money',
    description: 'Build details, developer info, and session controls.'
  }
];

const DEFAULT_SETTINGS_CARD_ORDER = SETTINGS_CARD_DEFS.map((card) => card.id);
const SETTINGS_CARD_BY_ID = new Map(SETTINGS_CARD_DEFS.map((card) => [card.id, card]));
const DEFAULT_COLLAPSED_SETTINGS_CARDS = DEFAULT_SETTINGS_CARD_ORDER.filter((id) => id !== 'about');

function normalizeSettingsCardOrder(value) {
  const incoming = Array.isArray(value) ? value : [];
  return [
    ...incoming.filter((id) => SETTINGS_CARD_BY_ID.has(id)),
    ...DEFAULT_SETTINGS_CARD_ORDER.filter((id) => !incoming.includes(id))
  ];
}

function readSettingsCardOrder() {
  try {
    return normalizeSettingsCardOrder(
      JSON.parse(localStorage.getItem(SETTINGS_CARD_ORDER_STORAGE_KEY) || '[]')
    );
  } catch {
    return DEFAULT_SETTINGS_CARD_ORDER;
  }
}

function writeSettingsCardOrder(order) {
  const normalized = normalizeSettingsCardOrder(order);
  try {
    localStorage.setItem(SETTINGS_CARD_ORDER_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    /* ignore */
  }
  return normalized;
}

function formatDateTime(iso) {
  if (!iso) return '-';
  const d = new Date(iso.endsWith('Z') ? iso : iso + 'Z');
  return d.toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  });
}

function todayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function displayPerson(person) {
  return person?.display_name || person?.displayName || person?.email || person?.username || 'Shared user';
}

function SettingsCard({
  id,
  title,
  description,
  collapsed,
  onToggle,
  className = '',
  collapsedContent,
  children
}) {
  return (
    <section
      className={`settings-section settings-card ${collapsed ? 'is-collapsed' : ''} ${className}`.trim()}
      aria-labelledby={`settings-card-${id}`}
    >
      <button
        type="button"
        className="settings-card-header-button"
        onClick={onToggle}
        aria-expanded={!collapsed}
        aria-controls={`settings-card-body-${id}`}
      >
        <span className="settings-section-header">
          <h3 id={`settings-card-${id}`}>{title}</h3>
          {description && <p>{description}</p>}
        </span>
        <CollapseIndicator expanded={!collapsed} className="settings-card-caret" />
      </button>

      {collapsed ? (
        collapsedContent && (
          <div className="settings-card-collapsed" id={`settings-card-body-${id}`}>
            {collapsedContent}
          </div>
        )
      ) : (
        <div className="settings-card-body" id={`settings-card-body-${id}`}>
          {children}
        </div>
      )}
    </section>
  );
}

export default function Settings({
  themeMode = 'system',
  onThemeChange,
  darkVariant = 'classic',
  onDarkVariantChange,
  onLogout,
  mhaTrackerEnabled = false,
  onMhaTrackerChange,
  navigationPreferences,
  onNavigationPreferencesChange,
  onImportComplete
}) {
  const { alert, confirm, Dialog } = useAppDialog();

  const [status, setStatus] = useState(null);
  const [setupToken, setSetupToken] = useState('');
  const [cutoverDate, setCutoverDate] = useState(todayIso());
  const [setupError, setSetupError] = useState('');
  const [setupBusy, setSetupBusy] = useState(false);

  const [syncBusy, setSyncBusy] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const [syncError, setSyncError] = useState('');

  const [syncLog, setSyncLog] = useState([]);
  const [showLog, setShowLog] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [sharing, setSharing] = useState(null);
  const [sharingEmail, setSharingEmail] = useState('');
  const [sharingBusy, setSharingBusy] = useState(false);
  const [sharingError, setSharingError] = useState('');
  const [sharingMessage, setSharingMessage] = useState('');
  const [mhaBusy, setMhaBusy] = useState(false);
  const [mhaError, setMhaError] = useState('');
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState('');
  const [collapsedCards, setCollapsedCards] = useState(
    () => new Set(DEFAULT_COLLAPSED_SETTINGS_CARDS)
  );
  const [settingsCardOrder, setSettingsCardOrder] = useState(readSettingsCardOrder);
  const [settingsReorderMode, setSettingsReorderMode] = useState(false);
  const [settingsDragId, setSettingsDragId] = useState(null);
  const [settingsOverId, setSettingsOverId] = useState(null);
  const [moreReorderMode, setMoreReorderMode] = useState(false);
  const [moreDragId, setMoreDragId] = useState(null);
  const [moreOverId, setMoreOverId] = useState(null);
  const fileInputRef = useRef(null);
  const sensors = useReorderSensors();

  const normalizedNavigationPreferences = useMemo(
    () => normalizeNavigationPreferences(navigationPreferences),
    [navigationPreferences]
  );

  const moreRoutes = useMemo(() => {
    const moreByPath = new Map(
      ROUTES.filter((route) => route.nav === 'more').map((route) => [route.path, route])
    );
    return normalizedNavigationPreferences.moreRouteOrder
      .map((path) => moreByPath.get(path))
      .filter(Boolean);
  }, [normalizedNavigationPreferences]);

  const optionalFeatureRoutes = ROUTES.filter((route) => route.nav && !route.locked);
  const settingsOrder = normalizeSettingsCardOrder(settingsCardOrder);

  useDragInteractionLock(Boolean(settingsDragId || moreDragId));

  async function loadStatus() {
    try {
      const data = await api.get('/api/simplefin/status');
      setStatus(data);
    } catch (err) {
      console.error('SimpleFIN status failed:', err);
    }
  }

  async function loadSyncLog() {
    try {
      const data = await api.get('/api/simplefin/sync-log?limit=20');
      setSyncLog(data.items);
    } catch (err) {
      console.error('Sync log fetch failed:', err);
    }
  }

  async function loadSharing() {
    try {
      const data = await api.get('/api/household-sharing');
      setSharing(data);
    } catch (err) {
      console.error('Household sharing fetch failed:', err);
      setSharingError(err.message || 'Could not load sharing settings.');
    }
  }

  useEffect(() => {
    loadStatus();
    loadSharing();
  }, []);

  function isFeatureVisible(route) {
    if (route.feature === 'mha') return !!mhaTrackerEnabled;
    return !normalizedNavigationPreferences.hiddenRoutePaths[route.path];
  }

  function updateNavigationPreferences(updater) {
    onNavigationPreferencesChange?.((prev) =>
      normalizeNavigationPreferences(
        typeof updater === 'function'
          ? updater(normalizeNavigationPreferences(prev))
          : updater
      )
    );
  }

  function updateSettingsOrder(updater) {
    setSettingsCardOrder((prev) => {
      const next =
        typeof updater === 'function'
          ? updater(normalizeSettingsCardOrder(prev))
          : updater;
      return writeSettingsCardOrder(next);
    });
  }

  function toggleCardCollapsed(id) {
    if (id === 'about') return;
    setCollapsedCards((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  async function handleSetup(e) {
    e.preventDefault();
    setSetupError('');
    setSetupBusy(true);
    try {
      await api.post('/api/simplefin/setup', { setupToken, cutoverDate });
      setSetupToken('');
      await loadStatus();
    } catch (err) {
      setSetupError(err.message || 'Setup failed');
    } finally {
      setSetupBusy(false);
    }
  }

  async function handleSync() {
    setSyncBusy(true);
    setSyncError('');
    setSyncResult(null);
    try {
      const data = await api.post('/api/simplefin/sync');
      setSyncResult(data);
      await loadStatus();
    } catch (err) {
      setSyncError(err.message || 'Sync failed');
    } finally {
      setSyncBusy(false);
    }
  }

  async function handleDisconnect() {
    const ok = await confirm(
      'Disconnect SimpleFIN? Your transactions stay, but sync stops until you reconnect.',
      {
        title: 'Disconnect SimpleFIN',
        confirmLabel: 'Disconnect',
        destructive: true
      }
    );
    if (!ok) return;
    try {
      await api.post('/api/simplefin/disconnect');
      setSyncResult(null);
      setSyncError('');
      await loadStatus();
    } catch (err) {
      alert(err.message || 'Disconnect failed', { title: 'Disconnect Failed' });
    }
  }

  async function toggleLog() {
    if (!showLog) await loadSyncLog();
    setShowLog(!showLog);
  }

  async function handleSignOut() {
    const ok = await confirm(
      'Sign out on this device?',
      {
        title: 'Sign Out',
        confirmLabel: 'Sign Out',
        destructive: true
      }
    );
    if (!ok || !onLogout) return;
    setSigningOut(true);
    try {
      await onLogout();
    } finally {
      setSigningOut(false);
    }
  }

  function handleDonatePlaceholder() {
    alert('Donation link is not configured yet.', { title: 'Donate' });
  }

  async function handleShare(e) {
    e.preventDefault();
    const email = sharingEmail.trim();
    if (!email) return;
    setSharingBusy(true);
    setSharingError('');
    setSharingMessage('');
    try {
      const data = await api.post('/api/household-sharing/shares', { email });
      setSharing(data);
      setSharingEmail('');
      setSharingMessage(`${email} can now sign in with an Overbay.App account.`);
    } catch (err) {
      setSharingError(err.message || 'Sharing failed.');
    } finally {
      setSharingBusy(false);
    }
  }

  async function handleRevokeShare(share) {
    const ok = await confirm(
      `Stop sharing with ${share.invited_email}? If they have not signed in yet, this removes their pending access.`,
      {
        title: 'Remove Share',
        confirmLabel: 'Remove',
        destructive: true
      }
    );
    if (!ok) return;
    setSharingBusy(true);
    setSharingError('');
    setSharingMessage('');
    try {
      const data = await api.del(`/api/household-sharing/shares/${share.id}`);
      setSharing(data);
    } catch (err) {
      setSharingError(err.message || 'Could not remove share.');
    } finally {
      setSharingBusy(false);
    }
  }

  async function handleRemoveFamilyMember(user) {
    const name = displayPerson(user);
    const ok = await confirm(
      `Remove ${name} from this household? They will no longer be able to access this family's data.`,
      {
        title: 'Remove Family Member',
        confirmLabel: 'Remove',
        destructive: true
      }
    );
    if (!ok) return;
    setSharingBusy(true);
    setSharingError('');
    setSharingMessage('');
    try {
      const data = await api.del(`/api/household-sharing/users/${user.id}`);
      setSharing(data);
      setSharingMessage(`${name} was removed from this household.`);
    } catch (err) {
      setSharingError(err.message || 'Could not remove family member.');
    } finally {
      setSharingBusy(false);
    }
  }

  async function handleMhaToggle() {
    const next = !mhaTrackerEnabled;
    setMhaBusy(true);
    setMhaError('');
    try {
      const result = await api.put('/api/mha/settings', { enabled: next });
      onMhaTrackerChange?.(!!result.enabled);
    } catch (err) {
      setMhaError(err.message || 'MHA Tracker update failed');
    } finally {
      setMhaBusy(false);
    }
  }

  function handleFeatureToggle(route) {
    if (route.locked) return;
    if (route.feature === 'mha') {
      handleMhaToggle();
      return;
    }

    const visible = isFeatureVisible(route);
    updateNavigationPreferences((prefs) => {
      const hiddenRoutePaths = { ...prefs.hiddenRoutePaths };
      if (visible) {
        hiddenRoutePaths[route.path] = true;
      } else {
        delete hiddenRoutePaths[route.path];
      }
      return { ...prefs, hiddenRoutePaths };
    });
  }

  function handleMoreDragEnd(event) {
    const { active, over } = event;
    setMoreDragId(null);
    setMoreOverId(null);
    if (!over || active.id === over.id) return;

    const oldIndex = moreRoutes.findIndex((route) => route.path === active.id);
    const newIndex = moreRoutes.findIndex((route) => route.path === over.id);
    if (oldIndex < 0 || newIndex < 0) return;

    const nextRoutes = arrayMove(moreRoutes, oldIndex, newIndex);
    updateNavigationPreferences((prefs) => ({
      ...prefs,
      moreRouteOrder: nextRoutes.map((route) => route.path)
    }));
  }

  function handleSettingsDragEnd(event) {
    const { active, over } = event;
    setSettingsDragId(null);
    setSettingsOverId(null);
    if (!over || active.id === over.id) return;

    updateSettingsOrder((prev) => {
      const oldIndex = prev.indexOf(active.id);
      const newIndex = prev.indexOf(over.id);
      if (oldIndex < 0 || newIndex < 0) return prev;
      return arrayMove(prev, oldIndex, newIndex);
    });
  }

  function handleFile(f) {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.csv')) {
      setImportError('Please choose a .csv file.');
      return;
    }
    if (f.size > 20 * 1024 * 1024) {
      setImportError('File is larger than 20 MB.');
      return;
    }
    setImportError('');
    setImportResult(null);
    setFile(f);
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragging(false);
    handleFile(e.dataTransfer.files?.[0]);
  }

  async function handleImport() {
    if (!file) return;
    setImporting(true);
    setImportError('');
    setImportResult(null);

    try {
      const form = new FormData();
      form.append('file', file);

      const res = await fetch('/api/import/rocket-money', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `Import failed: ${res.status}`);
      }

      setImportResult(data);
      setFile(null);
    } catch (err) {
      setImportError(err.message || 'Import failed');
    } finally {
      setImporting(false);
    }
  }

  function renderAppearanceCard() {
    const activeTheme = THEME_OPTIONS.find((option) => option.value === themeMode);
    const activeDarkVariant = DARK_VARIANT_OPTIONS.find((option) => option.value === darkVariant);

    return (
      <SettingsCard
        id="appearance"
        key="appearance"
        title="Appearance"
        description="Choose how the app looks on this device."
        collapsed={collapsedCards.has('appearance')}
        onToggle={() => toggleCardCollapsed('appearance')}
        collapsedContent={
          <div className="settings-collapsed-summary settings-collapsed-summary-with-icon">
            <span className="settings-collapsed-icon" aria-hidden>
              {activeTheme?.emoji || '💻'}
            </span>
            <span className="settings-collapsed-inline-copy">
              <strong>{activeTheme?.label || 'System'}</strong>
              <span aria-hidden="true">|</span>
              <span>{activeDarkVariant?.label || 'Soft Dark'} after dark.</span>
            </span>
          </div>
        }
      >
        <div className="settings-theme-toggle" role="radiogroup" aria-label="Theme">
          {THEME_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={`settings-theme-option ${themeMode === option.value ? 'active' : ''}`}
              onClick={() => onThemeChange?.(option.value)}
              role="radio"
              aria-checked={themeMode === option.value}
            >
              <span className="settings-theme-emoji" aria-hidden>{option.emoji}</span>
              <span className="settings-theme-text">
                <span className="settings-theme-label">{option.label}</span>
                <span className="settings-theme-copy">{option.description}</span>
              </span>
            </button>
          ))}
        </div>

        <div className="settings-subsection">
          <div className="settings-subsection-heading">
            <h4>Night Style</h4>
            <p>Used for Night mode and for System when this device is in dark mode.</p>
          </div>
          <div className="settings-theme-toggle settings-theme-toggle-two" role="radiogroup" aria-label="Night style">
            {DARK_VARIANT_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={`settings-theme-option ${darkVariant === option.value ? 'active' : ''}`}
                onClick={() => onDarkVariantChange?.(option.value)}
                role="radio"
                aria-checked={darkVariant === option.value}
              >
                <span className="settings-theme-emoji" aria-hidden>{option.emoji}</span>
                <span className="settings-theme-text">
                  <span className="settings-theme-label">{option.label}</span>
                  <span className="settings-theme-copy">{option.description}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      </SettingsCard>
    );
  }

  function renderFeaturesCard() {
    const featureRoutes = optionalFeatureRoutes;

    return (
      <SettingsCard
        id="features"
        key="features"
        title="Turn App Features On/Off"
        description="Show, hide, and reorder navigation sections."
        collapsed={collapsedCards.has('features')}
        onToggle={() => toggleCardCollapsed('features')}
      >
        <div className="settings-card-top-action">
          <button
            type="button"
            className={moreReorderMode ? 'btn-primary btn-compact' : 'btn-secondary btn-compact'}
            onClick={() => {
              setMoreReorderMode(true);
              setMoreDragId(null);
              setMoreOverId(null);
            }}
          >
            Reorder
          </button>
        </div>

        <div className="settings-theme-toggle settings-feature-toggle" role="group" aria-label="Feature visibility">
          {featureRoutes.map((route) => {
            const visible = isFeatureVisible(route);
            const disabled = route.feature === 'mha' && mhaBusy;
            return (
              <button
                key={route.path}
                type="button"
                className={`settings-theme-option settings-feature-option ${visible ? 'active' : ''}`}
                onClick={() => handleFeatureToggle(route)}
                disabled={disabled}
                aria-pressed={visible}
              >
                <AppIcon name={route.icon} className="settings-feature-icon" />
                <span className="settings-theme-text">
                  <span className="settings-theme-label">{route.label}</span>
                  <span className="settings-theme-copy">
                    {visible ? 'On' : 'Off'}
                  </span>
                </span>
              </button>
            );
          })}
        </div>

        {mhaError && <div className="error" style={{ marginTop: 12 }}>{mhaError}</div>}
      </SettingsCard>
    );
  }

  function renderSimpleFinCard() {
    const statusCopy =
      status === null
        ? 'Loading...'
        : status.connected
        ? 'Connected - syncs daily at 6 AM.'
        : 'Not connected.';

    return (
      <SettingsCard
        id="simplefin"
        key="simplefin"
        title="SimpleFIN"
        description={statusCopy}
        collapsed={collapsedCards.has('simplefin')}
        onToggle={() => toggleCardCollapsed('simplefin')}
        collapsedContent={
          <div className="settings-collapsed-action">
            <div className="settings-collapsed-summary">
              <strong>{status?.connected ? 'Connected' : 'Not Connected'}</strong>
              <span>Last sync: {formatDateTime(status?.lastSyncAt)}</span>
            </div>
            {status?.connected ? (
              <button type="button" className="btn-primary" onClick={handleSync} disabled={syncBusy}>
                {syncBusy ? 'Syncing...' : 'Sync Now'}
              </button>
            ) : (
              <a className="btn-secondary settings-action-link" href={SIMPLEFIN_BRIDGE_URL} target="_blank" rel="noreferrer">
                Open Bridge
              </a>
            )}
          </div>
        }
      >
        {status?.connected ? (
          <>
            <div className="status-grid">
              <div><dt>Cutover Date</dt><dd>{status.cutoverDate || '-'}</dd></div>
              <div><dt>Last Sync</dt><dd>{formatDateTime(status.lastSyncAt)}</dd></div>
              <div>
                <dt>Last Status</dt>
                <dd>
                  {status.lastSync ? (() => {
                    const msg = status.lastSync.error_message || '';
                    const isPartial = /^partial sync/i.test(msg);
                    const s = status.lastSync.status;
                    let pillClass = 'warning';
                    let pillLabel = s;
                    if (s === 'success') {
                      pillClass = 'success';
                      pillLabel = 'success';
                    } else if (s === 'error' && isPartial) {
                      pillClass = 'warning';
                      pillLabel = 'partial sync';
                    } else if (s === 'error') {
                      pillClass = 'danger';
                      pillLabel = 'error';
                    }
                    return <span className={`pill ${pillClass}`}>{pillLabel}</span>;
                  })() : '-'}
                </dd>
              </div>
            </div>

            {status.lastSync?.error_message && (
              <div className="warning-banner">
                <strong>Last sync had an issue:</strong> {status.lastSync.error_message}
              </div>
            )}

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>Sync Now</strong>
                <p>Pulls transactions since the last successful sync.</p>
              </div>
              <button
                type="button"
                className="btn-primary"
                onClick={handleSync}
                disabled={syncBusy}
              >
                {syncBusy ? (<><span className="spinner-inline" /> Syncing...</>) : 'Sync Now'}
              </button>
            </div>

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>SimpleFIN Bridge</strong>
                <p>Open the bridge site to manage or refresh the connection.</p>
              </div>
              <a className="btn-secondary settings-action-link" href={SIMPLEFIN_BRIDGE_URL} target="_blank" rel="noreferrer">
                Open Bridge
              </a>
            </div>

            {syncError && <div className="error" style={{ marginTop: 16 }}>{syncError}</div>}

            {syncResult && (
              <div className={`result-card ${syncResult.status === 'error' ? 'error-tone' : ''}`}>
                <dl className="stat-grid">
                  <div><dt>Inserted</dt><dd>{syncResult.inserted.toLocaleString()}</dd></div>
                  <div><dt>Skipped</dt><dd>{syncResult.skipped.toLocaleString()}</dd></div>
                  <div><dt>RM Removed</dt><dd>{syncResult.rmDeleted.toLocaleString()}</dd></div>
                  <div><dt>Accounts</dt><dd>{syncResult.accountsCreated.toLocaleString()}</dd></div>
                  <div><dt>Transfers</dt><dd>{syncResult.transfersPaired.toLocaleString()}</dd></div>
                </dl>
              </div>
            )}

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>Sync History</strong>
                <p>Recent runs and errors.</p>
              </div>
              <button type="button" className="btn-secondary" onClick={toggleLog}>
                {showLog ? 'Hide' : 'Show'}
              </button>
            </div>

            {showLog && (
              <div className="sync-log-table">
                {syncLog.length === 0 ? (
                  <p className="muted" style={{ padding: 16 }}>No sync history yet.</p>
                ) : (
                  <table>
                    <thead>
                      <tr>
                        <th>Started</th>
                        <th>Trigger</th>
                        <th>Status</th>
                        <th>Inserted</th>
                        <th>Error</th>
                      </tr>
                    </thead>
                    <tbody>
                      {syncLog.map((row) => {
                        const msg = row.error_message || '';
                        const isPartial = /^partial sync/i.test(msg);
                        let pillClass = 'warning';
                        let pillLabel = row.status;
                        if (row.status === 'success') {
                          pillClass = 'success';
                          pillLabel = 'success';
                        } else if (row.status === 'error' && isPartial) {
                          pillClass = 'warning';
                          pillLabel = 'partial';
                        } else if (row.status === 'error') {
                          pillClass = 'danger';
                          pillLabel = 'error';
                        }
                        return (
                          <tr key={row.id}>
                            <td>{formatDateTime(row.started_at)}</td>
                            <td>{row.trigger}</td>
                            <td>
                              <span className={`pill ${pillClass}`}>
                                {pillLabel}
                              </span>
                            </td>
                            <td>{row.transactions_inserted}</td>
                            <td className="error-col">{row.error_message || '-'}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            )}

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>Disconnect</strong>
                <p>Transactions and accounts stay. Sync stops until you reconnect.</p>
              </div>
              <button type="button" className="btn-danger" onClick={handleDisconnect}>
                Disconnect
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={handleSetup}>
            <div className="settings-action">
              <div className="settings-action-info">
                <strong>SimpleFIN Bridge</strong>
                <p>Get a setup token from the bridge, then paste it below.</p>
              </div>
              <a className="btn-secondary settings-action-link" href={SIMPLEFIN_BRIDGE_URL} target="_blank" rel="noreferrer">
                Open Bridge
              </a>
            </div>

            <p style={{ marginBottom: 20 }}>
              Paste the setup token below. You'll also pick a <strong>cutover date</strong> - Rocket Money
              data is kept for dates before it, SimpleFIN owns dates after.
            </p>

            <label className="field">
              <span>Setup Token</span>
              <textarea
                value={setupToken}
                onChange={(e) => setSetupToken(e.target.value)}
                rows={4}
                required
                spellCheck={false}
                placeholder="Paste the base64 setup token here"
              />
            </label>

            <label className="field">
              <span>Cutover Date</span>
              <input
                type="date"
                value={cutoverDate}
                onChange={(e) => setCutoverDate(e.target.value)}
                required
                max={todayIso()}
              />
            </label>

            {setupError && <div className="error">{setupError}</div>}

            <button
              type="submit"
              className="btn-primary"
              disabled={setupBusy || !setupToken || !cutoverDate}
              style={{ width: '100%' }}
            >
              {setupBusy ? (<><span className="spinner-inline" /> Connecting...</>) : 'Connect'}
            </button>
          </form>
        )}
      </SettingsCard>
    );
  }

  function renderImportCard() {
    return (
      <SettingsCard
        id="import"
        key="import"
        title="Import Data"
        description="Bring in Rocket Money data when you need to reload history."
        collapsed={collapsedCards.has('import')}
        onToggle={() => toggleCardCollapsed('import')}
        collapsedContent={
          <div className="settings-collapsed-action">
            <div className="settings-collapsed-summary">
              <strong>{file ? file.name : importResult ? 'Import Complete' : 'CSV Import'}</strong>
              <span>{file ? `${(file.size / 1024).toFixed(0)} KB selected` : 'Expand to choose a file.'}</span>
            </div>
            <button
              type="button"
              className="btn-primary"
              disabled={!file || importing}
              onClick={handleImport}
            >
              {importing ? 'Importing...' : 'Import'}
            </button>
          </div>
        }
      >
        {importResult ? (
          <div className="result-card">
            <dl className="stat-grid">
              <div><dt>Imported</dt><dd>{importResult.inserted.toLocaleString()}</dd></div>
              <div><dt>Skipped</dt><dd>{importResult.skipped.toLocaleString()}</dd></div>
              <div><dt>Accounts</dt><dd>{importResult.accountsCreated.toLocaleString()}</dd></div>
              <div><dt>Rules</dt><dd>{importResult.rulesCreated.toLocaleString()}</dd></div>
            </dl>

            {importResult.parseWarnings > 0 && (
              <p className="muted" style={{ marginTop: 0 }}>
                Parser reported {importResult.parseWarnings} minor warnings - usually fine.
              </p>
            )}

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>Import Complete</strong>
                <p>Refresh account lookups and review the imported transactions.</p>
              </div>
              <button type="button" className="btn-primary" onClick={onImportComplete}>
                View Transactions
              </button>
            </div>
          </div>
        ) : (
          <>
            <p style={{ marginBottom: 20 }}>
              Export your transactions from Rocket Money as CSV, then drop the file below.
              Accounts will be created, Custom Names preserved, and rename rules generated
              automatically.
            </p>

            <div
              className={`drop-zone ${dragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
              onDragOver={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              role="button"
              tabIndex={0}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".csv,text/csv"
                onChange={(e) => handleFile(e.target.files?.[0])}
                style={{ display: 'none' }}
              />
              {file ? (
                <>
                  <div className="drop-zone-icon">OK</div>
                  <div className="drop-zone-filename">{file.name}</div>
                  <div className="subtle">{(file.size / 1024).toFixed(0)} KB - click to pick a different file</div>
                </>
              ) : (
                <>
                  <div className="drop-zone-icon">CSV</div>
                  <div className="drop-zone-primary">Drop your CSV here</div>
                  <div className="subtle">or click to browse</div>
                </>
              )}
            </div>

            {importError && <div className="error">{importError}</div>}

            <button
              type="button"
              className="btn-primary settings-import-button"
              disabled={!file || importing}
              onClick={handleImport}
            >
              {importing ? (
                <>
                  <span className="spinner-inline" /> Importing...
                </>
              ) : (
                'Import'
              )}
            </button>
          </>
        )}
      </SettingsCard>
    );
  }

  function renderAccountCard() {
    const currentUserCopy = sharing?.currentUser
      ? `${displayPerson(sharing.currentUser)} - ${sharing.currentUser.role}`
      : 'Loading account details...';

    return (
      <SettingsCard
        id="account"
        key="account"
        title="Account"
        description="Household access and signed-in user details."
        collapsed={collapsedCards.has('account')}
        onToggle={() => toggleCardCollapsed('account')}
        collapsedContent={
          <div className="settings-collapsed-summary">
            <strong>Signed In</strong>
            <span>{currentUserCopy}</span>
          </div>
        }
      >
        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Signed In</strong>
            <p>{currentUserCopy}</p>
          </div>
        </div>

        {sharing?.currentUser?.canManageSharing && (
          <form className="settings-share-form" onSubmit={handleShare}>
            <label className="field">
              <span>Share With A Partner</span>
              <input
                type="email"
                value={sharingEmail}
                onChange={(e) => setSharingEmail(e.target.value)}
                placeholder="partner@example.com"
                autoComplete="email"
                disabled={sharingBusy}
              />
            </label>
            <button
              type="submit"
              className="btn-primary"
              disabled={sharingBusy || !sharingEmail.trim()}
            >
              {sharingBusy ? 'Sharing...' : 'Share'}
            </button>
          </form>
        )}

        {sharingError && <div className="error" style={{ marginTop: 12 }}>{sharingError}</div>}
        {sharingMessage && <div className="success-banner" style={{ marginTop: 12 }}>{sharingMessage}</div>}

        {sharing && (
          <div className="settings-share-list" aria-label="Household access">
            {sharing.users.map((user) => (
              <div className="settings-share-row" key={`user-${user.id}`}>
                <div>
                  <strong>{displayPerson(user)}</strong>
                  <span>{user.email || user.username || 'No email on file'}</span>
                </div>
                <div className="settings-share-row-actions">
                  <span className="pill accent">{user.role}</span>
                  {sharing.currentUser?.canRemoveUsers && user.id !== sharing.currentUser.id && (
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => handleRemoveFamilyMember(user)}
                      disabled={sharingBusy}
                    >
                      Remove
                    </button>
                  )}
                </div>
              </div>
            ))}

            {sharing.shares
              .filter((share) => !share.accepted_at)
              .map((share) => (
                <div className="settings-share-row" key={`share-${share.id}`}>
                  <div>
                    <strong>{share.invited_email}</strong>
                    <span>Waiting for OIDC sign in</span>
                  </div>
                  {sharing.currentUser?.canManageSharing ? (
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => handleRevokeShare(share)}
                      disabled={sharingBusy}
                    >
                      Remove
                    </button>
                  ) : (
                    <span className="pill warning">pending</span>
                  )}
                </div>
              ))}
          </div>
        )}
      </SettingsCard>
    );
  }

  function renderAboutCard() {
    return (
      <section className="settings-section settings-about-section" key="about">
        <div className="settings-about-brand">
          <img src={APP_ICON_512} alt="" className="settings-about-icon" />
          <div>
            <h3><BrandLogo /></h3>
          </div>
        </div>

        <div className="settings-about-grid">
          <div className="settings-about-info-card">
            <span className="settings-about-info-label">Developer</span>
            <strong>Neal Overbay</strong>
          </div>

          <button
            type="button"
            className="settings-about-info-card settings-donate-card"
            onClick={handleDonatePlaceholder}
          >
            <span className="settings-donate-icon" aria-hidden>☕</span>
            <strong>Donate</strong>
          </button>

          <div className="settings-about-info-card settings-about-build">
            <span className="settings-about-info-label">Build</span>
            <strong>{APP_VERSION_LABEL}</strong>
          </div>

          <div className="settings-about-info-card settings-signout-card">
            <button
              type="button"
              className="btn-danger"
              onClick={handleSignOut}
              disabled={signingOut}
            >
              {signingOut ? 'Signing Out...' : 'Sign Out'}
            </button>
          </div>
        </div>

        <div className="settings-about-footer">
          <span>Copyright 2026 Neal Overbay. All rights reserved.</span>
        </div>
      </section>
    );
  }

  function renderMoreReorderModal() {
    if (!moreReorderMode) return null;
    return (
      <AnimatedModal
        onClose={() => {
          setMoreReorderMode(false);
          setMoreDragId(null);
          setMoreOverId(null);
        }}
        size="lg"
      >
        {({ close }) => (
          <>
            <div className="modal-header">
              <h3>Page Order</h3>
              <button type="button" className="modal-close" onClick={close} aria-label="Close">
                x
              </button>
            </div>

            <div className="settings-reorder-modal">
              <p className="muted">Drag pages into the order they should appear in the More sheet.</p>
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragStart={(event) => setMoreDragId(event.active.id)}
                onDragOver={(event) => setMoreOverId(event.over?.id ?? null)}
                onDragCancel={() => {
                  setMoreDragId(null);
                  setMoreOverId(null);
                }}
                onDragEnd={handleMoreDragEnd}
              >
                <SortableContext
                  items={moreRoutes.map((route) => route.path)}
                  strategy={verticalListSortingStrategy}
                >
                  <div className="settings-reorder-list reorder-active reorder-drag-scope">
                    {moreRoutes.map((route) => (
                      <ReorderListItem
                        key={route.path}
                        id={route.path}
                        leading={<AppIcon name={route.icon} className="settings-reorder-icon" />}
                        handleLabel={`Move ${route.label}`}
                        title={route.label}
                        subtitle={route.description}
                        sidePrimary={route.locked ? 'Locked' : isFeatureVisible(route) ? 'On' : 'Off'}
                        previewDisplaced={route.path === moreOverId && route.path !== moreDragId}
                      />
                    ))}
                  </div>
                </SortableContext>
                {moreDragId && <div className="drag-screen-blocker" aria-hidden="true" />}
              </DndContext>
            </div>

            <div className="modal-actions">
              <button type="button" className="btn-primary" onClick={close}>
                Done
              </button>
            </div>
          </>
        )}
      </AnimatedModal>
    );
  }

  function renderSettingsCard(id) {
    switch (id) {
      case 'appearance':
        return renderAppearanceCard();
      case 'features':
        return renderFeaturesCard();
      case 'simplefin':
        return renderSimpleFinCard();
      case 'import':
        return renderImportCard();
      case 'account':
        return renderAccountCard();
      case 'about':
        return renderAboutCard();
      default:
        return null;
    }
  }

  function renderSettingsReorderModal() {
    if (!settingsReorderMode) return null;
    return (
      <AnimatedModal
        onClose={() => {
          setSettingsReorderMode(false);
          setSettingsDragId(null);
          setSettingsOverId(null);
        }}
        size="lg"
      >
        {({ close }) => (
          <>
            <div className="modal-header">
              <h3>Settings Card Order</h3>
              <button type="button" className="modal-close" onClick={close} aria-label="Close">
                x
              </button>
            </div>

            <div className="settings-reorder-modal">
              <p className="muted">Drag cards into the order you want them to appear.</p>
              <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragStart={(event) => setSettingsDragId(event.active.id)}
                onDragOver={(event) => setSettingsOverId(event.over?.id ?? null)}
                onDragCancel={() => {
                  setSettingsDragId(null);
                  setSettingsOverId(null);
                }}
                onDragEnd={handleSettingsDragEnd}
              >
                <SortableContext items={settingsOrder} strategy={verticalListSortingStrategy}>
                  <div className="settings-reorder-list reorder-active reorder-drag-scope">
                    {settingsOrder.map((id) => {
                      const card = SETTINGS_CARD_BY_ID.get(id);
                      return (
                        <ReorderListItem
                          key={id}
                          id={id}
                          handleLabel={`Move ${card.title}`}
                          title={card.title}
                          subtitle={card.description}
                          previewDisplaced={id === settingsOverId && id !== settingsDragId}
                        />
                      );
                    })}
                  </div>
                </SortableContext>
                {settingsDragId && <div className="drag-screen-blocker" aria-hidden="true" />}
              </DndContext>
            </div>

            <div className="modal-actions">
              <button type="button" className="btn-primary" onClick={close}>
                Done
              </button>
            </div>
          </>
        )}
      </AnimatedModal>
    );
  }

  return (
    <div className="settings-view">
      <PageHero
        id="settings-title"
        variant="settings"
        kicker="Control Center"
        title="Settings"
        subtitle="Maintenance and configuration."
        toolbar={
          <div className="settings-hero-toolbar">
            <button
              type="button"
              className={settingsReorderMode ? 'btn-primary' : 'btn-secondary'}
              onClick={() => {
                setSettingsReorderMode(true);
                setSettingsDragId(null);
                setSettingsOverId(null);
              }}
            >
              Reorder
            </button>
          </div>
        }
      />

      <div className={settingsReorderMode ? 'settings-card-stack reorder-visible' : 'settings-card-stack'}>
        {settingsOrder.map((id, index) => (
          <div key={id} className={index === 0 && !settingsReorderMode ? 'settings-section-top' : ''}>
            {renderSettingsCard(id)}
          </div>
        ))}
      </div>

      <Dialog />
      {renderMoreReorderModal()}
      {renderSettingsReorderModal()}
    </div>
  );
}
