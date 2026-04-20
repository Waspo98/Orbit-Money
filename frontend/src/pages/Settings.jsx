import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAppDialog } from '../components/AppDialog.jsx';

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

export default function Settings() {
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

  useEffect(() => {
    loadStatus();
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

  return (
    <div className="settings-view">
      <div className="view-header">
        <div>
          <h2>Settings</h2>
          <p className="muted">Maintenance and configuration.</p>
        </div>
      </div>

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
      <Dialog />
    </div>
  );
}
