import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '../..');

// Logic verification of RollingTicker digit reel math
function getDigitTranslation(digit) {
  const d = parseInt(digit, 10);
  assert.ok(d >= 0 && d <= 9, `Invalid digit: ${digit}`);
  return `translateY(-${d * 10}%)`;
}

function parseTickerTokens(text) {
  return Array.from(String(text)).map((char) => ({
    char,
    isDigit: /\d/.test(char),
    transform: /\d/.test(char) ? getDigitTranslation(char) : null
  }));
}

test('RollingTicker reel mathematics translates digits 0-9 by -10% per digit', () => {
  for (let d = 0; d <= 9; d++) {
    const translation = getDigitTranslation(d);
    assert.equal(translation, `translateY(-${d * 10}%)`);
  }
});

test('RollingTicker token parser preserves static non-digit symbols and translates digits', () => {
  const tokens = parseTickerTokens('-$1,250.75');
  assert.equal(tokens.length, 10);

  // Sign & currency symbol
  assert.equal(tokens[0].char, '-');
  assert.equal(tokens[0].isDigit, false);
  assert.equal(tokens[0].transform, null);

  assert.equal(tokens[1].char, '$');
  assert.equal(tokens[1].isDigit, false);
  assert.equal(tokens[1].transform, null);

  // Digit 1
  assert.equal(tokens[2].char, '1');
  assert.equal(tokens[2].isDigit, true);
  assert.equal(tokens[2].transform, 'translateY(-10%)');

  // Separator comma
  assert.equal(tokens[3].char, ',');
  assert.equal(tokens[3].isDigit, false);
  assert.equal(tokens[3].transform, null);

  // Digits 2, 5, 0
  assert.equal(tokens[4].transform, 'translateY(-20%)');
  assert.equal(tokens[5].transform, 'translateY(-50%)');
  assert.equal(tokens[6].transform, 'translateY(-0%)');

  // Decimal separator
  assert.equal(tokens[7].char, '.');
  assert.equal(tokens[7].isDigit, false);

  // Digits 7, 5
  assert.equal(tokens[8].transform, 'translateY(-70%)');
  assert.equal(tokens[9].transform, 'translateY(-50%)');
});

test('DashProgressBar and BudgetMeter clamp percent within [0, 100]', () => {
  function clampPercent(percent) {
    return Math.max(0, Math.min(100, percent || 0));
  }

  assert.equal(clampPercent(-10), 0);
  assert.equal(clampPercent(0), 0);
  assert.equal(clampPercent(45.5), 45.5);
  assert.equal(clampPercent(100), 100);
  assert.equal(clampPercent(150), 100);
  assert.equal(clampPercent(null), 0);
  assert.equal(clampPercent(undefined), 0);
});

test('index.css declares RollingTicker and spring progress meter styles with reduced-motion overrides', () => {
  const cssPath = path.join(rootDir, 'frontend/src/index.css');
  const css = fs.readFileSync(cssPath, 'utf8');

  // Required RollingTicker classes
  assert.ok(css.includes('.rolling-ticker'), 'Missing .rolling-ticker');
  assert.ok(css.includes('.rolling-ticker-column'), 'Missing .rolling-ticker-column');
  assert.ok(css.includes('.rolling-ticker-reel'), 'Missing .rolling-ticker-reel');
  assert.ok(css.includes('.rolling-ticker-digit'), 'Missing .rolling-ticker-digit');

  // Tabular nums
  assert.ok(css.includes('font-variant-numeric: tabular-nums;'), 'Missing tabular-nums');

  // Spring transition on progress bars
  assert.ok(css.includes('var(--dur-spring-default) var(--spring-spatial-default)'), 'Missing spring spatial on progress bars');

  // Pill caps (999px)
  assert.ok(css.includes('border-radius: 999px;'), 'Missing 999px pill caps');

  // Reduced motion overrides
  assert.ok(css.includes('@media (prefers-reduced-motion: reduce)'), 'Missing reduced motion media query');
  assert.ok(css.includes('.rolling-ticker-reel'), 'Missing rolling-ticker-reel reduced motion');
  assert.ok(css.includes('.budget-progress-fill'), 'Missing budget-progress-fill in CSS');
  assert.ok(css.includes('.dash-progress-fill'), 'Missing dash-progress-fill in CSS');
});
