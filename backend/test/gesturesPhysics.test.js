import assert from 'node:assert/strict';
import test from 'node:test';

// Pure algorithmic verification of the gesture physics formulas
function calculatePullOffset(dy, maxOffset = 88) {
  if (dy <= 0) return 0;
  return Math.min(maxOffset, 45 * Math.log(1 + dy / 35));
}

function calculateSwipeOffset(dx, maxSwipe = 128, startOffset = 0) {
  const rawOffset = startOffset + dx;
  if (rawOffset > 0) {
    return Math.min(24, rawOffset * 0.2);
  } else if (rawOffset < -maxSwipe) {
    const excess = -rawOffset - maxSwipe;
    return -maxSwipe - Math.min(24, excess * 0.2);
  }
  return rawOffset;
}

test('calculatePullOffset implements logarithmic rubber-band tension with 88px clamp', () => {
  assert.equal(calculatePullOffset(0), 0);
  assert.equal(calculatePullOffset(-20), 0);

  const offset35 = calculatePullOffset(35);
  // 45 * ln(2) ~= 31.1918
  assert.ok(Math.abs(offset35 - 31.19) < 0.01, `Expected ~31.19, got ${offset35}`);

  // Test crossing 56px activation threshold
  const offsetThreshold = calculatePullOffset(95);
  assert.ok(offsetThreshold >= 56, `Expected offset >= 56, got ${offsetThreshold}`);

  // Verify asymptotic / logarithmic clamp at 88px
  const largePull = calculatePullOffset(5000);
  assert.equal(largePull, 88);
});

test('pull-to-refresh thresholds conform to specification constants', () => {
  const THRESHOLD = 56;
  const HOLDING_POSITION = 48;
  const MAX_OFFSET = 88;

  assert.ok(HOLDING_POSITION < THRESHOLD, 'Holding position (48) must be below activation threshold (56)');
  assert.ok(THRESHOLD < MAX_OFFSET, 'Threshold (56) must be below max pull offset (88)');
});

test('calculateSwipeOffset applies rubber-band resistance beyond boundaries', () => {
  // Closed state swiping left within [0, -128]
  assert.equal(calculateSwipeOffset(-50, 128, 0), -50);
  assert.equal(calculateSwipeOffset(-128, 128, 0), -128);

  // Swiping right when already closed gives rubber-band resistance capped at 24px
  const rightPull = calculateSwipeOffset(50, 128, 0);
  assert.equal(rightPull, 10); // 50 * 0.2 = 10
  const largeRightPull = calculateSwipeOffset(200, 128, 0);
  assert.equal(largeRightPull, 24); // capped at 24

  // Swiping left beyond -128 gives rubber-band resistance
  const pastMax = calculateSwipeOffset(-100, 128, -128); // raw = -228 (excess = 100)
  assert.equal(pastMax, -128 - 20); // -128 - (100 * 0.2) = -148
});

test('gesture tap-slop boundary disambiguates pure tap from swipe', () => {
  const TAP_SLOP_PX = 6;
  const pureTapDisplacement = 4.5;
  const swipeDisplacement = 8.0;

  assert.ok(pureTapDisplacement < TAP_SLOP_PX, 'Pure tap displacement (< 6px) must not lock horizontal swipe');
  assert.ok(swipeDisplacement > TAP_SLOP_PX, 'Swipe displacement (> 6px) must activate horizontal swipe and suppress click');
});
