// =============================================================================
// Orbit Money - Service Worker
// =============================================================================
// Offline read-only support. App shell and static assets are cached so the PWA
// can open without a network connection. API data stays network-first and is
// handled by the frontend's scoped IndexedDB cache.
// =============================================================================

const CACHE_NAME = 'orbit-money-v0.6-offline-readonly-20260502c';
const APP_SHELL_URLS = [
  '/',
  '/manifest.webmanifest',
  '/icon.svg',
  '/icon-192.png',
  '/icon-512.png',
  '/icon-1024.png',
  '/icon-alternate.png',
  '/icon-maskable.svg',
  '/icon-maskable-512.png',
  '/apple-touch-icon.png',
  '/splash-wordmark-light.svg',
  '/splash-wordmark-dark.svg'
];
const APP_SHELL_PATHS = new Set(APP_SHELL_URLS.filter((url) => url !== '/'));

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((k) => k !== CACHE_NAME)
          .map((k) => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request, '/'));
    return;
  }

  event.respondWith(cacheFirst(request));
});

async function networkFirst(request, fallbackUrl) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const response = await fetch(request);
    cache.put(request, response.clone());
    if (fallbackUrl) cache.put(fallbackUrl, response.clone());
    return response;
  } catch {
    return (
      await cache.match(request) ||
      await cache.match(fallbackUrl) ||
      Response.error()
    );
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  const url = new URL(request.url);
  const isAppShellAsset = APP_SHELL_PATHS.has(url.pathname);
  const cached =
    await cache.match(request) ||
    (isAppShellAsset ? await cache.match(request, { ignoreSearch: true }) : null);
  if (cached) return cached;

  const response = await fetch(request);
  cache.put(request, response.clone());
  if (isAppShellAsset) {
    cache.put(url.pathname, response.clone());
  }
  return response;
}
