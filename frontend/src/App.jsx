import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
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

function useIsMobileRouteDeck() {
  const [enabled, setEnabled] = useState(() =>
    typeof window !== 'undefined' ? window.innerWidth < 1080 : false
  );

  useEffect(() => {
    function update() {
      setEnabled(window.innerWidth < 1080);
    }

    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  return enabled;
}

function isSwipeInteractiveTarget(target) {
  return !!target?.closest?.(
    'a, button, input, textarea, select, summary, [role="button"], [data-no-page-swipe], .dropdown-wrap, .inline-popover, .modal, .more-sheet, .goal-list.reorder-active'
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
  const useRouteDeck = useIsMobileRouteDeck();
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

  useLayoutEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    });
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

  function renderRoute(pathname = location.pathname) {
    switch (pathname) {
      case '/dashboard':
        return (
          <Dashboard
            accounts={accounts}
            categories={categories}
            mhaTrackerEnabled={mhaTrackerEnabled}
          />
        );
      case '/transactions':
        return (
          <Transactions
            accounts={accounts}
            categories={categories}
            mhaTrackerEnabled={mhaTrackerEnabled}
          />
        );
      case '/budgets':
        return <Budgets />;
      case '/accounts':
        return <Accounts onChange={loadLookups} />;
      case '/rules':
        return <Rules />;
      case '/categories':
        return (
          <Categories
            mhaTrackerEnabled={mhaTrackerEnabled}
            onChange={loadLookups}
          />
        );
      case '/housing-calculator':
        return <HousingCalculator accounts={accounts} />;
      case '/net-worth':
        return <NetWorth />;
      case '/goals':
        return <Goals />;
      case '/mha-tracker':
        return mhaTrackerEnabled ? <MhaTracker /> : <Navigate to="/settings" replace />;
      case '/settings':
        return (
          <Settings
            themeMode={themeMode}
            onThemeChange={setThemeMode}
            onLogout={handleLogout}
            mhaTrackerEnabled={mhaTrackerEnabled}
            onMhaTrackerChange={setMhaTrackerEnabled}
            onImportComplete={handleSettingsImportComplete}
          />
        );
      case '/':
      case '/import':
        return <Navigate to={pathname === '/' ? '/dashboard' : '/settings'} replace />;
      default:
        return <Navigate to="/dashboard" replace />;
    }
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

      <main className="app-main">
        {!lookupsReady ? (
          <div className="center-loading">
            <div className="spinner" />
          </div>
        ) : useRouteDeck && navigationRoutes.includes(location.pathname) ? (
          <MobileRouteDeck
            currentPath={location.pathname}
            routeTransition={location.state?.transition}
            routes={navigationRoutes}
            renderRoute={renderRoute}
            navigate={navigate}
          />
        ) : (
          <Routes location={location}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={renderRoute('/dashboard')} />
            <Route path="/transactions" element={renderRoute('/transactions')} />
            <Route path="/budgets" element={renderRoute('/budgets')} />
            <Route path="/accounts" element={renderRoute('/accounts')} />
            <Route path="/rules" element={renderRoute('/rules')} />
            <Route path="/categories" element={renderRoute('/categories')} />
            <Route path="/housing-calculator" element={renderRoute('/housing-calculator')} />
            <Route path="/net-worth" element={renderRoute('/net-worth')} />
            <Route path="/goals" element={renderRoute('/goals')} />
            <Route path="/mha-tracker" element={renderRoute('/mha-tracker')} />
            <Route path="/settings" element={renderRoute('/settings')} />
            <Route path="/import" element={<Navigate to="/settings" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
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

function MobileRouteDeck({ currentPath, routeTransition, routes, renderRoute, navigate }) {
  const currentIndex = routes.indexOf(currentPath);
  const previousPath = currentIndex > 0 ? routes[currentIndex - 1] : null;
  const nextPath = currentIndex >= 0 && currentIndex < routes.length - 1
    ? routes[currentIndex + 1]
    : null;
  const panePaths = [previousPath, currentPath, nextPath].filter(Boolean);
  const activePaneIndex = previousPath ? 1 : 0;
  const [dragX, setDragX] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [settling, setSettling] = useState(false);
  const [bottomEntering, setBottomEntering] = useState(false);
  const startRef = useRef(null);
  const timerRef = useRef(null);
  const suppressNextEnterRef = useRef(false);

  useLayoutEffect(() => {
    setDragging(false);
    startRef.current = null;
    if (timerRef.current) clearTimeout(timerRef.current);

    if (suppressNextEnterRef.current) {
      suppressNextEnterRef.current = false;
      setBottomEntering(false);
      setDragX(0);
      setSettling(false);
      return;
    }

    if (routeTransition === 'forward' || routeTransition === 'back') {
      setBottomEntering(false);
      setSettling(true);
      setDragX(routeTransition === 'forward' ? window.innerWidth : -window.innerWidth);
      window.requestAnimationFrame(() => {
        window.requestAnimationFrame(() => {
          setDragX(0);
          finishSettle(() => setSettling(false));
        });
      });
      return;
    }

    if (routeTransition === 'from-more') {
      setDragX(0);
      setSettling(false);
      setBottomEntering(true);
      finishSettle(() => setBottomEntering(false));
      return;
    }

    setBottomEntering(false);
    setDragX(0);
    setSettling(false);
  }, [currentPath]);

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  function finishSettle(callback) {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(callback, 230);
  }

  function handleTouchStart(event) {
    if (settling || isSwipeInteractiveTarget(event.target)) {
      startRef.current = null;
      return;
    }

    const touch = event.touches[0];
    const edgeInset = 24;
    if (touch.clientX <= edgeInset || touch.clientX >= window.innerWidth - edgeInset) {
      startRef.current = null;
      return;
    }

    startRef.current = {
      x: touch.clientX,
      y: touch.clientY,
      time: Date.now(),
      locked: null
    };
  }

  function handleTouchMove(event) {
    const start = startRef.current;
    if (!start || settling) return;

    const touch = event.touches[0];
    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);

    if (!start.locked) {
      if (absY > 12 && absY > absX) {
        start.locked = 'vertical';
        return;
      }
      if (absX > 12 && absX > absY * 1.2) {
        start.locked = 'horizontal';
        setDragging(true);
      }
    }

    if (start.locked !== 'horizontal') return;
    if ((deltaX > 0 && !previousPath) || (deltaX < 0 && !nextPath)) {
      setDragX(deltaX * 0.22);
      return;
    }

    event.preventDefault();
    setDragX(deltaX);
  }

  function handleTouchEnd(event) {
    const start = startRef.current;
    startRef.current = null;
    if (!start || start.locked !== 'horizontal') {
      setDragging(false);
      setDragX(0);
      return;
    }

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - start.x;
    const elapsed = Math.max(1, Date.now() - start.time);
    const velocity = Math.abs(deltaX) / elapsed;
    const threshold = Math.min(132, window.innerWidth * 0.26);
    const wantsNext = deltaX < 0;
    const targetPath = wantsNext ? nextPath : previousPath;
    const shouldCommit =
      !!targetPath && (Math.abs(deltaX) > threshold || (velocity > 0.58 && Math.abs(deltaX) > 48));

    setDragging(false);
    setSettling(true);

    if (!shouldCommit) {
      setDragX(0);
      finishSettle(() => setSettling(false));
      return;
    }

    setDragX(wantsNext ? -window.innerWidth : window.innerWidth);
    finishSettle(() => {
      suppressNextEnterRef.current = true;
      navigate(targetPath, {
        state: { transition: wantsNext ? 'forward' : 'back' }
      });
    });
  }

  const transform = `translate3d(calc(${-activePaneIndex * 100}% + ${dragX}px), 0, 0)`;

  return (
    <div
      className={`mobile-route-deck-viewport ${dragging ? 'is-dragging' : ''} ${bottomEntering ? 'from-more-enter' : ''}`}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={handleTouchEnd}
    >
      <div
        className="mobile-route-deck"
        style={{
          transform,
          transition: dragging ? 'none' : undefined
        }}
      >
        {panePaths.map((path) => (
          <section
            key={path}
            className={`mobile-route-pane ${path === currentPath ? 'is-current' : ''}`}
            aria-hidden={path === currentPath ? undefined : true}
            inert={path === currentPath ? undefined : ''}
          >
            {renderRoute(path)}
          </section>
        ))}
      </div>
    </div>
  );
}
