import { createAuthFetch } from './auth-fetch';
import { client } from './generated/client.gen';

export * from './generated';
export { apiErrorCode } from './errors';

/**
 * Points the generated client at this origin (the dev server proxies /api)
 * and wires authentication: the Bearer token goes on every operation the
 * contract marks as secured, and a 401 triggers one refresh and a replay.
 */
export function configureApiClient(auth: {
	accessToken: () => string | null;
	refresh: () => Promise<string | null>;
}) {
	client.setConfig({
		baseUrl: globalThis.location?.origin ?? '',
		auth: () => auth.accessToken() ?? undefined,
		fetch: createAuthFetch({ fetch: (request) => globalThis.fetch(request), refresh: auth.refresh })
	});
}
