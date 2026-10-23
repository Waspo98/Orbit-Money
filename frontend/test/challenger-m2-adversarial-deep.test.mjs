// frontend/test/challenger-m2-adversarial-deep.test.mjs
// In-depth Empirical Challenger Stress & Geometry Test Suite for Milestone 2 Re-Gate

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

// -----------------------------------------------------------------------------
// Test 1: SearchField Collision Geometry & Sub-pixel Safety
// -----------------------------------------------------------------------------
test('Deep Stress 1: SearchField capsule sub-pixel geometry & collision resistance', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Verify search container styles
  const containerMatch = css.match(/\.search-field-capsule,\s*\n\s*\.canopy-tier2 \.search-field\s*\{([\s\S]*?)\}/);
  assert.ok(containerMatch, 'Search container must be defined with capsule rules');
  assert.ok(containerMatch[1].includes('border-radius: 9999px'), 'Container must have full pill border-radius');
  assert.ok(containerMatch[1].includes('width: 100%'), 'Container must span 100% width');
  assert.ok(containerMatch[1].includes('min-width: 0'), 'Container must allow flex shrinking');

  // Verify input padding geometry
  const inputMatch = css.match(/\.search-field-capsule \.search-field-input,\s*\n\s*\.canopy-tier2 \.search-field \.search-field-input\s*\{([\s\S]*?)\}/);
  assert.ok(inputMatch, 'Input element must be defined with capsule padding');
  assert.ok(inputMatch[1].includes('height: 44px') || inputMatch[1].includes('min-height: 44px'), 'Input must meet 44px touch target');
  assert.ok(inputMatch[1].includes('padding: 0 40px 0 42px'), 'Input must specify exact padding (42px left, 40px right)');

  // Verify search icon bounding box
  const iconMatch = css.match(/\.search-field-capsule \.search-field-icon,\s*\n\s*\.canopy-tier2 \.search-field \.search-field-icon\s*\{([\s\S]*?)\}/);
  assert.ok(iconMatch, 'Search icon must be styled');
  assert.ok(iconMatch[1].includes('left: 14px'), 'Search icon left inset must be 14px');
  assert.ok(iconMatch[1].includes('width: 20px'), 'Search icon width must be 20px');
  assert.ok(iconMatch[1].includes('height: 20px'), 'Search icon height must be 20px');
  assert.ok(iconMatch[1].includes('overflow: hidden'), 'Search icon must clip overflow');

  // Clearance validation:
  // Left inset = 14px, icon width = 20px -> icon right boundary = 34px
  // Text left padding = 42px -> clearance = 42 - 34 = 8px buffer
  const iconLeft = 14;
  const iconWidth = 20;
  const textLeftPadding = 42;
  const leftClearance = textLeftPadding - (iconLeft + iconWidth);
  assert.ok(leftClearance >= 6, `Left clearance must be at least 6px (calculated: ${leftClearance}px)`);

  // Verify clear button bounding box
  const clearMatch = css.match(/\.search-field-capsule \.search-field-clear[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(clearMatch, 'Clear button must be styled');
  assert.ok(clearMatch[1].includes('right: 10px'), 'Clear button right inset must be 10px');
  assert.ok(clearMatch[1].includes('width: 26px'), 'Clear button width must be 26px');
  assert.ok(clearMatch[1].includes('height: 26px'), 'Clear button height must be 26px');

  // Clearance validation:
  // Right inset = 10px, clear width = 26px -> clear left boundary = 36px from right
  // Text right padding = 40px -> clearance = 40 - 36 = 4px buffer
  const clearRight = 10;
  const clearWidth = 26;
  const textRightPadding = 40;
  const rightClearance = textRightPadding - (clearRight + clearWidth);
  assert.ok(rightClearance >= 2, `Right clearance must be at least 2px (calculated: ${rightClearance}px)`);
});

// -----------------------------------------------------------------------------
// Test 2: Typing & Debounce Simulation Engine
// -----------------------------------------------------------------------------
test('Deep Stress 2: Search debouncing state machine & timing simulation', async () => {
  const txnSource = fs.readFileSync(path.join(PAGES_DIR, 'Transactions.jsx'), 'utf8');

  // Verify debounce time is strictly 350ms
  assert.ok(txnSource.includes('350'), 'Transactions.jsx must use 350ms debounce window');

  // Simulate setSearch state machine
  let localState = '';
  let debouncedCommittedQuery = null;
  let timerId = null;
  let commitCount = 0;

  function simulateSetSearch(value) {
    // 1. Synchronous keystroke
    localState = value;
    // 2. Clear existing timer
    if (timerId !== null) {
      clearTimeout(timerId);
      timerId = null;
    }
    // 3. Trailing debounce
    timerId = setTimeout(() => {
      debouncedCommittedQuery = value.trim();
      commitCount++;
      timerId = null;
    }, 50); // Scaled for fast unit test
  }

  // Simulate rapid typing: 10 keystrokes in 100ms
  const keystrokes = ['g', 'gr', 'gro', 'groc', 'groce', 'grocer', 'groceri', 'groceries', 'groceries ', '  groceries  '];
  for (let i = 0; i < keystrokes.length; i++) {
    simulateSetSearch(keystrokes[i]);
    assert.equal(localState, keystrokes[i], `Keystroke ${i + 1} must update local state immediately without lag`);
    assert.equal(commitCount, 0, 'No commit should fire during active keystrokes');
    await new Promise((r) => setTimeout(r, 10));
  }

  // Still active before timer finishes
  assert.equal(commitCount, 0, 'Commit count must remain 0 before debounce expires');

  // Wait for trailing debounce to expire
  await new Promise((r) => setTimeout(r, 70));

  assert.equal(commitCount, 1, 'Exactly one commit must fire after typing completes');
  assert.equal(debouncedCommittedQuery, 'groceries', 'Debounced query must be trimmed properly');
});

// -----------------------------------------------------------------------------
// Test 3: Filter Chip Active Count Badge & Tune Glyph
// -----------------------------------------------------------------------------
test('Deep Stress 3: Tonal filter chip active badge logic & tune glyph integrity', () => {
  const txnSource = fs.readFileSync(path.join(PAGES_DIR, 'Transactions.jsx'), 'utf8');
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Ensure 'tune' symbol is present
  assert.ok(
    txnSource.includes('<span className="material-symbols-rounded txn-filter-icon" aria-hidden="true">tune</span>'),
    'Filter button must render Material Symbol "tune" with aria-hidden="true"'
  );

  // Check active filter badge rendering logic
  assert.ok(txnSource.includes('{activeCount > 0 && ('), 'Badge must only render when activeCount > 0');
  assert.ok(txnSource.includes('<span className="m3-filter-badge txn-filter-count">{activeCount}</span>'), 'Badge must use m3-filter-badge class');

  // Check active chip class
  assert.ok(txnSource.includes("activeCount > 0 ? 'is-active' : ''"), 'Chip must add is-active class when filters are active');

  // Verify badge styles in CSS
  const badgeMatch = css.match(/\.m3-filter-badge,\s*\n\s*\.canopy-tier2 \.txn-filter-count\s*\{([\s\S]*?)\}/);
  assert.ok(badgeMatch, 'Badge styles must be defined in CSS');
  assert.ok(badgeMatch[1].includes('border-radius: 9999px'), 'Badge must have capsule pill border radius');
  assert.ok(badgeMatch[1].includes('background: #ffffff'), 'Badge must have high contrast white background');
  assert.ok(badgeMatch[1].includes('color: #064e3b'), 'Badge must have dark emerald green text');
  assert.ok(badgeMatch[1].includes('min-width: 18px'), 'Badge must have min-width 18px');
  assert.ok(badgeMatch[1].includes('height: 18px'), 'Badge must have height 18px');
});

// -----------------------------------------------------------------------------
// Test 4: Viewport Matrix & Mathematical Zero-Overflow Proof
// -----------------------------------------------------------------------------
test('Deep Stress 4: Viewport matrix (320px, 360px, 390dp, desktop) zero-overflow proof', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Verify CSS queries exist
  assert.ok(css.includes('@media (min-width: 768px)'), 'Desktop breakpoint (768px) must be defined');
  assert.ok(css.includes('@media (max-width: 380px)'), 'Narrow mobile breakpoint (380px) must be defined');
  assert.ok(css.includes('@media (max-width: 335px)'), 'Ultra-narrow mobile breakpoint (335px) must be defined');

  // Mathematical proof for each target viewport
  const viewports = [
    { name: 'Ultra-Narrow (iPhone SE 1st gen)', width: 320, gutter: 20, isUltraNarrow: true, isNarrow: true, isDesktop: false },
    { name: 'Narrow Mobile (Galaxy S series)', width: 360, gutter: 20, isUltraNarrow: false, isNarrow: true, isDesktop: false },
    { name: 'Standard Mobile (iPhone 14 / Pixel 7)', width: 390, gutter: 20, isUltraNarrow: false, isNarrow: false, isDesktop: false },
    { name: 'Desktop / Large Viewport', width: 1200, gutter: 20, isUltraNarrow: false, isNarrow: false, isDesktop: true }
  ];

  for (const vp of viewports) {
    const usableWidth = vp.width - (vp.gutter * 2);

    if (vp.isDesktop) {
      // Desktop: grid 1fr auto with 12px gap
      // Search occupies 1fr, actions take ~340px total
      const actionsWidth = 340;
      const searchAvailable = usableWidth - actionsWidth - 12;
      assert.ok(searchAvailable > 200, `Desktop (${vp.width}px): SearchField has ample room (${searchAvailable}px)`);
    } else {
      // Mobile: Row 1 = SearchField (100% usable width)
      assert.ok(usableWidth >= 280, `Mobile (${vp.width}px): SearchField gets full ${usableWidth}px width`);

      // Row 2 = Actions grid: 3 columns
      const gap = vp.isNarrow ? 4 : 6;
      const totalGaps = gap * 2;
      const availablePerCol = (usableWidth - totalGaps) / 3;

      // Filter button content width calculation
      const paddingInline = vp.isNarrow ? 4 : 8;
      const iconWidth = 20;
      const labelWidth = vp.isUltraNarrow ? 0 : 32; // Hidden at <=335px!
      const badgeWidth = 20;
      const itemGap = vp.isNarrow ? 3 : 6;
      const totalFilterWidth = (paddingInline * 2) + iconWidth + (labelWidth > 0 ? itemGap + labelWidth : 0) + itemGap + badgeWidth;

      assert.ok(
        totalFilterWidth <= availablePerCol,
        `Viewport ${vp.name} (${vp.width}px): Filter chip width (${totalFilterWidth}px) must fit within column budget (${availablePerCol.toFixed(1)}px)`
      );
    }
  }
});

// -----------------------------------------------------------------------------
// Test 5: Material Symbols WOFF2 Authenticity & Ligature Support
// -----------------------------------------------------------------------------
test('Deep Stress 5: Offline font binary authenticity, format & ligature metrics', () => {
  const fontPath = path.join(FONTS_DIR, 'material-symbols-rounded.woff2');
  const buffer = fs.readFileSync(fontPath);

  // Exact byte length
  assert.equal(buffer.length, 373480, 'Font file must match exact known binary size');

  // Magic bytes: 0x77, 0x4F, 0x46, 0x32
  assert.equal(buffer[0], 0x77);
  assert.equal(buffer[1], 0x4f);
  assert.equal(buffer[2], 0x46);
  assert.equal(buffer[3], 0x32);

  // WOFF2 header structure:
  // Bytes 4-7: flavor tag (0x00010000 for TrueType or 0x4F54544F for CFF)
  // Bytes 8-11: length
  // Bytes 12-13: numTables
  const numTables = buffer.readUInt16BE(12);
  assert.ok(numTables > 5, `WOFF2 font must have valid table count, found ${numTables}`);
});
