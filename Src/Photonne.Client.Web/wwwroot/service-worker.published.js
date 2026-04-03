// Blazor PWA service worker — cache app shell, cache-first for thumbnails

const SW_VERSION = '1.1.0';
const CACHE_NAME = 'photonne-cache-v1';
const THUMBNAIL_CACHE = 'photonne-thumbnails-v1';
const THUMBNAIL_CACHE_MAX_ENTRIES = 1000;

async function trimThumbnailCache(cache) {
    const keys = await cache.keys();
    if (keys.length > THUMBNAIL_CACHE_MAX_ENTRIES) {
        const toDelete = keys.slice(0, keys.length - THUMBNAIL_CACHE_MAX_ENTRIES);
        await Promise.all(toDelete.map(k => cache.delete(k)));
    }
}

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache =>
            fetch('service-worker-assets.js')
                .then(response => response.text())
                .then(text => {
                    // The manifest is a JS file that assigns to self.assetsManifest
                    const fn = new Function(text);
                    fn();
                    const assets = self.assetsManifest.assets
                        .filter(a => a.url !== 'service-worker.js')
                        .map(a => new Request(a.url, { integrity: a.hash, cache: 'no-cache' }));
                    return cache.addAll(assets);
                })
        )
    );
    // Do NOT call self.skipWaiting() here — the app will prompt the user
    // and call it explicitly via the SKIP_WAITING message below.
});

const KNOWN_CACHES = [CACHE_NAME, THUMBNAIL_CACHE];

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(keys.filter(k => !KNOWN_CACHES.includes(k)).map(k => caches.delete(k)))
        )
    );
    self.clients.claim();
});

self.addEventListener('message', event => {
    if (event.data?.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
    if (event.data?.type === 'GET_VERSION') {
        event.ports[0]?.postMessage({ version: SW_VERSION });
    }
});

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);

    // Cache-first for thumbnails (immutable per assetId+size, safe to cache long-term)
    if (/^\/api\/assets\/[^/]+\/thumbnail$/.test(url.pathname)) {
        event.respondWith(
            caches.open(THUMBNAIL_CACHE).then(cache =>
                cache.match(event.request).then(cached => {
                    if (cached) return cached;
                    return fetch(event.request).then(response => {
                        if (response.ok) {
                            cache.put(event.request, response.clone())
                                .then(() => trimThumbnailCache(cache));
                        }
                        return response;
                    });
                })
            )
        );
        return;
    }

    // Always go to network for other API calls
    if (url.pathname.startsWith('/api/')) {
        return;
    }

    // For navigation requests, serve index.html from cache (SPA routing)
    if (event.request.mode === 'navigate') {
        event.respondWith(
            caches.match('index.html').then(cached => cached || fetch(event.request))
        );
        return;
    }

    // For other requests, try cache first, then network
    event.respondWith(
        caches.match(event.request).then(cached => cached || fetch(event.request))
    );
});
