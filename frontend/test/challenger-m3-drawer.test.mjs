// frontend/test/challenger-m3-drawer.test.mjs
// Verification suite for Milestone 3 (5th Bottom Tab & M3E Right Drawer)

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

test('Milestone 3 - BottomTabs component 5th tab replacement & test invariants', () => {
  const bottomTabsCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'BottomTabs.jsx'), 'utf8');

  // Verify 5th tab has Settings label and icon
  assert.ok(bottomTabsCode.includes('<span className="bottom-tab-label">Settings</span>'), 'BottomTabs must render "Settings" as label');
  assert.ok(bottomTabsCode.includes('<AppIcon name="settings" className="bottom-tab-icon" />'), 'BottomTabs must render gear icon via AppIcon name="settings"');

  // Verify onSettingsClick and onMoreClick backwards compatibility
  assert.ok(bottomTabsCode.includes('onSettingsClick'), 'BottomTabs must accept onSettingsClick');
  assert.ok(bottomTabsCode.includes('onMoreClick'), 'BottomTabs must accept onMoreClick');
  assert.ok(bottomTabsCode.includes('const handleOpenDrawer = onSettingsClick || onMoreClick;'), 'BottomTabs must fallback to onMoreClick');

  // Verify selection haptic on 5th tab click
  assert.ok(bottomTabsCode.includes("triggerHaptic('selection')"), 'BottomTabs must trigger selection haptic on tap');

  // CRITICAL TEST INVARIANTS: required by expressive-visuals.test.mjs
  assert.ok(bottomTabsCode.includes('const isMoreActive = !isPrimaryTab;'), 'BottomTabs must compute isMoreActive');
  assert.ok(bottomTabsCode.includes('const activeIndex = isPrimaryTab ? currentIndex : primaryTabs.length;'), 'BottomTabs must dock activeIndex to 5th tab when isMoreActive');

  // Verify MoreDotsIcon.jsx is retained in the codebase for DropdownMenu
  const moreDotsIconPath = path.join(COMPONENTS_DIR, 'MoreDotsIcon.jsx');
  assert.ok(fs.existsSync(moreDotsIconPath), 'MoreDotsIcon.jsx must be retained in codebase');
});

test('Milestone 3 - RightDrawer component structure, M3E card layout & icon badges', () => {
  const rightDrawerPath = path.join(COMPONENTS_DIR, 'RightDrawer.jsx');
  assert.ok(fs.existsSync(rightDrawerPath), 'RightDrawer.jsx must exist');
  const code = fs.readFileSync(rightDrawerPath, 'utf8');

  // Default export
  assert.ok(code.includes('export default function RightDrawer'), 'RightDrawer must export default component');

  // Semantic elements and accessibility
  assert.ok(code.includes('role="dialog"'), 'RightDrawer backdrop must have role="dialog"');
  assert.ok(code.includes('aria-modal="true"'), 'RightDrawer backdrop must have aria-modal="true"');
  assert.ok(code.includes('<aside'), 'RightDrawer must render semantic <aside> drawer element');

  // Header & Close button
  assert.ok(code.includes('className="right-drawer-header"'), 'RightDrawer must render header container');
  assert.ok(code.includes('Settings & Navigation'), 'RightDrawer header must display title');
  assert.ok(code.includes('className="right-drawer-close-btn"'), 'RightDrawer must render close button');
  assert.ok(code.includes("triggerHaptic('tap')"), 'Close button must trigger tap haptic');

  // Secondary items rendering
  assert.ok(code.includes('getMoreRoutes({ mhaTrackerEnabled, navigationPreferences })'), 'RightDrawer must query getMoreRoutes');
  assert.ok(code.includes('right-drawer-item m3-tactile'), 'RightDrawer item must include m3-tactile styling');
  assert.ok(code.includes('right-drawer-badge'), 'RightDrawer item must render squircle icon badge');
  assert.ok(code.includes('right-drawer-icon'), 'RightDrawer item must render icon');
  assert.ok(code.includes('right-drawer-item-title'), 'RightDrawer item must render title');
  assert.ok(code.includes('right-drawer-item-desc'), 'RightDrawer item must render description');
  assert.ok(code.includes('right-drawer-chevron'), 'RightDrawer item must render trailing chevron');
  assert.ok(code.includes('displayLabel'), 'RightDrawer must support shortLabel fallback');
});

test('Milestone 3 - Route ordering alignment & feature flags', async () => {
  const navModule = await import('../src/navigation.js');
  const { getMoreRoutes } = navModule;

  // With MHA enabled (11 routes)
  const allRoutes = getMoreRoutes({ mhaTrackerEnabled: true });
  assert.equal(allRoutes.length, 11, 'getMoreRoutes with MHA enabled must return exactly 11 items');

  const expectedPaths = [
    '/rules',
    '/spending-trends',
    '/categories',
    '/goals',
    '/upcoming',
    '/retirement-calculator',
    '/housing-calculator',
    '/net-worth',
    '/household',
    '/mha-tracker',
    '/settings'
  ];
  assert.deepEqual(allRoutes.map((r) => r.path), expectedPaths, 'Secondary routes must match exact desktop sidebar sequence');

  const displayLabels = allRoutes.map((r) => r.shortLabel || r.label);
  const expectedLabels = [
    'Rules',
    'Spending Trends',
    'Categories',
    'Goals',
    'Upcoming',
    'Retirement Calculator',
    'Housing Calculator',
    'Net Worth',
    'Household',
    'MHA Tracker',
    'Settings'
  ];
  assert.deepEqual(displayLabels, expectedLabels, 'Display labels must match expected concise labels');

  // With MHA disabled (10 routes)
  const noMhaRoutes = getMoreRoutes({ mhaTrackerEnabled: false });
  assert.equal(noMhaRoutes.length, 10, 'getMoreRoutes with MHA disabled must return 10 items');
  assert.ok(!noMhaRoutes.some((r) => r.path === '/mha-tracker'), 'MHA Tracker must be omitted when disabled');
});

test('Milestone 3 - 4 Dismissal paths, touch gestures & scroll locking', () => {
  const code = fs.readFileSync(path.join(COMPONENTS_DIR, 'RightDrawer.jsx'), 'utf8');

  // Dismissal path 1: Backdrop click
  assert.ok(code.includes('onClick={() => close({ animate: true })}'), 'Backdrop must trigger animated close on click');
  assert.ok(code.includes('onClick={(e) => e.stopPropagation()}'), 'Drawer body must prevent backdrop click bubbling');

  // Dismissal path 2: Right-swipe touch gesture
  assert.ok(code.includes('onTouchStart={onTouchStart}'), 'Drawer must handle onTouchStart');
  assert.ok(code.includes('onTouchMove={onTouchMove}'), 'Drawer must handle onTouchMove');
  assert.ok(code.includes('onTouchEnd={onTouchEnd}'), 'Drawer must handle onTouchEnd');
  assert.ok(code.includes('onTouchCancel={onTouchEnd}'), 'Drawer must handle onTouchCancel');
  assert.ok(code.includes('deltaX = touch.clientX - gesture.startX'), 'Gesture must track horizontal deltaX');
  assert.ok(code.includes('translate3d(${distanceX}px, 0, 0)'), 'Gesture must apply inline translate3d transform');
  assert.ok(code.includes('triggerHaptic(\'selection\')'), 'Gesture must fire haptic on threshold crossing');

  // Dismissal path 3: Escape key
  assert.ok(code.includes("e.key === 'Escape'"), 'Drawer must listen for Escape key');

  // Dismissal path 4: Android hardware back button & web popstate
  assert.ok(code.includes('useOverlayBackDismiss(open,'), 'Drawer must register useOverlayBackDismiss');
  assert.ok(code.includes('useBodyScrollLock(open)'), 'Drawer must lock body scroll with useBodyScrollLock');

  // Navigation without resetting route state or scroll jump
  assert.ok(code.includes('releaseCurrentOverlayHistoryEntry()'), 'Item navigation must release overlay history entry');
  assert.ok(code.includes("navigate(item.path"), 'Item navigation must use navigate(item.path)');
  assert.ok(code.includes("transition: 'forward'"), 'Item navigation must preserve forward route transition');
});

test('Milestone 3 - App.jsx wiring & Android hardware back button', () => {
  const appCode = fs.readFileSync(path.join(SRC_DIR, 'App.jsx'), 'utf8');

  // Component import
  assert.ok(appCode.includes("import RightDrawer from './components/RightDrawer.jsx';"), 'App.jsx must import RightDrawer');
  assert.ok(!appCode.includes("import MoreSheet from './components/MoreSheet.jsx';"), 'App.jsx must not import MoreSheet');

  // State management
  assert.ok(appCode.includes('const [drawerOpen, setDrawerOpen] = useState(false);'), 'App.jsx must manage drawerOpen state');

  // BottomTabs & RightDrawer wiring
  assert.ok(appCode.includes('onSettingsClick={() => setDrawerOpen(true)}'), 'App.jsx must connect onSettingsClick on BottomTabs');
  assert.ok(appCode.includes('<RightDrawer') && appCode.includes('open={drawerOpen}'), 'App.jsx must render RightDrawer with drawerOpen');
  assert.ok(appCode.includes('onClose={() => setDrawerOpen(false)}'), 'App.jsx must pass onClose to RightDrawer');

  // Hardware back button interception
  assert.ok(appCode.includes('if (drawerOpen) {'), 'initNativeApp must check drawerOpen in backButton handler');
  assert.ok(appCode.includes('setDrawerOpen(false);'), 'initNativeApp must setDrawerOpen(false) on hardware back button');

  // Route auto-close
  assert.ok(/setDrawerOpen\(false\);?\s*\},?\s*\[location\.pathname\]\);/.test(appCode), 'App.jsx must auto-close drawer on route change');
});

test('Milestone 3 - index.css M3E Right Drawer styling, desktop suppression & reduced motion', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Backdrop styling
  assert.ok(css.includes('.right-drawer-backdrop {'), 'index.css must define .right-drawer-backdrop');
  assert.ok(css.includes('background: rgba(0, 0, 0, 0.45);'), 'Backdrop must use rgba(0,0,0,0.45)');
  assert.ok(css.includes('backdrop-filter: blur(10px)'), 'Backdrop must use 10px blur scrim');

  // Drawer styling
  assert.ok(css.includes('.right-drawer {'), 'index.css must define .right-drawer');
  assert.ok(css.includes('width: 85vw;'), 'Drawer must span ~85% mobile viewport width');
  assert.ok(css.includes('max-width: 380px;'), 'Drawer must have max-width 380px');
  assert.ok(css.includes('transform: translate3d(100%, 0, 0);'), 'Drawer must start off-canvas at 100%');

  // Card & Badge styling
  assert.ok(css.includes('border-radius: var(--radius-lg, 16px);'), 'Drawer item cards must use 16px rounded corners');
  assert.ok(css.includes('.right-drawer-badge {'), 'index.css must style squircle icon badge');
  assert.ok(css.includes('border-radius: 12px;'), 'Squircle badge must use 12px squircle radius');
  assert.ok(css.includes('.right-drawer-item.active'), 'Active route item must have active highlight');

  // Desktop integrity (hidden at >= 1080px)
  assert.ok(/@media\s*\(min-width:\s*1080px\)\s*\{\s*\.right-drawer-backdrop\s*\{/.test(css), 'index.css must hide .right-drawer-backdrop at >= 1080px');

  // Reduced motion
  assert.ok(/\.right-drawer,\s*\.right-drawer-backdrop\s*\{\s*transition:\s*none\s*!important;/.test(css), 'Reduced motion must disable drawer transitions');
});

// Import in-depth adversarial stress tests
import './challenger-m3-adversarial-deep.test.mjs';

