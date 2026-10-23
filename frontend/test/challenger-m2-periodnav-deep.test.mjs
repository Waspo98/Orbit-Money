// frontend/test/challenger-m2-periodnav-deep.test.mjs
// Adversarial Deep Stress Harness for Budgets Tier 2 PeriodNav Calendar Pill
// Created by Challenger 2

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

test('Deep Stress 1: PeriodNav defensive handling with empty or null props', () => {
  const source = fs.readFileSync(path.join(COMPONENTS_DIR, 'PeriodNav.jsx'), 'utf8');

  // Verify optional chaining on all callback invocations
  assert.ok(source.includes('onPrev?.(e)'), 'onPrev must use optional chaining to prevent crash if undefined');
  assert.ok(source.includes('onNext?.(e)'), 'onNext must use optional chaining to prevent crash if undefined');
  assert.ok(source.includes('onJump?.(val)'), 'onJump must use optional chaining to prevent crash if undefined');

  // Verify default props
  assert.ok(source.includes("canGoForward = true"), 'canGoForward must default to true');
  assert.ok(source.includes("previousLabel = 'Previous period'"), 'previousLabel must have fallback');
  assert.ok(source.includes("nextLabel = 'Next period'"), 'nextLabel must have fallback');
  assert.ok(source.includes("jumpLabel = 'Jump to period'"), 'jumpLabel must have fallback');
});

test('Deep Stress 2: Forward boundary lock prevents forward navigation & haptics', () => {
  const source = fs.readFileSync(path.join(COMPONENTS_DIR, 'PeriodNav.jsx'), 'utf8');

  // Next button click handler must strictly guard before triggering haptics
  const nextButtonMatch = source.match(/className="btn-icon period-nav-arrow period-nav-next"[\s\S]*?onClick=\{([\s\S]*?)\}/);
  assert.ok(nextButtonMatch, 'Next button must have an onClick handler');
  const handlerBody = nextButtonMatch[1];

  const canGoForwardGuardIndex = handlerBody.indexOf('if (!canGoForward) return;');
  const hapticIndex = handlerBody.indexOf("triggerHaptic('selection');");

  assert.ok(canGoForwardGuardIndex !== -1, 'Guard check for canGoForward must exist');
  assert.ok(hapticIndex !== -1, 'Haptic call must exist');
  assert.ok(
    canGoForwardGuardIndex < hapticIndex,
    'canGoForward guard MUST execute before triggerHaptic so no haptic feedback fires on disabled boundaries'
  );
});

test('Deep Stress 3: Capsule Geometry & Boundary containment (Mobile Ergonomics)', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  const pillMatch = css.match(/\.period-nav-connected[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(pillMatch, 'Must find .period-nav-connected');
  const pillCss = pillMatch[1];

  // Max width 320px ensures 20px margins on standard 360dp mobile viewports
  assert.ok(pillCss.includes('max-width: 320px'), 'Capsule max-width must be 320px');
  assert.ok(pillCss.includes('height: 44px'), 'Capsule height must be 44px');
  assert.ok(pillCss.includes('min-height: 44px'), 'Capsule min-height must be 44px');
  assert.ok(pillCss.includes('border-radius: 9999px'), 'Capsule border-radius must be 9999px');
  assert.ok(pillCss.includes('overflow: hidden'), 'Capsule overflow must be hidden');

  // Left arrow: width 44px, height 44px, radius 9999px 0 0 9999px
  const arrowMatch = css.match(/\.period-nav-connected \.period-nav-arrow[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(arrowMatch, 'Must find .period-nav-arrow');
  const arrowCss = arrowMatch[1];
  assert.ok(arrowCss.includes('width: 44px'), 'Arrow width 44px');
  assert.ok(arrowCss.includes('height: 44px'), 'Arrow height 44px');
  assert.ok(arrowCss.includes('flex-shrink: 0'), 'Arrow flex-shrink: 0');
  assert.ok(arrowCss.includes('overflow: hidden'), 'Arrow overflow: hidden');

  // Middle label wrap: flex: 1, min-width: 0, overflow/containment
  const labelWrapMatch = css.match(/\.period-nav-connected \.period-nav-label-wrap[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(labelWrapMatch, 'Must find .period-nav-label-wrap');
  const labelWrapCss = labelWrapMatch[1];
  assert.ok(labelWrapCss.includes('flex: 1'), 'Label wrap flex: 1');
  assert.ok(labelWrapCss.includes('min-width: 0'), 'Label wrap min-width: 0 prevents flex blowouts on long month names');

  // Chevron icons 24x24 inside 44x44
  const iconMatch = css.match(/\.period-nav-connected \.period-nav-arrow \.material-symbols-rounded[\s\S]*?\{([\s\S]*?)\}/);
  assert.ok(iconMatch, 'Must find icon rule inside arrow');
  const iconCss = iconMatch[1];
  assert.ok(iconCss.includes('width: 24px'), 'Icon width 24px');
  assert.ok(iconCss.includes('height: 24px'), 'Icon height 24px');
  assert.ok(iconCss.includes('overflow: hidden'), 'Icon overflow: hidden');
});

test('Deep Stress 4: AppSelect mobile page-centered portal menu placement without shifts', () => {
  const appSelectSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'AppSelect.jsx'), 'utf8');

  // Check portal usage
  assert.ok(appSelectSource.includes("createPortal(menu, document.body)"), 'AppSelect must render menu via createPortal to body to prevent container reflows');

  // Check page-center placement math
  assert.ok(appSelectSource.includes("menuPlacement === 'page-center'"), 'AppSelect must handle page-center placement');
  assert.ok(appSelectSource.includes("const width = Math.min(320, viewportWidth - gutter * 2);"), 'Menu width constrained to 320px max or viewport width');
  assert.ok(appSelectSource.includes("left: Math.max(gutter, (viewportWidth - width) / 2)"), 'Menu centered horizontally');
  assert.ok(appSelectSource.includes("position: 'fixed'"), 'Menu rendered with fixed positioning');
});

test('Deep Stress 5: Accessibility of chevrons and month dropdown', () => {
  const periodSource = fs.readFileSync(path.join(COMPONENTS_DIR, 'PeriodNav.jsx'), 'utf8');

  // Aria labels
  assert.ok(periodSource.includes('aria-label={previousLabel}'), 'Previous button has aria-label');
  assert.ok(periodSource.includes('aria-label={nextLabel}'), 'Next button has aria-label');
  assert.ok(periodSource.includes('ariaLabel={jumpLabel}'), 'AppSelect receives jumpLabel');

  // Decorative icon spans
  const prevIconMatch = periodSource.match(/<span className="material-symbols-rounded" aria-hidden="true">chevron_left<\/span>/);
  assert.ok(prevIconMatch, 'chevron_left span has aria-hidden="true"');

  const nextIconMatch = periodSource.match(/<span className="material-symbols-rounded" aria-hidden="true">chevron_right<\/span>/);
  assert.ok(nextIconMatch, 'chevron_right span has aria-hidden="true"');
});
