import { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import PageHero from '../components/PageHero.jsx';
import { useAppDialog } from '../components/AppDialog.jsx';
import { APP_VERSION_LABEL } from '../version.js';

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

function formatDateTime(iso) {
  if (!iso) return '—';
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

export default function Settings({
  themeMode = 'system',
  onThemeChange,
  onLogout,
  mhaTrackerEnabled = false,
  onMhaTrackerChange,
  onImportComplete
}) {
  const { alert, confirm, Dialog } = useAppDialog();
  // SimpleFIN section
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
  const fileInputRef = useRef(null);

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
    if (!ok) {
      return;
    }
    try {
      await api.post('/api/simplefin/disconnect');
      setSyncResult(null);
      setSyncError('');
      await loadStatus();
    } catch (err) {
      alert(err.message || 'Disconnect failed', { title: 'Disconnect failed' });
    }
  }

  async function toggleLog() {
    if (!showLog) await loadSyncLog();
    setShowLog(!showLog);
  }

  async function handleSignOut() {
    const ok = await confirm(
      'Sign out of Orbit Money on this device?',
      {
        title: 'Sign out',
        confirmLabel: 'Sign out',
        destructive: true
      }
    );
    if (!ok || !onLogout) {
      return;
    }
    setSigningOut(true);
    try {
      await onLogout();
    } finally {
      setSigningOut(false);
    }
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
        title: 'Remove share',
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

  return (
    <div className="settings-view">
      <PageHero
        id="settings-title"
        variant="settings"
        kicker="Control Center"
        title="Settings"
        subtitle="Maintenance and configuration."
      />

      <section className="settings-section settings-section-top">
        <div className="settings-section-header">
          <h3>Appearance</h3>
          <p>Choose how Orbit Money looks on this device.</p>
        </div>

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
      </section>

      {/* ===== SimpleFIN ===== */}
      <section className="settings-section">
        <div className="settings-section-header">
          <h3>SimpleFIN</h3>
          <p>
            {status === null
              ? 'Loading…'
              : status.connected
              ? 'Connected — syncs daily at 6 AM.'
              : 'Not connected.'}
          </p>
        </div>

        {status?.connected ? (
          <>
            <div className="status-grid">
              <div><dt>Cutover date</dt><dd>{status.cutoverDate || '—'}</dd></div>
              <div><dt>Last sync</dt><dd>{formatDateTime(status.lastSyncAt)}</dd></div>
              <div>
                <dt>Last status</dt>
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
                  })() : '—'}
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
                <strong>Sync now</strong>
                <p>Pulls transactions since the last successful sync.</p>
              </div>
              <button
                type="button"
                className="btn-primary"
                onClick={handleSync}
                disabled={syncBusy}
              >
                {syncBusy ? (<><span className="spinner-inline" /> Syncing…</>) : 'Sync now'}
              </button>
            </div>

            {syncError && <div className="error" style={{ marginTop: 16 }}>{syncError}</div>}

            {syncResult && (
              <div className={`result-card ${syncResult.status === 'error' ? 'error-tone' : ''}`}>
                <dl className="stat-grid">
                  <div><dt>Inserted</dt><dd>{syncResult.inserted.toLocaleString()}</dd></div>
                  <div><dt>Skipped</dt><dd>{syncResult.skipped.toLocaleString()}</dd></div>
                  <div><dt>RM removed</dt><dd>{syncResult.rmDeleted.toLocaleString()}</dd></div>
                  <div><dt>Accounts</dt><dd>{syncResult.accountsCreated.toLocaleString()}</dd></div>
                  <div><dt>Transfers</dt><dd>{syncResult.transfersPaired.toLocaleString()}</dd></div>
                </dl>
              </div>
            )}

            <div className="settings-action">
              <div className="settings-action-info">
                <strong>Sync history</strong>
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
                            <td className="error-col">{row.error_message || '—'}</td>
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
            <p style={{ marginBottom: 20 }}>
              Get a setup token from{' '}
              <a href="https://beta-bridge.simplefin.org/" target="_blank" rel="noreferrer">
                simplefin.org
              </a>{' '}
              and paste it below. You'll also pick a <strong>cutover date</strong> — Rocket Money
              data is kept for dates before it, SimpleFIN owns dates after.
            </p>

            <label className="field">
              <span>Setup token</span>
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
              <span>Cutover date</span>
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
              {setupBusy ? (<><span className="spinner-inline" /> Connecting…</>) : 'Connect'}
            </button>
          </form>
        )}
      </section>

      <section className="settings-section">
        <div className="settings-section-header">
          <h3>Import</h3>
          <p>Bring in Rocket Money data when you need to reload history.</p>
        </div>

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
                <strong>Import complete</strong>
                <p>Refresh account lookups and review the imported transactions.</p>
              </div>
              <button type="button" className="btn-primary" onClick={onImportComplete}>
                View transactions
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
      </section>

      <section className="settings-section">
        <div className="settings-section-header">
          <h3>MHA Tracker</h3>
          <p>Show Ministerial Housing Allowance tools when you need them.</p>
        </div>

        <div className="settings-action">
          <div className="settings-action-info">
            <strong>MHA Tracker</strong>
            <p>When off, the tracker page and transaction MHA controls are hidden.</p>
          </div>
          <button
            type="button"
            className={`btn-secondary ${mhaTrackerEnabled ? 'btn-active' : ''}`}
            onClick={handleMhaToggle}
            disabled={mhaBusy}
            aria-pressed={mhaTrackerEnabled}
          >
            {mhaBusy ? 'Saving...' : mhaTrackerEnabled ? 'On' : 'Off'}
          </button>
        </div>
        {mhaError && <div className="error" style={{ marginTop: 12 }}>{mhaError}</div>}
      </section>

      <section className="settings-section settings-account-section">
        <div className="settings-section-header">
          <h3>Account</h3>
          <p>Session controls and app build details.</p>
        </div>

        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Signed in</strong>
            <p>
              {sharing?.currentUser
                ? `${displayPerson(sharing.currentUser)} - ${sharing.currentUser.role}`
                : 'Loading account details...'}
            </p>
          </div>
        </div>

        {sharing?.currentUser?.canManageSharing && (
          <form className="settings-share-form" onSubmit={handleShare}>
            <label className="field">
              <span>Share with a partner</span>
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
                <span className="pill accent">{user.role}</span>
              </div>
            ))}

            {sharing.shares
              .filter((share) => !share.accepted_at)
              .map((share) => (
                <div className="settings-share-row" key={`share-${share.id}`}>
                  <div>
                    <strong>{share.invited_email}</strong>
                    <span>Waiting for Authentik sign in</span>
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

        <div className="settings-action">
          <div className="settings-action-info">
            <strong>Sign out</strong>
            <p>Ends this browser session and returns to the login screen.</p>
          </div>
          <button
            type="button"
            className="btn-danger"
            onClick={handleSignOut}
            disabled={signingOut}
          >
            {signingOut ? 'Signing out...' : 'Sign out'}
          </button>
        </div>
      </section>

      <section className="settings-section settings-about-section" aria-labelledby="settings-about-title">
        <div className="settings-about-brand">
          <img src="/icon-512.png" alt="" className="settings-about-icon" />
          <div>
            <h3 id="settings-about-title">Orbit Money</h3>
            <p>Personal finance PWA</p>
          </div>
        </div>

        <dl className="settings-about-details">
          <div>
            <dt>Build</dt>
            <dd>{APP_VERSION_LABEL}</dd>
          </div>
          <div>
            <dt>Developer</dt>
            <dd>Neal Overbay</dd>
          </div>
        </dl>

        <div className="settings-about-footer">
          <span>© 2026 Neal Overbay. All rights reserved.</span>
        </div>
      </section>
      <Dialog />
    </div>
  );
}
