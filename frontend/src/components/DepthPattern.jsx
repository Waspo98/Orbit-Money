import { useMemo } from 'react';

const DEPTH_PATTERN_WIDTH = 1760;
const DEPTH_PATTERN_HEIGHT = 1280;
const DEPTH_PATTERN_ID = 'orbit-depth-mesh';
const DEPTH_SESSION_SEED = Math.random().toString(36).slice(2);

const MESH_TEMPLATE = {
  points: [
    { id: 'a', x: -220, y: -190, r: 2.6 },
    { id: 'b', x: -186, y: -88, r: 2.4 },
    { id: 'c', x: -132, y: -146, r: 2.1 },
    { id: 'd', x: -112, y: -36, r: 1.9 },
    { id: 'e', x: -42, y: 92, r: 2.5 },
    { id: 'f', x: 60, y: 10, r: 2.1 },
    { id: 'g', x: 84, y: 164, r: 2.3 },
    { id: 'h', x: 168, y: 118, r: 2.1 },
    { id: 'i', x: 190, y: -54, r: 1.9 },
    { id: 'j', x: 214, y: 300, r: 2.5 },
    { id: 'k', x: 286, y: 230, r: 2.4 },
    { id: 'l', x: 382, y: 292, r: 2.5 }
  ],
  paths: [
    ['a', 'b', 'd', 'f', 'i'],
    ['b', 'c', 'd', 'e', 'f'],
    ['a', 'c', 'f', 'k'],
    ['e', 'g', 'h', 'k', 'l'],
    ['f', 'h', 'i', 'k'],
    ['g', 'j', 'l', 'k']
  ]
};

const COMPACT_MESH_TEMPLATE = {
  points: [
    { id: 'm', x: -118, y: -96, r: 1.7 },
    { id: 'n', x: -18, y: -36, r: 2.1 },
    { id: 'o', x: 52, y: 78, r: 1.8 },
    { id: 'p', x: -12, y: 204, r: 2.2 },
    { id: 'q', x: -132, y: 156, r: 2 },
    { id: 'r', x: -188, y: 10, r: 1.6 },
    { id: 's', x: -230, y: 224, r: 1.8 }
  ],
  paths: [
    ['m', 'n', 'o', 'p', 'q'],
    ['n', 'q', 'r', 'm'],
    ['r', 's', 'q', 'p']
  ]
};

function hashString(value) {
  let hash = 2166136261;
  for (let i = 0; i < value.length; i += 1) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function createRandom(seed) {
  let state = seed || 1;
  return function nextRandom() {
    state |= 0;
    state = (state + 0x6D2B79F5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function randomBetween(random, min, max) {
  return min + random() * (max - min);
}

function formatMeshNumber(value) {
  return Number(value.toFixed(1));
}

function rotatePoint(point, rotation) {
  const cos = Math.cos(rotation);
  const sin = Math.sin(rotation);
  return {
    x: point.x * cos - point.y * sin,
    y: point.x * sin + point.y * cos
  };
}

function transformMeshPoint(point, options) {
  const rotated = rotatePoint(point, options.rotation);
  return {
    id: point.id,
    x: formatMeshNumber(
      options.x + rotated.x * options.scale + randomBetween(options.random, -options.jitter, options.jitter)
    ),
    y: formatMeshNumber(
      options.y + rotated.y * options.scale + randomBetween(options.random, -options.jitter, options.jitter)
    ),
    r: formatMeshNumber(point.r * options.scale * randomBetween(options.random, 0.72, 1.08))
  };
}

function createMeshCluster(template, options) {
  const pointById = new Map(
    template.points.map((point) => {
      const transformed = transformMeshPoint(point, options);
      return [point.id, transformed];
    })
  );

  return {
    tone: options.tone,
    paths: template.paths.map((path) => path.map((id) => pointById.get(id))),
    circles: Array.from(pointById.values())
  };
}

function meshPathD(points) {
  return points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x} ${point.y}`)
    .join(' ');
}

function createDepthPattern(seedKey) {
  const random = createRandom(hashString(`${DEPTH_SESSION_SEED}:${seedKey}`));
  const clusters = [];
  const clusterCount = 5;

  for (let index = 0; index < clusterCount; index += 1) {
    const template = random() > 0.34 ? MESH_TEMPLATE : COMPACT_MESH_TEMPLATE;
    const scale = randomBetween(random, 0.58, 1.18);
    clusters.push(
      createMeshCluster(template, {
        random,
        x: randomBetween(random, 130, DEPTH_PATTERN_WIDTH - 130),
        y: randomBetween(random, 120, DEPTH_PATTERN_HEIGHT - 120),
        scale,
        rotation: randomBetween(random, -0.95, 0.95),
        jitter: randomBetween(random, 10, 28),
        tone: index < 3 ? 'primary' : 'secondary'
      })
    );
  }

  const stars = Array.from({ length: 48 }, () => ({
    x: formatMeshNumber(randomBetween(random, 24, DEPTH_PATTERN_WIDTH - 24)),
    y: formatMeshNumber(randomBetween(random, 28, DEPTH_PATTERN_HEIGHT - 28)),
    r: formatMeshNumber(randomBetween(random, 0.7, 1.45))
  }));

  return { clusters, stars };
}

export default function DepthPattern({ className = '', seedKey = 'global' }) {
  const { clusters, stars } = useMemo(() => createDepthPattern(seedKey), [seedKey]);

  return (
    <div className={`depth-pattern ${className}`} aria-hidden="true">
      <svg focusable="false" role="presentation">
        <defs>
          <pattern
            id={DEPTH_PATTERN_ID}
            width={DEPTH_PATTERN_WIDTH}
            height={DEPTH_PATTERN_HEIGHT}
            patternUnits="userSpaceOnUse"
          >
            {clusters.map((cluster, clusterIndex) => (
              <g
                key={`cluster-${clusterIndex}`}
                className={`depth-pattern-cluster depth-pattern-cluster-${cluster.tone}`}
              >
                {cluster.paths.map((path, pathIndex) => (
                  <path key={`cluster-${clusterIndex}-path-${pathIndex}`} d={meshPathD(path)} />
                ))}
                {cluster.circles.map((point) => (
                  <circle
                    key={`cluster-${clusterIndex}-dot-${point.id}`}
                    cx={point.x}
                    cy={point.y}
                    r={point.r}
                  />
                ))}
              </g>
            ))}

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
