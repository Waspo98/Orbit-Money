import { useEffect, useRef } from 'react';

const DEPTH_PATTERN_WIDTH = 720;
const DEPTH_PATTERN_HEIGHT = 520;
const DEPTH_PATTERN_ID = 'orbit-depth-mesh';
const DEPTH_PATTERN = createDepthPattern();

function randomBetween(min, max) {
  return min + Math.random() * (max - min);
}

function formatMeshNumber(value) {
  return Number(value.toFixed(1));
}

function jitterMeshPoint(point, amount) {
  return {
    id: point.id,
    x: formatMeshNumber(point.x + randomBetween(-amount, amount)),
    y: formatMeshNumber(point.y + randomBetween(-amount, amount)),
    r: formatMeshNumber(point.r * randomBetween(0.78, 1.14))
  };
}

function createMeshCluster(points, paths, jitterAmount) {
  const pointById = new Map(
    points.map((point) => {
      const jittered = jitterMeshPoint(point, jitterAmount);
      return [point.id, jittered];
    })
  );

  return {
    paths: paths.map((path) => path.map((id) => pointById.get(id))),
    circles: Array.from(pointById.values())
  };
}

function meshPathD(points) {
  return points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x} ${point.y}`)
    .join(' ');
}

function createDepthPattern() {
  const primary = createMeshCluster(
    [
      { id: 'a', x: 48, y: 42, r: 2.6 },
      { id: 'b', x: 76, y: 126, r: 2.4 },
      { id: 'c', x: 118, y: 78, r: 2.1 },
      { id: 'd', x: 128, y: 168, r: 1.9 },
      { id: 'e', x: 176, y: 286, r: 2.5 },
      { id: 'f', x: 252, y: 226, r: 2.1 },
      { id: 'g', x: 264, y: 352, r: 2.3 },
      { id: 'h', x: 326, y: 318, r: 2.1 },
      { id: 'i', x: 346, y: 168, r: 1.9 },
      { id: 'j', x: 386, y: 480, r: 2.5 },
      { id: 'k', x: 404, y: 410, r: 2.4 },
      { id: 'l', x: 476, y: 462, r: 2.5 }
    ],
    [
      ['a', 'b', 'd', 'f', 'i'],
      ['b', 'c', 'd', 'e', 'f'],
      ['a', 'c', 'f', 'k'],
      ['e', 'g', 'h', 'k', 'l'],
      ['f', 'h', 'i', 'k'],
      ['g', 'j', 'l', 'k']
    ],
    18
  );

  const secondary = createMeshCluster(
    [
      { id: 'm', x: 546, y: 62, r: 1.7 },
      { id: 'n', x: 626, y: 108, r: 2.1 },
      { id: 'o', x: 668, y: 192, r: 1.8 },
      { id: 'p', x: 606, y: 286, r: 2.2 },
      { id: 'q', x: 514, y: 250, r: 2 },
      { id: 'r', x: 470, y: 138, r: 1.6 },
      { id: 's', x: 438, y: 300, r: 1.8 }
    ],
    [
      ['m', 'n', 'o', 'p', 'q'],
      ['n', 'q', 'r', 'm'],
      ['r', 's', 'q', 'p']
    ],
    24
  );

  const baseStars = [
    { x: 32, y: 338, r: 1.1 },
    { x: 96, y: 452, r: 1.4 },
    { x: 154, y: 36, r: 1.2 },
    { x: 212, y: 92, r: 1.5 },
    { x: 294, y: 58, r: 1 },
    { x: 366, y: 76, r: 1.3 },
    { x: 424, y: 248, r: 1.1 },
    { x: 504, y: 386, r: 1.6 },
    { x: 580, y: 446, r: 1.2 },
    { x: 656, y: 384, r: 1 },
    { x: 696, y: 48, r: 1.3 }
  ].map((point) => jitterMeshPoint({ ...point, id: '' }, 26));

  const extraStars = Array.from({ length: 8 }, () => ({
    x: formatMeshNumber(randomBetween(24, DEPTH_PATTERN_WIDTH - 24)),
    y: formatMeshNumber(randomBetween(28, DEPTH_PATTERN_HEIGHT - 28)),
    r: formatMeshNumber(randomBetween(0.8, 1.5))
  }));

  return {
    primary,
    secondary,
    stars: [...baseStars, ...extraStars]
  };
}

function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export default function DepthPattern({ className = '' }) {
  const patternRef = useRef(null);
  const { primary, secondary, stars } = DEPTH_PATTERN;

  useEffect(() => {
    const pattern = patternRef.current;
    if (!pattern || prefersReducedMotion()) return undefined;

    let frameId = 0;
    function updateDepthOffset() {
      frameId = 0;
      const offset = Math.round(window.scrollY * 0.61);
      pattern.style.setProperty('--depth-pattern-offset', `${offset}px`);
    }

    function scheduleDepthOffsetUpdate() {
      if (frameId) return;
      frameId = window.requestAnimationFrame(updateDepthOffset);
    }

    updateDepthOffset();
    window.addEventListener('scroll', scheduleDepthOffsetUpdate, { passive: true });
    window.addEventListener('resize', scheduleDepthOffsetUpdate);
    return () => {
      window.removeEventListener('scroll', scheduleDepthOffsetUpdate);
      window.removeEventListener('resize', scheduleDepthOffsetUpdate);
      if (frameId) window.cancelAnimationFrame(frameId);
    };
  }, []);

  return (
    <div ref={patternRef} className={`depth-pattern ${className}`} aria-hidden="true">
      <svg focusable="false" role="presentation">
        <defs>
          <pattern
            id={DEPTH_PATTERN_ID}
            width={DEPTH_PATTERN_WIDTH}
            height={DEPTH_PATTERN_HEIGHT}
            patternUnits="userSpaceOnUse"
          >
            <g className="depth-pattern-cluster depth-pattern-cluster-primary">
              {primary.paths.map((path, index) => (
                <path key={`primary-path-${index}`} d={meshPathD(path)} />
              ))}
              {primary.circles.map((point) => (
                <circle key={`primary-dot-${point.id}`} cx={point.x} cy={point.y} r={point.r} />
              ))}
            </g>

            <g className="depth-pattern-cluster depth-pattern-cluster-secondary">
              {secondary.paths.map((path, index) => (
                <path key={`secondary-path-${index}`} d={meshPathD(path)} />
              ))}
              {secondary.circles.map((point) => (
                <circle key={`secondary-dot-${point.id}`} cx={point.x} cy={point.y} r={point.r} />
              ))}
            </g>

            <g className="depth-pattern-stars">
              {stars.map((point, index) => (
                <circle key={`star-${index}`} cx={point.x} cy={point.y} r={point.r} />
              ))}
            </g>
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill={`url(#${DEPTH_PATTERN_ID})`} />
      </svg>
    </div>
  );
}
