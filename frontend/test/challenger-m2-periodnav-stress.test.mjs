// frontend/test/challenger-m2-periodnav-stress.test.mjs
// Adversarial Stress Harness for Budgets Tier 2 PeriodNav Calendar Pill
// Executed by Challenger 2

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
const PAGES_DIR = path.resolve(SRC_DIR, 'pages');
const CSS_PATH = path.resolve(SRC_DIR, 'index.css');

test('Adversarial 1: Single continuous capsule container geometry in index.css', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Verify selector grouping covering both .period-nav-connected and canopy-tier2
  const connectedBlockMatch = css.match(/\.period-nav-connected[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(connectedBlockMatch, 'Must find .period-nav-connected CSS rule');
  const connectedStyles = connectedBlockMatch[1];

  // Must be single continuous capsule
  assert.ok(
    connectedStyles.includes('border-radius: 9999px'),
    'Container must define border-radius: 9999px for continuous capsule shape'
  );
  assert.ok(
    connectedStyles.includes('overflow: hidden'),
    'Container must have overflow: hidden to clip child boundaries inside the capsule'
  );
  assert.ok(
    connectedStyles.includes('height: 44px') && connectedStyles.includes('min-height: 44px'),
    'Container must enforce fixed 44px height to prevent vertical layout shifts'
  );
  assert.ok(
    connectedStyles.includes('max-width: 320px'),
    'Container must constrain max-width to 320px for mobile ergonomics'
  );
  assert.ok(
    connectedStyles.includes('margin: 0 auto'),
    'Container must center horizontally via margin: 0 auto'
  );
  assert.ok(
    connectedStyles.includes('backdrop-filter: blur(12px)'),
    'Container must apply M3 frosted glass backdrop blur'
  );
});

test('Adversarial 2: Previous/next chevrons integrated seamlessly into capsule pill', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Integrated chevron buttons have no independent borders, transparent background, fixed dimensions
  const arrowBlockMatch = css.match(/\.period-nav-connected \.period-nav-arrow[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(arrowBlockMatch, 'Must find .period-nav-connected .period-nav-arrow CSS rule');
  const arrowStyles = arrowBlockMatch[1];

  assert.ok(arrowStyles.includes('width: 44px'), 'Chevrons must have fixed 44px width');
  assert.ok(arrowStyles.includes('height: 44px'), 'Chevrons must have fixed 44px height');
  assert.ok(arrowStyles.includes('border: 0'), 'Chevrons must have border: 0 to integrate seamlessly into capsule');
  assert.ok(arrowStyles.includes('background: transparent'), 'Chevrons must have transparent background');
  assert.ok(arrowStyles.includes('flex-shrink: 0'), 'Chevrons must not shrink on narrow viewports');

  // Pill curvature integration: left chevron rounded on left, right chevron rounded on right
  const prevBlock = css.match(/\.period-nav-connected \.period-nav-prev[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(prevBlock, 'Must find .period-nav-prev rule');
  assert.ok(prevBlock[1].includes('border-radius: 9999px 0 0 9999px'), 'Previous button must match left capsule curve');

  const nextBlock = css.match(/\.period-nav-connected \.period-nav-next[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(nextBlock, 'Must find .period-nav-next rule');
  assert.ok(nextBlock[1].includes('border-radius: 0 9999px 9999px 0'), 'Next button must match right capsule curve');

  // Active tactile press feedback
  const activeBlock = css.match(/\.period-nav-connected \.period-nav-arrow:active[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(activeBlock, 'Must find .period-nav-arrow:active rule');
  assert.ok(activeBlock[1].includes('transform: scale(0.92)'), 'Chevrons must scale to 0.92 on active press');

  // Disabled chevron styling
  const disabledBlock = css.match(/\.period-nav-connected \.period-nav-arrow:disabled[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(disabledBlock, 'Must find .period-nav-arrow:disabled rule');
  assert.ok(disabledBlock[1].includes('opacity: 0.35'), 'Disabled chevron must have reduced opacity');
  assert.ok(disabledBlock[1].includes('cursor: not-allowed'), 'Disabled chevron must have cursor: not-allowed');
});

test('Adversarial 3: Centered month picker seamless layout without shifts', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Label wrapper flex-1 and seamless integration
  const labelWrapMatch = css.match(/\.period-nav-connected \.period-nav-label-wrap[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(labelWrapMatch, 'Must find .period-nav-label-wrap rule');
  const labelWrapStyles = labelWrapMatch[1];

  assert.ok(labelWrapStyles.includes('flex: 1'), 'Label wrap must flex to fill remaining capsule space');
  assert.ok(labelWrapStyles.includes('min-width: 0'), 'Label wrap must have min-width: 0 to prevent flex overflow');
  assert.ok(labelWrapStyles.includes('border: 0'), 'Label wrap must have border: 0');
  assert.ok(labelWrapStyles.includes('background: transparent'), 'Label wrap must have background: transparent');

  // Select trigger inside pill
  const selectTriggerMatch = css.match(/\.period-nav-connected \.period-nav-select \.app-select-trigger[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(selectTriggerMatch, 'Must find app-select-trigger inside period-nav rule');
  const selectTriggerStyles = selectTriggerMatch[1];

  assert.ok(selectTriggerStyles.includes('min-height: 44px'), 'Select trigger must match 44px container height');
  assert.ok(selectTriggerStyles.includes('border: 0'), 'Select trigger must have border: 0');
  assert.ok(selectTriggerStyles.includes('color: #ffffff'), 'Select trigger must render high-contrast white text');
  assert.ok(selectTriggerStyles.includes('justify-content: center'), 'Select trigger must center text');
});

test('Adversarial 4: Selection haptic triggers on all interactive elements in PeriodNav.jsx', () => {
  const source = fs.readFileSync(path.join(COMPONENTS_DIR, 'PeriodNav.jsx'), 'utf8');

  // Must import triggerHaptic
  assert.ok(
    source.includes("import { triggerHaptic } from '../lib/haptics.js'"),
    'PeriodNav must import triggerHaptic from haptics lib'
  );

  // Chevron prev click must trigger haptic
  const prevMatch = source.match(/className="btn-icon period-nav-arrow period-nav-prev"[\s\S]*?onClick=\{[\s\S]*?\}/);
  assert.ok(prevMatch, 'Previous button must have onClick handler');
  assert.ok(
    prevMatch[0].includes("triggerHaptic('selection')"),
    'Previous chevron click must trigger selection haptic'
  );

  // Chevron next click must trigger haptic, guard against disabled canGoForward
  const nextMatch = source.match(/className="btn-icon period-nav-arrow period-nav-next"[\s\S]*?onClick=\{[\s\S]*?\}/);
  assert.ok(nextMatch, 'Next button must have onClick handler');
  assert.ok(
    nextMatch[0].includes("triggerHaptic('selection')"),
    'Next chevron click must trigger selection haptic'
  );
  assert.ok(
    nextMatch[0].includes('if (!canGoForward) return;'),
    'Next chevron click must check canGoForward before triggering haptic or onNext'
  );

  // Month selection in AppSelect must trigger haptic
  const selectMatch = source.match(/<AppSelect[\s\S]*?\/>/);
  assert.ok(selectMatch, 'Must render AppSelect');
  assert.ok(
    selectMatch[0].includes("triggerHaptic('selection')"),
    'AppSelect onChange must trigger selection haptic on month jump'
  );
  assert.ok(
    selectMatch[0].includes('onJump?.(val)'),
    'AppSelect onChange must forward selected value to onJump'
  );
});

test('Adversarial 5: Budgets.jsx month jumping without page reload or jumpy layout shift', () => {
  const budgetSource = fs.readFileSync(path.join(PAGES_DIR, 'Budgets.jsx'), 'utf8');

  // Must wire PeriodNav in PageHero toolbar
  assert.ok(
    budgetSource.includes('<PeriodNav'),
    'Budgets.jsx must instantiate PeriodNav'
  );
  assert.ok(
    budgetSource.includes('variant="hero"'),
    'Budgets.jsx must pass variant="hero"'
  );
  assert.ok(
    budgetSource.includes('menuPlacement="page-center"'),
    'Budgets.jsx must specify menuPlacement="page-center" for centered modal dropdown on mobile'
  );

  // goToMonth updates URL query without reloading the page
  const goToMonthMatch = budgetSource.match(/function goToMonth\(month\)\s*\{([\s\S]*?)\}/);
  assert.ok(goToMonthMatch, 'Must define goToMonth function');
  assert.ok(
    goToMonthMatch[1].includes('setSearchParams(next)'),
    'goToMonth must use setSearchParams for client-side state update without page reload'
  );
  assert.ok(
    !goToMonthMatch[1].includes('window.location.reload'),
    'goToMonth must NOT invoke window.location.reload'
  );
  assert.ok(
    !goToMonthMatch[1].includes('window.location.href'),
    'goToMonth must NOT invoke window.location.href assignment'
  );

  // load() distinguishes firstLoad vs refreshing to avoid blank loading layout thrashing
  const loadMatch = budgetSource.match(/async function load\(\)\s*\{([\s\S]*?)\}/);
  assert.ok(loadMatch, 'Must define load function');
  assert.ok(
    loadMatch[1].includes('const firstLoad = data == null;'),
    'load must detect whether data has already been loaded'
  );
  assert.ok(
    loadMatch[1].includes('if (firstLoad) setLoading(true);') &&
    loadMatch[1].includes('else setRefreshing(true);'),
    'load must use setRefreshing(true) on month jump, keeping existing data rendered and preventing full-screen spinner flash'
  );
});

test('Adversarial 6: Year wrapping and month boundary date arithmetic', async () => {
  const { addMonthsToLocalMonth, formatMonthKeyLabel } = await import('../src/lib/localDate.js');

  // Year boundary backwards
  assert.equal(addMonthsToLocalMonth('2026-01', -1), '2025-12', 'January minus 1 month must wrap to December of previous year');
  // Year boundary forwards
  assert.equal(addMonthsToLocalMonth('2025-12', 1), '2026-01', 'December plus 1 month must wrap to January of next year');
  // Multi-year leap
  assert.equal(addMonthsToLocalMonth('2026-06', -18), '2024-12', 'June 2026 minus 18 months must be December 2024');
  assert.equal(addMonthsToLocalMonth('2026-06', 12), '2027-06', 'June 2026 plus 12 months must be June 2027');

  // Month label formatting
  assert.equal(formatMonthKeyLabel('2026-04'), 'April 2026');
  assert.equal(formatMonthKeyLabel('2026-01'), 'January 2026');
  assert.equal(formatMonthKeyLabel('2026-12'), 'December 2026');
});

test('Adversarial 7: Reduced-motion accessibility check', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Target the Tier 2 reduced-motion block around line 3566
  const tier2ReducedMotionMatch = css.match(/@media \(prefers-reduced-motion: reduce\) \{[\s\S]*?\.period-nav-arrow[\s\S]*?\}/);
  assert.ok(tier2ReducedMotionMatch, 'CSS must have @media (prefers-reduced-motion: reduce) block covering .period-nav-arrow');
  const reducedMotionStyles = tier2ReducedMotionMatch[0];

  assert.ok(
    reducedMotionStyles.includes('.period-nav-arrow'),
    'Reduced motion must include .period-nav-arrow'
  );
  assert.ok(
    reducedMotionStyles.includes('transform: none !important'),
    'Reduced motion must disable transform animations'
  );
  assert.ok(
    reducedMotionStyles.includes('transition: none !important'),
    'Reduced motion must disable CSS transitions'
  );
});
