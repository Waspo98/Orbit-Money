import { useEffect, useState } from 'react';

export default function OfflineBanner({ isOnline }) {
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (isOnline) setDismissed(false);
  }, [isOnline]);

  if (isOnline || dismissed) return null;

  return (
    <div className="offline-banner" role="status">
      <div className="offline-banner-text">
        You are offline. Orbit Money is read-only until you reconnect.
      </div>
      <div className="offline-banner-actions">
        <button type="button" className="btn-secondary" onClick={() => setDismissed(true)}>
          Dismiss
        </button>
      </div>
    </div>
  );
}

