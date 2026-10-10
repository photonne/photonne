import { version } from '$app/env';
import { assets, immutable } from '$app/manifest';
import { self as sw } from '$app/service-worker';

// Makes the app installable and lets its shell open without a connection (it
// then offers to retry reaching the server). Library data and media are never
// cached here: they go through the API with the HTTP cache headers it sets.

const CACHE = `photonne-${version}`;
const SHELL = '/index.html';
const ASSETS = new Set([...immutable, ...assets].map((file) => `/${file.path.replace(/^\//, '')}`));

sw.addEventListener('install', (event) => {
	event.waitUntil(
		(async () => {
			const cache = await caches.open(CACHE);
			await cache.addAll([...ASSETS]);
			// The shell is the SPA fallback; any app path answers with it.
			await cache.add(new Request('/', { cache: 'no-cache' }));
		})()
	);
	// A new release (or this app replacing the previous Blazor client, whose
	// worker waits to be told) takes over straight away.
	sw.skipWaiting();
});

sw.addEventListener('activate', (event) => {
	event.waitUntil(
		(async () => {
			let replacedOtherClient = false;
			for (const key of await caches.keys()) {
				if (key === CACHE) continue;
				// Older releases of this app are `photonne-<version>`; anything else
				// is the previous web client's (photonne-cache-v1, …).
				if (!/^photonne-\d+$/.test(key)) replacedOtherClient = true;
				await caches.delete(key);
			}
			await sw.clients.claim();
			// Pages still showing the previous web client reload into this one.
			if (replacedOtherClient) {
				for (const client of await sw.clients.matchAll({ type: 'window' })) {
					client.navigate(client.url).catch(() => {});
				}
			}
		})()
	);
});

sw.addEventListener('fetch', (event) => {
	const request = event.request;
	if (request.method !== 'GET') return;
	const url = new URL(request.url);
	if (url.origin !== sw.location.origin) return;
	if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/openapi')) return;

	if (ASSETS.has(url.pathname)) {
		event.respondWith(cacheFirst(request));
	} else if (request.mode === 'navigate') {
		event.respondWith(networkFirstShell(request));
	}
});

async function cacheFirst(request: Request) {
	// Build files are named by their content: a module request (with an
	// Origin header) and the install-time request (without) are the same file
	// even if the server answered with `Vary: Origin`.
	const cached = await caches.match(request, { ignoreVary: true });
	return cached ?? fetch(request);
}

async function networkFirstShell(request: Request) {
	try {
		const response = await fetch(request);
		if (response.ok && response.headers.get('content-type')?.includes('text/html')) {
			const cache = await caches.open(CACHE);
			await cache.put('/', response.clone());
		}
		return response;
	} catch (error) {
		const shell = (await caches.match('/')) ?? (await caches.match(SHELL));
		if (shell) return shell;
		throw error;
	}
}
