import { useEffect, useState } from 'react';
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
  useNavigate
} from 'react-router-dom';

import Login from './Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Transactions from './pages/Transactions.jsx';
import Budgets from './pages/Budgets.jsx';
import Accounts from './pages/Accounts.jsx';
import Settings from './pages/Settings.jsx';
import Import from './pages/Import.jsx';
import Rules from './pages/Rules.jsx';
import BottomTabs from './components/BottomTabs.jsx';
import DesktopSidebar from './components/DesktopSidebar.jsx';
import HamburgerMenu from './components/HamburgerMenu.jsx';
import SyncErrorBanner from './components/SyncErrorBanner.jsx';
import MoreSheet from './components/MoreSheet.jsx';
import { useTheme } from './hooks/useTheme.js';
import { api } from './api.js';

// Bottom tabs always visible. (Previously we hid them on Settings/Import to
// keep the UI focused, but with the More tab the sheet can be opened from any
// page so keeping tabs visible is fine and more consistent.)

export default function App() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const [authState, setAuthState] = useState('loading');
  const [menuOpen, setMenuOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);

  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [lookupsReady, setLookupsReady] = useState(false);

  const { mode: themeMode, setMode: setThemeMode } = useTheme();

  const location = useLocation();
  const navigate = useNavigate();

  async function checkAuth() {
    try {
      const me = await api.get('/api/auth/me');
      setAuthState(me.authenticated ? 'in' : 'out');
    } catch {
      setAuthState('out');
    }
  }

  async function loadLookups() {
    try {
      const [a, c] = await Promise.all([
        api.get('/api/accounts'),
        api.get('/api/categories')
      ]);
      setAccounts(a.items);
      setCategories(c.items);
      setLookupsReady(true);
    } catch (err) {
      console.error('Failed to load lookups:', err);
    }
  }

  useEffect(() => {
    checkAuth();
  }, []);

  useEffect(() => {
    if (authState === 'in') loadLookups();
  }, [authState]);

  // Close the More sheet whenever the route changes.
  useEffect(() => {
    setMoreOpen(false);
  }, [location.pathname]);

  async function handleLogout() {
    try {
      await api.post('/api/auth/logout');
    } catch { /* ignore */ }
    setAuthState('out');
    setAccounts([]);
    setCategories([]);
    setLookupsReady(false);
    navigate('/', { replace: true });
  }

  async function handleImportComplete() {
    await loadLookups();
    navigate('/transactions');
  }

  if (authState === 'loading') {
    return (
      <div className="center-screen">
        <div className="spinner" />
      </div>
    );
  }

  if (authState === 'out') {
    return <Login onLogin={() => setAuthState('in')} />;
  }

  return (
    <div className="app-shell has-sidebar">
      <DesktopSidebar onMoreClick={() => setMoreOpen(true)} />

      <header className="app-header">
        <button
          type="button"
          className="brand brand-home"
          onClick={() => navigate('/dashboard')}
          aria-label="Go to dashboard"
        >
          <div className="brand-mark">$</div>
          <div className="brand-name">Orbit Money</div>
        </button>

        <button
          type="button"
          className="btn-icon"
          onClick={() => setMenuOpen(true)}
          aria-label="Open menu"
          aria-expanded={menuOpen}
        >
          ☰
        </button>
      </header>

      <SyncErrorBanner onOpenSettings={() => navigate('/settings')} />

      <main className="app-main">
        {!lookupsReady ? (
          <div className="center-loading">
            <div className="spinner" />
          </div>
        ) : (
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route
              path="/dashboard"
              element={<Dashboard accounts={accounts} categories={categories} />}
            />
            <Route
              path="/transactions"
              element={
                <Transactions accounts={accounts} categories={categories} />
              }
            />
            <Route path="/budgets" element={<Budgets />} />
            <Route
              path="/accounts"
              element={<Accounts onChange={loadLookups} />}
            />
            <Route path="/rules" element={<Rules />} />
            <Route path="/settings" element={<Settings />} />
            <Route
              path="/import"
              element={<Import onComplete={handleImportComplete} />}
            />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        )}
      </main>

      <BottomTabs onMoreClick={() => setMoreOpen(true)} />

      <HamburgerMenu
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        themeMode={themeMode}
        onThemeChange={setThemeMode}
        onLogout={handleLogout}
      />

      <MoreSheet open={moreOpen} onClose={() => setMoreOpen(false)} />
    </div>
  );
}
