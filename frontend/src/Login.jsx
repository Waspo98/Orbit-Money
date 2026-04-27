import { useEffect, useState } from 'react';
import { api } from './api.js';

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [authConfig, setAuthConfig] = useState({ authProvider: 'local' });

  useEffect(() => {
    api.get('/api/auth/config')
      .then(setAuthConfig)
      .catch(() => setAuthConfig({ authProvider: 'local' }));
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

  function handleAuthentikLogin() {
    window.location.href = authConfig.oidcLoginUrl || '/api/auth/oidc/login';
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1 className="login-title">Orbit Money</h1>
        <p className="login-subtitle">Welcome back.</p>

        {authConfig.authProvider === 'authentik' ? (
          <button type="button" className="btn-primary" onClick={handleAuthentikLogin}>
            Sign in with Overbay.App account
          </button>
        ) : (
          <>
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoFocus
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
      </form>
    </div>
  );
}
