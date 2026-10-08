import { describe, expect, it, vi } from 'vitest';
import { createAuthFetch } from './auth-fetch.js';

const url = 'https://photonne.test/api/assets/timeline';

function respond(...statuses: number[]) {
	const seen: Request[] = [];
	const fetch = vi.fn(async (request: Request) => {
		seen.push(request);
		return new Response(null, { status: statuses[seen.length - 1] ?? 200 });
	});
	return { fetch: fetch as unknown as typeof globalThis.fetch, seen };
}

describe('createAuthFetch', () => {
	it('replays a 401 once with the refreshed token', async () => {
		const { fetch, seen } = respond(401, 200);
		const refresh = vi.fn(async () => 'fresh');
		const authFetch = createAuthFetch({ fetch, refresh });

		const response = await authFetch(url, {
			method: 'POST',
			headers: { Authorization: 'Bearer stale' },
			body: JSON.stringify({ ids: [1] })
		});

		expect(response.status).toBe(200);
		expect(refresh).toHaveBeenCalledOnce();
		expect(seen).toHaveLength(2);
		expect(seen[1].headers.get('Authorization')).toBe('Bearer fresh');
		expect(await seen[1].text()).toBe('{"ids":[1]}');
	});

	it('gives up when the session is over', async () => {
		const { fetch, seen } = respond(401);
		const authFetch = createAuthFetch({ fetch, refresh: async () => null });

		const response = await authFetch(url, { headers: { Authorization: 'Bearer stale' } });

		expect(response.status).toBe(401);
		expect(seen).toHaveLength(1);
	});

	it('leaves unauthenticated requests alone', async () => {
		const { fetch, seen } = respond(401);
		const refresh = vi.fn(async () => 'fresh');
		const authFetch = createAuthFetch({ fetch, refresh });

		const response = await authFetch('https://photonne.test/api/auth/login', { method: 'POST' });

		expect(response.status).toBe(401);
		expect(refresh).not.toHaveBeenCalled();
		expect(seen).toHaveLength(1);
	});

	it('does not refresh on other errors', async () => {
		const { fetch } = respond(403);
		const refresh = vi.fn(async () => 'fresh');
		const authFetch = createAuthFetch({ fetch, refresh });

		const response = await authFetch(url, { headers: { Authorization: 'Bearer ok' } });

		expect(response.status).toBe(403);
		expect(refresh).not.toHaveBeenCalled();
	});
});
