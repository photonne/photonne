import { invalidateLibrary, queryClient } from '#lib/api/query-client.js';
import { backoff, SseParser } from './sse.js';

const NOTIFICATION_QUERIES = [
	'getNotifications',
	'getNotificationStats',
	'getUnreadNotificationsCount'
];

// An import sends "library" every second for minutes: the views reload at
// most this often, the last one always after the burst.
const LIBRARY_EVERY = 5000;
let lastLibrary = 0;
let libraryTimer: ReturnType<typeof setTimeout> | undefined;

function reloadLibrary() {
	if (libraryTimer) return;
	const wait = Math.max(0, lastLibrary + LIBRARY_EVERY - Date.now());
	libraryTimer = setTimeout(() => {
		libraryTimer = undefined;
		lastLibrary = Date.now();
		invalidateLibrary();
	}, wait);
}

/** What each server event makes stale here. */
function apply(event: string) {
	if (event === 'library') reloadLibrary();
	else if (event === 'notifications')
		queryClient.invalidateQueries({
			predicate: (query) => {
				const id = (query.queryKey[0] as { _id?: unknown } | undefined)?._id;
				return (
					typeof id === 'string' && NOTIFICATION_QUERIES.some((prefix) => id.startsWith(prefix))
				);
			}
		});
}

/**
 * Listens to `GET /api/events` while signed in, so photos that arrive from
 * the phone or another tab show up without reloading. A plain fetch rather
 * than EventSource, which can't send the Bearer token. Reconnects with a
 * growing wait; on a reconnect everything library-wide is reloaded, since
 * changes made while away weren't heard. Returns the function that stops it.
 */
export function startEventStream(auth: {
	accessToken: () => string | null;
	refresh: () => Promise<string | null>;
}): () => void {
	let stopped = false;
	let controller: AbortController | null = null;
	let attempt = 0;
	let timer: ReturnType<typeof setTimeout> | undefined;

	async function connect() {
		if (stopped) return;
		controller = new AbortController();
		try {
			let token = auth.accessToken();
			let response = await fetch('/api/events', {
				headers: token ? { Authorization: `Bearer ${token}` } : {},
				signal: controller.signal
			});
			if (response.status === 401) {
				token = await auth.refresh();
				if (!token) return; // Signed out: nothing to listen to.
				response = await fetch('/api/events', {
					headers: { Authorization: `Bearer ${token}` },
					signal: controller.signal
				});
			}
			if (!response.ok || !response.body) throw new Error(`events ${response.status}`);

			if (attempt > 0) reloadLibrary();
			attempt = 0;
			const parser = new SseParser();
			const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
			for (;;) {
				const { value, done } = await reader.read();
				if (done) break;
				for (const event of parser.push(value)) apply(event);
			}
		} catch {
			// Network gone, server restarting, or stopped: the retry below decides.
		}
		if (stopped) return;
		timer = setTimeout(connect, backoff(attempt++));
	}

	connect();
	return () => {
		stopped = true;
		clearTimeout(timer);
		controller?.abort();
	};
}
