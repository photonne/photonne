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
				return authorized ? json(route, 200, library.buckets) : json(route, 401);
			default:
				if (path.startsWith('/api/assets/timeline/buckets/')) {
					const key = path.split('/').at(-1)!;
					return authorized ? json(route, 200, library.items(key)) : json(route, 401);
				}
				if (/^\/api\/assets\/[^/]+\/thumbnail$/.test(path)) {
					return route.fulfill({
						status: 200,
						contentType: 'image/svg+xml',
						body: thumbnail(path.split('/')[3])
					});
				}
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

/**
 * A fake library: months newest first, each with deterministic assets of
 * varied shapes (landscape, portrait, square, panorama), a few videos,
 * favourites and Live Photos.
 */
export const library = {
	buckets: [
		{ key: '2026-09', count: 23 },
		{ key: '2026-08', count: 9 },
		{ key: '2026-06', count: 60 },
		{ key: '2025-12', count: 40 },
		{ key: '2024-07', count: 75 },
		{ key: '2023-03', count: 120 }
	],
	items(key: string) {
		const bucket = this.buckets.find((b) => b.key === key);
		const shapes = [1.5, 0.75, 1, 1.78, 0.56, 2.4];
		return Array.from({ length: bucket?.count ?? 0 }, (_, i) => ({
			id: `${key}-${i}`,
			fileName: `IMG_${key.replace('-', '')}_${i}.jpg`,
			fileCreatedAt: `${key}-${String(28 - (i % 27)).padStart(2, '0')}T10:00:00Z`,
			type: i % 11 === 5 ? 'Video' : 'Image',
			tags: i % 13 === 7 ? ['LivePhoto'] : [],
			isFavorite: i % 9 === 2,
			aspectRatio: shapes[i % shapes.length],
			width: 4000,
			height: 3000,
			dominantColor: null,
			thumbnailsGeneratedAt: '2026-10-01T00:00:00Z'
		}));
	}
};

function thumbnail(id: string) {
	let hash = 0;
	for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
	const hue = hash % 360;
	return `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="200"><rect width="300" height="200" fill="hsl(${hue} 55% 55%)"/><circle cx="210" cy="70" r="28" fill="hsl(${(hue + 40) % 360} 80% 80%)"/><path d="M0 200 L90 110 L150 160 L210 120 L300 200Z" fill="hsl(${(hue + 180) % 360} 35% 35%)"/></svg>`;
}
