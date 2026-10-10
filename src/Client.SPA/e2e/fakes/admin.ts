import type { Page } from '@playwright/test';
import type {
	AdminStatsResponse,
	AttributionResponse,
	BackgroundTaskResponse,
	DemoInfoResponse,
	ExternalLibraryDto,
	ExternalLibraryPermissionDto,
	IndexingCoverageResponse,
	MonthlyGrowthPoint,
	PublicVersionResponse,
	RenamePreviewDto,
	ScanProgressUpdate,
	SharedTrashItemResponse,
	ShareableUserDto,
	TrashStatsResponse,
	UserDto,
	VersionInfoResponse
} from '../src/lib/api/generated/types.gen';
import type { FakeContext, FakeHandler } from '../fake-api';

const GIB = 1024 ** 3;

export const ADMIN_ID = '00000000-0000-0000-0000-000000000001';

/** The signed-in admin (the core fake signs in "ana" as a plain user). */
export const adminUser: UserDto = {
	id: ADMIN_ID,
	username: 'ana',
	email: 'ana@photonne.test',
	role: 'Admin',
	firstName: 'Ana',
	lastName: 'García',
	isActive: true,
	isPrimaryAdmin: true,
	createdAt: '2025-01-10T09:00:00Z',
	lastLoginAt: '2026-10-08T08:30:00Z',
	storageQuotaBytes: null
};

/** Signs in as the admin: the core fake, plus /api/users/me answering with an admin. */
export async function fakeAdminApi(page: Page) {
	const { fakeApi } = await import('../fake-api');
	const api = await fakeApi(page, { signedIn: true });
	// Registered after the core route, so it wins for this one path.
	await page.route('**/api/users/me', (route) =>
		route.request().headers()['authorization'] === 'Bearer token-1'
			? route.fulfill({
					status: 200,
					contentType: 'application/json',
					body: JSON.stringify(admin(api.state).me)
				})
			: route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
	);
	return api;
}

interface AdminState {
	me: UserDto;
	users: UserDto[];
	libraries: ExternalLibraryDto[];
	permissions: Record<string, ExternalLibraryPermissionDto[]>;
	sharedTrash: SharedTrashItemResponse[];
	tasks: BackgroundTaskResponse[];
	/** What the tests check: requests the pages sent. */
	sent: { method: string; path: string; body?: unknown }[];
	restoredFile: string | null;
	backupLevel: string | null;
}

/** The admin area's fake data, created on first use in each test. */
export function admin(state: Record<string, unknown>): AdminState {
	state.admin ??= seed();
	return state.admin as AdminState;
}

function seed(): AdminState {
	const users: UserDto[] = [
		adminUser,
		{
			id: '00000000-0000-0000-0000-000000000002',
			username: 'luis',
			email: 'luis@photonne.test',
			role: 'Admin',
			firstName: 'Luis',
			lastName: 'Martín',
			isActive: true,
			isPrimaryAdmin: false,
			createdAt: '2025-03-02T10:00:00Z',
			lastLoginAt: '2026-10-01T18:12:00Z',
			storageQuotaBytes: null
		},
		{
			id: '00000000-0000-0000-0000-000000000003',
			username: 'marta',
			email: 'marta@photonne.test',
			role: 'User',
			firstName: 'Marta',
			lastName: 'Ruiz',
			isActive: true,
			isPrimaryAdmin: false,
			createdAt: '2025-06-20T12:00:00Z',
			lastLoginAt: '2026-09-28T07:45:00Z',
			storageQuotaBytes: 50 * GIB
		},
		{
			id: '00000000-0000-0000-0000-000000000004',
			username: 'jorge',
			email: 'jorge@photonne.test',
			role: 'User',
			firstName: 'Jorge',
			lastName: null,
			isActive: false,
			isPrimaryAdmin: false,
			createdAt: '2026-02-11T16:00:00Z',
			lastLoginAt: null,
			storageQuotaBytes: 5 * GIB
		}
	];
	return {
		me: adminUser,
		users,
		libraries: [
			library('lib-1', 'NAS fotos', '/mnt/nas/fotos', '@daily', 'Completed', 18_432),
			library('lib-2', 'Archivo escaneado', '/mnt/archivo', null, 'Idle', 0)
		],
		permissions: {
			'lib-1': [
				{
					id: 'perm-1',
					userId: users[2].id!,
					username: 'marta',
					email: 'marta@photonne.test',
					canRead: true,
					grantedAt: '2026-05-01T10:00:00Z',
					grantedByUserId: ADMIN_ID
				}
			]
		},
		sharedTrash: Array.from({ length: 6 }, (_, i) => ({
			id: `shared-${i}`,
			fileName: `IMG_20260${(i % 3) + 7}_${i}.jpg`,
			fullPath: `/assets/shared/Familia/IMG_${i}.jpg`,
			fileSize: 2_400_000 + i * 100_000,
			type: i === 4 ? 'Video' : 'Image',
			extension: '.jpg',
			hasThumbnails: true,
			width: i % 2 ? 3000 : 4000,
			height: i % 2 ? 4000 : 3000,
			deletedAt: `2026-${i < 3 ? '10' : '09'}-0${(i % 5) + 1}T10:00:00Z`,
			deletedByUsername: i % 2 ? 'marta' : 'luis',
			deletedFromPath: '/assets/shared/Familia',
			deletedFromFolderName: 'Familia'
		})),
		tasks: [],
		sent: [],
		restoredFile: null,
		backupLevel: null
	};
}

function library(
	id: string,
	name: string,
	path: string,
	cronSchedule: string | null,
	lastScanStatus: string,
	assetCount: number
): ExternalLibraryDto {
	const scanned = lastScanStatus !== 'Idle';
	return {
		id,
		name,
		path,
		importSubfolders: true,
		cronSchedule,
		lastScannedAt: scanned ? '2026-10-07T03:00:00Z' : null,
		lastScanStatus,
		lastScanAssetsFound: scanned ? assetCount : null,
		lastScanAssetsAdded: scanned ? 120 : null,
		lastScanAssetsRemoved: scanned ? 3 : null,
		assetCount,
		createdAt: '2025-04-01T10:00:00Z'
	};
}

const stats: AdminStatsResponse = {
	totalPhotos: 48_210,
	totalVideos: 3_904,
	totalBytes: 612 * GIB,
	users: [
		{
			userId: ADMIN_ID,
			displayName: 'Ana García',
			email: 'ana@photonne.test',
			photos: 30_100,
			videos: 2_400,
			photoBytes: 210 * GIB,
			videoBytes: 180 * GIB
		},
		{
			userId: '00000000-0000-0000-0000-000000000002',
			displayName: 'Luis Martín',
			email: 'luis@photonne.test',
			photos: 12_010,
			videos: 1_204,
			photoBytes: 90 * GIB,
			videoBytes: 70 * GIB
		},
		{
			userId: '00000000-0000-0000-0000-000000000003',
			displayName: 'Marta Ruiz',
			email: 'marta@photonne.test',
			photos: 6_100,
			videos: 300,
			photoBytes: 32 * GIB,
			videoBytes: 10 * GIB
		}
	]
};

function growth(): MonthlyGrowthPoint[] {
	const points: MonthlyGrowthPoint[] = [];
	const now = new Date();
	for (let back = 0; back < 30; back++) {
		const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
		if (back % 7 === 3) continue; // a month without new photos
		points.push({
			year: date.getUTCFullYear(),
			month: date.getUTCMonth() + 1,
			photos: 400 + ((back * 137) % 900),
			videos: 20 + ((back * 53) % 120)
		});
	}
	return points;
}

const coverage: IndexingCoverageResponse = {
	hasResult: true,
	verifiedAtUtc: '2026-10-08T03:10:00Z',
	totalFiles: 52_400,
	indexed: 52_114,
	unsupported: 280,
	unindexed: 6,
	unindexedPaths: [
		'/mnt/nas/fotos/2019/IMG_0001.HEIC',
		'/mnt/nas/fotos/2019/IMG_0002.HEIC',
		'/assets/users/ana/Camera/VID_2024.mov'
	],
	unindexedTruncated: true,
	offlineLibraries: 0
};

const trashStats: TrashStatsResponse = {
	totalItems: 214,
	totalBytes: 3.2 * GIB,
	expiredItems: 12,
	retentionDays: 30,
	maxQuotaMb: 0,
	overQuotaUsers: 0,
	overQuotaBytes: 0,
	perUser: []
};

const version: VersionInfoResponse = {
	currentVersion: '1.8.2',
	latestVersion: '1.9.0',
	latestReleaseUrl: 'https://github.com/photonne/photonne/releases/tag/v1.9.0',
	releaseNotes: '- Nuevo cliente web\n- Mejoras en el reconocimiento facial',
	publishedAt: '2026-10-01T12:00:00Z',
	hasUpdate: true,
	isAhead: false,
	checkError: null,
	checkedAt: '2026-10-08T09:00:00Z'
};

const publicVersion: PublicVersionResponse = { version: '1.8.2', minClientVersion: '1.5.0' };

const attributions: AttributionResponse[] = [
	{
		name: 'GeoNames',
		license: 'CC BY 4.0',
		licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
		sourceUrl: 'https://www.geonames.org/',
		notice: 'Contiene datos de GeoNames (geonames.org) bajo licencia CC BY 4.0.',
		datasetDate: '2026-08-01T00:00:00Z'
	}
];

const demoInfo: DemoInfoResponse = {
	enabled: false,
	demoUsername: null,
	demoPassword: null,
	resetIntervalHours: null,
	nextResetAt: null
};

const scanUpdates: ScanProgressUpdate[] = [
	{
		message: 'Buscando archivos…',
		percentage: 10,
		assetsFound: 0,
		assetsIndexed: 0,
		assetsMarkedMissing: 0,
		isCompleted: false,
		error: null,
		taskId: 'task-scan-1'
	},
	{
		message: 'Indexando 40 de 80',
		percentage: 55,
		assetsFound: 80,
		assetsIndexed: 40,
		assetsMarkedMissing: 0,
		isCompleted: false,
		error: null,
		taskId: 'task-scan-1'
	},
	{
		message: 'Escaneo completado',
		percentage: 100,
		assetsFound: 80,
		assetsIndexed: 78,
		assetsMarkedMissing: 2,
		isCompleted: true,
		error: null,
		taskId: 'task-scan-1'
	}
];

function body(context: FakeContext) {
	try {
		return context.request.postDataJSON();
	} catch {
		return undefined;
	}
}

const ok = (context: FakeContext, status: number, payload?: unknown) =>
	context.json(status, payload).then(() => true);

const handle: FakeHandler = async (context) => {
	const { method, path, route } = context;
	const area =
		/^\/api\/(users|libraries|tasks|admin|settings|version|attributions|assets\/shared-trash)(\/|$)/.test(
			path
		);
	if (!area) return false;

	const data = admin(context.state);
	if (method !== 'GET') data.sent.push({ method, path, body: body(context) });
	const url = new URL(context.request.url());

	// ── Users ──────────────────────────────────────────────────────────
	if (method === 'GET' && path === '/api/users') return ok(context, 200, data.users);
	if (method === 'GET' && path === '/api/users/shareable') {
		const shareable: ShareableUserDto[] = data.users
			.filter((u) => u.id !== data.me.id && u.isActive)
			.map((u) => ({ id: u.id!, username: u.username!, email: u.email! }));
		return ok(context, 200, shareable);
	}
	if (method === 'POST' && path === '/api/users') {
		const request = body(context);
		if (data.users.some((u) => u.username === request.username)) {
			return ok(context, 400, {
				error: 'Username or email already exists',
				code: 'user_already_exists'
			});
		}
		const created: UserDto = {
			id: `00000000-0000-0000-0000-0000000001${String(data.users.length).padStart(2, '0')}`,
			username: request.username,
			email: request.email,
			role: request.role ?? 'User',
			firstName: request.firstName,
			lastName: request.lastName,
			isActive: request.isActive ?? true,
			isPrimaryAdmin: false,
			createdAt: new Date().toISOString(),
			lastLoginAt: null,
			storageQuotaBytes: request.storageQuotaBytes ?? null
		};
		data.users.push(created);
		return ok(context, 201, created);
	}
	const renamePreview = /^\/api\/users\/([^/]+)\/rename-preview$/.exec(path);
	if (method === 'GET' && renamePreview) {
		const user = data.users.find((u) => u.id === renamePreview[1])!;
		const next = url.searchParams.get('newUsername') ?? '';
		const preview: RenamePreviewDto = {
			isValid: next !== 'luis',
			isNoChange: false,
			errorMessage: next === 'luis' ? 'Ese nombre de usuario ya existe' : null,
			currentUsername: user.username!,
			newUsername: next,
			currentVirtualPath: `/assets/users/${user.username}`,
			newVirtualPath: `/assets/users/${next}`,
			currentPhysicalPath: `/data/assets/users/${user.username}`,
			newPhysicalPath: `/data/assets/users/${next}`,
			folderExistsOnDisk: true,
			assetsToUpdate: 1520,
			foldersToUpdate: 12
		};
		return ok(context, 200, preview);
	}
	const userAction = /^\/api\/users\/([^/]+)\/(reset-password|promote-to-primary)$/.exec(path);
	if (method === 'POST' && userAction) {
		const [, id, action] = userAction;
		if (action === 'reset-password')
			return ok(context, 200, { message: 'Password reset successfully' });
		const previous = data.me.id!;
		data.users = data.users.map((u) => ({ ...u, isPrimaryAdmin: u.id === id }));
		data.me = data.users.find((u) => u.id === previous)!;
		return ok(context, 200, {
			message: 'ok',
			previousPrimaryUserId: previous,
			newPrimaryUserId: id
		});
	}
	const userPath = /^\/api\/users\/([0-9a-f-]{36})$/.exec(path);
	if (userPath) {
		const index = data.users.findIndex((u) => u.id === userPath[1]);
		if (index < 0) return ok(context, 404);
		if (method === 'PUT') {
			const request = body(context);
			const user = { ...data.users[index] };
			for (const key of [
				'username',
				'email',
				'firstName',
				'lastName',
				'role',
				'isActive'
			] as const) {
				if (request[key] !== null && request[key] !== undefined)
					(user as Record<string, unknown>)[key] = request[key];
			}
			if (request.storageQuotaBytes !== null && request.storageQuotaBytes !== undefined) {
				user.storageQuotaBytes =
					request.storageQuotaBytes === -1 ? null : request.storageQuotaBytes;
			}
			data.users[index] = user;
			return ok(context, 200, user);
		}
		if (method === 'DELETE') {
			if (data.users[index].isPrimaryAdmin) {
				return ok(context, 400, { error: 'protected', code: 'primary_admin_protected' });
			}
			data.users.splice(index, 1);
			await route.fulfill({ status: 204 });
			return true;
		}
	}

	// ── Settings (user defaults for new accounts) ─────────────────────
	const userDefaults: Record<string, string> = {
		'UserSettings.DefaultRole': 'User',
		'UserSettings.DefaultIsActive': 'true',
		'UserSettings.DefaultStorageQuotaGb': '10'
	};
	const key = url.searchParams.get('key') ?? '';
	if (method === 'GET' && path === '/api/settings' && key in userDefaults) {
		return ok(context, 200, { key, value: userDefaults[key] });
	}
	if (method === 'GET' && path === '/api/settings/server-info') {
		return ok(context, 200, { processorCount: 8 });
	}

	// ── Dashboard ──────────────────────────────────────────────────────
	if (method === 'GET') {
		const fixed: Record<string, unknown> = {
			'/api/admin/stats': stats,
			'/api/admin/stats/growth': growth(),
			'/api/admin/indexing-coverage': coverage,
			'/api/admin/maintenance/ml-pending-total': { count: 1_250 },
			'/api/admin/trash/stats': trashStats,
			'/api/admin/version': version,
			'/api/version': publicVersion,
			'/api/version/latest-release': {
				latestVersion: '1.9.0',
				releaseUrl: version.latestReleaseUrl
			},
			'/api/attributions': attributions,
			'/api/admin/demo-info': demoInfo,
			'/api/admin/enrichment/queue-summary': {
				types: {
					FaceRecognition: { inQueue: 800, processing: 2, retrying: 0, failed: 3 },
					ObjectDetection: { inQueue: 450, processing: 1, retrying: 1, failed: 0 }
				}
			}
		};
		if (path in fixed) return ok(context, 200, fixed[path]);
	}

	// ── External libraries ────────────────────────────────────────────
	if (method === 'GET' && path === '/api/libraries') return ok(context, 200, data.libraries);
	if (method === 'POST' && path === '/api/libraries') {
		const request = body(context);
		if (!request.path.startsWith('/mnt/')) {
			return ok(context, 400, { error: 'Directory does not exist', code: 'directory_not_found' });
		}
		const created = {
			...library(
				`lib-${data.libraries.length + 1}`,
				request.name,
				request.path,
				request.cronSchedule,
				'Idle',
				0
			),
			importSubfolders: request.importSubfolders
		};
		data.libraries.unshift(created);
		return ok(context, 201, created);
	}
	const scan = /^\/api\/libraries\/([^/]+)\/scan\/stream$/.exec(path);
	if (method === 'GET' && scan) {
		const index = data.libraries.findIndex((l) => l.id === scan[1]);
		data.libraries[index] = {
			...data.libraries[index],
			lastScanStatus: 'Completed',
			lastScannedAt: new Date().toISOString(),
			lastScanAssetsFound: 80,
			lastScanAssetsAdded: 78,
			lastScanAssetsRemoved: 2,
			assetCount: data.libraries[index].assetCount + 78
		};
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(scanUpdates)
		});
		return true;
	}
	const permissions = /^\/api\/libraries\/([^/]+)\/permissions(?:\/([^/]+))?$/.exec(path);
	if (permissions) {
		const [, id, userId] = permissions;
		const list = (data.permissions[id] ??= []);
		if (method === 'GET') return ok(context, 200, list);
		if (method === 'POST') {
			const request = body(context);
			const user = data.users.find((u) => u.id === request.userId)!;
			const granted: ExternalLibraryPermissionDto = {
				id: `perm-${list.length + 10}`,
				userId: user.id!,
				username: user.username!,
				email: user.email!,
				canRead: true,
				grantedAt: new Date().toISOString(),
				grantedByUserId: ADMIN_ID
			};
			list.push(granted);
			return ok(context, 200, granted);
		}
		if (method === 'DELETE') {
			data.permissions[id] = list.filter((p) => p.userId !== userId);
			await route.fulfill({ status: 204 });
			return true;
		}
	}
	const libraryPath = /^\/api\/libraries\/([^/]+)$/.exec(path);
	if (libraryPath) {
		const index = data.libraries.findIndex((l) => l.id === libraryPath[1]);
		if (index < 0) return ok(context, 404, { error: 'not found', code: 'library_not_found' });
		if (method === 'PUT') {
			const request = body(context);
			data.libraries[index] = { ...data.libraries[index], ...request };
			await route.fulfill({ status: 204 });
			return true;
		}
		if (method === 'DELETE') {
			data.libraries.splice(index, 1);
			await route.fulfill({ status: 204 });
			return true;
		}
	}
	// Background tasks belong to the tasks area; only answered here for a
	// library scan a test has put in the list.
	if (method === 'GET' && path === '/api/tasks' && data.tasks.length) {
		return ok(context, 200, data.tasks);
	}
	const task = /^\/api\/tasks\/([^/]+)(\/stream)?$/.exec(path);
	if (task && data.tasks.some((t) => t.id === task[1])) {
		if (method === 'DELETE') {
			data.tasks = data.tasks.filter((t) => t.id !== task[1]);
			await route.fulfill({ status: 204 });
			return true;
		}
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(scanUpdates)
		});
		return true;
	}

	// ── Backup ─────────────────────────────────────────────────────────
	if (method === 'GET' && path === '/api/admin/database/backup') {
		data.backupLevel = url.searchParams.get('level');
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			headers: {
				'content-disposition': `attachment; filename=photonne_backup_${data.backupLevel}_20261008_100000.json`
			},
			body: JSON.stringify({ version: '3.0', users: [] })
		});
		return true;
	}
	if (method === 'POST' && path === '/api/admin/database/restore') {
		const raw = context.request.postData() ?? '';
		data.restoredFile = /filename="([^"]+)"/.exec(raw)?.[1] ?? null;
		return ok(context, 200, {
			message: 'Base de datos restaurada correctamente.',
			stats: {
				users: 2,
				assets: 3,
				albums: 1,
				folders: 1,
				externalLibraries: 0,
				people: 0,
				faces: 0,
				embeddings: 0,
				ocrLines: 0,
				includesConfig: true,
				includesLibrary: true,
				includesMlData: false
			}
		});
	}

	// ── Shared trash ──────────────────────────────────────────────────
	if (method === 'GET' && path === '/api/assets/shared-trash') {
		return ok(context, 200, { items: data.sharedTrash, hasMore: false, nextCursor: null });
	}
	if (
		method === 'POST' &&
		(path === '/api/assets/shared-trash/restore' || path === '/api/assets/shared-trash/purge')
	) {
		const ids: string[] = body(context).assetIds;
		data.sharedTrash = data.sharedTrash.filter((item) => !ids.includes(item.id));
		await route.fulfill({ status: 204 });
		return true;
	}

	return false;
};

export default handle;
