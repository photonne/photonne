import type { Page, Route } from '@playwright/test';

/**
 * A stand-in for the Photonne API at the browser's network layer, so the e2e
 * tests run against the built SPA without a server or database.
 */
export async function fakeApi(page: Page, options: { signedIn?: boolean; offline?: boolean } = {}) {
	let signedIn = options.signedIn ?? false;
	const favorites = new Set<string>();
	const descriptions: string[] = [];
	const added: string[] = [];
	const removed: string[] = [];
	const restored: string[] = [];
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
				if (/^\/api\/assets\/[^/]+\/favorite$/.test(path) && request.method() === 'POST') {
					const id = path.split('/')[3];
					if (favorites.has(id)) favorites.delete(id);
					else favorites.add(id);
					return json(route, 200, { isFavorite: favorites.has(id) });
				}
				if (/^\/api\/assets\/[^/]+\/description$/.test(path)) {
					descriptions.push(request.postDataJSON().caption);
					return json(route, 200, { caption: request.postDataJSON().caption });
				}
				if (/^\/api\/assets\/[0-9]{4}-[0-9]{2}-[0-9]+$/.test(path) && request.method() === 'GET') {
					return authorized
						? json(route, 200, detail(path.split('/')[3], favorites))
						: json(route, 401);
				}
				if (path === '/api/tags') return json(route, 200, ['familia', 'viaje']);
				if (path === '/api/albums' && request.method() === 'GET') return json(route, 200, albums);
				if (path === '/api/folders' && request.method() === 'GET') return json(route, 200, folders);
				if (path === '/api/folders/tree') return json(route, 200, folders);
				if (/^\/api\/albums\/[^/]+\/assets\/batch$/.test(path)) {
					added.push(...request.postDataJSON().assetIds);
					return json(route, 200, { added: request.postDataJSON().assetIds.length, skipped: 0 });
				}
				if (path === '/api/assets/delete' || path === '/api/assets/archive') {
					removed.push(...request.postDataJSON().assetIds);
					return route.fulfill({ status: 204 });
				}
				if (path === '/api/assets/restore' || path === '/api/assets/unarchive') {
					restored.push(...request.postDataJSON().assetIds);
					return route.fulfill({ status: 204 });
				}
				if (path.startsWith('/api/assets/timeline/buckets/')) {
					const key = path.split('/').at(-1)!;
					return authorized ? json(route, 200, library.items(key)) : json(route, 401);
				}
				if (/^\/api\/assets\/[^/]+\/(thumbnail|content)$/.test(path)) {
					return route.fulfill({
						status: 200,
						contentType: 'image/svg+xml',
						body: thumbnail(path.split('/')[3])
					});
				}
				return json(route, 404, { error: 'Not faked', code: 'not_found' });
		}
	});
	return { descriptions, added, removed, restored };
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

function detail(id: string, favorites: Set<string>) {
	const [year, month, index] = id.split('-');
	const item = library.items(`${year}-${month}`)[Number(index)];
	return {
		...item,
		fullPath: `/assets/users/ana/${item.fileName}`,
		fileSize: 3_400_000,
		fileModifiedAt: item.fileCreatedAt,
		capturedAt: item.fileCreatedAt,
		extension: '.jpg',
		scannedAt: item.fileCreatedAt,
		checksum: `sum-${id}`,
		hasExif: true,
		hasThumbnails: true,
		folderId: null,
		folderPath: '/assets/users/ana/Camera',
		exif: {
			dateTaken: item.fileCreatedAt,
			cameraMake: 'Apple',
			cameraModel: 'iPhone 15',
			width: 4000,
			height: 3000,
			orientation: 1,
			latitude: 41.4,
			longitude: 2.17,
			altitude: null,
			iso: 50,
			aperture: 1.8,
			shutterSpeed: 0.004,
			focalLength: 6.9,
			description: null,
			keywords: null,
			software: null,
			placeName: 'Barcelona',
			placeCountryCode: 'ES'
		},
		thumbnails: [],
		userTags: ['familia'],
		autoTags: ['playa'],
		syncStatus: 'Synced',
		isFavorite: favorites.has(id) || item.isFavorite,
		isArchived: false,
		isFileMissing: false,
		caption: null,
		aiDescription: null,
		isReadOnly: false,
		isOwner: true,
		canEdit: true,
		canSaveMotionFrame: false
	};
}

const albums = [
	{
		id: 'album-1',
		name: 'Vacaciones',
		description: null,
		createdAt: '2026-01-01T00:00:00Z',
		updatedAt: '2026-01-01T00:00:00Z',
		assetCount: 12,
		coverThumbnailUrl: null,
		previewThumbnailUrls: [],
		isOwner: true,
		isShared: false,
		sharedWithCount: 0,
		canRead: true,
		canWrite: true,
		canDelete: true,
		canManagePermissions: true,
		hasActiveShareLink: false,
		isPinned: true,
		pinnedAt: '2026-01-01T00:00:00Z',
		kind: 'Manual'
	}
];

const folders = [
	{
		id: 'folder-1',
		path: '/assets/users/ana/Camera',
		name: 'Camera',
		parentFolderId: null,
		createdAt: '2026-01-01T00:00:00Z',
		assetCount: 40,
		firstAssetId: null,
		previewAssetIds: [],
		isShared: false,
		isOwner: true,
		canWrite: true,
		canDelete: true,
		sharedWithCount: 0,
		externalLibraryId: null,
		excludedFromDiscovery: false,
		isPinned: false,
		pinnedAt: null,
		subFolders: []
	}
];
