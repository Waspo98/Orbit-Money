import { useEffect, useState } from 'react';
import { api } from './api.js';
import BrandLogo from './components/BrandLogo.jsx';
import { APP_ICON_192 } from './brandAssets.js';
import { isNativeApp, getServerUrl, setServerUrl, normalizeServerUrl, resolveApiUrl } from './serverConfig.js';
import { openInAppBrowser, closeInAppBrowser, onAppUrlOpen } from './nativeApp.js';

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const [serverUrl, setServerUrlState] = useState(getServerUrl());
  const [customServerInput, setCustomServerInput] = useState(getServerUrl());
  const [showServerConfig, setShowServerConfig] = useState(isNativeApp() && !getServerUrl());
  const [serverError, setServerError] = useState('');
  const [serverTesting, setServerTesting] = useState(false);
  const [authConfig, setAuthConfig] = useState({
    authProvider: 'local',
    sampleDataEnabled: false
  });
  const localEnabled = authConfig.localEnabled ?? authConfig.authProvider === 'local';
  const oidcEnabled = authConfig.oidcEnabled ?? authConfig.authProvider === 'oidc';

  useEffect(() => {
    api.get('/api/auth/config')
      .then(setAuthConfig)
      .catch(() => setAuthConfig({ authProvider: 'local', sampleDataEnabled: false }));
  }, [serverUrl]);

  useEffect(() => {
    document.body.classList.add('login-scroll-lock');
    return () => document.body.classList.remove('login-scroll-lock');
  }, []);

  useEffect(() => {
    if (!isNativeApp()) return;

    const unsubscribe = onAppUrlOpen(async (data) => {
      const urlStr = data?.url || '';
      if (!urlStr.includes('://auth/callback')) return;

      try {
        setError('');
        setLoading(true);
        await closeInAppBrowser();

        const parsedUrl = new URL(urlStr.replace(/^[a-zA-Z0-9_-]+:\/\//, 'https://dummy.local/'));
        const code = parsedUrl.searchParams.get('code');
        if (!code) {
          throw new Error('No authentication code received from login provider.');
        }

        await api.post('/api/auth/oidc/native-exchange', { code });
        await onLogin();
      } catch (err) {
        setError(err.message || 'SSO Login failed');
      } finally {
        setLoading(false);
      }
    });

    return () => unsubscribe();
  }, [onLogin]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/api/auth/login', { username, password });
      await onLogin();
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  function sampleDeviceId() {
    const key = 'orbit_money_sample_device_id';
    const existing = window.localStorage.getItem(key);
    if (existing) return existing;
    const bytes = new Uint8Array(24);
    window.crypto.getRandomValues(bytes);
    const id = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    window.localStorage.setItem(key, id);
    return id;
  }

  async function handleSampleData() {
    setError('');
    setSampleLoading(true);
    try {
      await api.post('/api/auth/sample', { deviceId: sampleDeviceId() });
      await onLogin();
    } catch (err) {
      setError(err.message || 'Could not load sample data');
    } finally {
      setSampleLoading(false);
    }
  }

  async function handleOidcLogin() {
    const oidcUrl = authConfig.oidcLoginUrl || '/api/auth/oidc/login';
    const resolved = resolveApiUrl(oidcUrl);

    if (isNativeApp()) {
      const separator = resolved.includes('?') ? '&' : '?';
      const nativeUrl = `${resolved}${separator}app_return=1&app_scheme=orbitmoney`;
      await openInAppBrowser(nativeUrl);
      return;
    }

    window.location.href = resolved;
  }

  async function handleSaveServer(e) {
    if (e) e.preventDefault();
    setServerError('');
    const normalized = normalizeServerUrl(customServerInput);
    if (!normalized) {
      setServerError('Please enter a server address');
      return;
    }
    setCustomServerInput(normalized);
    setServerTesting(true);
    try {
      const res = await fetch(`${normalized}/api/health`, { credentials: 'omit' });
      const data = await res.json().catch(() => ({}));
      if (data.status === 'ok') {
        setServerUrl(normalized);
        setServerUrlState(normalized);
        setShowServerConfig(false);
      } else {
        setServerError('Server reachable, but health status is not ok.');
      }
    } catch (err) {
      setServerError(`Could not connect to ${normalized}. Check the address and ensure the server is online.`);
    } finally {
      setServerTesting(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-brand-lockup" aria-label="Orbit Money">
          <img src={APP_ICON_192} alt="" className="login-brand-icon" />
          <h1 className="login-title"><BrandLogo className="login-brand-logo" /></h1>
        </div>

        {isNativeApp() && (
          <div className="login-server-box">
            {showServerConfig ? (
              <>
                <label className="field">
                  <span>Server Address</span>
                  <input
                    type="url"
                    value={customServerInput}
                    onChange={(e) => setCustomServerInput(e.target.value)}
                    placeholder="https://orbit.yourdomain.com"
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck="false"
                  />
                </label>
                {serverError && <div className="error">{serverError}</div>}
                <div style={{ display: 'flex', gap: '8px', marginTop: '4px', marginBottom: '12px' }}>
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={handleSaveServer}
                    disabled={serverTesting}
                  >
                    {serverTesting ? 'Testing...' : 'Connect'}
                  </button>
                  {serverUrl && (
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => setShowServerConfig(false)}
                    >
                      Cancel
                    </button>
                  )}
                </div>
              </>
            ) : (
              <div
                className="login-server-status"
                style={{
                  textAlign: 'center',
                  marginBottom: '16px',
                  fontSize: '0.85rem',
                  color: 'var(--muted)'
                }}
              >
                <span>Server: <strong style={{ color: 'var(--text)' }}>{serverUrl}</strong></span>
                {' · '}
                <button
                  type="button"
                  onClick={() => setShowServerConfig(true)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--accent)',
                    textDecoration: 'underline',
                    cursor: 'pointer',
                    padding: 0,
                    font: 'inherit'
                  }}
                >
                  Change
                </button>
              </div>
            )}
          </div>
        )}

        {localEnabled && (
          <>
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </label>

            <label className="field">
              <span>Password</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>

            {error && <div className="error">{error}</div>}

            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Signing In...' : 'Sign In'}
            </button>
          </>
        )}

        {oidcEnabled && localEnabled && (
          <div className="login-divider" aria-hidden="true">
            <span>or</span>
          </div>
        )}

        {oidcEnabled && (
          <button type="button" className="btn-primary" onClick={handleOidcLogin}>
            {authConfig.oidcLoginLabel || 'Log in with OIDC'}
          </button>
        )}

        {authConfig.sampleDataEnabled && (
          <button
            type="button"
            className="login-sample-link"
            onClick={handleSampleData}
            disabled={sampleLoading}
          >
            {sampleLoading ? 'Loading sample data...' : 'Continue with sample data'}
          </button>
        )}
      </form>
    </div>
  );
}
