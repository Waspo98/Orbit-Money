import { useState, useEffect } from 'react';
import { promptBiometricAuth } from '../lib/biometrics.js';
import { triggerHaptic } from '../lib/haptics.js';
import { APP_ICON_192 } from '../brandAssets.js';

export default function BiometricLockOverlay({ onUnlock }) {
  const [authenticating, setAuthenticating] = useState(false);
  const [error, setError] = useState('');

  async function handleUnlock() {
    if (authenticating) return;
    setAuthenticating(true);
    setError('');

    try {
      const success = await promptBiometricAuth('Unlock Orbit Money');
      if (success) {
        triggerHaptic('success');
        onUnlock();
      } else {
        triggerHaptic('warning');
        setError('Authentication was cancelled or failed. Tap below to try again.');
      }
    } catch (err) {
      triggerHaptic('warning');
      setError(err?.message || 'Authentication failed. Please try again.');
    } finally {
      setAuthenticating(false);
    }
  }

  useEffect(() => {
    // Attempt biometric prompt automatically on display
    const timer = setTimeout(() => {
      handleUnlock();
    }, 250);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      className="biometric-lock-overlay"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        background: 'var(--color-bg, #fafaf7)',
        color: 'var(--color-text, #111827)'
      }}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          maxWidth: '320px',
          textAlign: 'center',
          gap: '16px'
        }}
      >
        <img
          src={APP_ICON_192}
          alt="Orbit Money"
          style={{
            width: '80px',
            height: '80px',
            borderRadius: '20px',
            boxShadow: '0 8px 24px rgba(0, 0, 0, 0.12)'
          }}
        />
        <h2 style={{ fontSize: '1.4rem', fontWeight: 600, margin: '8px 0 0 0' }}>
          Orbit Money
        </h2>
        <p style={{ margin: 0, color: 'var(--color-text-muted, #6b7280)', fontSize: '0.95rem' }}>
          App is locked for your privacy.
        </p>

        {error && (
          <p style={{ color: '#ef4444', fontSize: '0.85rem', margin: '4px 0 0 0' }}>
            {error}
          </p>
        )}

        <button
          type="button"
          className="btn-primary"
          onClick={handleUnlock}
          disabled={authenticating}
          style={{
            marginTop: '12px',
            minWidth: '180px',
            padding: '12px 24px',
            fontSize: '1rem',
            borderRadius: '12px'
          }}
        >
          {authenticating ? 'Verifying...' : 'Unlock'}
        </button>
      </div>
    </div>
  );
}
