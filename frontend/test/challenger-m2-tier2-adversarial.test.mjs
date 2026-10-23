// frontend/test/challenger-m2-tier2-adversarial.test.mjs
// Adversarial Stress Harness for Challenger 1 - Transactions Tier 2 Controls
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

test('Adversarial T1: SearchField capsule geometry & clear button physics', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');
  const searchSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'SearchField.jsx'), 'utf8');

  // Verify capsule CSS rules
  const capsuleMatch = css.match(/\.search-field-capsule[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(capsuleMatch, 'Must define .search-field-capsule');
  assert.ok(capsuleMatch[1].includes('border-radius: 9999px'), 'Container must have border-radius: 9999px');

  const inputMatch = css.match(/\.search-field-capsule \.search-field-input[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(inputMatch, 'Must define .search-field-capsule .search-field-input');
  assert.ok(inputMatch[1].includes('border-radius: 9999px'), 'Input must have border-radius: 9999px');
  assert.ok(inputMatch[1].includes('height: 44px') || inputMatch[1].includes('min-height: 44px'), 'Input must be 44px high');
  assert.ok(inputMatch[1].includes('padding: 0 40px 0 42px'), 'Input must reserve 42px left for icon and 40px right for clear button');

  // Clear button positioning & Material Symbol
  const clearMatch = css.match(/\.search-field-capsule \.search-field-clear[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(clearMatch, 'Must define .search-field-clear');
  assert.ok(clearMatch[1].includes('position: absolute'), 'Clear button must be absolute positioned');
  assert.ok(clearMatch[1].includes('right: 10px'), 'Clear button must be inset 10px from right');

  // Clear button behavior in JSX
  assert.ok(searchSource.includes("onClick={() => onChange('')}"), 'Clear button must trigger onChange with empty string');
  assert.ok(searchSource.includes('material-symbols-rounded') && searchSource.includes('close'), 'Clear button must render close Material Symbol');
});

test('Adversarial T2: Instant typing and debounced sync in Transactions.jsx', () => {
  const txnSource = fs.readFileSync(path.join(PAGES_DIR, 'Transactions.jsx'), 'utf8');

  // Local state binding
  assert.ok(txnSource.includes('const [searchLocal, setSearchLocal] = useState(filters.q);'), 'Must define searchLocal state initialized with filters.q');
  assert.ok(txnSource.includes('setSearchLocal(filters.q)'), 'Must synchronize searchLocal when filters.q updates from external sources');

  // Synchronous keystroke update + trailing debounce in setSearch function
  const setSearchIndex = txnSource.indexOf('function setSearch(value)');
  assert.ok(setSearchIndex !== -1, 'Must define setSearch handler');
  const setSearchSnippet = txnSource.slice(setSearchIndex, setSearchIndex + 300);

  assert.ok(setSearchSnippet.includes('setSearchLocal(value);'), 'setSearch must immediately update local state synchronously');
  assert.ok(setSearchSnippet.includes('clearTimeout(searchDebounceRef.current)'), 'setSearch must cancel existing debounce timer');
  assert.ok(setSearchSnippet.includes('setTimeout('), 'setSearch must set new debounce timer');
  assert.ok(setSearchSnippet.includes('350'), 'Debounce timer must be 350ms');
  assert.ok(setSearchSnippet.includes('q: value.trim()'), 'Debounced payload must trim query string');

  // Cleanup on unmount
  assert.ok(txnSource.includes('if (searchDebounceRef.current) window.clearTimeout(searchDebounceRef.current);'), 'Must clear search debounce timer on unmount');
});

test('Adversarial T3: Tonal filter chip, tune icon, count badge & FilterSheet opening', () => {
  const txnSource = fs.readFileSync(path.join(PAGES_DIR, 'Transactions.jsx'), 'utf8');
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Filter button wiring in toolbar
  const buttonMatch = txnSource.match(/<button[\s\S]*?className=\{`txn-filter-button m3-filter-chip[\s\S]*?<\/button>/);
  assert.ok(buttonMatch, 'Must find filter button with m3-filter-chip class');
  const buttonCode = buttonMatch[0];

  assert.ok(buttonCode.includes("triggerHaptic('selection')"), 'Clicking filter chip must trigger selection haptic');
  assert.ok(buttonCode.includes('setFilterSheetOpen(true)'), 'Clicking filter chip must set filterSheetOpen to true');
  assert.ok(buttonCode.includes('<span className="material-symbols-rounded txn-filter-icon" aria-hidden="true">tune</span>'), 'Filter chip must render tune Material Symbol');
  assert.ok(buttonCode.includes('activeCount > 0'), 'Active count badge must check activeCount > 0');
  assert.ok(buttonCode.includes('m3-filter-badge') && buttonCode.includes('txn-filter-count'), 'Badge must use m3-filter-badge and txn-filter-count classes');

  // FilterSheet rendered conditionally
  const filterSheetMatch = txnSource.match(/\{filterSheetOpen\s*&&\s*\(\s*<FilterSheet[\s\S]*?\/>\s*\)\}/);
  assert.ok(filterSheetMatch, 'FilterSheet must be rendered when filterSheetOpen is true');

  // Filter chip styling in CSS
  const chipBlock = css.match(/\.m3-filter-chip[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(chipBlock, 'Must find .m3-filter-chip CSS rule');
  assert.ok(chipBlock[1].includes('border-radius: 9999px'), 'Filter chip must have full capsule border-radius: 9999px');
  assert.ok(chipBlock[1].includes('min-height: 44px') || chipBlock[1].includes('height: 44px'), 'Filter chip must have 44px touch target');

  // Badge styling
  const badgeBlock = css.match(/\.m3-filter-badge[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(badgeBlock, 'Must find .m3-filter-badge CSS rule');
  assert.ok(badgeBlock[1].includes('background: #ffffff'), 'Badge must have high-contrast white background');
  assert.ok(badgeBlock[1].includes('color: #064e3b'), 'Badge text must be emerald green #064e3b');
});

test('Adversarial T4: Viewport responsiveness & zero horizontal overflow verification', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Mobile layout (<768px): 2-row layout with 3-column action grid
  const actionsBlock = css.match(/\.canopy-tier2 \.txn-toolbar-actions[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(actionsBlock, 'Must style .canopy-tier2 .txn-toolbar-actions');
  assert.ok(actionsBlock[1].includes('grid-template-columns: repeat(3, minmax(0, 1fr))'), 'Actions grid must use repeat(3, minmax(0, 1fr)) to allow zero-width shrinking');

  // Desktop layout (>=768px): single row grid
  assert.ok(css.includes('@media (min-width: 768px)'), 'Must include @media (min-width: 768px)');
  assert.ok(css.includes('.canopy-tier2 .txn-toolbar {') || css.includes('.canopy-tier2 .txn-toolbar'), 'Must target .canopy-tier2 .txn-toolbar');
  assert.ok(css.includes('grid-template-columns: 1fr auto;'), 'Desktop toolbar must place SearchField on left (1fr) and actions on right (auto)');

  // Narrow mobile (<=380px): gap tightening
  const narrowIndex = css.indexOf('@media (max-width: 380px)');
  assert.ok(narrowIndex !== -1, 'Must include max-width: 380px media query');
  const narrowSnippet = css.slice(narrowIndex, narrowIndex + 400);
  assert.ok(narrowSnippet.includes('gap: 4px;'), 'Narrow mobile must tighten grid gap to 4px');

  // Ultra-narrow mobile (<=335px): label suppression prevents overflow at 320px
  const ultraNarrowIndex = css.indexOf('@media (max-width: 335px)');
  assert.ok(ultraNarrowIndex !== -1, 'Must include max-width: 335px media query');
  const ultraNarrowSnippet = css.slice(ultraNarrowIndex, ultraNarrowIndex + 300);
  assert.ok(ultraNarrowSnippet.includes('.canopy-tier2 .txn-control-label'), 'Ultra-narrow query must target .canopy-tier2 .txn-control-label');
  assert.ok(ultraNarrowSnippet.includes('display: none;'), 'Ultra-narrow query must hide text label to prevent horizontal overflow');
});
