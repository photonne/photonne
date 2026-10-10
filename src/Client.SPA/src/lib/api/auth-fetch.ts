/**
 * A `fetch` that survives an expired access token: when a request that
 * carried a Bearer token comes back 401, it asks for a fresh token once and
 * replays the request with it. Requests without a token (login, refresh, the
 * public share endpoints) are passed through untouched, so a failing refresh
 * can't loop.
 */
export function createAuthFetch(options: {
	fetch: typeof globalThis.fetch;
	/** A new access token, or null when the session is over. */
	refresh: () => Promise<string | null>;
}): typeof globalThis.fetch {
	return async (input, init) => {
		const request = new Request(input, init);
		// Cloned before sending: a body can only be read once.
		const replay = request.headers.has('Authorization') ? request.clone() : null;

		const response = await options.fetch(request);
		if (response.status !== 401 || replay === null) return response;

		const token = await options.refresh();
		if (token === null) return response;

		replay.headers.set('Authorization', `Bearer ${token}`);
		return options.fetch(replay);
	};
}
