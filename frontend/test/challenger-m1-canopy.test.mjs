// frontend/test/challenger-m1-canopy.test.mjs
// Empirical Verification & Adversarial Stress Harness for Milestone 1 (Canopy Header & Actions)
// Executed by Challenger 1

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
const CSS_PATH = path.resolve(SRC_DIR, 'index.css');
const PAGE_HERO_PATH = path.resolve(SRC_DIR, 'components/PageHero.jsx');

test('Milestone 1 - PageHero component static contracts & structure', () => {
  const source = fs.readFileSync(PAGE_HERO_PATH, 'utf8');

  // Semantic header and canopy classes
  assert.ok(source.includes('<header'), 'PageHero must render a semantic <header> tag');
  assert.ok(source.includes('page-hero-canopy'), 'PageHero must include page-hero-canopy class');

  // 2-column left-aligned hero top row layout (no dead leading back-button column)
  assert.ok(source.includes('className="canopy-top-row"'), 'PageHero must render canopy-top-row');
  assert.ok(!source.includes('className="canopy-leading"'), 'PageHero must not render dead canopy-leading cell');
  assert.ok(!source.includes('className="page-hero-back-btn"'), 'PageHero must not render in-header back button');
  assert.ok(source.includes('className="canopy-center page-hero-main"'), 'PageHero must render canopy-center cell');
  assert.ok(source.includes('className="canopy-trailing"'), 'PageHero must render canopy-trailing cell');
  assert.ok(source.includes('isCollapsed && collapsedTrailing'), 'PageHero must render collapsedTrailing when collapsed');
  assert.ok(source.includes("triggerHaptic('selection')"), 'Collapsed title tap must trigger selection haptic');

  // Action button contract
  assert.ok(source.includes('className="canopy-add-btn"'), 'Action button must use canopy-add-btn class');
  assert.ok(source.includes("triggerHaptic('impact')"), 'Action button must trigger impact haptic');
  assert.ok(source.includes('aria-label={effectiveActionLabel}'), 'Action button must have accessible aria-label');
  assert.ok(source.includes('<span className="material-symbols-rounded">{actionIcon}</span>'), 'Action button must render action icon');

  // Tier 2 slot
  assert.ok(source.includes('className="canopy-tier2"'), 'PageHero must render canopy-tier2 container when toolbar is present');

  // Morphing & Physics backwards compatibility
  assert.ok(source.includes('export function useMorphingPageHero'), 'PageHero must export useMorphingPageHero');
  assert.ok(source.includes('--hero-scroll-progress'), 'PageHero must set --hero-scroll-progress');
  assert.ok(source.includes('requestAnimationFrame'), 'Scroll updates must be throttled with requestAnimationFrame');
});

test('Milestone 1 - index.css sticky edge-to-edge canopy layout & safe area padding', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Sticky, 100% full-width pinning
  const heroBlock = css.match(/^\.page-hero\s*\{([^}]+)\}/m);
  assert.ok(heroBlock, '.page-hero CSS rule must exist in index.css');
  const heroStyles = heroBlock[1];

  assert.ok(heroStyles.includes('position: sticky'), 'Canopy must be position: sticky');
  assert.ok(heroStyles.includes('top: 0'), 'Canopy must stick to top: 0');
  assert.ok(heroStyles.includes('width: 100%'), 'Canopy must span width: 100%');
  assert.ok(heroStyles.includes('max-width: 100%'), 'Canopy must allow full 100% max-width');
  assert.ok(heroStyles.includes('margin: 0'), 'Canopy must have zero margins to touch edges');
  assert.ok(heroStyles.includes('z-index: 40'), 'Canopy must have z-index: 40');
  assert.ok(heroStyles.includes('border-radius: 0'), 'Canopy must have border-radius: 0 for edge-to-edge flush design');

  // Mobile notch safe area padding
  assert.ok(
    heroStyles.includes('env(safe-area-inset-top, 0px)'),
    'Canopy must pad for mobile status bar notch using env(safe-area-inset-top, 0px)'
  );

  // Emerald gradient & specular border lighting
  assert.ok(heroStyles.includes('linear-gradient(135deg, rgba(5, 46, 36,'), 'Canopy must have emerald gradient background');
  assert.ok(heroStyles.includes('border-bottom: 1px solid rgba(255, 255, 255, 0.12)'), 'Canopy must have specular bottom border');

  // 2-column top row grid (left-aligned hero stack + right-aligned action row)
  const rowBlock = css.match(/\.canopy-top-row\s*\{([^}]+)\}/);
  assert.ok(rowBlock, '.canopy-top-row CSS rule must exist');
  assert.ok(rowBlock[1].includes('display: grid'), 'canopy-top-row must be a CSS grid');
  assert.ok(rowBlock[1].includes('grid-template-columns: minmax(0, 1fr) auto'), 'canopy-top-row must define 2-column left-aligned grid');

  // Tactile (+) action button styling
  const addBtnBlock = css.match(/\.canopy-add-btn\s*\{([^}]+)\}/);
  assert.ok(addBtnBlock, '.canopy-add-btn CSS rule must exist');
  assert.ok(addBtnBlock[1].includes('width: 40px'), 'canopy-add-btn must have width: 40px');
  assert.ok(addBtnBlock[1].includes('height: 40px'), 'canopy-add-btn must have height: 40px');
  assert.ok(addBtnBlock[1].includes('border-radius: 50%'), 'canopy-add-btn must be circular');

  // Title & Subtitle ellipsis hygiene
  const titleBlock = css.match(/\.canopy-title[\s\S]*?\{([^}]+)\}/);
  assert.ok(titleBlock, '.canopy-title CSS rule must exist');
  assert.ok(titleBlock[1].includes('text-overflow: ellipsis'), 'canopy-title must have text-overflow: ellipsis');
  assert.ok(titleBlock[1].includes('overflow: hidden'), 'canopy-title must have overflow: hidden');
  assert.ok(titleBlock[1].includes('white-space: nowrap'), 'canopy-title must have white-space: nowrap');

  // Reduced motion override
  assert.ok(css.includes('@media (prefers-reduced-motion: reduce)'), 'CSS must include prefers-reduced-motion block');
  assert.ok(css.includes('.canopy-add-btn'), 'Reduced motion must cover canopy-add-btn');
});

test('Milestone 1 - In-header (+) action button on 8 designated actionable pages', () => {
  const actionablePages = [
    { file: 'Transactions.jsx', actionFn: 'setManualTxnOpen(true)', label: 'Add Transaction' },
    { file: 'Budgets.jsx', actionFn: 'setAddingBudget(true)', label: 'Add Budget' },
    { file: 'Accounts.jsx', actionFn: 'setAddingManual(true)', label: 'Add Account' },
    { file: 'Upcoming.jsx', actionFn: 'openNew()', label: 'Add Recurring' },
    { file: 'Goals.jsx', actionFn: 'openNewGoal', label: 'Add Goal' },
    { file: 'Rules.jsx', actionFn: 'setEditing({})', label: 'Add Rule' },
    { file: 'Categories.jsx', actionFn: 'setEditing({})', label: 'Add Category' },
    { file: 'Household.jsx', actionFn: "setEditingMember({ mode: 'new' })", label: 'Add Member' }
  ];

  for (const { file, actionFn, label } of actionablePages) {
    const filePath = path.join(PAGES_DIR, file);
    const content = fs.readFileSync(filePath, 'utf8');

    // Verify onAction prop is wired
    assert.ok(
      content.includes('onAction='),
      `${file} must provide onAction prop to PageHero`
    );
    assert.ok(
      content.includes(actionFn),
      `${file} onAction must trigger ${actionFn}`
    );
    assert.ok(
      content.includes(`actionLabel="${label}"`),
      `${file} must provide actionLabel="${label}"`
    );
  }
});

test('Milestone 1 - Dashboard and read-only pages strictly omit (+) action button', () => {
  const readOnlyPages = [
    'Dashboard.jsx',
    'SpendingTrends.jsx',
    'NetWorth.jsx',
    'RetirementCalculator.jsx',
    'HousingCalculator.jsx',
    'MhaTracker.jsx',
    'Settings.jsx'
  ];

  for (const file of readOnlyPages) {
    const filePath = path.join(PAGES_DIR, file);
    const content = fs.readFileSync(filePath, 'utf8');

    // Extract PageHero block
    const heroMatch = content.match(/<PageHero[\s\S]*?\/>/);
    assert.ok(heroMatch, `${file} must contain a <PageHero /> component`);
    const heroInvocation = heroMatch[0];

    assert.ok(
      !heroInvocation.includes('onAction='),
      `${file} PageHero must strictly omit onAction`
    );
    assert.ok(
      !heroInvocation.includes('onAdd='),
      `${file} PageHero must strictly omit onAdd`
    );
  }
});

test('Milestone 1 - Zero header back buttons across all 15 pages', () => {
  const allPages = [
    'Dashboard.jsx',
    'Transactions.jsx',
    'Budgets.jsx',
    'Accounts.jsx',
    'Settings.jsx',
    'Upcoming.jsx',
    'Goals.jsx',
    'Rules.jsx',
    'Categories.jsx',
    'Household.jsx',
    'SpendingTrends.jsx',
    'NetWorth.jsx',
    'RetirementCalculator.jsx',
    'HousingCalculator.jsx',
    'MhaTracker.jsx'
  ];

  for (const file of allPages) {
    const filePath = path.join(PAGES_DIR, file);
    const content = fs.readFileSync(filePath, 'utf8');
    const heroMatch = content.match(/<PageHero[\s\S]*?\/>/);
    assert.ok(heroMatch, `${file} must contain a <PageHero />`);
    const heroInvocation = heroMatch[0];

    assert.ok(
      !heroInvocation.includes('showBackButton'),
      `${file} must omit showBackButton from PageHero`
    );
    assert.ok(
      !heroInvocation.includes('onBack'),
      `${file} must omit onBack from PageHero`
    );
    assert.ok(
      !heroInvocation.includes('backTo'),
      `${file} must omit backTo from PageHero`
    );
  }
});

test('Milestone 1 - Full-width canopy view layout expansion and content centering', () => {
  const css = fs.readFileSync(CSS_PATH, 'utf8');

  // Verify that views with app-page-width on root are overridden to 100% width
  assert.ok(css.includes('.budget-beta-view.app-page-width'), 'CSS must override budget-beta-view.app-page-width');
  assert.ok(css.includes('.accounts-view.app-page-width'), 'CSS must override accounts-view.app-page-width');
  assert.ok(css.includes('.rules-view.app-page-width'), 'CSS must override rules-view.app-page-width');
  assert.ok(css.includes('.settings-view.app-page-width'), 'CSS must override settings-view.app-page-width');
  assert.ok(css.includes('.spending-trends-view.app-page-width'), 'CSS must override spending-trends-view.app-page-width');
  assert.ok(css.includes('.housing-view.app-page-width'), 'CSS must override housing-view.app-page-width');

  // Verify all child elements in views (except .page-hero) are centered with gutter
  assert.ok(css.includes('> :not(.page-hero)'), 'CSS must constrain non-hero child content in view containers');
  assert.ok(css.includes('max-width: var(--app-page-max-width)'), 'Non-hero child content must have max-width constraint');
});
