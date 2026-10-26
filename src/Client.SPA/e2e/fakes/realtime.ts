import type { FakeHandler } from '../fake-api';

/**
 * GET /api/events. Unavailable (503) unless a test queues events in
 * `state.events`: the next reconnect then gets them, once, and the stream ends.
 */
const realtime: FakeHandler = async ({ route, method, path, state }) => {
	if (method !== 'GET' || path !== '/api/events') return false;
	const events = state.events as string[] | undefined;
	if (!events?.length) {
		await route.fulfill({ status: 503 });
		return true;
	}
	state.events = [];
	await route.fulfill({
		status: 200,
		contentType: 'text/event-stream',
		body: ': connected\n\n' + events.map((event) => `event: ${event}\ndata: {}\n\n`).join('')
	});
	return true;
};

export default realtime;
