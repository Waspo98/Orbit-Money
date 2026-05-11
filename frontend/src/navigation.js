export const ROUTES = [
  {
    path: '/dashboard',
    label: 'Dashboard',
    icon: 'dashboard',
    nav: 'primary',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/transactions',
    label: 'Transactions',
    icon: 'transactions',
    nav: 'primary',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/budgets',
    label: 'Budgets',
    icon: 'budgets',
    nav: 'primary',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/accounts',
    label: 'Accounts',
    icon: 'accounts',
    nav: 'primary',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/rules',
    label: 'Rules',
    description: 'Automate merchant names and categories',
    icon: 'rules',
    nav: 'more',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/spending-trends',
    label: 'Spending Trends',
    description: 'Income, expenses, and category history',
    icon: 'spending',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/categories',
    label: 'Category Manager',
    description: 'Organize spending categories',
    icon: 'categories',
    nav: 'more',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/goals',
    label: 'Savings Goals',
    description: 'Track savings targets',
    icon: 'goals',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/upcoming',
    label: 'Upcoming',
    description: 'Bills, subscriptions, and income',
    icon: 'upcoming',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/retirement-calculator',
    label: 'Retirement Calculator',
    description: 'Compare retirement projection scenarios',
    icon: 'retirement',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/housing-calculator',
    label: 'Housing Calculator',
    description: 'Selling, buying, and payment estimates',
    icon: 'housing',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/net-worth',
    label: 'Net Worth',
    description: 'Assets minus liabilities over time',
    icon: 'networth',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/household',
    label: 'Household',
    description: 'Net pay, benefits, and income history',
    icon: 'household',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/mha-tracker',
    label: 'MHA Tracker',
    description: 'Track housing allowance transactions',
    icon: 'mha',
    nav: 'more',
    feature: 'mha',
    hasPageHero: true
  },
  {
    path: '/settings',
    label: 'Settings',
    description: 'Maintenance and configuration',
    icon: 'settings',
    nav: 'more',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/settings/preferences',
    label: 'Preferences',
    description: 'Appearance, notifications, and app display options',
    icon: 'settings',
    locked: true,
    hasPageHero: true
  },
  {
    path: '/settings/data-management',
    label: 'Data Management',
    description: 'Connections, sharing, imports, exports, and backups',
    icon: 'transactions',
    locked: true,
    hasPageHero: true
  }
];

export const NAVIGATION_PREFS_STORAGE_KEY = 'orbit-money-navigation-preferences';

const DEFAULT_MORE_ROUTE_ORDER = ROUTES
  .filter((route) => route.nav === 'more')
  .map((route) => route.path);

export function normalizeNavigationPreferences(value = {}) {
  const prefs = value && typeof value === 'object' ? value : {};
  const hiddenRoutePaths = {};
  const routesByPath = new Map(ROUTES.map((route) => [route.path, route]));

  for (const route of ROUTES) {
    if (route.locked) continue;
    if (prefs.hiddenRoutePaths?.[route.path] === true) {
      hiddenRoutePaths[route.path] = true;
    }
  }

  const incomingOrder = Array.isArray(prefs.moreRouteOrder) ? prefs.moreRouteOrder : [];
  const moreRouteOrder = [
    ...incomingOrder.filter((path) => routesByPath.get(path)?.nav === 'more'),
    ...DEFAULT_MORE_ROUTE_ORDER.filter((path) => !incomingOrder.includes(path))
  ];

  return { hiddenRoutePaths, moreRouteOrder };
}

export function readNavigationPreferences() {
  try {
    return normalizeNavigationPreferences(
      JSON.parse(localStorage.getItem(NAVIGATION_PREFS_STORAGE_KEY) || '{}')
    );
  } catch {
    return normalizeNavigationPreferences();
  }
}

export function writeNavigationPreferences(preferences) {
  const normalized = normalizeNavigationPreferences(preferences);
  try {
    localStorage.setItem(NAVIGATION_PREFS_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    /* ignore */
  }
  return normalized;
}

export function isRouteVisible(
  route,
  { mhaTrackerEnabled = false, navigationPreferences } = {}
) {
  const prefs = normalizeNavigationPreferences(navigationPreferences);
  if (route.feature === 'mha' && !mhaTrackerEnabled) return false;
  if (!route.locked && prefs.hiddenRoutePaths[route.path]) return false;
  return true;
}

function sortMoreRoutes(routes, navigationPreferences) {
  const prefs = normalizeNavigationPreferences(navigationPreferences);
  const order = new Map(prefs.moreRouteOrder.map((path, index) => [path, index]));
  return [...routes].sort(
    (a, b) =>
      (order.get(a.path) ?? Number.MAX_SAFE_INTEGER) -
      (order.get(b.path) ?? Number.MAX_SAFE_INTEGER)
  );
}

export function getPrimaryRoutes(options) {
  return ROUTES.filter((route) => route.nav === 'primary' && isRouteVisible(route, options));
}

export function getMoreRoutes(options) {
  return sortMoreRoutes(
    ROUTES.filter((route) => route.nav === 'more' && isRouteVisible(route, options)),
    options?.navigationPreferences
  );
}

export function getNavigationRoutes(options) {
  return [
    ...getPrimaryRoutes(options),
    ...getMoreRoutes(options)
  ];
}

export function getRoute(path) {
  return ROUTES.find((route) => route.path === path) || null;
}
