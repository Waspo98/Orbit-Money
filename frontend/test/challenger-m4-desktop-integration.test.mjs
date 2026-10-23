// frontend/test/challenger-m4-desktop-integration.test.mjs
// Dedicated Integration & Desktop Verification Suite for Milestone 4
// Verifies desktop sidebar parity, viewport breakpoints (>=1080px, 768px, 380px, 335px),
// Accounts.jsx PageActionRow integrity, and offline WOFF2 font loading with zero CDN dependencies.

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../..');
const FRONTEND_DIR = path.resolve(ROOT_DIR, 'frontend');
const SRC_DIR = path.resolve(FRONTEND_DIR, 'src');
const COMPONENTS_DIR = path.resolve(SRC_DIR, 'components');
const PAGES_DIR = path.resolve(SRC_DIR, 'pages');
const CSS_PATH = path.resolve(SRC_DIR, 'index.css');
const INDEX_HTML_PATH = path.resolve(FRONTEND_DIR, 'index.html');
const FONTS_DIR = path.resolve(SRC_DIR, 'assets/fonts');
const ICONS_DIR = path.resolve(SRC_DIR, 'assets/icons');

// =============================================================================
// SUITE 1: Desktop Sidebar & Right Drawer Route Parity
// =============================================================================

test('Suite 1.1: Desktop sidebar secondary route parity with RightDrawer (default config)', async () => {
  const { getPrimaryRoutes, getMoreRoutes, getNavigationRoutes } = await import('../src/navigation.js');

  const primaryRoutes = getPrimaryRoutes();
  assert.equal(primaryRoutes.length, 4, 'Must have exactly 4 primary routes');
  assert.deepEqual(
    primaryRoutes.map((r) => r.path),
    ['/dashboard', '/transactions', '/budgets', '/accounts'],
    'Primary routes must match expected core navigation'
  );

  const moreRoutes = getMoreRoutes({ mhaTrackerEnabled: false });
  assert.equal(moreRoutes.length, 10, 'Default more routes (without MHA) must have 10 items');

  const expectedSecondaryPaths = [
    '/rules',
    '/spending-trends',
    '/categories',
    '/goals',
    '/upcoming',
    '/retirement-calculator',
    '/housing-calculator',
    '/net-worth',
    '/household',
    '/settings'
  ];

  assert.deepEqual(
    moreRoutes.map((r) => r.path),
    expectedSecondaryPaths,
    'getMoreRoutes order must follow canonical desktop sequence'
  );

  // DesktopSidebar uses getNavigationRoutes, which concatenates primary + more
  const allDesktopRoutes = getNavigationRoutes({ mhaTrackerEnabled: false });
  assert.equal(allDesktopRoutes.length, 14, 'Desktop nav must contain 4 primary + 10 secondary = 14 routes');

  // Primary slice matches getPrimaryRoutes
  assert.deepEqual(
    allDesktopRoutes.slice(0, 4).map((r) => r.path),
    primaryRoutes.map((r) => r.path),
    'Desktop sidebar first 4 items must be primary routes'
  );

  // Secondary slice matches getMoreRoutes exactly
  assert.deepEqual(
    allDesktopRoutes.slice(4).map((r) => r.path),
    moreRoutes.map((r) => r.path),
    'Desktop sidebar secondary routes must achieve 100% path parity with RightDrawer'
  );

  // Check icon and label consistency between desktop and drawer
  for (let i = 0; i < moreRoutes.length; i++) {
    const desktopItem = allDesktopRoutes.slice(4)[i];
    const drawerItem = moreRoutes[i];

    assert.equal(desktopItem.path, drawerItem.path);
    assert.equal(desktopItem.icon, drawerItem.icon, `Icon parity mismatch for ${desktopItem.path}`);
    assert.ok(desktopItem.label, `Desktop label missing for ${desktopItem.path}`);
    assert.ok(drawerItem.shortLabel || drawerItem.label, `Drawer label missing for ${drawerItem.path}`);
  }
});

test('Suite 1.2: Desktop sidebar and RightDrawer parity with MHA Tracker flag enabled', async () => {
  const { getMoreRoutes, getNavigationRoutes } = await import('../src/navigation.js');

  const moreRoutesWithMha = getMoreRoutes({ mhaTrackerEnabled: true });
  assert.equal(moreRoutesWithMha.length, 11, 'Must have 11 secondary routes when MHA is enabled');

  const mhaIndex = moreRoutesWithMha.findIndex((r) => r.path === '/mha-tracker');
  assert.equal(mhaIndex, 9, 'MHA tracker must be positioned immediately before Settings at index 9');
  assert.equal(moreRoutesWithMha[10].path, '/settings', 'Settings must remain the final item');

  const desktopRoutesWithMha = getNavigationRoutes({ mhaTrackerEnabled: true });
  assert.equal(desktopRoutesWithMha.length, 15, 'Desktop nav must contain 4 primary + 11 secondary = 15 routes');

  assert.deepEqual(
    desktopRoutesWithMha.slice(4).map((r) => r.path),
    moreRoutesWithMha.map((r) => r.path),
    'Desktop sidebar and RightDrawer must preserve exact 1:1 order when MHA is toggled'
  );
});

test('Suite 1.3: Custom user preferences propagate identically to DesktopSidebar and RightDrawer', async () => {
  const { getMoreRoutes, getNavigationRoutes } = await import('../src/navigation.js');

  const customPreferences = {
    moreRouteOrder: ['/settings', '/upcoming', '/goals', '/spending-trends'],
    hiddenRoutePaths: {
      '/housing-calculator': true,
      '/net-worth': true
    }
  };

  const customizedDrawer = getMoreRoutes({
    mhaTrackerEnabled: false,
    navigationPreferences: customPreferences
  });

  const customizedDesktop = getNavigationRoutes({
    mhaTrackerEnabled: false,
    navigationPreferences: customPreferences
  });

  // Verify hidden routes are filtered from both
  assert.ok(!customizedDrawer.some((r) => r.path === '/housing-calculator'));
  assert.ok(!customizedDrawer.some((r) => r.path === '/net-worth'));
  assert.ok(!customizedDesktop.some((r) => r.path === '/housing-calculator'));
  assert.ok(!customizedDesktop.some((r) => r.path === '/net-worth'));

  // Verify secondary slice of desktop matches drawer exactly
  assert.deepEqual(
    customizedDesktop.slice(4).map((r) => r.path),
    customizedDrawer.map((r) => r.path),
    'Custom route ordering and visibility must stay identical between desktop sidebar and right drawer'
  );
});

test('Suite 1.4: DesktopSidebar.jsx component implementation contracts', () => {
  const sidebarCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'DesktopSidebar.jsx'), 'utf8');

  // Verify core structural elements
  assert.ok(sidebarCode.includes('<aside className="desktop-sidebar">'), 'Must render <aside className="desktop-sidebar">');
  assert.ok(sidebarCode.includes('className="sidebar-brand brand-home"'), 'Must render brand button with sidebar-brand brand-home');
  assert.ok(sidebarCode.includes("navigate('/dashboard')"), 'Clicking brand must navigate to /dashboard');
  assert.ok(sidebarCode.includes('<BrandLogo />'), 'Must render BrandLogo component');
  assert.ok(sidebarCode.includes('className="sidebar-nav"'), 'Must render navigation container <nav className="sidebar-nav">');
  assert.ok(sidebarCode.includes('getNavigationRoutes({ mhaTrackerEnabled, navigationPreferences })'), 'Must call getNavigationRoutes with props');
  assert.ok(sidebarCode.includes('<NavLink'), 'Must use NavLink for active route styling');
  assert.ok(sidebarCode.includes('className={({ isActive }) =>'), 'Must compute dynamic active link class');
  assert.ok(sidebarCode.includes('sidebar-link'), 'Must apply sidebar-link class');
  assert.ok(sidebarCode.includes('<AppIcon name={t.icon} className="sidebar-icon" />'), 'Must render AppIcon with sidebar-icon class');
});

// =============================================================================
// SUITE 2: Breakpoint CSS Rules (1080px, 768px, 380px, 335px) & Canopy Layout
// =============================================================================

test('Suite 2.1: 1080px Desktop Breakpoint — BottomTabs and RightDrawer suppression, Sidebar display', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // 1. Mobile default: desktop sidebar is display: none
  assert.ok(
    css.includes('.desktop-sidebar { display: none; }') || css.includes('.desktop-sidebar{display:none}'),
    '.desktop-sidebar must be hidden by default on mobile viewports'
  );

  // 2. Desktop (>= 1080px): Bottom tabs strictly hidden
  const bottomTabsHideRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.bottom-tabs\s*\{\s*display:\s*none;\s*\}[\s\S]*?\}/;
  assert.ok(bottomTabsHideRegex.test(css), '.bottom-tabs must have display: none at min-width: 1080px');

  // 3. Desktop (>= 1080px): Right drawer backdrop strictly hidden with !important
  const rightDrawerHideRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.right-drawer-backdrop\s*\{\s*display:\s*none\s*!important;\s*\}[\s\S]*?\}/;
  assert.ok(rightDrawerHideRegex.test(css), '.right-drawer-backdrop must have display: none !important at min-width: 1080px');

  // 4. Desktop (>= 1080px): Desktop sidebar displayed and width: var(--sidebar-width)
  const sidebarShowRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.desktop-sidebar\s*\{[\s\S]*?display:\s*flex;[\s\S]*?width:\s*var\(--sidebar-width\);/;
  assert.ok(sidebarShowRegex.test(css), '.desktop-sidebar must have display: flex and width: var(--sidebar-width) at min-width: 1080px');

  // 5. App shell grid layout at >= 1080px
  const shellGridRegex = /@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.app-shell\.has-sidebar\s*\{[\s\S]*?grid-template-columns:\s*var\(--sidebar-width\)\s+1fr;/;
  assert.ok(shellGridRegex.test(css), '.app-shell.has-sidebar must declare grid-template-columns: var(--sidebar-width) 1fr');
});

test('Suite 2.2: Canopy Header edge-to-edge geometry and zero horizontal overflow', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Pinned sticky full width header
  assert.ok(css.includes('position: sticky;'), '.page-hero must be position: sticky');
  assert.ok(css.includes('top: 0;'), '.page-hero must be top: 0');
  assert.ok(css.includes('width: 100%;'), '.page-hero must span 100% width');
  assert.ok(css.includes('max-width: 100%;'), '.page-hero must restrict max-width to 100%');
  assert.ok(css.includes('box-sizing: border-box;'), '.page-hero must use box-sizing: border-box to prevent overflow');
  assert.ok(css.includes('env(safe-area-inset-top'), '.page-hero must include safe-area-inset-top padding for mobile status bar');

  // Canopy top row symmetrical 3-column grid
  const canopyTopRowBlock = css.match(/\.canopy-top-row\s*\{([^}]+)\}/);
  assert.ok(canopyTopRowBlock, '.canopy-top-row rule must exist');
  const topRowStyles = canopyTopRowBlock[1];
  assert.ok(topRowStyles.includes('display: grid;'), '.canopy-top-row must be display: grid');
  assert.ok(topRowStyles.includes('grid-template-columns: minmax(40px, auto) 1fr minmax(40px, auto);'), 'Must use 3-column symmetrical template');

  // Ellipsis overflow protection on canopy typography
  const titleBlock = css.match(/\.canopy-title\s*\{([^}]+)\}/);
  assert.ok(titleBlock, '.canopy-title rule must exist');
  assert.ok(titleBlock[1].includes('white-space: nowrap;'), '.canopy-title must prevent wrapping');
  assert.ok(titleBlock[1].includes('overflow: hidden;'), '.canopy-title must hide overflow');
  assert.ok(titleBlock[1].includes('text-overflow: ellipsis;'), '.canopy-title must truncate with ellipsis');

  const subtitleBlock = css.match(/\.canopy-subtitle\s*\{([^}]+)\}/);
  assert.ok(subtitleBlock, '.canopy-subtitle rule must exist');
  assert.ok(subtitleBlock[1].includes('white-space: nowrap;'), '.canopy-subtitle must prevent wrapping');
  assert.ok(subtitleBlock[1].includes('overflow: hidden;'), '.canopy-subtitle must hide overflow');
  assert.ok(subtitleBlock[1].includes('text-overflow: ellipsis;'), '.canopy-subtitle must truncate with ellipsis');
});

test('Suite 2.3: Tablet (>=768px), Narrow Mobile (<=380px), and Ultra-Narrow (<=335px) responsive rules', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // 1. Tablet/Desktop (>= 768px): Canopy Tier 2 Transactions toolbar transitions to single row
  const tabletRegex = /@media\s*\(min-width:\s*768px\)\s*\{[\s\S]*?\.canopy-tier2\s+\.txn-toolbar\s*\{[\s\S]*?grid-template-columns:\s*1fr\s+auto;/;
  assert.ok(tabletRegex.test(css), 'Canopy Tier 2 toolbar must use grid-template-columns: 1fr auto at min-width: 768px');

  // 2. Narrow mobile (<= 380px): Toolbar actions gap tightening to 4px
  const narrowRegex = /@media\s*\(max-width:\s*380px\)\s*\{[\s\S]*?\.canopy-tier2\s+\.txn-toolbar-actions\s*\{[\s\S]*?gap:\s*4px;/;
  assert.ok(narrowRegex.test(css), 'Canopy Tier 2 toolbar actions gap must tighten to 4px at max-width: 380px');

  // 3. Ultra-narrow mobile (<= 335px): Text labels hidden on control buttons to prevent overflow on 320px
  const ultraNarrowRegex = /@media\s*\(max-width:\s*335px\)\s*\{[\s\S]*?\.canopy-tier2\s+\.txn-control-label\s*\{[\s\S]*?display:\s*none;/;
  assert.ok(ultraNarrowRegex.test(css), 'Canopy Tier 2 .txn-control-label must be display: none at max-width: 335px');
});

// =============================================================================
// SUITE 3: Accounts.jsx PageActionRow and In-Header Action Integrity
// =============================================================================

test('Suite 3.1: Accounts.jsx preserves accounts.length > 0 && <PageActionRow className="accounts-toolbar contract', () => {
  const accountsCode = fs.readFileSync(path.join(PAGES_DIR, 'Accounts.jsx'), 'utf8');

  // Verbatim contract check required by expressive-visuals.test.mjs
  const hasGuard = accountsCode.includes('accounts.length > 0 &&');
  const hasToolbar = accountsCode.includes('<PageActionRow className="accounts-toolbar');
  assert.ok(hasGuard, 'Accounts.jsx must check accounts.length > 0 before rendering PageActionRow');
  assert.ok(hasToolbar, 'Accounts.jsx must render <PageActionRow className="accounts-toolbar');

  // Verify PageActionRow import
  assert.ok(accountsCode.includes("import PageActionRow from '../components/PageActionRow.jsx';"), 'Accounts.jsx must import PageActionRow');

  // Verify PageHero in-header creation button is wired
  assert.ok(accountsCode.includes('onAction={() => setAddingManual(true)}'), 'Accounts.jsx PageHero must wire onAction to setAddingManual(true)');
  assert.ok(accountsCode.includes('actionLabel="Add Account"'), 'Accounts.jsx PageHero must set actionLabel="Add Account"');

  // Verify secondary action buttons in PageActionRow
  assert.ok(accountsCode.includes('Show archived'), 'PageActionRow must include Show archived toggle');
  assert.ok(accountsCode.includes('Reorder Groups'), 'PageActionRow must include Reorder Groups button');
  assert.ok(accountsCode.includes('+ Manual Account'), 'PageActionRow must include + Manual Account fallback button');
});

// =============================================================================
// SUITE 4: Offline WOFF2 Font Loading, Zero External CDNs, and Ligature Hygiene
// =============================================================================

test('Suite 4.1: index.html zero external CDN links and valid viewport meta', () => {
  const html = fs.readFileSync(INDEX_HTML_PATH, 'utf8');

  // No external font CDNs or stylesheets
  assert.ok(!html.includes('fonts.googleapis.com'), 'index.html must not link to fonts.googleapis.com');
  assert.ok(!html.includes('fonts.gstatic.com'), 'index.html must not link to fonts.gstatic.com');
  assert.ok(!html.includes('cdnjs.cloudflare.com'), 'index.html must not link to cdnjs.cloudflare.com');
  assert.ok(!html.includes('cdn.jsdelivr.net'), 'index.html must not link to cdn.jsdelivr.net');
  assert.ok(!html.includes('unpkg.com'), 'index.html must not link to unpkg.com');

  // Viewport configuration for full edge-to-edge rendering
  assert.ok(html.includes('viewport-fit=cover'), 'index.html viewport meta must include viewport-fit=cover');
});

test('Suite 4.2: Local @font-face declarations and ligature feature settings in index.css', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Material Symbols Rounded @font-face declaration
  const materialSymbolsFontRegex = /@font-face\s*\{[^}]*font-family:\s*['"]Material Symbols Rounded['"][^}]*url\(['"]\.\/assets\/fonts\/material-symbols-rounded\.woff2['"]\)\s+format\(['"]woff2['"]\)[^}]*font-display:\s*block;[^}]*\}/;
  assert.ok(materialSymbolsFontRegex.test(css), 'index.css must declare @font-face for Material Symbols Rounded pointing to local WOFF2');

  // Inter Tight @font-face declarations (Regular, Medium, SemiBold, Bold)
  assert.ok(css.includes("url('./assets/fonts/InterTight-Regular.woff2')"), 'Must declare InterTight-Regular.woff2');
  assert.ok(css.includes("url('./assets/fonts/InterTight-Medium.woff2')"), 'Must declare InterTight-Medium.woff2');
  assert.ok(css.includes("url('./assets/fonts/InterTight-SemiBold.woff2')"), 'Must declare InterTight-SemiBold.woff2');
  assert.ok(css.includes("url('./assets/fonts/InterTight-Bold.woff2')"), 'Must declare InterTight-Bold.woff2');

  // DM Sans @font-face declaration
  assert.ok(css.includes("url('./assets/fonts/DMSans-VariableFont_opsz,wght.ttf')"), 'Must declare DMSans-VariableFont');

  // .material-symbols-rounded class ligature settings
  const ligatureRegex = /\.material-symbols-rounded\s*\{[^}]*font-feature-settings:\s*['"]liga['"];[^}]*\}/;
  assert.ok(ligatureRegex.test(css), '.material-symbols-rounded must declare font-feature-settings: "liga"');
  assert.ok(css.includes("-webkit-font-feature-settings: 'liga';"), '.material-symbols-rounded must declare -webkit-font-feature-settings');
});

test('Suite 4.3: Binary WOFF2 header verification for all bundled fonts', () => {
  const expectedWoff2Files = [
    'material-symbols-rounded.woff2',
    'InterTight-Regular.woff2',
    'InterTight-Medium.woff2',
    'InterTight-SemiBold.woff2',
    'InterTight-Bold.woff2'
  ];

  // WOFF2 magic bytes: 'wOF2' -> 0x77, 0x4F, 0x46, 0x32
  const WOFF2_MAGIC = Buffer.from([0x77, 0x4f, 0x46, 0x32]);

  for (const filename of expectedWoff2Files) {
    const filePath = path.join(FONTS_DIR, filename);
    assert.ok(fs.existsSync(filePath), `Font file ${filename} must exist on disk`);

    const stats = fs.statSync(filePath);
    assert.ok(stats.size > 50000, `Font file ${filename} must be valid non-empty asset (found ${stats.size} bytes)`);

    const fd = fs.openSync(filePath, 'r');
    const header = Buffer.alloc(4);
    fs.readSync(fd, header, 0, 4, 0);
    fs.closeSync(fd);

    assert.ok(header.equals(WOFF2_MAGIC), `Font file ${filename} must have valid WOFF2 magic header 'wOF2'`);
  }

  // TTF file verification for DM Sans
  const dmsansPath = path.join(FONTS_DIR, 'DMSans-VariableFont_opsz,wght.ttf');
  assert.ok(fs.existsSync(dmsansPath), 'DMSans-VariableFont_opsz,wght.ttf must exist on disk');
  const dmsansStats = fs.statSync(dmsansPath);
  assert.ok(dmsansStats.size > 100000, 'DMSans font file must be > 100KB');
});

test('Suite 4.4: AppIcon SVG asset coverage prevents raw ligature leaks across all routes', async () => {
  const { ROUTES } = await import('../src/navigation.js');
  const appIconCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'AppIcon.jsx'), 'utf8');

  // Verify that each navigation route has an SVG icon mapped in ICON_PATHS
  for (const route of ROUTES) {
    const iconName = route.icon;
    assert.ok(iconName, `Route ${route.path} must define an icon name`);

    // Verify AppIcon.jsx maps this icon
    const hasMapping = appIconCode.includes(`${iconName}: new URL(`);
    assert.ok(hasMapping, `AppIcon.jsx ICON_PATHS must map icon '${iconName}' for route ${route.path}`);

    // Verify the SVG file actually exists in assets/icons
    const iconPathMatches = appIconCode.match(new RegExp(`${iconName}:\\s*new URL\\(['"]([^'"]+)['"]`));
    assert.ok(iconPathMatches, `Could not parse icon URL for '${iconName}'`);
    const relPath = iconPathMatches[1];
    const resolvedPath = path.resolve(COMPONENTS_DIR, relPath);
    assert.ok(fs.existsSync(resolvedPath), `SVG icon file ${resolvedPath} must exist on disk`);

    // Verify SVG file content begins with valid XML/SVG markup
    const svgContent = fs.readFileSync(resolvedPath, 'utf8').trim();
    assert.ok(
      svgContent.startsWith('<svg') || svgContent.startsWith('<?xml'),
      `Icon file for '${iconName}' must contain valid SVG XML markup`
    );
  }
});

// =============================================================================
// SUITE 5: 15-View Application Integrity & PageHero Action Consistency
// =============================================================================

test('Suite 5.1: Actionable vs Read-Only in-header (+) creation wiring across all pages', () => {
  const actionablePages = [
    { file: 'Transactions.jsx', expectedAction: 'setManualTxnOpen(true)' },
    { file: 'Budgets.jsx', expectedAction: 'setAddingBudget(true)' },
    { file: 'Accounts.jsx', expectedAction: 'setAddingManual(true)' },
    { file: 'Upcoming.jsx', expectedAction: 'openNew()' },
    { file: 'Goals.jsx', expectedAction: 'openNewGoal' },
    { file: 'Rules.jsx', expectedAction: 'setEditing({})' },
    { file: 'Categories.jsx', expectedAction: 'setEditing({})' },
    { file: 'Household.jsx', expectedAction: "setEditingMember({ mode: 'new' })" }
  ];

  for (const { file, expectedAction } of actionablePages) {
    const code = fs.readFileSync(path.join(PAGES_DIR, file), 'utf8');
    assert.ok(
      code.includes('onAction=') && code.includes(expectedAction),
      `Actionable page ${file} must wire onAction with ${expectedAction}`
    );
  }

  // Dashboard is strictly read-only overview: must NOT wire onAction
  const dashboardCode = fs.readFileSync(path.join(PAGES_DIR, 'Dashboard.jsx'), 'utf8');
  assert.ok(!dashboardCode.includes('onAction='), 'Dashboard.jsx must omit onAction button');
});

test('Suite 5.2: App.jsx registers and lazy-loads all 15 views without orphan routes', () => {
  const appCode = fs.readFileSync(path.join(SRC_DIR, 'App.jsx'), 'utf8');

  const expectedViews = [
    'Dashboard',
    'Transactions',
    'Budgets',
    'SpendingTrends',
    'Accounts',
    'Settings',
    'Rules',
    'Categories',
    'HousingCalculator',
    'NetWorth',
    'MhaTracker',
    'Goals',
    'Upcoming',
    'RetirementCalculator',
    'Household'
  ];

  for (const viewName of expectedViews) {
    assert.ok(
      appCode.includes(`const ${viewName} = lazy(`),
      `App.jsx must lazy-load view ${viewName}`
    );
  }

  // Verify DesktopSidebar and RightDrawer are rendered in App.jsx shell
  assert.ok(appCode.includes('<DesktopSidebar'), 'App.jsx must render DesktopSidebar');
  assert.ok(appCode.includes('<RightDrawer'), 'App.jsx must render RightDrawer');
  assert.ok(appCode.includes('<BottomTabs'), 'App.jsx must render BottomTabs');
});
