// frontend/test/challenger-m2-tier2.test.mjs
// Empirical Verification & Adversarial Stress Harness for Milestone 2 (In-Header Actions & M3E Tier 2 Controls)
// Updated for authentic offline Material Symbols font rendering & zero-facade verification

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../..');
const SRC_DIR = path.resolve(ROOT_DIR, 'frontend/src');
const PAGES_DIR = path.resolve(SRC_DIR, 'pages');
const COMPONENTS_DIR = path.resolve(SRC_DIR, 'components');
const CSS_PATH = path.resolve(SRC_DIR, 'index.css');
const FONTS_DIR = path.resolve(SRC_DIR, 'assets/fonts');

test('Milestone 2 - Authentic Material Symbols Rounded local font asset & @font-face declaration', () => {
  const fontPath = path.join(FONTS_DIR, 'material-symbols-rounded.woff2');
  assert.ok(fs.existsSync(fontPath), 'material-symbols-rounded.woff2 must exist locally in frontend/src/assets/fonts/');

  const stats = fs.statSync(fontPath);
  assert.ok(stats.size > 200000, `material-symbols-rounded.woff2 must be a complete font file (>200KB), got ${stats.size} bytes`);

  // Verify WOFF2 magic header (0x77 0x4F 0x46 0x32 = "wOF2")
  const buffer = Buffer.alloc(4);
  const fd = fs.openSync(fontPath, 'r');
  fs.readSync(fd, buffer, 0, 4, 0);
  fs.closeSync(fd);
  assert.equal(buffer.toString('utf8'), 'wOF2', 'Font file must have valid WOFF2 binary signature');

  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Verify @font-face declaration in index.css
  const fontFaceMatch = css.match(/@font-face\s*\{[\s\S]*?font-family:\s*['"]Material Symbols Rounded['"][\s\S]*?\}/);
  assert.ok(fontFaceMatch, 'CSS must declare @font-face for Material Symbols Rounded');
  const fontFaceStyles = fontFaceMatch[0];
  assert.ok(fontFaceStyles.includes('material-symbols-rounded.woff2'), '@font-face must reference local material-symbols-rounded.woff2');
  assert.ok(fontFaceStyles.includes("format('woff2')"), "@font-face format must specify 'woff2'");
  assert.ok(fontFaceStyles.includes('font-display: block') || fontFaceStyles.includes('font-display: swap'), '@font-face must declare font-display');

  // Verify base .material-symbols-rounded class definition in index.css
  const baseClassMatch = css.match(/\.material-symbols-rounded\s*\{[\s\S]*?\}/);
  assert.ok(baseClassMatch, 'CSS must declare base .material-symbols-rounded class');
  const baseClassStyles = baseClassMatch[0];
  assert.ok(baseClassStyles.includes("font-family: 'Material Symbols Rounded'"), "Base class must set font-family: 'Material Symbols Rounded'");
  assert.ok(baseClassStyles.includes('display: inline-block'), 'Base class must set display: inline-block');
  assert.ok(
    baseClassStyles.includes('liga') || baseClassStyles.includes('font-feature-settings'),
    'Base class must enable ligature font features'
  );
});

test('Milestone 2 - SearchField component capsule variant, Material Symbols & collision prevention', () => {
  const searchSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'SearchField.jsx'), 'utf8');
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Variant prop & capsule class
  assert.ok(searchSource.includes("variant = 'default'"), 'SearchField must accept variant prop with default');
  assert.ok(searchSource.includes("isCapsule ? 'search-field-capsule' : ''"), 'SearchField must apply search-field-capsule class');

  // Search Material Symbol with aria-hidden
  assert.ok(searchSource.includes('material-symbols-rounded'), 'SearchField must use material-symbols-rounded');
  assert.ok(searchSource.includes('search'), 'SearchField must render Material Symbol search icon');
  assert.ok(searchSource.includes('search-field-symbol'), 'SearchField must include search-field-symbol class');
  assert.ok(searchSource.includes('aria-hidden="true"'), 'Search icon must have aria-hidden="true"');

  // Clear button Material Symbol
  assert.ok(searchSource.includes('search-field-clear'), 'SearchField must render search-field-clear button');
  assert.ok(searchSource.includes('aria-label="Clear search"'), 'Clear button must have accessible aria-label');
  assert.ok(searchSource.includes('close'), 'Clear button must render close icon');

  // CSS Icon dimensions and padding collision prevention
  const iconRule = css.match(/\.search-field-capsule \.search-field-icon[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(iconRule, 'CSS must style .search-field-capsule .search-field-icon');
  assert.ok(iconRule[1].includes('position: absolute'), 'Icon must be positioned absolutely');
  assert.ok(iconRule[1].includes('left: 14px'), 'Icon must be inset 14px from left');
  assert.ok(iconRule[1].includes('width: 20px'), 'Icon must be constrained to 20px width');
  assert.ok(iconRule[1].includes('height: 20px'), 'Icon must be constrained to 20px height');
  assert.ok(iconRule[1].includes('overflow: hidden'), 'Icon must prevent overflow clipping collision');

  const clearIconRule = css.match(/\.search-field-capsule \.search-field-clear \.material-symbols-rounded[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(clearIconRule, 'CSS must style clear button material symbol');
  assert.ok(clearIconRule[1].includes('width: 18px'), 'Clear icon must be constrained to 18px width');
  assert.ok(clearIconRule[1].includes('height: 18px'), 'Clear icon must be constrained to 18px height');
  assert.ok(clearIconRule[1].includes('overflow: hidden'), 'Clear icon must have overflow: hidden');

  const inputRule = css.match(/\.search-field-capsule \.search-field-input[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(inputRule, 'CSS must style capsule search-field-input');
  assert.ok(inputRule[1].includes('padding: 0 40px 0 42px'), 'Input must reserve 42px left padding to prevent collision');
});

test('Milestone 2 - Transactions.jsx canopy Tier 2 toolbar & M3E controls', () => {
  const txnSource = fs.readFileSync(path.join(PAGES_DIR, 'Transactions.jsx'), 'utf8');
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // SearchField capsule inside canopy toolbar
  const toolbarMatch = txnSource.match(/toolbar=\{[\s\S]*?\)\}\s*\n\s*\/>/);
  assert.ok(toolbarMatch, 'Transactions.jsx must provide a toolbar prop to PageHero');
  const toolbarContent = toolbarMatch[0];

  assert.ok(toolbarContent.includes('<SearchField'), 'Toolbar must render SearchField');
  assert.ok(toolbarContent.includes('variant="capsule"'), 'SearchField inside canopy must have variant="capsule"');
  assert.ok(toolbarContent.includes('value={searchLocal}'), 'SearchField must bind to searchLocal');
  assert.ok(toolbarContent.includes('onChange={setSearch}'), 'SearchField must bind to setSearch');

  // Synchronous typing + 350ms debounce
  assert.ok(txnSource.includes('setSearchLocal(value)'), 'setSearch must synchronously update searchLocal');
  assert.ok(txnSource.includes('350'), 'setSearch must debounce URL update with 350ms timer');

  // M3E Tonal Filter Chip
  assert.ok(toolbarContent.includes('m3-filter-chip'), 'Toolbar must include m3-filter-chip class');
  assert.ok(toolbarContent.includes('txn-filter-button'), 'Toolbar must include txn-filter-button class');
  assert.ok(toolbarContent.includes('tune</span>'), 'Filter button must use modern Material Symbol tune icon');
  assert.ok(toolbarContent.includes('aria-hidden="true"'), 'Filter icon must have aria-hidden="true"');
  assert.ok(toolbarContent.includes('m3-filter-badge'), 'Filter button must render m3-filter-badge');
  assert.ok(toolbarContent.includes('txn-filter-count'), 'Filter button must render txn-filter-count');
  assert.ok(toolbarContent.includes("triggerHaptic('selection')"), 'Filter button must trigger selection haptic');
  assert.ok(toolbarContent.includes('setFilterSheetOpen(true)'), 'Filter button must open FilterSheet');

  // Filter chip styling
  const chipBlock = css.match(/\.m3-filter-chip[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(chipBlock, 'CSS must style .m3-filter-chip');
  assert.ok(chipBlock[1].includes('border-radius: 9999px'), 'Filter chip must have full capsule border-radius: 9999px');
  assert.ok(chipBlock[1].includes('min-height: 44px') || chipBlock[1].includes('height: 44px'), 'Filter chip must have 44px touch target');
});

test('Milestone 2 - PeriodNav.jsx connected calendar pill, chevrons & selection haptics', () => {
  const periodSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'PeriodNav.jsx'), 'utf8');
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Connected pill logic
  assert.ok(
    periodSource.includes("variant === 'hero' || variant === 'connected'"),
    'PeriodNav must identify hero and connected variants'
  );
  assert.ok(
    periodSource.includes('period-nav-connected m3-period-nav'),
    'PeriodNav must apply period-nav-connected and m3-period-nav classes'
  );

  // Chevron Material Symbols with aria-hidden
  assert.ok(periodSource.includes('chevron_left</span>'), 'PeriodNav must use chevron_left Material Symbol');
  assert.ok(periodSource.includes('chevron_right</span>'), 'PeriodNav must use chevron_right Material Symbol');
  assert.ok(periodSource.includes('period-nav-prev'), 'Previous button must have period-nav-prev class');
  assert.ok(periodSource.includes('period-nav-next'), 'Next button must have period-nav-next class');
  assert.ok(periodSource.includes('aria-label={previousLabel}'), 'Previous button must have accessible aria-label');
  assert.ok(periodSource.includes('aria-label={nextLabel}'), 'Next button must have accessible aria-label');

  // Chevron CSS sizing & overflow clipping
  const arrowBlock = css.match(/\.period-nav-connected \.period-nav-arrow[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(arrowBlock, 'CSS must style .period-nav-connected .period-nav-arrow');
  assert.ok(arrowBlock[1].includes('width: 44px'), 'Chevrons must have fixed 44px width');
  assert.ok(arrowBlock[1].includes('height: 44px'), 'Chevrons must have fixed 44px height');
  assert.ok(arrowBlock[1].includes('overflow: hidden'), 'Chevrons must have overflow: hidden');

  const arrowIconBlock = css.match(/\.period-nav-connected \.period-nav-arrow \.material-symbols-rounded[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(arrowIconBlock, 'CSS must style chevron material symbol');
  assert.ok(arrowIconBlock[1].includes('width: 24px'), 'Chevron icon must be constrained to 24px width');
  assert.ok(arrowIconBlock[1].includes('height: 24px'), 'Chevron icon must be constrained to 24px height');
  assert.ok(arrowIconBlock[1].includes('overflow: hidden'), 'Chevron icon must have overflow: hidden');

  // Selection haptics
  assert.ok(periodSource.includes("import { triggerHaptic } from '../lib/haptics.js'"), 'PeriodNav must import triggerHaptic');
  assert.ok(periodSource.includes("triggerHaptic('selection')"), 'PeriodNav must trigger selection haptic on arrow taps');
});

test('Milestone 2 - Budgets.jsx connects PeriodNav to canopy header Tier 2', () => {
  const budgetSource = fs.readFileSync(path.join(PAGES_DIR, 'Budgets.jsx'), 'utf8');

  const toolbarMatch = budgetSource.match(/toolbar=\{[\s\S]*?\n\s*\/>/);
  assert.ok(toolbarMatch, 'Budgets.jsx must provide a toolbar prop to PageHero');
  const toolbarContent = toolbarMatch[0];

  assert.ok(toolbarContent.includes('<PeriodNav'), 'Budgets toolbar must render PeriodNav');
  assert.ok(toolbarContent.includes('variant="hero"'), 'PeriodNav in Budgets must pass variant="hero"');
  assert.ok(toolbarContent.includes('value={selectedMonth}'), 'PeriodNav must bind value to selectedMonth');
  assert.ok(toolbarContent.includes('onPrev='), 'PeriodNav must wire onPrev');
  assert.ok(toolbarContent.includes('onNext='), 'PeriodNav must wire onNext');
  assert.ok(toolbarContent.includes('onJump={goToMonth}'), 'PeriodNav must wire onJump to goToMonth');
});

test('Milestone 2 - index.css Tier 2 controls, styling & responsive layout', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // SearchField capsule styles
  assert.ok(css.includes('.search-field-capsule'), 'CSS must define .search-field-capsule');
  assert.ok(css.includes('.canopy-tier2 .search-field'), 'CSS must define .canopy-tier2 .search-field');
  const searchBlock = css.match(/\.search-field-capsule \.search-field-input[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(searchBlock, 'CSS must style capsule search-field-input');
  assert.ok(searchBlock[1].includes('border-radius: 9999px'), 'Capsule search input must have border-radius: 9999px');
  assert.ok(searchBlock[1].includes('rgba(255, 255, 255, 0.16)'), 'Capsule search input must have translucent glass background');

  // M3E Tonal Filter Chip styles
  assert.ok(css.includes('.m3-filter-chip'), 'CSS must define .m3-filter-chip');
  const filterBlock = css.match(/\.m3-filter-chip[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(filterBlock, 'CSS must style .m3-filter-chip');
  assert.ok(filterBlock[1].includes('border-radius: 9999px'), 'Filter chip must have border-radius: 9999px');
  assert.ok(filterBlock[1].includes('min-height: 44px') || filterBlock[1].includes('height: 44px'), 'Filter chip must have min-height: 44px');
  assert.ok(css.includes('.m3-filter-badge'), 'CSS must define .m3-filter-badge');

  // Connected PeriodNav calendar pill styles
  assert.ok(css.includes('.period-nav-connected'), 'CSS must define .period-nav-connected');
  const periodBlock = css.match(/\.period-nav-connected[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(periodBlock, 'CSS must style .period-nav-connected');
  assert.ok(periodBlock[1].includes('border-radius: 9999px'), 'PeriodNav connected pill must have border-radius: 9999px');
  assert.ok(periodBlock[1].includes('justify-content: space-between'), 'PeriodNav connected pill must justify space-between');
  assert.ok(periodBlock[1].includes('overflow: hidden'), 'PeriodNav connected pill must have overflow: hidden');

  // Integrated Arrow chevrons
  assert.ok(css.includes('.period-nav-connected .period-nav-prev'), 'CSS must style left chevron');
  assert.ok(css.includes('.period-nav-connected .period-nav-next'), 'CSS must style right chevron');
  assert.ok(css.includes('transform: scale(0.92)'), 'PeriodNav arrows must have scale(0.92) active press state');

  // Responsive mobile layout
  assert.ok(css.includes('.canopy-tier2 .txn-toolbar'), 'CSS must style canopy-tier2 txn-toolbar');
  assert.ok(css.includes('@media (min-width: 768px)'), 'CSS must include desktop/tablet breakpoint');
  assert.ok(css.includes('@media (max-width: 380px)'), 'CSS must include narrow mobile breakpoint');
  assert.ok(css.includes('@media (max-width: 335px)'), 'CSS must include ultra-narrow mobile breakpoint');

  // Reduced motion
  assert.ok(css.includes('.m3-filter-chip'), 'Reduced motion must include .m3-filter-chip');
  assert.ok(css.includes('.period-nav-arrow'), 'Reduced motion must include .period-nav-arrow');
});

test('Milestone 2 - Vite production build emits Material Symbols font asset', () => {
  const distAssetsDir = path.resolve(ROOT_DIR, 'frontend/dist/assets');
  if (fs.existsSync(distAssetsDir)) {
    const distFiles = fs.readdirSync(distAssetsDir);
    const fontEmitted = distFiles.some((f) => f.startsWith('material-symbols-rounded') && f.endsWith('.woff2'));
    assert.ok(fontEmitted, 'Vite production build must emit material-symbols-rounded-[hash].woff2 in dist/assets/');
  }
});

test('Milestone 2 - PageHero.jsx accessible Material Symbols icons with aria-hidden', () => {
  const pageHeroSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'PageHero.jsx'), 'utf8');
  assert.ok(
    !pageHeroSource.includes('arrow_back'),
    'PageHero must not render an in-header arrow_back button'
  );
  assert.ok(
    pageHeroSource.includes('<span className="material-symbols-rounded" aria-hidden="true">{actionIcon}</span>'),
    'PageHero action button icon must declare aria-hidden="true"'
  );
});

