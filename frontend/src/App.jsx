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
import BottomTabs from './components/BottomTabs.jsx';
import DesktopSidebar from './components/DesktopSidebar.jsx';
import SyncErrorBanner from './components/SyncErrorBanner.jsx';
import MoreSheet from './components/MoreSheet.jsx';
import { useTheme } from './hooks/useTheme.js';
import { api } from './api.js';

// Bottom tabs always visible. (Previously we hid them on Settings/import to
// keep the UI focused, but with the More tab the sheet can be opened from any
// page so keeping tabs visible is fine and more consistent.)

const PRIMARY_NAV_ROUTES = ['/dashboard', '/transactions', '/budgets', '/accounts'];
const MORE_NAV_ROUTES = [
  '/rules',
  '/categories',
  '/goals',
  '/housing-calculator',
  '/net-worth',
  '/mha-tracker',
  '/settings'
];

function transitionBetween(fromPath, toPath, routes) {
  const fromIndex = routes.indexOf(fromPath);
  const toIndex = routes.indexOf(toPath);

  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return 'none';
  return toIndex > fromIndex ? 'forward' : 'back';
}

function isSwipeInteractiveTarget(target) {
  return !!target?.closest?.(
    'a, button, input, textarea, select, summary, [role="button"], [data-no-page-swipe], .dropdown-wrap, .inline-popover'
  );
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
  const [mhaTrackerEnabled, setMhaTrackerEnabled] = useState(false);

  const { mode: themeMode, setMode: setThemeMode } = useTheme();

  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const previousPathRef = useRef(location.pathname);
  const touchRef = useRef(null);
  const hasPageHero =
    location.pathname === '/dashboard' ||
    location.pathname === '/transactions' ||
    location.pathname === '/categories' ||
    location.pathname === '/budgets' ||
    location.pathname === '/accounts' ||
    location.pathname === '/rules' ||
    location.pathname === '/housing-calculator' ||
    location.pathname === '/net-worth' ||
    location.pathname === '/goals' ||
    location.pathname === '/settings' ||
    location.pathname === '/mha-tracker';
  const navigationRoutes = useMemo(
    () =>
      [
        ...PRIMARY_NAV_ROUTES,
        ...MORE_NAV_ROUTES.filter((route) => route !== '/mha-tracker' || mhaTrackerEnabled)
      ],
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
    try {
      const [a, c] = await Promise.all([
        api.get('/api/accounts'),
        api.get('/api/categories')
      ]);
      const mha = await api.get('/api/mha/settings');
      setAccounts(a.items);
      setCategories(c.items);
      setMhaTrackerEnabled(!!mha.enabled);
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
    setMhaTrackerEnabled(false);
    setLookupsReady(false);
    navigate('/', { replace: true });
  }

  async function handleSettingsImportComplete() {
    await loadLookups();
    navigate('/transactions', { state: { transition: 'back' } });
  }

  function navigateAdjacentFromSwipe(direction) {
    const currentIndex = navigationRoutes.indexOf(location.pathname);
    if (currentIndex < 0) return;

    const nextIndex = direction === 'forward' ? currentIndex + 1 : currentIndex - 1;
    const nextPath = navigationRoutes[nextIndex];
    if (!nextPath) return;

    navigate(nextPath, { state: { transition: direction } });
  }

  function handleMainTouchStart(event) {
    if (window.innerWidth >= 1080 || isSwipeInteractiveTarget(event.target)) {
      touchRef.current = null;
      return;
    }

    const touch = event.touches[0];
    const edgeInset = 24;
    if (touch.clientX <= edgeInset || touch.clientX >= window.innerWidth - edgeInset) {
      touchRef.current = null;
      return;
    }

    touchRef.current = {
      x: touch.clientX,
      y: touch.clientY,
      time: Date.now()
    };
  }

  function handleMainTouchEnd(event) {
    const start = touchRef.current;
    touchRef.current = null;
    if (!start || window.innerWidth >= 1080) return;

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);
    const elapsed = Date.now() - start.time;

    if (elapsed > 700 || absX < 72 || absX < absY * 1.25) return;
    navigateAdjacentFromSwipe(deltaX < 0 ? 'forward' : 'back');
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

      <main
        className="app-main"
        onTouchStart={handleMainTouchStart}
        onTouchEnd={handleMainTouchEnd}
      >
        {!lookupsReady ? (
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
              <Route
                path="/dashboard"
                element={
                  <Dashboard
                    accounts={accounts}
                    categories={categories}
                    mhaTrackerEnabled={mhaTrackerEnabled}
                  />
                }
              />
              <Route
                path="/transactions"
                element={
                  <Transactions
                    accounts={accounts}
                    categories={categories}
                    mhaTrackerEnabled={mhaTrackerEnabled}
                  />
                }
              />
              <Route path="/budgets" element={<Budgets />} />
              <Route
                path="/accounts"
                element={<Accounts onChange={loadLookups} />}
              />
              <Route path="/rules" element={<Rules />} />
              <Route
                path="/categories"
                element={
                  <Categories
                    mhaTrackerEnabled={mhaTrackerEnabled}
                    onChange={loadLookups}
                  />
                }
              />
              <Route
                path="/housing-calculator"
                element={<HousingCalculator accounts={accounts} />}
              />
              <Route path="/net-worth" element={<NetWorth />} />
              <Route path="/goals" element={<Goals />} />
              <Route
                path="/mha-tracker"
                element={
                  mhaTrackerEnabled ? (
                    <MhaTracker />
                  ) : (
                    <Navigate to="/settings" replace />
                  )
                }
              />
              <Route
                path="/settings"
                element={
                  <Settings
                    themeMode={themeMode}
                    onThemeChange={setThemeMode}
                    onLogout={handleLogout}
                    mhaTrackerEnabled={mhaTrackerEnabled}
                    onMhaTrackerChange={setMhaTrackerEnabled}
                    onImportComplete={handleSettingsImportComplete}
                  />
                }
              />
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
