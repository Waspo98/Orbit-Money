// frontend/test/challenger-m3-adversarial-deep.test.mjs
// Adversarial Empirical Stress Test Suite for Milestone 3 (Right Drawer Ergonomics & Gesture Verification)

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

// -----------------------------------------------------------------------------
// Test 1: Adversarial Touch Gesture Physics & Velocity Simulation
// -----------------------------------------------------------------------------
test('Adversarial Gesture Physics - Distance threshold, velocity flick, left-drag prevention & hysteresis', () => {
  const drawerCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'RightDrawer.jsx'), 'utf8');

  // Verify constant thresholds defined in component
  assert.ok(drawerCode.includes('const DRAG_START_PX = 10;'), 'Must define DRAG_START_PX = 10');
  assert.ok(drawerCode.includes('const DRAG_CLOSE_PX = 80;'), 'Must define DRAG_CLOSE_PX = 80');
  assert.ok(drawerCode.includes('const DRAG_CLOSE_VELOCITY = 0.45;'), 'Must define DRAG_CLOSE_VELOCITY = 0.45');

  // Exact gesture state machine simulator based on RightDrawer.jsx logic
  class GestureSimulator {
    constructor() {
      this.DRAG_START_PX = 10;
      this.DRAG_CLOSE_PX = 80;
      this.DRAG_CLOSE_VELOCITY = 0.45;
      this.reset();
    }

    reset() {
      this.state = {
        active: false,
        mode: 'idle',
        startX: 0,
        startY: 0,
        lastX: 0,
        lastMoveAt: 0,
        distanceX: 0,
        velocityX: 0
      };
      this.crossedThreshold = false;
      this.hapticCalls = [];
      this.drawerTransform = '';
      this.backdropOpacity = '';
      this.closed = false;
      this.suppressClick = false;
    }

    touchStart(touch, now = 1000) {
      this.state = {
        active: true,
        mode: 'pending',
        startX: touch.clientX,
        startY: touch.clientY,
        lastX: touch.clientX,
        lastMoveAt: now,
        distanceX: 0,
        velocityX: 0
      };
      this.crossedThreshold = false;
    }

    touchMove(touch, now = 1016) {
      if (!this.state.active) return;
      const deltaX = touch.clientX - this.state.startX;
      const deltaY = touch.clientY - this.state.startY;
      const elapsed = Math.max(1, now - this.state.lastMoveAt);
      this.state.velocityX = (touch.clientX - this.state.lastX) / elapsed;
      this.state.lastX = touch.clientX;
      this.state.lastMoveAt = now;

      if (this.state.mode === 'scroll') return;

      if (this.state.mode === 'pending') {
        if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > this.DRAG_START_PX) {
          this.state.mode = 'scroll';
          return;
        }
        if (deltaX > this.DRAG_START_PX) {
          this.state.mode = 'drawer';
        } else if (deltaX < -this.DRAG_START_PX) {
          this.state.mode = 'scroll';
          return;
        }
      }

      if (this.state.mode !== 'drawer') return;

      const distanceX = Math.max(0, deltaX);
      this.state.distanceX = distanceX;
      this.drawerTransform = `translate3d(${distanceX}px, 0, 0)`;

      const progress = Math.min(1, distanceX / 320);
      this.backdropOpacity = String(Math.max(0, 1 - progress * 0.9));

      if (distanceX > this.DRAG_CLOSE_PX && !this.crossedThreshold) {
        this.crossedThreshold = true;
        this.hapticCalls.push('selection');
      } else if (distanceX <= this.DRAG_CLOSE_PX && this.crossedThreshold) {
        this.crossedThreshold = false;
      }
    }

    touchEnd() {
      if (!this.state.active) return;
      this.drawerTransform = '';
      this.backdropOpacity = '';

      if (this.state.mode === 'drawer') {
        const shouldClose =
          this.state.distanceX > this.DRAG_CLOSE_PX ||
          (this.state.distanceX > 20 && this.state.velocityX > this.DRAG_CLOSE_VELOCITY);

        if (shouldClose) {
          this.closed = true;
          this.suppressClick = true;
        }
      }
    }
  }

  const sim = new GestureSimulator();

  // Scenario A: Slow drag past 80px distance threshold -> Dismisses & triggers haptic
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 320, clientY: 400 }, 1050); // deltaX = 20 > 10, mode becomes 'drawer'
  assert.equal(sim.state.mode, 'drawer');
  sim.touchMove({ clientX: 385, clientY: 400 }, 1100); // deltaX = 85 > 80, crossed threshold
  assert.equal(sim.hapticCalls.length, 1, 'Haptic must trigger on crossing 80px threshold');
  assert.equal(sim.crossedThreshold, true);
  sim.touchEnd();
  assert.equal(sim.closed, true, 'Drawer must close when dragged > 80px');
  assert.equal(sim.suppressClick, true, 'Must suppress subsequent clicks after swipe dismissal');

  // Scenario B: Fast flick past 20px with velocity > 0.45 px/ms -> Dismisses
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 320, clientY: 400 }, 1020); // deltaX = 20, mode = 'drawer'
  sim.touchMove({ clientX: 345, clientY: 400 }, 1040); // deltaX = 45 (< 80), elapsed = 20ms, velocity = 25/20 = 1.25 > 0.45
  assert.equal(sim.state.mode, 'drawer');
  assert.equal(sim.crossedThreshold, false, 'Distance 45px < 80px, threshold not crossed');
  sim.touchEnd();
  assert.equal(sim.closed, true, 'Drawer must close on fast velocity flick (velocity > 0.45, distance > 20)');

  // Scenario C: Slow drag below 80px -> Does NOT dismiss (springs back)
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 320, clientY: 400 }, 1100); // deltaX = 20
  sim.touchMove({ clientX: 360, clientY: 400 }, 1300); // deltaX = 60 (< 80), velocity = 40/200 = 0.2 < 0.45
  sim.touchEnd();
  assert.equal(sim.closed, false, 'Drawer must NOT close when dragged < 80px with slow velocity');
  assert.equal(sim.drawerTransform, '', 'Drawer inline transform must be cleared on spring back');

  // Scenario D: Microscopic high-velocity twitch (<= 20px) -> Does NOT dismiss
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 315, clientY: 400 }, 1010); // deltaX = 15 <= 20, velocity = 15/10 = 1.5 > 0.45
  sim.touchEnd();
  assert.equal(sim.closed, false, 'Drawer must NOT close for twitch distances <= 20px even with high velocity');

  // Scenario E: Left-drag prevention (deltaX < -10px) -> switches to scroll, clamped distanceX = 0
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 280, clientY: 400 }, 1020); // deltaX = -20 < -10
  assert.equal(sim.state.mode, 'scroll', 'Left drag must switch mode to scroll to prevent pulling drawer into empty space');
  sim.touchMove({ clientX: 250, clientY: 400 }, 1040); // deltaX = -50
  assert.equal(sim.drawerTransform, '', 'Drawer transform must never be set when dragged left');
  sim.touchEnd();
  assert.equal(sim.closed, false, 'Left drag must never close drawer');

  // Scenario F: Vertical scroll dominance -> does not engage drawer drag
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 305, clientY: 435 }, 1020); // deltaY = 35 > deltaX = 5, deltaY > 10
  assert.equal(sim.state.mode, 'scroll', 'Vertical scroll must dominate and lock out drawer drag');
  sim.touchMove({ clientX: 400, clientY: 500 }, 1040); // subsequent horizontal movement ignored
  assert.equal(sim.drawerTransform, '', 'Drawer transform must not move when in scroll mode');
  sim.touchEnd();
  assert.equal(sim.closed, false);

  // Scenario G: Hysteresis threshold trigger (0 -> 90 -> 60 -> 90)
  sim.reset();
  sim.touchStart({ clientX: 300, clientY: 400 }, 1000);
  sim.touchMove({ clientX: 320, clientY: 400 }, 1020); // mode = drawer
  sim.touchMove({ clientX: 390, clientY: 400 }, 1040); // distance = 90 > 80 -> haptic 1
  assert.equal(sim.hapticCalls.length, 1);
  assert.equal(sim.crossedThreshold, true);
  sim.touchMove({ clientX: 400, clientY: 400 }, 1060); // distance = 100 > 80 -> no duplicate haptic
  assert.equal(sim.hapticCalls.length, 1);
  sim.touchMove({ clientX: 360, clientY: 400 }, 1080); // distance = 60 <= 80 -> resets crossedThreshold
  assert.equal(sim.crossedThreshold, false);
  assert.equal(sim.hapticCalls.length, 1);
  sim.touchMove({ clientX: 395, clientY: 400 }, 1100); // distance = 95 > 80 -> triggers haptic 2
  assert.equal(sim.hapticCalls.length, 2, 'Haptic must trigger again when crossing threshold a second time');
});

// -----------------------------------------------------------------------------
// Test 2: Dismissal Mechanics - Backdrop Tap, stopPropagation, Escape Key & Native Back
// -----------------------------------------------------------------------------
test('Adversarial Dismissals - Backdrop tap propagation, Escape key lifecycle & Android back interception', () => {
  const drawerCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'RightDrawer.jsx'), 'utf8');
  const appCode = fs.readFileSync(path.join(SRC_DIR, 'App.jsx'), 'utf8');
  const nativeAppCode = fs.readFileSync(path.join(SRC_DIR, 'nativeApp.js'), 'utf8');

  // 1. Backdrop tap dismiss vs aside stopPropagation
  assert.ok(
    drawerCode.includes('onClick={() => close({ animate: true })}'),
    'Backdrop must have onClick to close'
  );
  assert.ok(
    drawerCode.includes('onClick={(e) => e.stopPropagation()}'),
    'Drawer aside element must stop propagation to prevent backdrop dismissal on drawer body clicks'
  );

  // Close button tap
  assert.ok(drawerCode.includes('className="right-drawer-close-btn"'), 'Close button must exist');
  assert.ok(drawerCode.includes("triggerHaptic('tap')"), 'Close button must trigger tap haptic');

  // Double-close guard
  assert.ok(
    drawerCode.includes("if (drawerState === 'closing') return;"),
    'close() must debounce/guard against multiple closing executions'
  );

  // 2. Escape key dismissal lifecycle
  assert.ok(drawerCode.includes("if (e.key === 'Escape') close({ animate: true });"), 'Must check e.key === "Escape"');
  assert.ok(drawerCode.includes("document.addEventListener('keydown', onKeyDown)"), 'Must attach keydown listener to document');
  assert.ok(drawerCode.includes("document.removeEventListener('keydown', onKeyDown)"), 'Must cleanly detach keydown listener on unmount/close');

  // 3. Android hardware back button handler in initNativeApp
  // Verify nativeApp.js CapApp backButton routing
  assert.ok(
    nativeAppCode.includes("CapApp.addListener('backButton'"),
    'nativeApp.js must attach CapApp backButton listener'
  );
  assert.ok(
    nativeAppCode.includes('if (backButtonHandler && backButtonHandler())'),
    'nativeApp.js backButton must consult backButtonHandler first'
  );

  // Verify App.jsx backButton interception
  assert.ok(appCode.includes('onBackButton: () => {'), 'App.jsx must provide onBackButton handler to initNativeApp');
  assert.ok(
    /if\s*\(drawerOpen\)\s*\{\s*setDrawerOpen\(false\);\s*return\s*true;\s*\}/.test(appCode),
    'App.jsx onBackButton must dismiss drawer and return true to consume back event'
  );

  // Verify App.jsx auto-closes on route change
  assert.ok(
    /useEffect\(\(\)\s*=>\s*\{\s*setDrawerOpen\(false\);?\s*\},?\s*\[location\.pathname\]\);/.test(appCode),
    'App.jsx must close drawer whenever location.pathname changes'
  );
});

// -----------------------------------------------------------------------------
// Test 3: Navigation & Route State Integrity
// -----------------------------------------------------------------------------
test('Adversarial Navigation - Item tap, same-route preservation, forward transition & scroll lock restoration', () => {
  const drawerCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'RightDrawer.jsx'), 'utf8');
  const overlayCode = fs.readFileSync(path.join(COMPONENTS_DIR, 'overlayBehavior.js'), 'utf8');

  // 1. Same-route preservation: if tapping current route, close drawer without navigating
  assert.ok(
    drawerCode.includes('const isCurrentRoute = item.path === location.pathname;'),
    'Must detect if tapped item is the current route'
  );
  assert.ok(
    drawerCode.includes('if (isCurrentRoute) return;'),
    'Must return without calling navigate() when tapping current route to preserve scroll and state'
  );

  // 2. Different-route navigation: release overlay history entry and navigate forward
  assert.ok(
    /const shouldReplaceOverlayEntry = !isCurrentRoute\s*\?\s*releaseCurrentOverlayHistoryEntry\(\)\s*:\s*false;/.test(drawerCode),
    'Must release overlay history entry before route navigation'
  );
  assert.ok(
    drawerCode.includes("triggerHaptic('selection');"),
    'Must trigger selection haptic on item tap'
  );
  assert.ok(
    drawerCode.includes("close({ animate: true });"),
    'Must initiate drawer close on item tap'
  );
  assert.ok(
    drawerCode.includes("state: { transition: 'forward' }"),
    'Must pass transition: "forward" in navigate state'
  );

  // 3. Disabled / comingSoon items
  assert.ok(
    drawerCode.includes('if (suppressNextClickRef.current || item.comingSoon) return;'),
    'Must ignore taps if next click is suppressed or item is comingSoon'
  );

  // 4. Scroll restoration mechanics in overlayBehavior.js
  assert.ok(
    overlayCode.includes('window.scrollTo(snapshot.scrollX || 0, snapshot.scrollY || 0);'),
    'useBodyScrollLock cleanup must restore window scroll position exactly'
  );
});

// -----------------------------------------------------------------------------
// Test 4: CSS Layout, Z-Index Hierarchy & Responsive Safety
// -----------------------------------------------------------------------------
test('CSS Geometry & Accessibility - Z-Index layering, drawer width, backdrop filter & reduced motion', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Z-index hierarchy check:
  // Canopy header: z-index 40
  // Bottom tabs: z-index 50
  // Right drawer backdrop: z-index 190 (must be higher than both)
  const canopyZMatch = css.match(/\.canopy-header\s*\{[\s\S]*?z-index:\s*(\d+);/);
  const backdropZMatch = css.match(/\.right-drawer-backdrop\s*\{[\s\S]*?z-index:\s*(\d+);/);

  assert.ok(backdropZMatch, 'Backdrop z-index must be defined');
  const backdropZ = parseInt(backdropZMatch[1], 10);
  assert.ok(backdropZ >= 100, `Backdrop z-index (${backdropZ}) must be >= 100 to overlay all page chrome`);
  if (canopyZMatch) {
    const canopyZ = parseInt(canopyZMatch[1], 10);
    assert.ok(backdropZ > canopyZ, `Backdrop z-index (${backdropZ}) must be higher than canopy header (${canopyZ})`);
  }

  // Mobile viewport width: 85vw with max-width 380px and min-width 280px
  const drawerRuleMatch = css.match(/\.right-drawer\s*\{([\s\S]*?)\}/);
  assert.ok(drawerRuleMatch, '.right-drawer rule must exist in CSS');
  const drawerStyles = drawerRuleMatch[1];
  assert.ok(drawerStyles.includes('width: 85vw;'), 'Drawer width must be 85vw');
  assert.ok(drawerStyles.includes('max-width: 380px;'), 'Drawer max-width must be 380px');
  assert.ok(drawerStyles.includes('min-width: 280px;'), 'Drawer min-width must be 280px');
  assert.ok(drawerStyles.includes('transform: translate3d(100%, 0, 0);'), 'Drawer initial transform must be off-canvas (100%)');

  // Safe area padding
  assert.ok(css.includes('env(safe-area-inset-top'), 'Drawer header must pad for mobile status bar');
  assert.ok(css.includes('env(safe-area-inset-bottom'), 'Drawer scroll container must pad for navigation bar / gesture bar');

  // Desktop suppression (>= 1080px)
  const desktopMatch = css.match(/@media\s*\(min-width:\s*1080px\)\s*\{[\s\S]*?\.right-drawer-backdrop\s*\{[\s\S]*?display:\s*none\s*!important;[\s\S]*?\}/);
  assert.ok(desktopMatch, 'Right drawer backdrop must be suppressed (display: none !important) on viewports >= 1080px');

  // Reduced motion
  const reducedMotionMatch = css.match(/@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{[\s\S]*?\.right-drawer,\s*\.right-drawer-backdrop\s*\{[\s\S]*?transition:\s*none\s*!important;[\s\S]*?\}/);
  assert.ok(reducedMotionMatch, 'Drawer and backdrop transitions must be disabled under prefers-reduced-motion');
});
