import { useEffect, useState } from 'react';
import { api } from '../api.js';

const DISMISS_KEY = 'budget-tracker-dismissed-sync-error';

function getDismissedMessage() {
  try {
    return localStorage.getItem(DISMISS_KEY) || '';
  } catch {
    return '';
  }
}

function setDismissedMessage(msg) {
  try {
    if (msg) localStorage.setItem(DISMISS_KEY, msg);
    else localStorage.removeItem(DISMISS_KEY);
  } catch {
    /* ignore */
  }
}

/**
 * Banner shown below the header when the most recent SimpleFIN sync had a
 * problem. Polls status every 5 minutes.
 *
 * Dismissable via button. Dismissal is remembered per-browser keyed by the
 * exact error message — so a new or different error will re-appear, but the
 * same one stays hidden until a different run.
 *
 * Partial-sync errors (message starts with "Partial sync") are rendered in
 * yellow/warning styling rather than red.
 */
export default function SyncErrorBanner({ onOpenSettings }) {
  const [status, setStatus] = useState(null);
  const [dismissedMsg, setDismissed] = useState(() => getDismissedMessage());

  async function load() {
    try {
      const data = await api.get('/api/simplefin/status');
      setStatus(data);
    } catch {
      /* ignore */
    }
  }

  useEffect(() => {
    load();
    const id = setInterval(load, 5 * 60 * 1000);
    return () => clearInterval(id);
  }, []);

  if (!status?.connected) return null;
  if (!status.lastSync || status.lastSync.status !== 'error') return null;

  const msg = status.lastSync.error_message || 'Last sync failed.';
  if (msg === dismissedMsg) return null;

  const isPartial = /^partial sync/i.test(msg);

  function handleDismiss() {
    setDismissedMessage(msg);
    setDismissed(msg);
  }

  return (
    <div
      className={`sync-error-banner ${isPartial ? 'partial' : ''}`}
      role="alert"
    >
      <div className="sync-error-banner-text">
        <strong>
          {isPartial ? 'Partial sync:' : 'SimpleFIN sync issue:'}
        </strong>{' '}
        {msg.replace(/^partial sync\s*[—-]\s*/i, '')}
      </div>
      <div className="sync-error-banner-actions">
        <button type="button" className="btn-secondary" onClick={onOpenSettings}>
          View
        </button>
        <button type="button" className="btn-secondary" onClick={handleDismiss}>
          Dismiss
        </button>
      </div>
    </div>
  );
}
