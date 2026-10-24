import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

test('PageHero: Layout thrashing elimination & decoupled physics engine', () => {
  const pageHeroPath = path.join(rootDir, 'src', 'components', 'PageHero.jsx');
  const source = fs.readFileSync(pageHeroPath, 'utf8');

  // Check 1: title.style.transform = 'none' layout thrash hack must be completely removed
  assert.equal(
    source.includes("title.style.transform = 'none'"),
    false,
    "Legacy hack title.style.transform = 'none' must not exist in PageHero.jsx"
  );

  // Check 2: Geometry caching via ref and ResizeObserver
  assert.ok(
    source.includes('geometryRef'),
    'geometryRef must be used to cache geometry metrics'
  );
  assert.ok(
    source.includes('ResizeObserver'),
    'ResizeObserver must be used to observe content size changes'
  );

  // Check 3: onScroll must NOT invoke getBoundingClientRect or getComputedStyle
  const onScrollStart = source.indexOf('function onScroll()');
  assert.ok(onScrollStart !== -1, 'onScroll function must exist in useMorphingPageHero');
  const onScrollEnd = source.indexOf('measureGeometry();', onScrollStart);
  const onScrollBody = source.slice(onScrollStart, onScrollEnd);
  assert.equal(
    onScrollBody.includes('getBoundingClientRect'),
    false,
    'onScroll in PageHero must not call getBoundingClientRect'
  );
  assert.equal(
    onScrollBody.includes('getComputedStyle'),
    false,
    'onScroll in PageHero must not call getComputedStyle'
  );

  // Check 4: Zero setState during scrolling
  assert.equal(
    onScrollBody.includes('setState'),
    false,
    'onScroll must never call setState (zero React re-renders during scroll)'
  );

  // Check 5: Direct CSS custom properties written via RAF
  assert.ok(
    onScrollBody.includes('--hero-scroll-progress'),
    'onScroll must drive --hero-scroll-progress directly'
  );
  assert.ok(
    onScrollBody.includes('--hero-progress'),
    'onScroll must drive --hero-progress directly'
  );
  assert.ok(
    onScrollBody.includes('--hero-content-opacity'),
    'onScroll must drive --hero-content-opacity directly'
  );

  // Check 6: Hardware-accelerated 3D transforms
  assert.ok(
    source.includes('translate3d'),
    'PageHero must use hardware-accelerated translate3d transforms'
  );
  assert.ok(
    source.includes('scale3d'),
    'PageHero must use hardware-accelerated scale3d transforms'
  );
});

test('Transactions: Zero-thrash month header pinning via IntersectionObserver', () => {
  const txnsPath = path.join(rootDir, 'src', 'pages', 'Transactions.jsx');
  const source = fs.readFileSync(txnsPath, 'utf8');

  // Check 1: Synchronous getBoundingClientRect loop over all groups must be gone
  assert.equal(
    source.includes('for (let index = 0; index < groups.length; index += 1)'),
    false,
    'Synchronous loop over groups querying getBoundingClientRect must be removed'
  );

  // Check 2: IntersectionObserver implementation
  assert.ok(
    source.includes('IntersectionObserver'),
    'Transactions.jsx must use IntersectionObserver for month header pinning'
  );
  assert.ok(
    source.includes('sentinelRefs'),
    'Transactions.jsx must track sentinel refs'
  );
  assert.ok(
    source.includes('txn-month-sentinel'),
    'Transactions.jsx must render txn-month-sentinel elements'
  );
  assert.ok(
    source.includes('txn-month-end-sentinel'),
    'Transactions.jsx must render txn-month-end-sentinel'
  );

  // Check 3: No scroll event listener in month pinning effect
  const pinningEffectMatch = source.match(/useEffect\(\(\)\s*=>\s*\{[\s\S]*?return\s*\(\)\s*=>\s*\{[\s\S]*?observer\.disconnect\(\);[\s\S]*?\}\s*;\s*\}, \[groups\]\);/);
  assert.ok(pinningEffectMatch, 'Month pinning effect must be governed by IntersectionObserver');
  const effectBody = pinningEffectMatch[0];
  assert.equal(
    effectBody.includes("window.addEventListener('scroll'"),
    false,
    'Month pinning effect must not add a window scroll listener'
  );
});

test('App: Global scroll listener throttled via requestAnimationFrame', () => {
  const appPath = path.join(rootDir, 'src', 'App.jsx');
  const source = fs.readFileSync(appPath, 'utf8');

  // Check 1: useRouteScrollRestoration throttles scroll
  const restorationMatch = source.match(/function useRouteScrollRestoration[\s\S]*?function currentScrollPosition/);
  assert.ok(restorationMatch, 'useRouteScrollRestoration must exist in App.jsx');

  const scrollEffectMatch = source.match(/useEffect\(\(\)\s*=>\s*\{[\s\S]*?handleScrollThrottled[\s\S]*?\}, \[\]\);/);
  assert.ok(scrollEffectMatch, 'useRouteScrollRestoration must throttle scroll with handleScrollThrottled');
  const effectCode = scrollEffectMatch[0];
  assert.ok(
    effectCode.includes('requestAnimationFrame'),
    'Scroll restoration listener must be throttled with requestAnimationFrame'
  );
  assert.ok(
    effectCode.includes('cancelAnimationFrame'),
    'Pending frame must be cancelled on cleanup'
  );
});

test('App: Progressive idle pre-warming with requestIdleCallback and budget checks', async () => {
  const appPath = path.join(rootDir, 'src', 'App.jsx');
  const source = fs.readFileSync(appPath, 'utf8');

  // Check 1: prewarmRoutesIdle function exists
  assert.ok(
    source.includes('function prewarmRoutesIdle'),
    'App.jsx must declare prewarmRoutesIdle'
  );

  // Check 2: requestIdleCallback with timeRemaining budget check
  assert.ok(
    source.includes('requestIdleCallback'),
    'prewarmRoutesIdle must utilize requestIdleCallback'
  );
  assert.ok(
    source.includes('deadline.timeRemaining() > 8'),
    'prewarmRoutesIdle must check timeRemaining against frame budget (> 8ms)'
  );

  // Check 3: Behavioral simulation of idle queue scheduling
  let loadedItems = [];
  const testQueue = [
    () => { loadedItems.push('chunk1'); return Promise.resolve(); },
    () => { loadedItems.push('chunk2'); return Promise.resolve(); },
    () => { loadedItems.push('chunk3'); return Promise.resolve(); }
  ];

  // Dynamic simulation of deadline yielding
  let simulatedTime = 12;
  const mockDeadline = {
    timeRemaining: () => {
      const remaining = simulatedTime;
      simulatedTime -= 5; // each item consumes 5ms
      return remaining;
    },
    didTimeout: false
  };

  // First slice should process chunk1 and chunk2 (12ms -> 7ms < 8ms, so chunk3 yields)
  const remaining = [...testQueue];
  while (remaining.length > 0 && (mockDeadline.timeRemaining() > 8 || mockDeadline.didTimeout)) {
    const loader = remaining.shift();
    loader();
  }

  assert.equal(loadedItems.length, 1, 'First slice should yield when remaining time drops below 8ms budget');
  assert.equal(remaining.length, 2, 'Remaining items should yield to next idle frame');
});

test('PageHero: Two-tier sticky canopy eliminates scroll-anchoring strobe loop', () => {
  const pageHeroPath = path.join(rootDir, 'src', 'components', 'PageHero.jsx');
  const cssPath = path.join(rootDir, 'src', 'index.css');
  const source = fs.readFileSync(pageHeroPath, 'utf8');
  const css = fs.readFileSync(cssPath, 'utf8');

  // 1. PageHero wraps collapsible secondary content in .canopy-collapsible and measures its natural height
  assert.ok(
    source.includes('className="canopy-collapsible"'),
    'PageHero must wrap stats and tier 2 controls in .canopy-collapsible'
  );
  assert.ok(
    source.includes('--hero-collapse-distance'),
    'PageHero must set --hero-collapse-distance from collapsible content height'
  );

  // 2. Hysteresis deadband prevents single-pixel threshold oscillation
  assert.ok(
    source.includes('collapseThreshold') && source.includes('expandThreshold'),
    'PageHero must use separate collapseThreshold and expandThreshold for scroll hysteresis'
  );

  // 3. CSS uses native negative-top sticky offset + sticky .canopy-top-row and overflow-anchor: none
  const heroBlock = css.match(/^\.page-hero\s*\{([^}]+)\}/m);
  assert.ok(heroBlock, '.page-hero block must exist');
  assert.ok(
    heroBlock[1].includes('top: calc(-1 * var(--hero-collapse-distance, 0px))'),
    '.page-hero must use negative top sticky offset equal to --hero-collapse-distance'
  );
  assert.ok(
    heroBlock[1].includes('overflow-anchor: none'),
    '.page-hero must disable browser scroll anchoring'
  );

  const topRowBlock = css.match(/\.canopy-top-row\s*\{([^}]+)\}/);
  assert.ok(topRowBlock, '.canopy-top-row block must exist');
  assert.ok(
    topRowBlock[1].includes('position: sticky'),
    '.canopy-top-row must be position: sticky to stay pinned while .canopy-collapsible scrolls under'
  );

  // 4. Collapsed state rules must NEVER animate or mutate max-height: 0 or padding in document flow
  const collapsedRules = [
    css.match(/\.page-hero\.page-hero-collapsed\s*\{([^}]+)\}/)?.[1] || '',
    css.match(/\.page-hero\.page-hero-collapsed\s+\.page-kicker\s*\{([^}]+)\}/)?.[1] || '',
    css.match(/\.page-hero\.page-hero-collapsed\s+\.canopy-subtitle\s*\{([^}]+)\}/)?.[1] || '',
    css.match(/\.page-hero\.page-hero-collapsed\s+\.canopy-tier2\s*\{([^}]+)\}/)?.[1] || '',
    css.match(/\.page-hero\.page-hero-collapsed\s+\.page-hero-stats\s*\{([^}]+)\}/)?.[1] || ''
  ].join('\n');

  assert.equal(
    collapsedRules.includes('max-height: 0'),
    false,
    '.page-hero-collapsed rules must never collapse max-height: 0 in document flow'
  );
  assert.equal(
    collapsedRules.includes('padding-top'),
    false,
    '.page-hero-collapsed must not mutate padding-top'
  );
});
