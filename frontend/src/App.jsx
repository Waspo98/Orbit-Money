import {
  Component,
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
import OfflineBanner from './components/OfflineBanner.jsx';
import SyncErrorBanner from './components/SyncErrorBanner.jsx';
import MoreSheet from './components/MoreSheet.jsx';
import { useTheme } from './hooks/useTheme.js';
import { useOnlineStatus } from './hooks/useOnlineStatus.js';
import { api } from './api.js';
import { APP_ICON_192 } from './brandAssets.js';
import { clearOfflineFinancialCache } from './offlineCache.js';
import { sortCategoriesByName } from './lib/categorySort.js';
import {
  ROUTES,
  getNavigationRoutes,
  getRoute
} from './navigation.js';
import {
  USER_PREFERENCE_KEYS,
  mergeUserPreferences,
  missingServerPreferencePatch,
  normalizePreferenceValue,
  readLocalUserPreferences,
  writeLocalPreference
} from './userPreferences.js';

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

const OFFLINE_ROUTE_PRELOADS = [
  () => import('./pages/Dashboard.jsx'),
  () => import('./pages/Transactions.jsx'),
  () => import('./pages/Budgets.jsx'),
  () => import('./pages/Accounts.jsx'),
  () => import('./pages/Settings.jsx'),
  () => import('./pages/Rules.jsx'),
  () => import('./pages/Categories.jsx'),
  () => import('./pages/HousingCalculator.jsx'),
  () => import('./pages/NetWorth.jsx'),
  () => import('./pages/MhaTracker.jsx'),
  () => import('./pages/Goals.jsx'),
  () => import('./pages/Upcoming.jsx'),
  () => import('./pages/RetirementCalculator.jsx'),
  () => import('./pages/Household.jsx')
];

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

class RouteErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="empty-state app-load-error route-load-error">
          <div className="empty-state-icon">!</div>
          <h2>Could Not Load This Screen</h2>
          <p>Reconnect and try again. This screen may not be cached on this device yet.</p>
          <button type="button" className="btn-primary" onClick={() => window.location.reload()}>
            Retry
          </button>
        </div>
      );
    }

    return this.props.children;
  }
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
  const [offlineAccessMessage, setOfflineAccessMessage] = useState('');
  const [moreOpen, setMoreOpen] = useState(false);

  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [lookupsReady, setLookupsReady] = useState(false);
  const [lookupError, setLookupError] = useState('');
  const [mhaTrackerEnabled, setMhaTrackerEnabled] = useState(false);
  const [userPreferences, setUserPreferencesState] = useState(readLocalUserPreferences);
  const userPreferencesRef = useRef(userPreferences);
  const isOnline = useOnlineStatus();
  const wasOnlineRef = useRef(isOnline);
  const offlineRoutesPreloadedRef = useRef(false);

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
  const navigationPreferences =
    userPreferences[USER_PREFERENCE_KEYS.navigationPreferences];
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

  async function checkAuth({ refreshLookups = false } = {}) {
    try {
      const me = await api.get('/api/auth/me');
      setOfflineAccessMessage('');
      setAuthState(me.authenticated ? 'in' : 'out');
      if (me.authenticated && refreshLookups) {
        await loadLookups();
      }
    } catch (err) {
      if (err?.offlineAccessExpired) {
        setOfflineAccessMessage(err.message);
        setAuthState('offline-expired');
        return;
      }
      setAuthState('out');
    }
  }

  async function loadLookups() {
    setLookupError('');
    try {
      const [a, c, mha, preferenceResponse] = await Promise.all([
        api.get('/api/accounts'),
        api.get('/api/categories'),
        api.get('/api/mha/settings'),
        api.get('/api/preferences')
      ]);
      const localPreferences = readLocalUserPreferences();
      const serverPreferences = preferenceResponse.preferences || {};
      const mergedPreferences = mergeUserPreferences(serverPreferences, localPreferences);
      setAccounts(a.items);
      setCategories(sortCategoriesByName(c.items));
      setMhaTrackerEnabled(!!mha.enabled);
      userPreferencesRef.current = mergedPreferences;
      setUserPreferencesState(mergedPreferences);
      setLookupsReady(true);
      const missingPreferences = missingServerPreferencePatch(serverPreferences, localPreferences);
      if (Object.keys(missingPreferences).length > 0) {
        api.put('/api/preferences', { preferences: missingPreferences }).catch((err) => {
          console.warn('Preference migration failed:', err);
        });
      }
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

  useEffect(() => {
    if (!wasOnlineRef.current && isOnline) {
      checkAuth({ refreshLookups: authState === 'in' });
    }
    wasOnlineRef.current = isOnline;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOnline, authState]);

  useEffect(() => {
    if (authState !== 'in' || !lookupsReady || !isOnline || offlineRoutesPreloadedRef.current) {
      return;
    }
    offlineRoutesPreloadedRef.current = true;
    Promise.allSettled(OFFLINE_ROUTE_PRELOADS.map((load) => load())).then((results) => {
      const failed = results.filter((result) => result.status === 'rejected');
      if (failed.length > 0) {
        offlineRoutesPreloadedRef.current = false;
        console.warn('Offline route preload failed:', failed.map((result) => result.reason));
      }
    });
  }, [authState, isOnline, lookupsReady]);

  // Close the More sheet whenever the route changes.
  useEffect(() => {
    setMoreOpen(false);
  }, [location.pathname]);

  async function handleLogout() {
    try {
      await api.post('/api/auth/logout');
    } catch { /* ignore */ }
    await clearOfflineFinancialCache();
    setAuthState('out');
    setAccounts([]);
    setCategories([]);
    setMhaTrackerEnabled(false);
    const localPreferences = readLocalUserPreferences();
    userPreferencesRef.current = localPreferences;
    setUserPreferencesState(localPreferences);
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

  function setUserPreference(key, nextOrUpdater) {
    const prev = userPreferencesRef.current;
    const rawValue =
      typeof nextOrUpdater === 'function'
        ? nextOrUpdater(prev[key], prev)
        : nextOrUpdater;
    const valueToSave = writeLocalPreference(key, normalizePreferenceValue(key, rawValue));
    const next = { ...prev, [key]: valueToSave };
    userPreferencesRef.current = next;
    setUserPreferencesState(next);
    api.put(`/api/preferences/${encodeURIComponent(key)}`, { value: valueToSave }).catch((err) => {
      console.warn(`Preference save failed for ${key}:`, err);
    });
  }

  function setNavigationPreferences(nextOrUpdater) {
    setUserPreference(USER_PREFERENCE_KEYS.navigationPreferences, (prevNavigation) =>
      typeof nextOrUpdater === 'function'
        ? nextOrUpdater(prevNavigation)
        : nextOrUpdater
    );
  }

  const renderSettings = (settingsPage = 'home') => (
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
      settingsPage={settingsPage}
    />
  );

  const routeElements = {
    '/dashboard': (
      <Dashboard
        accounts={accounts}
        categories={categories}
        mhaTrackerEnabled={mhaTrackerEnabled}
        userPreferences={userPreferences}
        onUserPreferenceChange={setUserPreference}
      />
    ),
    '/transactions': (
      <Transactions
        accounts={accounts}
        categories={categories}
        mhaTrackerEnabled={mhaTrackerEnabled}
      />
    ),
    '/budgets': (
      <Budgets
        budgetedSortPreference={userPreferences[USER_PREFERENCE_KEYS.budgetedSort]}
        onBudgetedSortPreferenceChange={(value) =>
          setUserPreference(USER_PREFERENCE_KEYS.budgetedSort, value)
        }
      />
    ),
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
    '/settings': renderSettings(),
    '/settings/preferences': renderSettings('preferences'),
    '/settings/data-management': renderSettings('data-management')
  };

  if (authState === 'loading') {
    return (
      <div className="center-screen">
        <div className="spinner" />
      </div>
    );
  }

  if (authState === 'out') {
    return <Login onLogin={() => checkAuth()} />;
  }

  if (authState === 'offline-expired') {
    return (
      <div className="center-screen offline-access-expired">
        <div className="empty-state">
          <div className="empty-state-icon">!</div>
          <h2>Reconnect To Verify Access</h2>
          <p>
            {offlineAccessMessage || 'Reconnect to verify household access before viewing cached shared data.'}
          </p>
          <button type="button" className="btn-primary" onClick={() => checkAuth()}>
            Retry
          </button>
        </div>
      </div>
    );
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

      <div className="app-banners">
        <OfflineBanner isOnline={isOnline} />
        <SyncErrorBanner onOpenSettings={() => navigate('/settings')} />
      </div>

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
            <RouteErrorBoundary resetKey={location.pathname}>
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
            </RouteErrorBoundary>
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
