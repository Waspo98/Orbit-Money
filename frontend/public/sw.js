// =============================================================================
// Orbit Money — Service Worker
// =============================================================================
// Minimal SW that satisfies the "installable PWA" criteria without adding
// offline complexity. We pass every request straight through to the network.
//
// Full offline caching (stale-while-revalidate, precache manifest, etc.) is
// deferred to a later iteration. If/when we add it, we'll bump the CACHE_NAME
// below to invalidate the existing cache.
// =============================================================================

const CACHE_NAME = 'orbit-money-v0.51-brand-20260428a';

self.addEventListener('install', (event) => {
  // Take over immediately on first install.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // Clean up any old caches if we ever added caching later.
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
  // Pass-through. No caching (yet).
  // Having a fetch listener — even a trivial one — is required by some
  // browsers to consider the PWA installable.
  event.respondWith(fetch(event.request));
});
