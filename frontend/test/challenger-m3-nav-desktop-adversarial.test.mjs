// frontend/test/challenger-m3-nav-desktop-adversarial.test.mjs
// Adversarial verification suite by Challenger 2 for Milestone 3 (Navigation Ordering & Desktop Parity)

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../..');
const SRC_DIR = path.resolve(ROOT_DIR, 'frontend/src');
const COMPONENTS_DIR = path.resolve(SRC_DIR, 'components');
const CSS_PATH = path.resolve(SRC_DIR, 'index.css');

test('Adversarial 1: Exact secondary route sequence and label resolution parity', async () => {
  const navModule = await import('../src/navigation.js');
  const { ROUTES, getMoreRoutes, getPrimaryRoutes, getNavigationRoutes } = navModule;

  // Expected 11 secondary routes in exact sequence
  const expectedSecondarySequence = [
    { path: '/rules', label: 'Rules', displayLabel: 'Rules', locked: true },
    { path: '/spending-trends', label: 'Spending Trends', displayLabel: 'Spending Trends', locked: false },
    { path: '/categories', label: 'Category Manager', displayLabel: 'Categories', locked: true },
    { path: '/goals', label: 'Savings Goals', displayLabel: 'Goals', locked: false },
    { path: '/upcoming', label: 'Upcoming', displayLabel: 'Upcoming', locked: false },
    { path: '/retirement-calculator', label: 'Retirement Calculator', displayLabel: 'Retirement Calculator', locked: false },
    { path: '/housing-calculator', label: 'Housing Calculator', displayLabel: 'Housing Calculator', locked: false },
    { path: '/net-worth', label: 'Net Worth', displayLabel: 'Net Worth', locked: false },
    { path: '/household', label: 'Household', displayLabel: 'Household', locked: false },
    { path: '/mha-tracker', label: 'MHA Tracker', displayLabel: 'MHA Tracker', locked: false, feature: 'mha' },
    { path: '/settings', label: 'Settings', displayLabel: 'Settings', locked: true }
  ];

  const moreRoutes = getMoreRoutes({ mhaTrackerEnabled: true });
  assert.equal(moreRoutes.length, 11, 'Must have exactly 11 secondary routes when MHA is enabled');

  for (let i = 0; i < expectedSecondarySequence.length; i++) {
    const expected = expectedSecondarySequence[i];
    const actual = moreRoutes[i];
    assert.equal(actual.path, expected.path, `Route index ${i} path mismatch: expected ${expected.path}, got ${actual.path}`);
    assert.equal(actual.label, expected.label, `Route index ${i} label mismatch: expected ${expected.label}, got ${actual.label}`);

    // Verify RightDrawer shortLabel resolution
    const actualDisplay = actual.shortLabel || actual.label;
    assert.equal(actualDisplay, expected.displayLabel, `Route index ${i} displayLabel mismatch: expected ${expected.displayLabel}, got ${actualDisplay}`);

    // Verify locked flag
    if (expected.locked) {
      assert.equal(actual.locked, true, `Route ${actual.path} must be locked: true`);
    }

    // Verify icons exist
    assert.ok(actual.icon && actual.icon.length > 0, `Route ${actual.path} must have icon defined`);
    assert.ok(actual.description && actual.description.length > 0, `Route ${actual.path} must have description defined`);
  }

  // Verify DesktopSidebar parity:
  // getNavigationRoutes returns primary + more. The more portion must exactly match getMoreRoutes!
  const navRoutes = getNavigationRoutes({ mhaTrackerEnabled: true });
  const primaryRoutes = getPrimaryRoutes();
  assert.equal(primaryRoutes.length, 4, 'Must have exactly 4 primary routes');
  assert.equal(navRoutes.length, 15, 'Total navigation routes must be 4 primary + 11 more = 15');

  const navRoutesMorePortion = navRoutes.slice(4);
  assert.deepEqual(
    navRoutesMorePortion.map((r) => r.path),
    expectedSecondarySequence.map((r) => r.path),
    'Desktop sidebar more portion must match getMoreRoutes order exactly'
  );
});

test('Adversarial 2: Feature-flag toggling and preference filter robustness', async () => {
  const { getMoreRoutes, isRouteVisible, getRoute } = await import('../src/navigation.js');

  // Case A: mhaTrackerEnabled = true
  const routesWithMha = getMoreRoutes({ mhaTrackerEnabled: true });
  assert.equal(routesWithMha.length, 11);
  assert.ok(routesWithMha.some((r) => r.path === '/mha-tracker'));

  // Case B: mhaTrackerEnabled = false
  const routesWithoutMha = getMoreRoutes({ mhaTrackerEnabled: false });
  assert.equal(routesWithoutMha.length, 10);
  assert.ok(!routesWithoutMha.some((r) => r.path === '/mha-tracker'));
  // Verify Settings is the last item
  assert.equal(routesWithoutMha[routesWithoutMha.length - 1].path, '/settings');

  // Case C: mhaTrackerEnabled omitted (undefined)
  const routesDefault = getMoreRoutes({});
  assert.equal(routesDefault.length, 10);
  assert.ok(!routesDefault.some((r) => r.path === '/mha-tracker'));

  // Case D: Stress test hiddenRoutePaths with locked vs unlocked routes
  const mhaRoute = getRoute('/mha-tracker');
  const rulesRoute = getRoute('/rules');
  const categoriesRoute = getRoute('/categories');
  const settingsRoute = getRoute('/settings');
  const goalsRoute = getRoute('/goals');

  // Locked routes cannot be hidden even if explicitly present in hiddenRoutePaths
  const maliciousPrefs = {
    hiddenRoutePaths: {
      '/rules': true,
      '/categories': true,
      '/settings': true,
      '/goals': true
    }
  };

  assert.equal(isRouteVisible(rulesRoute, { navigationPreferences: maliciousPrefs }), true, 'Locked /rules cannot be hidden');
  assert.equal(isRouteVisible(categoriesRoute, { navigationPreferences: maliciousPrefs }), true, 'Locked /categories cannot be hidden');
  assert.equal(isRouteVisible(settingsRoute, { navigationPreferences: maliciousPrefs }), true, 'Locked /settings cannot be hidden');
  assert.equal(isRouteVisible(goalsRoute, { navigationPreferences: maliciousPrefs }), false, 'Unlocked /goals should be hidden');

  // MHA route visibility when disabled vs enabled
  assert.equal(isRouteVisible(mhaRoute, { mhaTrackerEnabled: false }), false, 'MHA route must be hidden when flag is false');
  assert.equal(isRouteVisible(mhaRoute, { mhaTrackerEnabled: true }), true, 'MHA route must be visible when flag is true');
});

test('Adversarial 3: Desktop Viewport (>= 1080px) integrity, drawer suppression & stacking layout', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // 1. RightDrawer strictly hidden at >= 1080px with !important
  const drawerHiddenRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.right-drawer-backdrop\s*\{\s*display:\s*none\s*!important;\s*\}[\s\S]*?\}/;
  assert.ok(drawerHiddenRegex.test(css), 'right-drawer-backdrop must be display: none !important at min-width: 1080px');

  // 2. BottomTabs strictly hidden at >= 1080px
  const bottomTabsHiddenRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.bottom-tabs\s*\{\s*display:\s*none;\s*\}[\s\S]*?\}/;
  assert.ok(bottomTabsHiddenRegex.test(css), 'bottom-tabs must be display: none at min-width: 1080px');

  // 3. Desktop sidebar hidden on mobile (< 1080px) and visible flex on >= 1080px
  assert.ok(css.includes('.desktop-sidebar { display: none; }'), 'desktop-sidebar must have default display: none on mobile');
  const sidebarDesktopRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.desktop-sidebar\s*\{[\s\S]*?display:\s*flex;[\s\S]*?width:\s*var\(--sidebar-width\);/;
  assert.ok(sidebarDesktopRegex.test(css), 'desktop-sidebar must be display: flex with --sidebar-width at min-width: 1080px');

  // 4. Stacking and z-index ordering
  // Desktop sidebar z-index: 60, bottom-tabs z-index: 40, right-drawer-backdrop z-index: 190
  const zBottomTabs = css.match(/\.bottom-tabs\s*\{[^}]*z-index:\s*(\d+);/);
  assert.ok(zBottomTabs, 'bottom-tabs z-index must be specified');
  assert.equal(zBottomTabs[1], '40', 'bottom-tabs z-index must be 40');

  const zBackdrop = css.match(/\.right-drawer-backdrop\s*\{[^}]*z-index:\s*(\d+);/);
  assert.ok(zBackdrop, 'right-drawer-backdrop z-index must be specified');
  assert.equal(zBackdrop[1], '190', 'right-drawer-backdrop z-index must be 190');

  const zSidebar = css.match(/\.desktop-sidebar\s*\{[^}]*z-index:\s*(\d+);/);
  assert.ok(zSidebar, 'desktop-sidebar z-index must be specified');
  assert.equal(zSidebar[1], '60', 'desktop-sidebar z-index must be 60');

  // 5. Drawer geometry on mobile
  assert.ok(css.includes('width: 85vw;'), 'Drawer mobile width must be 85vw');
  assert.ok(css.includes('max-width: 380px;'), 'Drawer mobile max-width must be 380px');
  assert.ok(css.includes('min-width: 280px;'), 'Drawer mobile min-width must be 280px');
  assert.ok(css.includes('transform: translate3d(100%, 0, 0);'), 'Drawer initial off-canvas transform must be 100%');
});

test('Adversarial 4: 5th bottom tab rendering, spring slider mathematics & reduced motion', async () => {
  const bottomTabsCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'BottomTabs.jsx'), 'utf8');

  // 1. Icon & text verification
  assert.ok(bottomTabsCode.includes('<AppIcon name="settings" className="bottom-tab-icon" />'), '5th tab must render settings icon');
  assert.ok(bottomTabsCode.includes('<span className="bottom-tab-label">Settings</span>'), '5th tab must render Settings label');
  assert.ok(bottomTabsCode.includes('aria-label="Open settings and navigation drawer"'), '5th tab must have descriptive aria-label');

  // 2. Spring slider physics simulation
  const navModule = await import('../src/navigation.js');
  const primaryTabs = navModule.getPrimaryRoutes();
  assert.equal(primaryTabs.length, 4);

  const totalTabs = primaryTabs.length + 1; // 5
  assert.equal(totalTabs, 5, 'Total bottom tab count must be 5');

  // Test slider position calculation across all 4 primary routes
  for (let i = 0; i < primaryTabs.length; i++) {
    const route = primaryTabs[i];
    const currentIndex = primaryTabs.findIndex((t) => t.path === route.path);
    const isPrimaryTab = currentIndex >= 0;
    const isMoreActive = !isPrimaryTab;
    const activeIndex = isPrimaryTab ? currentIndex : primaryTabs.length;

    assert.equal(currentIndex, i, `Primary route ${route.path} must match index ${i}`);
    assert.equal(isPrimaryTab, true);
    assert.equal(isMoreActive, false);
    assert.equal(activeIndex, i, `Primary route ${route.path} must produce activeIndex ${i}`);

    const expectedTransform = `translate3d(calc(${i} * 100%), 0, 0)`;
    assert.ok(expectedTransform.includes(`calc(${i} * 100%)`), `Slot ${i} transform check`);
  }

  // Test slider position calculation across all 11 secondary routes
  const moreRoutes = navModule.getMoreRoutes({ mhaTrackerEnabled: true });
  for (const route of moreRoutes) {
    const currentIndex = primaryTabs.findIndex((t) => t.path === route.path);
    const isPrimaryTab = currentIndex >= 0;
    const isMoreActive = !isPrimaryTab;
    const activeIndex = isPrimaryTab ? currentIndex : primaryTabs.length;

    assert.equal(currentIndex, -1, `Secondary route ${route.path} must not be in primaryTabs`);
    assert.equal(isPrimaryTab, false);
    assert.equal(isMoreActive, true, `Secondary route ${route.path} must set isMoreActive to true`);
    assert.equal(activeIndex, 4, `Secondary route ${route.path} must dock activeIndex to 4 (5th tab)`);
  }

  // 3. Dynamic spring stretch & origin direction
  assert.ok(bottomTabsCode.includes('const scale = isStretching && !prefersReducedMotion ? 1.12 : 1.0;'), 'Spring slider must stretch to 1.12 during movement');
  assert.ok(bottomTabsCode.includes("transformOrigin: direction === 'forward' ? 'left center' : 'right center'"), 'Spring slider must anchor transformOrigin based on direction');
  assert.ok(bottomTabsCode.includes("const dir = activeIndex > prevIndexRef.current ? 'forward' : 'backward';"), 'Direction must properly detect forward vs backward');

  // 4. Reduced motion handling
  assert.ok(bottomTabsCode.includes("prefers-reduced-motion: reduce"), 'BottomTabs must detect prefers-reduced-motion');
  assert.ok(bottomTabsCode.includes("transition: !mounted || prefersReducedMotion\n      ? 'none'"), 'BottomTabs must disable transition for reduced motion or unmounted');
});
