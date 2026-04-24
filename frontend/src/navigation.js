export const ROUTES = [
  {
    path: '/dashboard',
    label: 'Dashboard',
    icon: 'dashboard',
    nav: 'primary',
    hasPageHero: true
  },
  {
    path: '/transactions',
    label: 'Transactions',
    icon: 'transactions',
    nav: 'primary',
    hasPageHero: true
  },
  {
    path: '/budgets',
    label: 'Budgets',
    icon: 'budgets',
    nav: 'primary',
    hasPageHero: true
  },
  {
    path: '/accounts',
    label: 'Accounts',
    icon: 'accounts',
    nav: 'primary',
    hasPageHero: true
  },
  {
    path: '/rules',
    label: 'Rules',
    description: 'Automate merchant names and categories',
    icon: 'rules',
    nav: 'more',
    hasPageHero: true
  },
  {
    path: '/categories',
    label: 'Category Manager',
    description: 'Organize spending categories',
    icon: 'categories',
    nav: 'more',
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
    path: '/retirement-calculator',
    label: 'Retirement Calculator',
    description: 'Project retirement age, savings, and income',
    icon: 'goals',
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
    hasPageHero: true
  }
];

export function isRouteVisible(route, { mhaTrackerEnabled = false } = {}) {
  return route.feature !== 'mha' || mhaTrackerEnabled;
}

export function getPrimaryRoutes(options) {
  return ROUTES.filter((route) => route.nav === 'primary' && isRouteVisible(route, options));
}

export function getMoreRoutes(options) {
  return ROUTES.filter((route) => route.nav === 'more' && isRouteVisible(route, options));
}

export function getNavigationRoutes(options) {
  return ROUTES.filter((route) => route.nav && isRouteVisible(route, options));
}

export function getRoute(path) {
  return ROUTES.find((route) => route.path === path) || null;
}
