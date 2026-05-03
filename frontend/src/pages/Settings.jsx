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
import AppSelect from '../components/AppSelect.jsx';
import BrandLogo from '../components/BrandLogo.jsx';
import PageHero from '../components/PageHero.jsx';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../components/ReorderListItem.jsx';
import SelectableListItem from '../components/SelectableListItem.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { useNavigate } from 'react-router-dom';
import {
  ROUTES,
  normalizeNavigationPreferences
} from '../navigation.js';
import { APP_VERSION_LABEL } from '../version.js';
import { APP_ICON_512 } from '../brandAssets.js';

const SIMPLEFIN_BRIDGE_URL = 'https://beta-bridge.simplefin.org/';

const THEME_OPTIONS = [
  {
    value: 'light',
    label: 'Day',
    emoji: '\u2600\uFE0F',
    description: 'A bright interface for daylight use.'
  },
  {
    value: 'dark',
    label: 'Night',
    emoji: '\u{1F319}',
    description: 'A dimmer interface for low light.'
  },
  {
    value: 'system',
    label: 'System',
    emoji: '\u{1F4BB}',
    description: 'Match this device automatically.'
  }
];

const DARK_VARIANT_OPTIONS = [
  {
    value: 'classic',
    label: 'Soft Dark',
    emoji: '\u{1F318}',
    description: "A gray dark theme that's gentle on the eyes."
  },
  {
    value: 'amoled',
    label: 'AMOLED Black',
    emoji: '\u2B1B',
    description: 'A pure black background for AMOLED devices.'
  }
];

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
  return person?.display_name || person?.displayName || person?.email || person?.invited_email || person?.username || 'Shared user';
}

function SettingsCard({
  id,
  title,
  description,
  className = '',
  children
}) {
  return (
    <section
      className={`settings-section settings-card ${className}`.trim()}
      aria-labelledby={`settings-card-${id}`}
    >
      <div className="settings-card-header-static">
        <span className="settings-section-header">
          <h3 id={`settings-card-${id}`}>{title}</h3>
          {description && <p>{description}</p>}
        </span>
      </div>

      <div id={`settings-card-body-${id}`} className="settings-card-body">
        {children}
      </div>
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
  onImportComplete,
  settingsPage = 'home'
}) {
  const { alert, confirm, Dialog } = useAppDialog();
  const navigate = useNavigate();

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
  const [sharingAccessLevel, setSharingAccessLevel] = useState('write');
  const [sharingBusy, setSharingBusy] = useState(false);
  const [sharingError, setSharingError] = useState('');
  const [sharingMessage, setSharingMessage] = useState('');
  const [mhaBusy, setMhaBusy] = useState(false);
  const [mhaError, setMhaError] = useState('');
  const [file, setFile] = useState(null);
  const [restoreFile, setRestoreFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [restoreDragging, setRestoreDragging] = useState(false);
  const [importing, setImporting] = useState(false);
  const [previewingImport, setPreviewingImport] = useState(false);
  const [importPreview, setImportPreview] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState('');
  const [restoreBusy, setRestoreBusy] = useState(false);
  const [restorePreview, setRestorePreview] = useState(null);
  const [restoreResult, setRestoreResult] = useState(null);
  const [restoreError, setRestoreError] = useState('');
  const [rulesBusy, setRulesBusy] = useState(false);
  const [rulesResult, setRulesResult] = useState(null);
  const [rulesError, setRulesError] = useState('');
  const [downloadBusy, setDownloadBusy] = useState('');
  const [downloadNotice, setDownloadNotice] = useState(null);
  const [moreReorderMode, setMoreReorderMode] = useState(false);
  const [moreDragId, setMoreDragId] = useState(null);
  const [moreOverId, setMoreOverId] = useState(null);
  const fileInputRef = useRef(null);
  const restoreFileInputRef = useRef(null);
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

  const pageOrderRoutes = useMemo(() => {
    const visible = [];
    const hidden = [];
    for (const route of moreRoutes) {
      if (isFeatureVisible(route)) visible.push(route);
      else hidden.push(route);
    }
    return [...visible, ...hidden];
  }, [moreRoutes, mhaTrackerEnabled, normalizedNavigationPreferences]);

  const optionalFeatureRoutes = ROUTES.filter((route) => route.nav && !route.locked);

  useDragInteractionLock(Boolean(moreDragId));

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
      const data = await api.post('/api/household-sharing/shares', {
        email,
        accessLevel: sharingAccessLevel
      });
      setSharing(data);
      setSharingEmail('');
      setSharingAccessLevel('write');
      setSharingMessage(`${email} can now sign in with their configured account.`);
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

  async function handleAccessChange(person, accessLevel, pending = false) {
    const label = displayPerson(person);
    setSharingBusy(true);
    setSharingError('');
    setSharingMessage('');
    try {
      const path = pending
        ? `/api/household-sharing/shares/${person.id}/access`
        : `/api/household-sharing/users/${person.id}/access`;
      const data = await api.patch(path, { accessLevel });
      setSharing(data);
      setSharingMessage(`${label} now has ${accessLevel === 'read' ? 'read-only' : 'read and write'} access.`);
    } catch (err) {
      setSharingError(err.message || 'Could not update permissions.');
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

    const visibleRoutes = pageOrderRoutes.filter((route) => isFeatureVisible(route));
    const hiddenRoutes = pageOrderRoutes.filter((route) => !isFeatureVisible(route));
    const overRoute = pageOrderRoutes.find((route) => route.path === over.id);
    const oldIndex = visibleRoutes.findIndex((route) => route.path === active.id);
    const newIndex = overRoute && !isFeatureVisible(overRoute)
      ? visibleRoutes.length - 1
      : visibleRoutes.findIndex((route) => route.path === over.id);
    if (oldIndex < 0 || newIndex < 0) return;

    const nextRoutes = [
      ...arrayMove(visibleRoutes, oldIndex, newIndex),
      ...hiddenRoutes
    ];
    updateNavigationPreferences((prefs) => ({
      ...prefs,
      moreRouteOrder: nextRoutes.map((route) => route.path)
    }));
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
    setImportPreview(null);
    setFile(f);
  }

  function handleRestoreFile(f) {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.json')) {
      setRestoreError('Please choose an Orbit Money backup .json file.');
      return;
    }
    if (f.size > 50 * 1024 * 1024) {
      setRestoreError('File is larger than 50 MB.');
      return;
    }
    setRestoreError('');
    setRestorePreview(null);
    setRestoreResult(null);
    setRestoreFile(f);
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragging(false);
    handleFile(e.dataTransfer.files?.[0]);
  }

  function handleRestoreDrop(e) {
    e.preventDefault();
    setRestoreDragging(false);
    handleRestoreFile(e.dataTransfer.files?.[0]);
  }

  function filenameFromDisposition(disposition, fallback) {
    const match = /filename="([^"]+)"/i.exec(disposition || '');
    return match?.[1] || fallback;
  }

  async function downloadData(path, { label, scope, fallbackFilename }) {
    setDownloadBusy(label);
    setDownloadNotice(null);
    try {
      const res = await fetch(path, { credentials: 'same-origin' });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || `Download failed: ${res.status}`);
      }

      const blob = await res.blob();
      const filename = filenameFromDisposition(
        res.headers.get('Content-Disposition'),
        fallbackFilename
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setDownloadNotice({ scope, text: `${label} downloaded.` });
    } catch (err) {
      setDownloadNotice({
        scope,
        text: err.message || 'Download failed.',
        tone: 'error'
      });
    } finally {
      setDownloadBusy('');
    }
  }

  function renderDownloadNotice(scope) {
    if (downloadNotice?.scope !== scope) return null;

    return (
      <div
        className={`${downloadNotice.tone === 'error' ? 'error' : 'success-banner'} settings-download-status`}
        role="status"
        aria-live="polite"
      >
        {downloadNotice.text}
      </div>
    );
  }

  async function handleImportPreview() {
    if (!file) return;
    setPreviewingImport(true);
    setImportError('');
    setImportResult(null);
    setImportPreview(null);

    try {
      const form = new FormData();
      form.append('file', file);

      const res = await fetch('/api/import/rocket-money/preview', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `Import failed: ${res.status}`);
      }

      setImportPreview(data);
    } catch (err) {
      setImportError(err.message || 'Preview failed');
    } finally {
      setPreviewingImport(false);
    }
  }

  async function handleImportCommit() {
    if (!importPreview?.batchId) return;
    const ok = await confirm(
      `Import ${importPreview.estimatedInserted.toLocaleString()} transactions from ${importPreview.filename || 'this CSV'}? You can undo this import from the result screen.`,
      {
        title: 'Import Financial Data',
        confirmLabel: 'Import'
      }
    );
    if (!ok) return;

    setImporting(true);
    setImportError('');
    try {
      const data = await api.post(`/api/import/rocket-money/${importPreview.batchId}/commit`);
      setImportResult(data);
      setFile(null);
      setImportPreview(null);
      onImportComplete?.({ stayOnSettings: true });
    } catch (err) {
      setImportError(err.message || 'Import failed');
    } finally {
      setImporting(false);
    }
  }

  async function handleUndoImport() {
    if (!importResult?.batchId) return;
    const ok = await confirm(
      'Undo this import? Orbit will remove the transactions, rules, and newly-created accounts that came only from this import.',
      {
        title: 'Undo Import',
        confirmLabel: 'Undo Import',
        destructive: true
      }
    );
    if (!ok) return;
    setImporting(true);
    setImportError('');
    try {
      const data = await api.post(`/api/import/batches/${importResult.batchId}/undo`);
      setImportResult({ ...importResult, undone: data });
      onImportComplete?.({ stayOnSettings: true });
    } catch (err) {
      setImportError(err.message || 'Undo failed');
    } finally {
      setImporting(false);
    }
  }

  async function handleRestorePreview() {
    if (!restoreFile) return;
    setRestoreBusy(true);
    setRestoreError('');
    setRestorePreview(null);
    setRestoreResult(null);
    try {
      const form = new FormData();
      form.append('file', restoreFile);
      const res = await fetch('/api/data/orbit-restore/preview', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `Restore preview failed: ${res.status}`);
      }
      setRestorePreview(data);
    } catch (err) {
      setRestoreError(err.message || 'Restore preview failed');
    } finally {
      setRestoreBusy(false);
    }
  }

  async function handleRestore() {
    if (!restoreFile || !restorePreview) return;
    const ok = await confirm(
      `Restore "${restorePreview.householdName}"? This will replace the current Orbit household data. SimpleFIN connection info is not included, so you will need to generate a new SimpleFIN API key and reconnect SimpleFIN.`,
      {
        title: 'Restore Orbit Money Data',
        confirmLabel: 'Restore',
        destructive: true
      }
    );
    if (!ok) return;
    setRestoreBusy(true);
    setRestoreError('');
    try {
      const form = new FormData();
      form.append('file', restoreFile);
      const res = await fetch('/api/data/orbit-restore', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `Restore failed: ${res.status}`);
      }
      setRestoreResult(data);
      setRestoreFile(null);
      setRestorePreview(null);
      await onImportComplete?.({ stayOnSettings: true });
      await loadStatus();
    } catch (err) {
      setRestoreError(err.message || 'Restore failed');
    } finally {
      setRestoreBusy(false);
    }
  }

  async function handleReapplyRules() {
    const ok = await confirm(
      'Reapply all enabled rules to every transaction? Manual edits will stay protected, but rule-owned edits may change if the matching rules changed.',
      {
        title: 'Reapply Rules',
        confirmLabel: 'Reapply Rules'
      }
    );
    if (!ok) return;

    setRulesBusy(true);
    setRulesError('');
    setRulesResult(null);
    try {
      const data = await api.post('/api/rules/reapply-all');
      setRulesResult(data);
      try {
        await onImportComplete?.({ stayOnSettings: true });
      } catch (err) {
        console.error('Post-rule reapply refresh failed:', err);
      }
    } catch (err) {
      setRulesError(err.message || 'Could not reapply rules.');
    } finally {
      setRulesBusy(false);
    }
  }

  function renderAppearanceCard() {
    return (
      <SettingsCard
        id="appearance"
        key="appearance"
        title="Appearance"
        description="Choose how the app looks on this device."
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
                Open SimpleFIN
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
                Open SimpleFIN
              </a>
            </div>

            <p style={{ marginBottom: 20 }}>
              Paste your SimpleFIN setup token below. You'll also pick a{' '}
              <strong>cutover date</strong> if you want to avoid duplicate transactions. SimpleFIN
              will only import transactions after the cutover date.
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
        title="Import / Export Budgeting App Data"
        description="Move financial data between Orbit Money and other budgeting apps."
      >
        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Export For Use In A Different Budgeting App</strong>
            <p>Download financial data only. Orbit settings, rules, auth, and SimpleFIN are not included.</p>
          </div>
          <div className="settings-action-buttons">
            <button
              type="button"
              className="btn-secondary"
              disabled={downloadBusy === 'CSV export'}
              onClick={() => downloadData('/api/data/budgeting-export?format=csv', {
                label: 'CSV export',
                scope: 'budgeting-export',
                fallbackFilename: 'orbit-money-transactions.csv'
              })}
            >
              {downloadBusy === 'CSV export' ? 'Downloading...' : 'CSV'}
            </button>
            <button
              type="button"
              className="btn-secondary"
              disabled={downloadBusy === 'JSON export'}
              onClick={() => downloadData('/api/data/budgeting-export?format=json', {
                label: 'JSON export',
                scope: 'budgeting-export',
                fallbackFilename: 'orbit-money-financial-export.json'
              })}
            >
              {downloadBusy === 'JSON export' ? 'Downloading...' : 'JSON'}
            </button>
          </div>
        </div>
        {renderDownloadNotice('budgeting-export')}

        <div className="settings-subsection">
          <div className="settings-subsection-heading">
            <h4>Import From Rocket Money Or Other Financial App</h4>
            <p>Preview the CSV first, then confirm the import. Applied imports can be undone.</p>
          </div>
        </div>

        {importResult ? (
          <div className="result-card">
            {importResult.undone ? (
              <>
                <dl className="stat-grid">
                  <div><dt>Transactions Removed</dt><dd>{importResult.undone.transactionsDeleted.toLocaleString()}</dd></div>
                  <div><dt>Rules Removed</dt><dd>{importResult.undone.rulesDeleted.toLocaleString()}</dd></div>
                  <div><dt>Accounts Removed</dt><dd>{importResult.undone.accountsDeleted.toLocaleString()}</dd></div>
                  <div><dt>Accounts Kept</dt><dd>{importResult.undone.accountsKept.toLocaleString()}</dd></div>
                </dl>
                <p className="muted" style={{ marginBottom: 0 }}>Import was undone.</p>
              </>
            ) : (
              <>
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
                    <p>Review imported transactions, or undo this import if the preview missed something.</p>
                  </div>
                  <div className="settings-action-buttons">
                    <button type="button" className="btn-secondary" onClick={handleUndoImport} disabled={importing}>
                      Undo Import
                    </button>
                    <button type="button" className="btn-primary" onClick={onImportComplete}>
                      View Transactions
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>
        ) : (
          <>
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

            {importPreview && (
              <div className="result-card">
                <dl className="stat-grid">
                  <div><dt>Would Import</dt><dd>{importPreview.estimatedInserted.toLocaleString()}</dd></div>
                  <div><dt>Duplicates</dt><dd>{importPreview.duplicateRows.toLocaleString()}</dd></div>
                  <div><dt>New Accounts</dt><dd>{importPreview.accountsCreated.toLocaleString()}</dd></div>
                  <div><dt>New Rules</dt><dd>{importPreview.rulesCreated.toLocaleString()}</dd></div>
                </dl>
                {importPreview.invalidRows > 0 && (
                  <div className="warning-banner">
                    {importPreview.invalidRows.toLocaleString()} row{importPreview.invalidRows === 1 ? '' : 's'} could not be imported.
                  </div>
                )}
              </div>
            )}

            {importError && <div className="error">{importError}</div>}

            <button
              type="button"
              className="btn-primary settings-import-button"
              disabled={!file || importing || previewingImport}
              onClick={importPreview ? handleImportCommit : handleImportPreview}
            >
              {importing || previewingImport ? (
                <>
                  <span className="spinner-inline" /> {previewingImport ? 'Previewing...' : 'Importing...'}
                </>
              ) : importPreview ? (
                'Import'
              ) : (
                'Preview Import'
              )}
            </button>
          </>
        )}
      </SettingsCard>
    );
  }

  function renderBackupCard() {
    return (
      <SettingsCard
        id="backup"
        key="backup"
        title="Backup / Restore Orbit Money Data"
        description="Save or restore Orbit Money app data, settings, and rules."
      >
        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Backup Orbit Money Data</strong>
            <p>Includes Orbit data, settings, rules, and permissions. SimpleFIN connection info is excluded.</p>
          </div>
          <button
            type="button"
            className="btn-primary"
            disabled={downloadBusy === 'Backup'}
            onClick={() => downloadData('/api/data/orbit-backup', {
              label: 'Backup',
              scope: 'backup',
              fallbackFilename: 'orbit-money-backup.json'
            })}
          >
            {downloadBusy === 'Backup' ? 'Backing Up...' : 'Backup'}
          </button>
        </div>
        {renderDownloadNotice('backup')}

        <div className="warning-banner">
          After restoring, you will need to generate a new SimpleFIN API key and reconnect SimpleFIN.
        </div>

        <div className="settings-subsection">
          <div className="settings-subsection-heading">
            <h4>Restore Orbit Money Data</h4>
            <p>Preview a backup file, then replace the current household data after confirmation.</p>
          </div>
        </div>

        <div
          className={`drop-zone ${restoreDragging ? 'dragging' : ''} ${restoreFile ? 'has-file' : ''}`}
          onDragOver={(e) => {
            e.preventDefault();
            setRestoreDragging(true);
          }}
          onDragLeave={() => setRestoreDragging(false)}
          onDrop={handleRestoreDrop}
          onClick={() => restoreFileInputRef.current?.click()}
          role="button"
          tabIndex={0}
        >
          <input
            ref={restoreFileInputRef}
            type="file"
            accept=".json,application/json"
            onChange={(e) => handleRestoreFile(e.target.files?.[0])}
            style={{ display: 'none' }}
          />
          {restoreFile ? (
            <>
              <div className="drop-zone-icon">JSON</div>
              <div className="drop-zone-filename">{restoreFile.name}</div>
              <div className="subtle">{(restoreFile.size / 1024).toFixed(0)} KB - click to pick a different file</div>
            </>
          ) : (
            <>
              <div className="drop-zone-icon">JSON</div>
              <div className="drop-zone-primary">Drop your Orbit backup here</div>
              <div className="subtle">or click to browse</div>
            </>
          )}
        </div>

        {restorePreview && (
          <div className="result-card">
            <dl className="stat-grid">
              <div><dt>Accounts</dt><dd>{restorePreview.accounts.toLocaleString()}</dd></div>
              <div><dt>Transactions</dt><dd>{restorePreview.transactions.toLocaleString()}</dd></div>
              <div><dt>Rules</dt><dd>{restorePreview.rules.toLocaleString()}</dd></div>
              <div><dt>Goals</dt><dd>{restorePreview.goals.toLocaleString()}</dd></div>
            </dl>
          </div>
        )}

        {restoreResult && (
          <div className="success-banner" style={{ marginTop: 12 }}>
            Restore complete. Reconnect SimpleFIN with a new API key when you are ready.
          </div>
        )}

        {restoreError && <div className="error">{restoreError}</div>}

        <div className="settings-restore-actions">
          <button
            type="button"
            className="btn-secondary"
            disabled={!restoreFile || restoreBusy}
            onClick={handleRestorePreview}
          >
            {restoreBusy && !restorePreview ? 'Previewing...' : 'Preview Restore'}
          </button>
          <button
            type="button"
            className="btn-danger"
            disabled={!restorePreview || restoreBusy}
            onClick={handleRestore}
          >
            {restoreBusy && restorePreview ? 'Restoring...' : 'Restore'}
          </button>
        </div>
      </SettingsCard>
    );
  }

  function renderRulesCard() {
    return (
      <SettingsCard
        id="rules"
        key="rules"
        title="Rules"
        description="Maintain saved transaction rules and re-run them when imported data changes."
      >
        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Reapply Rules</strong>
            <p>Run every enabled rule against every transaction again. Manual edits stay protected.</p>
          </div>
          <button
            type="button"
            className="btn-primary"
            disabled={rulesBusy}
            onClick={handleReapplyRules}
          >
            {rulesBusy ? (<><span className="spinner-inline" /> Reapplying...</>) : 'Reapply Rules'}
          </button>
        </div>

        {rulesResult && (
          <div className="success-banner" style={{ marginTop: 12 }}>
            Reapplied rules to {(rulesResult.processed || 0).toLocaleString()} transaction{rulesResult.processed === 1 ? '' : 's'}.
            {' '}
            Updated {(rulesResult.updated || 0).toLocaleString()} transaction{rulesResult.updated === 1 ? '' : 's'}.
          </div>
        )}

        {rulesError && <div className="error" style={{ marginTop: 12 }}>{rulesError}</div>}
      </SettingsCard>
    );
  }

  function renderAccountCard() {
    const currentUserCopy = sharing?.currentUser
      ? `${displayPerson(sharing.currentUser)} - ${sharing.currentUser.role} - ${sharing.currentUser.accessLevel === 'read' ? 'read only' : 'read/write'}`
      : 'Loading account details...';

    return (
      <SettingsCard
        id="account"
        key="account"
        title="Partner Share"
        description="Household access and signed-in user details."
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
            <label className="field">
              <span>Permission</span>
              <AppSelect
                value={sharingAccessLevel}
                onChange={setSharingAccessLevel}
                ariaLabel="Sharing permission"
                options={[
                  { value: 'write', label: 'Read & Write' },
                  { value: 'read', label: 'Read Only' }
                ]}
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
                  <span className={`pill ${user.access_level === 'read' ? 'warning' : 'success'}`}>
                    {user.role === 'owner' || user.access_level !== 'read' ? 'read/write' : 'read only'}
                  </span>
                  {sharing.currentUser?.canRemoveUsers && user.id !== sharing.currentUser.id && user.role !== 'owner' && (
                    <AppSelect
                      className="settings-share-access-select"
                      value={user.access_level === 'read' ? 'read' : 'write'}
                      onChange={(value) => handleAccessChange(user, value)}
                      ariaLabel={`Permission for ${displayPerson(user)}`}
                      options={[
                        { value: 'write', label: 'Read & Write' },
                        { value: 'read', label: 'Read Only' }
                      ]}
                    />
                  )}
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
                    <div className="settings-share-row-actions">
                      <span className={`pill ${share.access_level === 'read' ? 'warning' : 'success'}`}>
                        {share.access_level === 'read' ? 'read only' : 'read/write'}
                      </span>
                      {sharing.currentUser?.canRemoveUsers && (
                        <AppSelect
                          className="settings-share-access-select"
                          value={share.access_level === 'read' ? 'read' : 'write'}
                          onChange={(value) => handleAccessChange(share, value, true)}
                          ariaLabel={`Permission for ${share.invited_email}`}
                          options={[
                            { value: 'write', label: 'Read & Write' },
                            { value: 'read', label: 'Read Only' }
                          ]}
                        />
                      )}
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => handleRevokeShare(share)}
                        disabled={sharingBusy}
                      >
                        Remove
                      </button>
                    </div>
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
            <span className="settings-donate-icon" aria-hidden>{'\u2615'}</span>
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
                  items={pageOrderRoutes.map((route) => route.path)}
                  strategy={verticalListSortingStrategy}
                >
                  <div className="settings-reorder-list reorder-active reorder-drag-scope">
                    {pageOrderRoutes.map((route) => {
                      const visible = isFeatureVisible(route);
                      return (
                        <ReorderListItem
                          key={route.path}
                          id={route.path}
                          className={visible ? '' : 'settings-reorder-row-off'}
                          disabled={!visible}
                          leading={<AppIcon name={route.icon} className="settings-reorder-icon" />}
                          handleLabel={`Move ${route.label}`}
                          title={route.label}
                          subtitle={visible ? route.description : 'Turned off'}
                          previewDisplaced={visible && route.path === moreOverId && route.path !== moreDragId}
                        />
                      );
                    })}
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

  function openSettingsPage(path) {
    navigate(path, { state: { transition: 'forward' } });
  }

  function renderSettingsHub() {
    return (
      <section className="settings-section settings-hub-section settings-section-top" aria-labelledby="settings-hub-title">
        <div className="settings-section-header">
          <h3 id="settings-hub-title">Settings</h3>
          <p>Choose the kind of setting you want to change.</p>
        </div>

        <div className="settings-hub-list">
          <SelectableListItem
            className="settings-hub-row"
            leading={<AppIcon name="settings" className="settings-hub-icon" />}
            title="Preferences"
            subtitle="Appearance, feature visibility, and app display options."
            onClick={() => openSettingsPage('/settings/preferences')}
          />
          <SelectableListItem
            className="settings-hub-row"
            leading={<AppIcon name="transactions" className="settings-hub-icon" />}
            title="Data Management"
            subtitle="Connections, sharing, rules, imports, exports, and backups."
            onClick={() => openSettingsPage('/settings/data-management')}
          />
        </div>
      </section>
    );
  }

  function renderSettingsContent() {
    if (settingsPage === 'preferences') {
      return (
        <div className="settings-card-stack settings-section-top">
          {renderAppearanceCard()}
          {renderFeaturesCard()}
        </div>
      );
    }

    if (settingsPage === 'data-management') {
      return (
        <div className="settings-card-stack settings-section-top">
          {renderSimpleFinCard()}
          {renderAccountCard()}
          {renderRulesCard()}
          {renderImportCard()}
          {renderBackupCard()}
        </div>
      );
    }

    return (
      <>
        {renderSettingsHub()}
        {renderAboutCard()}
      </>
    );
  }

  const pageMeta = settingsPage === 'preferences'
    ? {
      title: 'Preferences',
      subtitle: 'Appearance, feature visibility, and app display options.'
    }
    : settingsPage === 'data-management'
      ? {
        title: 'Data Management',
        subtitle: 'Connections, sharing, rules, imports, exports, and backups.'
      }
      : {
        title: 'Settings',
        subtitle: 'Maintenance and configuration.'
      };
  const isSettingsSubpage = settingsPage !== 'home';

  return (
    <div className="settings-view">
      <PageHero
        id="settings-title"
        variant="settings"
        kicker="Control Center"
        title={pageMeta.title}
        subtitle={pageMeta.subtitle}
        toolbar={isSettingsSubpage ? (
          <div className="settings-hero-toolbar">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate('/settings', { state: { transition: 'back' } })}
            >
              Back To Settings
            </button>
          </div>
        ) : null}
      />

      {renderSettingsContent()}

      <Dialog />
      {renderMoreReorderModal()}
    </div>
  );
}
