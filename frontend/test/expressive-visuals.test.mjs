// frontend/test/expressive-visuals.test.mjs
// Verification suite for Requirements R3, R4, R5, and R6

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

test('R3: Obsidian canvas, frosted glass, and ambient glow tokens in index.css', () => {
  const css = fs.readFileSync(path.join(rootDir, 'src', 'index.css'), 'utf-8');

  // Verify obsidian canvas token #070c09
  assert.ok(css.includes('--bg:                #070c09;'), 'index.css must define obsidian canvas token #070c09');
  assert.ok(css.includes('--surface-glass:     rgba(13, 22, 18, 0.75);'), 'index.css must define --surface-glass token');
  assert.ok(css.includes('--border-specular:   inset 0 1px 1px rgba(255, 255, 255, 0.12);'), 'index.css must define --border-specular token');

  // Verify PageHero frosted glass & ambient glowing aura
  assert.ok(css.includes('backdrop-filter: blur(24px) saturate(180%);'), 'PageHero must have 24px blur and 180% saturation');
  assert.ok(css.includes('radial-gradient(circle at 50% -20%, rgba(16, 185, 129, 0.22)'), 'PageHero must have radial ambient glowing aura mesh');

  // Verify tabular numerals on hero metrics
  assert.ok(css.includes('font-variant-numeric: tabular-nums;'), 'Hero metrics must enforce tabular-nums typography');
  assert.ok(css.includes('font-feature-settings: "tnum" 1;'), 'Hero metrics must enforce tnum font feature');

  // Verify 24px M3 content card surfaces with 16px blur
  assert.ok(css.includes('.dashboard-card') && css.includes('border-radius: var(--radius-xl, 24px);'), 'Content cards must use 24px border radius');
  assert.ok(css.includes('backdrop-filter: blur(16px);'), 'Dark mode content cards must use 16px frosted glass blur');
});

test('R4: Mobile layout, responsive BottomTabs, safe area padding & status bar scrim', () => {
  const css = fs.readFileSync(path.join(rootDir, 'src', 'index.css'), 'utf-8');
  const bottomTabsCode = fs.readFileSync(path.join(rootDir, 'src', 'components', 'BottomTabs.jsx'), 'utf-8');
  const appCode = fs.readFileSync(path.join(rootDir, 'src', 'App.jsx'), 'utf-8');
  const pageHeroCode = fs.readFileSync(path.join(rootDir, 'src', 'components', 'PageHero.jsx'), 'utf-8');

  // Verify clamp typography on BottomTabs
  assert.ok(css.includes('font-size: clamp(0.62rem, 1.9vw, 0.72rem);'), 'BottomTabs must use clamp typography');
  assert.ok(css.includes('text-transform: none;'), 'BottomTabs must avoid uppercase to prevent text overflow');

  // Verify universal bottom safe padding
  assert.ok(css.includes('--universal-bottom-safe-space: calc(var(--bottom-tab-height) + env(safe-area-inset-bottom, 0px) + 24px);'), 'Universal bottom safe space must be declared');
  assert.ok(css.includes('padding-bottom: var(--universal-bottom-safe-space);'), 'Universal bottom safe space must be applied to views');

  // Verify edge-to-edge status bar safe area padding
  assert.ok(css.includes('padding: calc(env(safe-area-inset-top, 0px) +'), 'PageHero must pad for status bar notch');
  assert.ok(css.includes('.app-shell.has-page-hero'), 'AppShell must support has-page-hero');

  // Verify sub-route navigation keeping More active
  assert.ok(bottomTabsCode.includes('isMoreActive'), 'BottomTabs must compute isMoreActive');
  assert.ok(bottomTabsCode.includes('activeIndex = isPrimaryTab ? currentIndex : primaryTabs.length'), 'BottomTabs must dock slider to More when isMoreActive');

  // Verify frosted back button in PageHero
  assert.ok(pageHeroCode.includes('collapsedTrailing'), 'PageHero must support collapsedTrailing single-row actions');
  assert.ok(css.includes('.canopy-collapsed-btn'), 'index.css must style canopy-collapsed-btn');
});

test('R5: Local-first state & offline banner hygiene', () => {
  const offlineBannerCode = fs.readFileSync(path.join(rootDir, 'src', 'components', 'OfflineBanner.jsx'), 'utf-8');
  const settingsCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'Settings.jsx'), 'utf-8');
  const localApiCode = fs.readFileSync(path.join(rootDir, 'src', 'db', 'localApi.js'), 'utf-8');

  // Verify OfflineBanner suppression in local mode
  assert.ok(offlineBannerCode.includes('isLocalMode || isOnline || dismissed'), 'OfflineBanner must suppress warning in local storage mode');

  // Verify Settings quiet status badge
  assert.ok(settingsCode.includes("isLocal ? 'Local Device • Offline Ready' : 'Remote Server'"), 'Settings must render Local Device • Offline Ready badge');

  // Verify localApi fallbacks for household and preferences
  assert.ok(localApiCode.includes("requestUrl = '/api/preferences'"), 'localApi must alias /api/user/preferences');
  assert.ok(localApiCode.includes("pathname === '/api/household'"), 'localApi must handle /api/household fallback');
  assert.ok(localApiCode.includes("pathname === '/api/household/retirement-history'"), 'localApi must handle retirement-history fallback');
});

test('R6: Compact currency formatting, interactive charts & visual polish', async () => {
  const { formatCompactCurrency } = await import('../src/lib/formatters.js');
  const rulesCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'Rules.jsx'), 'utf-8');
  const accountsCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'Accounts.jsx'), 'utf-8');
  const transactionsCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'Transactions.jsx'), 'utf-8');
  const retCalcCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'RetirementCalculator.jsx'), 'utf-8');
  const trendsCode = fs.readFileSync(path.join(rootDir, 'src', 'pages', 'SpendingTrends.jsx'), 'utf-8');

  // Verify compact currency formatting
  assert.equal(formatCompactCurrency(4000000), '$4M', '4,000,000 formats as $4M');
  assert.equal(formatCompactCurrency(2000000), '$2M', '2,000,000 formats as $2M');
  assert.equal(formatCompactCurrency(500000), '$500K', '500,000 formats as $500K');
  assert.equal(formatCompactCurrency(1500000), '$1.5M', '1,500,000 formats as $1.5M');

  // Verify Rules Material Symbol icon and search placeholder
  assert.ok(rulesCode.includes('rule</span>'), 'Rules.jsx must use Material Symbol rule icon');
  assert.ok(rulesCode.includes('placeholder="Search rules..."'), 'Rules.jsx search placeholder must fit mobile viewports');

  // Verify Accounts duplicate button purge
  assert.ok(!accountsCode.includes('className="view-header" hidden'), 'Accounts.jsx must purge dead hidden view header');
  assert.ok(accountsCode.includes('accounts.length > 0 &&') && accountsCode.includes('<PageActionRow className="accounts-toolbar'), 'Accounts.jsx must only render PageActionRow when accounts exist');

  // Verify Transactions empty state & pagination alignment
  assert.ok(transactionsCode.includes('receipt_long</span>'), 'Transactions.jsx must use receipt_long icon');
  assert.ok(transactionsCode.includes('totalPages > 1 && ('), 'Transactions.jsx must omit pagination when only 1 page exists');

  // Verify RetirementCalculator and SpendingTrends touch scrubbers with haptics
  assert.ok(retCalcCode.includes("triggerHaptic('selection')"), 'RetirementCalculator must trigger haptic ticks while scrubbing');
  assert.ok(trendsCode.includes("triggerHaptic('selection')"), 'SpendingTrends must trigger haptic ticks while scrubbing');
});
