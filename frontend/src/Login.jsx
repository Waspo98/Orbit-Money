import { useEffect, useState } from 'react';
import { api } from './api.js';
import BrandLogo from './components/BrandLogo.jsx';
import { APP_ICON_192 } from './brandAssets.js';

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sampleLoading, setSampleLoading] = useState(false);
  const [authConfig, setAuthConfig] = useState({ authProvider: 'local' });
  const localEnabled = authConfig.localEnabled ?? authConfig.authProvider === 'local';
  const oidcEnabled = authConfig.oidcEnabled ?? authConfig.authProvider === 'oidc';

  useEffect(() => {
    api.get('/api/auth/config')
      .then(setAuthConfig)
      .catch(() => setAuthConfig({ authProvider: 'local' }));
  }, []);

  useEffect(() => {
    document.body.classList.add('login-scroll-lock');
    return () => document.body.classList.remove('login-scroll-lock');
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/api/auth/login', { username, password });
      onLogin();
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
      onLogin();
    } catch (err) {
      setError(err.message || 'Could not load sample data');
    } finally {
      setSampleLoading(false);
    }
  }

  function handleOidcLogin() {
    window.location.href = authConfig.oidcLoginUrl || '/api/auth/oidc/login';
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-brand-lockup" aria-label="Orbit Money">
          <img src={APP_ICON_192} alt="" className="login-brand-icon" />
          <h1 className="login-title"><BrandLogo className="login-brand-logo" /></h1>
        </div>
        <p className="login-subtitle">Welcome back.</p>

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

        <button
          type="button"
          className="login-sample-link"
          onClick={handleSampleData}
          disabled={sampleLoading}
        >
          {sampleLoading ? 'Loading sample data...' : 'Continue with sample data'}
        </button>
      </form>
    </div>
  );
}
