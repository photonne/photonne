import type { FakeContext, FakeHandler } from '../fake-api';

/**
 * Upload, notifications, settings and public share links. Tests read and
 * steer this fake through `state.account` (see `accountState`).
 */
export interface AccountState {
	/** Checksums the server "already has" (check-checksums answers them). */
	existingChecksums: string[];
	/** File names that fail once with a 500 before succeeding. */
	failOnce: string[];
	uploaded: string[];
	notifications: Notification[];
	profile: Record<string, unknown> | null;
	passwordChanges: { currentPassword: string; newPassword: string }[];
	accountDeleted: boolean;
	discovery: { folderId: string; included: boolean }[];
	guestUploads: { token: string; name: string | null; pw: string | null; file: string }[];
}

interface Notification {
	id: string;
	type: number;
	title: string;
	message: string;
	isRead: boolean;
	createdAt: string;
	actionUrl: string | null;
	groupKey: string | null;
	groupCount: number;
}

export function accountState(state: Record<string, unknown>): AccountState {
	state.account ??= {
		existingChecksums: [],
		failOnce: [],
		uploaded: [],
		notifications: seedNotifications(),
		profile: null,
		passwordChanges: [],
		accountDeleted: false,
		discovery: [],
		guestUploads: []
	} satisfies AccountState;
	return state.account as AccountState;
}

function seedNotifications(): Notification[] {
	const now = Date.now();
	const at = (minutesAgo: number) => new Date(now - minutesAgo * 60_000).toISOString();
	const items: Notification[] = [
		{
			id: 'n-1',
			type: 3,
			title: 'Álbum compartido visitado',
			message: '"Vacaciones" ha sido visitado 5 veces.',
			isRead: false,
			createdAt: at(5),
			actionUrl: '/albums/album-1',
			groupKey: null,
			groupCount: 1
		},
		{
			id: 'n-2',
			type: 2,
			title: 'Fallo al generar miniaturas',
			message: '3 archivos no se han podido procesar.',
			isRead: false,
			createdAt: at(180),
			actionUrl: '/admin/enrichment-failures?type=Thumbnails',
			groupKey: 'thumbs',
			groupCount: 3
		},
		{
			id: 'n-3',
			type: 1,
			title: 'Reconocimiento facial terminado',
			message: 'Se han encontrado 12 personas nuevas.',
			isRead: true,
			createdAt: at(60 * 30),
			actionUrl: '/people',
			groupKey: null,
			groupCount: 1
		}
	];
	// Older, read ones to fill a second page.
	for (let i = 0; i < 22; i++) {
		items.push({
			id: `n-old-${i}`,
			type: 1,
			title: `Tarea nocturna ${i + 1}`,
			message: 'Terminada sin errores.',
			isRead: true,
			createdAt: at(60 * 24 * (10 + i)),
			actionUrl: null,
			groupKey: null,
			groupCount: 1
		});
	}
	return items;
}

const SHARE_ASSETS = Array.from({ length: 14 }, (_, i) => ({
	id: `00000000-0000-0000-0000-0000000001${String(i).padStart(2, '0')}`,
	fileName: `BODA_${i + 1}.jpg`,
	type: i === 4 ? 'Video' : 'Image',
	fileCreatedAt: `2026-06-${String(10 + (i % 3)).padStart(2, '0')}T12:${String(i).padStart(2, '0')}:00Z`,
	fileSize: 2_400_000,
	width: [4000, 3000, 4000, 6000][i % 4],
	height: [3000, 4000, 4000, 2500][i % 4],
	thumbnailUrl: '',
	contentUrl: ''
}));

function shareContent(token: string, extra: Record<string, unknown> = {}) {
	return {
		token,
		requiresPassword: false,
		wrongPassword: false,
		allowDownload: true,
		allowUpload: false,
		album: {
			name: 'Boda de Marta y Joan',
			description: 'Barcelona, junio de 2026',
			assetCount: SHARE_ASSETS.length,
			coverThumbnailUrl: null
		},
		assets: SHARE_ASSETS,
		expiresAt: '2026-12-31T00:00:00Z',
		...extra
	};
}

async function shareLink(context: FakeContext, token: string) {
	const pw = new URL(context.request.url()).searchParams.get('pw');
	switch (token) {
		case 'boda':
			await context.json(200, shareContent(token, { allowUpload: true }));
			return;
		case 'privado':
			if (pw !== 'clave') {
				await context.json(200, {
					...shareContent(token),
					requiresPassword: true,
					wrongPassword: pw !== null,
					album: null,
					assets: null
				});
				return;
			}
			await context.json(200, shareContent(token, { allowDownload: false }));
			return;
		case 'caducado':
			await context.json(410, { error: 'This link has expired', code: 'share_link_expired' });
			return;
		case 'agotado':
			await context.json(410, {
				error: 'This link has reached its maximum number of views',
				code: 'share_link_max_views'
			});
			return;
		default:
			await context.json(404, { error: 'Share link not found', code: 'share_link_not_found' });
	}
}

function svg(seed: string) {
	let hash = 0;
	for (const char of seed) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
	const hue = hash % 360;
	return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="hsl(${hue} 50% 55%)"/><circle cx="290" cy="90" r="36" fill="hsl(${(hue + 40) % 360} 80% 82%)"/></svg>`;
}

/** The file name and fields of a multipart body (enough for the fake). */
function multipart(context: FakeContext) {
	const body = context.request.postDataBuffer()?.toString('latin1') ?? '';
	const fields: Record<string, string> = {};
	let fileName = '';
	for (const part of body.split(/--[^\r\n]+\r\n/)) {
		const name = /name="([^"]+)"/.exec(part)?.[1];
		if (!name) continue;
		const file = /filename="([^"]*)"/.exec(part)?.[1];
		if (file !== undefined) fileName = Buffer.from(file, 'latin1').toString('utf8');
		else fields[name] = part.split('\r\n\r\n')[1]?.replace(/\r\n$/, '') ?? '';
	}
	return { fileName, fields };
}

const handle: FakeHandler = async (context) => {
	const { method, path, authorized, json, state, route } = context;
	const account = accountState(state);

	// --- Public share links (no session) ------------------------------
	const shareMedia = /^\/api\/share\/([^/]+)\/asset\/([^/]+)\/(thumbnail|content)$/.exec(path);
	if (shareMedia && method === 'GET') {
		await route.fulfill({ status: 200, contentType: 'image/svg+xml', body: svg(shareMedia[2]) });
		return true;
	}
	const shareUpload = /^\/api\/share\/([^/]+)\/upload$/.exec(path);
	if (shareUpload && method === 'POST') {
		const { fileName, fields } = multipart(context);
		account.guestUploads.push({
			token: shareUpload[1],
			name: fields.uploaderName ?? null,
			pw: fields.pw ?? null,
			file: fileName
		});
		await json(200, { message: 'Asset uploaded' });
		return true;
	}
	const share = /^\/api\/share\/([^/]+)$/.exec(path);
	if (share && method === 'GET') {
		await shareLink(context, decodeURIComponent(share[1]));
		return true;
	}

	// Everything below needs the signed-in user.
	const mine =
		path.startsWith('/api/notifications') ||
		path.startsWith('/api/users/me') ||
		path === '/api/assets/check-checksums' ||
		path === '/api/assets/upload' ||
		/^\/api\/folders\/[^/]+\/discovery-visibility$/.test(path);
	if (!mine) return false;
	if (!authorized) {
		await json(401);
		return true;
	}

	// --- Upload --------------------------------------------------------
	if (method === 'POST' && path === '/api/assets/check-checksums') {
		const { checksums } = context.request.postDataJSON() as { checksums: string[] };
		const existing = Object.fromEntries(
			checksums
				.filter((sum) => account.existingChecksums.includes(sum))
				.map((sum) => [sum, `asset-${sum.slice(0, 8)}`])
		);
		await json(200, { existing });
		return true;
	}
	if (method === 'POST' && path === '/api/assets/upload') {
		const { fileName, fields } = multipart(context);
		if (account.failOnce.includes(fileName)) {
			account.failOnce = account.failOnce.filter((name) => name !== fileName);
			await json(500, { error: 'Boom', code: 'server_error' });
			return true;
		}
		account.uploaded.push(fileName);
		state.lastUploadDates = fields;
		await json(200, { message: 'Asset uploaded', assetId: `new-${account.uploaded.length}` });
		return true;
	}

	// --- Notifications -------------------------------------------------
	if (method === 'GET' && path === '/api/notifications') {
		const query = new URL(context.request.url()).searchParams;
		const page = Number(query.get('page') ?? 1);
		const pageSize = Number(query.get('pageSize') ?? 20);
		const unreadOnly = query.get('unreadOnly') === 'true';
		const all = account.notifications.filter((n) => !unreadOnly || !n.isRead);
		await json(200, {
			items: all.slice((page - 1) * pageSize, page * pageSize),
			totalCount: all.length,
			page,
			pageSize,
			totalPages: Math.max(1, Math.ceil(all.length / pageSize)),
			unreadCount: account.notifications.filter((n) => !n.isRead).length
		});
		return true;
	}
	if (method === 'GET' && path === '/api/notifications/unread-count') {
		await json(200, { count: account.notifications.filter((n) => !n.isRead).length });
		return true;
	}
	if (method === 'PATCH' && path === '/api/notifications/read-all') {
		for (const n of account.notifications) n.isRead = true;
		await route.fulfill({ status: 204 });
		return true;
	}
	const read = /^\/api\/notifications\/([^/]+)\/read$/.exec(path);
	if (method === 'PATCH' && read) {
		const item = account.notifications.find((n) => n.id === read[1]);
		if (item) item.isRead = true;
		await route.fulfill({ status: 204 });
		return true;
	}

	// --- Settings ------------------------------------------------------
	if (method === 'GET' && path === '/api/users/me/storage') {
		await json(200, {
			usedBytes: 47_500_000_000,
			quotaBytes: 50_000_000_000,
			photos: 18_240,
			videos: 512,
			photoBytes: 38_000_000_000,
			videoBytes: 9_500_000_000,
			personalPhotos: 12_240,
			personalVideos: 412,
			personalPhotoBytes: 25_000_000_000,
			personalVideoBytes: 8_000_000_000,
			libraries: [
				{
					id: 'lib-1',
					name: 'NAS familiar',
					photos: 6_000,
					videos: 100,
					photoBytes: 13_000_000_000,
					videoBytes: 1_500_000_000
				}
			]
		});
		return true;
	}
	if (method === 'PUT' && path === '/api/users/me') {
		const body = context.request.postDataJSON() as Record<string, unknown>;
		if (body.email === 'taken@photonne.test') {
			await json(400, { error: 'Email already exists', code: 'email_already_exists' });
			return true;
		}
		account.profile = body;
		await json(200, {
			id: '00000000-0000-0000-0000-000000000001',
			role: 'User',
			isActive: true,
			isPrimaryAdmin: false,
			createdAt: '2025-01-15T10:00:00Z',
			lastLoginAt: '2026-10-08T08:00:00Z',
			storageQuotaBytes: 50_000_000_000,
			...body
		});
		return true;
	}
	if (method === 'GET' && path === '/api/users/me/rename-preview') {
		const newUsername = new URL(context.request.url()).searchParams.get('newUsername') ?? '';
		await json(200, {
			isValid: true,
			isNoChange: newUsername === 'ana',
			errorMessage: null,
			currentUsername: 'ana',
			newUsername,
			currentVirtualPath: '/assets/users/ana',
			newVirtualPath: `/assets/users/${newUsername}`,
			currentPhysicalPath: null,
			newPhysicalPath: null,
			folderExistsOnDisk: true,
			assetsToUpdate: 1234,
			foldersToUpdate: 18
		});
		return true;
	}
	if (method === 'POST' && path === '/api/users/me/change-password') {
		const body = context.request.postDataJSON() as AccountState['passwordChanges'][number];
		if (body.currentPassword !== 'secreto') {
			await json(400, {
				error: 'La contraseña actual no es correcta',
				code: 'invalid_current_password'
			});
			return true;
		}
		account.passwordChanges.push(body);
		await json(200, { message: 'Password changed' });
		return true;
	}
	if (method === 'POST' && path === '/api/users/me/delete-account') {
		const { password } = context.request.postDataJSON() as { password: string };
		if (password !== 'secreto') {
			await json(400, { error: 'La contraseña no es correcta', code: 'invalid_password' });
			return true;
		}
		account.accountDeleted = true;
		await route.fulfill({ status: 204 });
		return true;
	}
	const discovery = /^\/api\/folders\/([^/]+)\/discovery-visibility$/.exec(path);
	if (method === 'PUT' && discovery) {
		const { included } = context.request.postDataJSON() as { included: boolean };
		account.discovery.push({ folderId: discovery[1], included });
		await json(200, { folderId: discovery[1], excludedFromDiscovery: !included });
		return true;
	}
	return false;
};

export default handle;
