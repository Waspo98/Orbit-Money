import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
  useNavigate,
  useNavigationType
} from 'react-router-dom';

import Login from './Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Transactions from './pages/Transactions.jsx';
import Budgets from './pages/Budgets.jsx';
import Accounts from './pages/Accounts.jsx';
import Settings from './pages/Settings.jsx';
import Rules from './pages/Rules.jsx';
import Categories from './pages/Categories.jsx';
import HousingCalculator from './pages/HousingCalculator.jsx';
import NetWorth from './pages/NetWorth.jsx';
import MhaTracker from './pages/MhaTracker.jsx';
import Goals from './pages/Goals.jsx';
import Upcoming from './pages/Upcoming.jsx';
import RetirementCalculator from './pages/RetirementCalculator.jsx';
import Household from './pages/Household.jsx';
import BottomTabs from './components/BottomTabs.jsx';
import DesktopSidebar from './components/DesktopSidebar.jsx';
import SyncErrorBanner from './components/SyncErrorBanner.jsx';
import MoreSheet from './components/MoreSheet.jsx';
import { useTheme } from './hooks/useTheme.js';
import { api } from './api.js';
import { sortCategoriesByName } from './lib/categorySort.js';
import { ROUTES, getNavigationRoutes, getRoute } from './navigation.js';

// Bottom tabs always visible. (Previously we hid them on Settings/import to
// keep the UI focused, but with the More tab the sheet can be opened from any
// page so keeping tabs visible is fine and more consistent.)

function transitionBetween(fromPath, toPath, routes) {
  const fromIndex = routes.indexOf(fromPath);
  const toIndex = routes.indexOf(toPath);

  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return 'none';
  return toIndex > fromIndex ? 'forward' : 'back';
}

export default function App() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const [authState, setAuthState] = useState('loading');
  const [moreOpen, setMoreOpen] = useState(false);

  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [lookupsReady, setLookupsReady] = useState(false);
  const [lookupError, setLookupError] = useState('');
  const [mhaTrackerEnabled, setMhaTrackerEnabled] = useState(false);

  const { mode: themeMode, setMode: setThemeMode } = useTheme();

  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const previousPathRef = useRef(location.pathname);
  const currentRoute = getRoute(location.pathname);
  const hasPageHero = !!currentRoute?.hasPageHero;
  const navigationRoutes = useMemo(
    () =>
      getNavigationRoutes({ mhaTrackerEnabled }).map((route) => route.path),
    [mhaTrackerEnabled]
  );
  const explicitTransition =
    navigationType === 'POP' ? null : location.state?.transition;
  const routeTransition =
    explicitTransition ||
    transitionBetween(previousPathRef.current, location.pathname, navigationRoutes);

  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    });
  }, [location.pathname]);

  useEffect(() => {
    previousPathRef.current = location.pathname;
  }, [location.pathname]);

  useEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration;
    window.history.scrollRestoration = 'manual';
    return () => {
      window.history.scrollRestoration = previousScrollRestoration;
    };
  }, []);

  async function checkAuth() {
    try {
      const me = await api.get('/api/auth/me');
      setAuthState(me.authenticated ? 'in' : 'out');
    } catch {
      setAuthState('out');
    }
  }

  async function loadLookups() {
    setLookupError('');
    try {
      const [a, c] = await Promise.all([
        api.get('/api/accounts'),
        api.get('/api/categories')
      ]);
      const mha = await api.get('/api/mha/settings');
      setAccounts(a.items);
      setCategories(sortCategoriesByName(c.items));
      setMhaTrackerEnabled(!!mha.enabled);
      setLookupsReady(true);
      return true;
    } catch (err) {
      console.error('Failed to load lookups:', err);
      setLookupError(err.message || 'Failed to load app data.');
      setLookupsReady(false);
      return false;
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
    setMhaTrackerEnabled(false);
    setLookupsReady(false);
    setLookupError('');
    navigate('/', { replace: true });
  }

  async function handleSettingsImportComplete() {
    const loaded = await loadLookups();
    if (loaded) {
      navigate('/transactions', { state: { transition: 'back' } });
    }
  }

  const routeElements = {
    '/dashboard': (
      <Dashboard
        accounts={accounts}
        categories={categories}
        mhaTrackerEnabled={mhaTrackerEnabled}
      />
    ),
    '/transactions': (
      <Transactions
        accounts={accounts}
        categories={categories}
        mhaTrackerEnabled={mhaTrackerEnabled}
      />
    ),
    '/budgets': <Budgets />,
    '/accounts': <Accounts onChange={loadLookups} />,
    '/rules': <Rules />,
    '/categories': (
      <Categories
        mhaTrackerEnabled={mhaTrackerEnabled}
        onChange={loadLookups}
      />
    ),
    '/housing-calculator': <HousingCalculator accounts={accounts} />,
    '/net-worth': <NetWorth />,
    '/household': <Household />,
    '/goals': <Goals />,
    '/upcoming': <Upcoming accounts={accounts} categories={categories} />,
    '/retirement-calculator': <RetirementCalculator />,
    '/mha-tracker': mhaTrackerEnabled ? <MhaTracker /> : <Navigate to="/settings" replace />,
    '/settings': (
      <Settings
        themeMode={themeMode}
        onThemeChange={setThemeMode}
        onLogout={handleLogout}
        mhaTrackerEnabled={mhaTrackerEnabled}
        onMhaTrackerChange={setMhaTrackerEnabled}
        onImportComplete={handleSettingsImportComplete}
      />
    )
  };

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
    <div className={`app-shell has-sidebar ${hasPageHero ? 'has-page-hero' : ''}`}>
      <DesktopSidebar mhaTrackerEnabled={mhaTrackerEnabled} />

      {!hasPageHero && <header className="app-header">
        <button
          type="button"
          className="brand brand-home"
          onClick={() => navigate('/dashboard')}
          aria-label="Go to dashboard"
        >
          <div className="brand-mark">$</div>
          <div className="brand-name">Orbit Money</div>
        </button>

      </header>}

      <SyncErrorBanner onOpenSettings={() => navigate('/settings')} />

      <main className="app-main">
        {lookupError ? (
          <div className="empty-state app-load-error">
            <div className="empty-state-icon">!</div>
            <h2>Could not load app data</h2>
            <p>{lookupError}</p>
            <button type="button" className="btn-primary" onClick={loadLookups}>
              Retry
            </button>
          </div>
        ) : !lookupsReady ? (
          <div className="center-loading">
            <div className="spinner" />
          </div>
        ) : (
          <div
            key={location.pathname}
            className={`route-transition route-transition-${routeTransition}`}
          >
            <Routes location={location}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              {ROUTES.map((route) => (
                <Route
                  key={route.path}
                  path={route.path}
                  element={routeElements[route.path]}
                />
              ))}
              <Route path="/import" element={<Navigate to="/settings" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </div>
        )}
      </main>

      <BottomTabs onMoreClick={() => setMoreOpen(true)} />

      <MoreSheet
        open={moreOpen}
        onClose={() => setMoreOpen(false)}
        mhaTrackerEnabled={mhaTrackerEnabled}
      />
    </div>
  );
}
