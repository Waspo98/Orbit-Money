import {
  Suspense,
  lazy,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState
} from 'react';
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
import BrandLogo from './components/BrandLogo.jsx';
import BottomTabs from './components/BottomTabs.jsx';
import DesktopSidebar from './components/DesktopSidebar.jsx';
import SyncErrorBanner from './components/SyncErrorBanner.jsx';
import MoreSheet from './components/MoreSheet.jsx';
import { useTheme } from './hooks/useTheme.js';
import { api } from './api.js';
import { APP_ICON_192 } from './brandAssets.js';
import { sortCategoriesByName } from './lib/categorySort.js';
import {
  ROUTES,
  getNavigationRoutes,
  getRoute,
  readNavigationPreferences,
  writeNavigationPreferences
} from './navigation.js';

const Dashboard = lazy(() => import('./pages/Dashboard.jsx'));
const Transactions = lazy(() => import('./pages/Transactions.jsx'));
const Budgets = lazy(() => import('./pages/Budgets.jsx'));
const Accounts = lazy(() => import('./pages/Accounts.jsx'));
const Settings = lazy(() => import('./pages/Settings.jsx'));
const Rules = lazy(() => import('./pages/Rules.jsx'));
const Categories = lazy(() => import('./pages/Categories.jsx'));
const HousingCalculator = lazy(() => import('./pages/HousingCalculator.jsx'));
const NetWorth = lazy(() => import('./pages/NetWorth.jsx'));
const MhaTracker = lazy(() => import('./pages/MhaTracker.jsx'));
const Goals = lazy(() => import('./pages/Goals.jsx'));
const Upcoming = lazy(() => import('./pages/Upcoming.jsx'));
const RetirementCalculator = lazy(() => import('./pages/RetirementCalculator.jsx'));
const Household = lazy(() => import('./pages/Household.jsx'));

function transitionBetween(fromPath, toPath, routes) {
  const fromIndex = routes.indexOf(fromPath);
  const toIndex = routes.indexOf(toPath);

  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return 'none';
  return toIndex > fromIndex ? 'forward' : 'back';
}

function RouteLoading() {
  return (
    <div className="center-loading">
      <div className="spinner" />
    </div>
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
  const [lookupError, setLookupError] = useState('');
  const [mhaTrackerEnabled, setMhaTrackerEnabled] = useState(false);
  const [navigationPreferences, setNavigationPreferencesState] = useState(
    readNavigationPreferences
  );

  const {
    mode: themeMode,
    setMode: setThemeMode,
    darkVariant,
    setDarkVariant
  } = useTheme();

  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const previousPathRef = useRef(location.pathname);
  const currentRoute = getRoute(location.pathname);
  const hasPageHero = !!currentRoute?.hasPageHero;
  const navigationRoutes = useMemo(
    () =>
      getNavigationRoutes({ mhaTrackerEnabled, navigationPreferences }).map(
        (route) => route.path
      ),
    [mhaTrackerEnabled, navigationPreferences]
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

  async function handleSettingsImportComplete(options = {}) {
    const loaded = await loadLookups();
    if (loaded && !options?.stayOnSettings) {
      navigate('/transactions', { state: { transition: 'back' } });
    }
  }

  function setNavigationPreferences(nextOrUpdater) {
    setNavigationPreferencesState((prev) => {
      const next =
        typeof nextOrUpdater === 'function'
          ? nextOrUpdater(prev)
          : nextOrUpdater;
      return writeNavigationPreferences(next);
    });
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
        darkVariant={darkVariant}
        onDarkVariantChange={setDarkVariant}
        onLogout={handleLogout}
        mhaTrackerEnabled={mhaTrackerEnabled}
        onMhaTrackerChange={setMhaTrackerEnabled}
        navigationPreferences={navigationPreferences}
        onNavigationPreferencesChange={setNavigationPreferences}
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
      <DesktopSidebar
        mhaTrackerEnabled={mhaTrackerEnabled}
        navigationPreferences={navigationPreferences}
      />

      {!hasPageHero && <header className="app-header">
        <button
          type="button"
          className="brand brand-home"
          onClick={() => navigate('/dashboard')}
          aria-label="Go to dashboard"
        >
          <img src={APP_ICON_192} alt="" className="brand-mark brand-mark-image" />
          <BrandLogo />
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
            <Suspense fallback={<RouteLoading />}>
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
            </Suspense>
          </div>
        )}
      </main>

      <BottomTabs onMoreClick={() => setMoreOpen(true)} />

      <MoreSheet
        open={moreOpen}
        onClose={() => setMoreOpen(false)}
        mhaTrackerEnabled={mhaTrackerEnabled}
        navigationPreferences={navigationPreferences}
      />
    </div>
  );
}
