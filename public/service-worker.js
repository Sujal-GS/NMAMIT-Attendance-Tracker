const CACHE_NAME = 'nmamit-attendance-v15';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/css/style.css',
  '/js/api.js',
  '/js/app.js',
  '/js/calendar.js',
  '/js/summary.js',
  '/js/heatmap.js',
  '/js/simulator.js',
  '/js/iamarks.js',
  '/js/wrapped.js',
  '/js/dropdown.js',
  '/manifest.json'
];

// Install: Cache Shell Assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS).catch((err) => {
        console.warn('[ServiceWorker] Some assets failed to cache:', err);
      });
    }).then(() => self.skipWaiting())
  );
});

// Activate: Clear Outdated Caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch: Stale-While-Revalidate for Static Assets, Network-Only for dynamic student API routes to prevent multi-user cross-contamination
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Skip non-GET requests
  if (event.request.method !== 'GET') return;

  // Network-Only for dynamic student data and analytics to ensure data privacy and prevent cross-user leak
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/_vercel')) {
    // Only allow universities public directory to be cached
    if (url.pathname === '/api/universities') {
      event.respondWith(
        caches.match(event.request).then((cached) => {
          const fetchPromise = fetch(event.request).then((res) => {
            if (res && res.status === 200) {
              const clone = res.clone();
              caches.open(CACHE_NAME).then((c) => c.put(event.request, clone));
            }
            return res;
          });
          return cached || fetchPromise;
        })
      );
      return;
    }

    event.respondWith(fetch(event.request));
    return;
  }

  // For static assets, serve from cache first, then revalidate in background
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      const fetchPromise = fetch(event.request)
        .then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            const clone = networkResponse.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
          }
          return networkResponse;
        })
        .catch(() => {
          // Network failed, nothing extra to do if cachedResponse exists
        });

      return cachedResponse || fetchPromise;
    })
  );
});
