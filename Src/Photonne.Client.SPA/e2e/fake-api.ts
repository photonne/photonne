import type { Page, Route } from '@playwright/test';

/**
 * A stand-in for the Photonne API at the browser's network layer, so the e2e
 * tests run against the built SPA without a server or database.
 */
export async function fakeApi(page: Page, options: { signedIn?: boolean; offline?: boolean } = {}) {
	let signedIn = options.signedIn ?? false;
	const json = (route: Route, status: number, body?: unknown) =>
		route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body ?? {}) });

	await page.route('**/api/**', async (route) => {
		if (options.offline) return route.abort('connectionrefused');

		const request = route.request();
		const path = new URL(request.url()).pathname;
		const authorized = request.headers()['authorization'] === 'Bearer token-1';

		switch (`${request.method()} ${path}`) {
			case 'POST /api/auth/refresh':
				return signedIn
					? json(route, 200, { token: 'token-1', refreshToken: '' })
					: json(route, 401);
			case 'POST /api/auth/login': {
				const body = request.postDataJSON();
				if (body.username === 'ana' && body.password === 'secreto') {
					signedIn = true;
					return json(route, 200, { token: 'token-1', refreshToken: '', user });
				}
				return json(route, 401);
			}
			case 'POST /api/auth/logout':
				signedIn = false;
				return route.fulfill({ status: 204 });
			case 'GET /api/users/me':
				return authorized ? json(route, 200, user) : json(route, 401);
			case 'GET /api/assets/timeline/buckets':
				return authorized
					? json(route, 200, [
							{ key: '2026-09', count: 12 },
							{ key: '2026-08', count: 3 }
						])
					: json(route, 401);
			default:
				return json(route, 404, { error: 'Not faked', code: 'not_found' });
		}
	});
}

const user = {
	id: '00000000-0000-0000-0000-000000000001',
	username: 'ana',
	email: 'ana@photonne.test',
	role: 'User',
	isActive: true,
	isPrimaryAdmin: false
};
