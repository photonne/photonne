import type { Page, Route } from '@playwright/test';
import type {
	AlbumPermissionDto,
	AlbumResponse,
	FolderResponse,
	PeoplePageResponse,
	ShareLinkResponse,
	ShareableUserDto,
	SmartRuleNode,
	TimelineResponse
} from '../../src/lib/api/generated/types.gen';
import type { FakeContext, FakeHandler } from '../fake-api';
import { library } from '../fake-api';

/*
 * Albums and folders, with state: every write changes what later reads
 * return, and the requests a test cares about are recorded in `log`.
 */

const ME = '00000000-0000-0000-0000-000000000001';

/** A library item as the API sends it (TimelineResponse). */
function asset(item: ReturnType<typeof library.items>[number]): TimelineResponse {
	return {
		...item,
		fullPath: `/assets/users/ana/${item.fileName}`,
		fileSize: 3_400_000,
		fileModifiedAt: item.fileCreatedAt,
		extension: '.jpg',
		scannedAt: item.fileCreatedAt,
		checksum: `sum-${item.id}`,
		hasExif: true,
		hasThumbnails: true,
		syncStatus: 'Synced',
		deletedAt: null,
		isArchived: false,
		isFileMissing: false,
		isReadOnly: false
	} as TimelineResponse;
}

function album(overrides: Partial<AlbumResponse> & Pick<AlbumResponse, 'id' | 'name'>) {
	return {
		description: null,
		createdAt: '2026-01-01T00:00:00Z',
		updatedAt: '2026-01-01T00:00:00Z',
		assetCount: 0,
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
		isPinned: false,
		pinnedAt: null,
		kind: 'Manual',
		...overrides
	} satisfies AlbumResponse;
}

function folder(
	overrides: Partial<FolderResponse> & Pick<FolderResponse, 'id' | 'name' | 'path'>
): FolderResponse {
	return {
		parentFolderId: null,
		createdAt: '2026-01-01T00:00:00Z',
		assetCount: 0,
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
		subFolders: [],
		...overrides
	};
}

export interface AlbumsState {
	albums: AlbumResponse[];
	/** Album id → asset ids in album order. */
	members: Record<string, string[]>;
	rules: Record<string, SmartRuleNode>;
	permissions: Record<string, AlbumPermissionDto[]>;
	links: ShareLinkResponse[];
	folders: FolderResponse[];
	/** Folder id → asset ids. */
	folderAssets: Record<string, string[]>;
	/** Requests worth asserting on, as "METHOD path" plus their body. */
	log: { call: string; body?: unknown }[];
}

const allAssets = () =>
	new Map(
		library.buckets.flatMap((bucket) => library.items(bucket.key)).map((item) => [item.id, item])
	);

function seed(): AlbumsState {
	const september = library.items('2026-09').map((item) => item.id);
	const june = library.items('2026-06').map((item) => item.id);
	return {
		albums: [
			album({
				id: 'album-1',
				name: 'Vacaciones',
				description: 'Verano en la costa',
				updatedAt: '2026-09-20T10:00:00Z',
				assetCount: 6,
				coverThumbnailUrl: '/api/assets/2026-09-0/thumbnail?size=Medium',
				isPinned: true,
				pinnedAt: '2026-09-01T00:00:00Z'
			}),
			album({
				id: 'album-2',
				name: 'Perros',
				kind: 'Smart',
				updatedAt: '2026-08-01T00:00:00Z',
				assetCount: 4,
				coverThumbnailUrl: '/api/assets/2026-08-1/thumbnail?size=Medium'
			}),
			album({
				id: 'album-3',
				name: 'Boda de Lucía',
				isOwner: false,
				isShared: true,
				canWrite: false,
				canDelete: false,
				canManagePermissions: false,
				createdAt: '2025-04-12T00:00:00Z',
				updatedAt: '2026-05-01T00:00:00Z',
				assetCount: 3
			})
		],
		members: {
			'album-1': september.slice(0, 6),
			'album-2': library
				.items('2026-08')
				.slice(0, 4)
				.map((item) => item.id),
			'album-3': june.slice(0, 3)
		},
		rules: {
			'album-2': {
				op: 'AND',
				conditions: [
					{ type: 'object', labels: ['dog'], match: 'any' },
					{ type: 'not', condition: { type: 'mediaType', mediaType: 'Video' } }
				]
			}
		},
		permissions: {
			'album-1': [
				{
					id: 'perm-1',
					userId: 'user-luis',
					username: 'luis',
					email: 'luis@photonne.test',
					canRead: true,
					canWrite: false,
					canDelete: false,
					canManagePermissions: false,
					grantedAt: '2026-02-01T00:00:00Z',
					grantedByUserId: ME
				}
			]
		},
		links: [],
		folders: [
			folder({
				id: 'folder-1',
				name: 'Camera',
				path: '/assets/users/ana/Camera',
				assetCount: 20,
				previewAssetIds: june.slice(0, 1)
			}),
			folder({
				id: 'folder-2',
				name: '2025',
				path: '/assets/users/ana/Camera/2025',
				parentFolderId: 'folder-1',
				assetCount: 5
			}),
			folder({
				id: 'folder-3',
				name: 'Documentos',
				path: '/assets/users/ana/Documentos'
			}),
			folder({
				id: 'folder-4',
				name: 'Familia',
				path: '/assets/shared/Familia',
				isShared: true,
				sharedWithCount: 2,
				canDelete: false
			})
		],
		folderAssets: {
			'folder-1': june.slice(0, 20),
			'folder-2': june.slice(20, 25),
			'folder-3': [],
			'folder-4': []
		},
		log: []
	};
}

const users: ShareableUserDto[] = [
	{ id: 'user-luis', username: 'luis', email: 'luis@photonne.test' },
	{ id: 'user-marta', username: 'marta', email: 'marta@photonne.test' }
];

const people: PeoplePageResponse = {
	total: 2,
	items: [
		{
			id: 'person-1',
			name: 'Lucía',
			coverFaceId: null,
			faceCount: 40,
			isHidden: false,
			createdAt: '2026-01-01T00:00:00Z',
			updatedAt: '2026-01-01T00:00:00Z',
			pendingSuggestionsCount: 0
		},
		{
			id: 'person-2',
			name: 'Pablo',
			coverFaceId: null,
			faceCount: 25,
			isHidden: false,
			createdAt: '2026-01-01T00:00:00Z',
			updatedAt: '2026-01-01T00:00:00Z',
			pendingSuggestionsCount: 0
		}
	]
};

const labels = [
	{ label: 'dog', assetCount: 12, coverAssetId: null },
	{ label: 'cat', assetCount: 3, coverAssetId: null }
];

function tree(folders: readonly FolderResponse[], counts: Record<string, string[]>) {
	const nodes = new Map(
		folders.map((f) => [f.id, { ...f, assetCount: counts[f.id]?.length ?? 0, subFolders: [] }])
	);
	const roots: FolderResponse[] = [];
	for (const node of nodes.values()) {
		const parent = node.parentFolderId ? nodes.get(node.parentFolderId) : undefined;
		if (parent) parent.subFolders.push(node);
		else roots.push(node);
	}
	return roots;
}

function stateOf(context: FakeContext): AlbumsState {
	return (context.state.albums ??= seed()) as AlbumsState;
}

const handle: FakeHandler = async (context) => {
	const { method, path, request, json, route } = context;
	if (!/^\/api\/(albums|folders|share|users\/shareable|people|objects|scenes)(\/|$)/.test(path))
		return false;
	const s = stateOf(context);
	const body = () => (request.postData() ? request.postDataJSON() : undefined);
	const parts = path.split('/').slice(2);
	const noContent = () => route.fulfill({ status: 204 }).then(() => true);
	const ok = (value: unknown) => json(200, value).then(() => true);
	const log = (extra?: unknown) => s.log.push({ call: `${method} ${path}`, body: extra });
	const assets = allAssets();

	// --- Catalogs used by the smart-rule editor and the share dialog ---
	if (path === '/api/users/shareable') return ok(users);
	if (path === '/api/people') return ok(people);
	if (path === '/api/objects/labels' || path === '/api/scenes/labels') return ok(labels);

	// --- Albums ---
	if (parts[0] === 'albums') {
		const [, id, sub, extra] = parts;
		const found = s.albums.find((a) => a.id === id);
		if (!id) {
			if (method === 'GET') return ok(s.albums);
			const { name, description, smartRule } = body();
			log({ name, description, smartRule });
			const created = album({
				id: `album-new-${s.albums.length + 1}`,
				name,
				description: description ?? null,
				kind: smartRule ? 'Smart' : 'Manual',
				updatedAt: '2026-10-08T00:00:00Z'
			});
			s.albums.push(created);
			s.members[created.id] = smartRule ? ['2026-09-1', '2026-09-2'] : [];
			if (smartRule) s.rules[created.id] = smartRule;
			return json(201, created).then(() => true);
		}
		if (id === 'preview') {
			log(body());
			return ok({ count: 7, sampleAssetIds: ['2026-09-3', '2026-09-4', '2026-09-5'] });
		}
		if (!found)
			return json(404, { error: 'Album not found', code: 'album_not_found' }).then(() => true);
		if (!sub) {
			if (method === 'GET') return ok(found);
			if (method === 'PUT') {
				const { name, description, smartRule } = body();
				log({ name, description, smartRule });
				Object.assign(found, { name, description });
				if (smartRule) s.rules[id] = smartRule;
				return ok(found);
			}
			if (method === 'DELETE') {
				log();
				if (!found.isOwner && !found.canDelete)
					return json(403, { error: 'Forbidden', code: 'forbidden' }).then(() => true);
				s.albums = s.albums.filter((a) => a.id !== id);
				return noContent();
			}
		}
		if (sub === 'assets' && method === 'GET') {
			return ok(
				(s.members[id] ?? []).flatMap((a) => (assets.has(a) ? [asset(assets.get(a)!)] : []))
			);
		}
		if (sub === 'assets' && extra === 'batch') {
			const { assetIds } = body();
			log({ assetIds });
			s.members[id] = [...new Set([...(s.members[id] ?? []), ...assetIds])];
			found.assetCount = s.members[id].length;
			return ok({ added: assetIds.length, skipped: 0 });
		}
		if (sub === 'assets' && method === 'DELETE') {
			log();
			s.members[id] = (s.members[id] ?? []).filter((a) => a !== extra);
			found.assetCount = s.members[id].length;
			return noContent();
		}
		if (sub === 'rule') return ok({ rule: s.rules[id], people: [], folders: [] });
		if (sub === 'cover') {
			const { assetId } = body();
			log({ assetId });
			found.coverThumbnailUrl = `/api/assets/${assetId}/thumbnail?size=Medium`;
			return ok(found);
		}
		if (sub === 'pin') {
			log();
			found.isPinned = method === 'PUT';
			found.pinnedAt = found.isPinned ? '2026-10-08T00:00:00Z' : null;
			return ok({ albumId: id, isPinned: found.isPinned, pinnedAt: found.pinnedAt });
		}
		if (sub === 'leave') {
			log();
			s.albums = s.albums.filter((a) => a.id !== id);
			return noContent();
		}
		if (sub === 'permissions') {
			const list = (s.permissions[id] ??= []);
			if (method === 'GET') return ok(list);
			if (method === 'DELETE') {
				log();
				s.permissions[id] = list.filter((p) => p.userId !== extra);
				return noContent();
			}
			const grant = body();
			log(grant);
			const user = users.find((u) => u.id === grant.userId)!;
			const permission: AlbumPermissionDto = {
				id: `perm-${grant.userId}`,
				userId: user.id,
				username: user.username,
				email: user.email,
				canRead: grant.canRead,
				canWrite: grant.canWrite,
				canDelete: grant.canDelete,
				canManagePermissions: grant.canManagePermissions,
				grantedAt: '2026-10-08T00:00:00Z',
				grantedByUserId: ME
			};
			s.permissions[id] = [...list.filter((p) => p.userId !== user.id), permission];
			return ok(permission);
		}
	}

	// --- Public links ---
	if (parts[0] === 'share') {
		const token = parts[1];
		if (!token && method === 'GET') {
			const albumId = new URL(request.url()).searchParams.get('albumId');
			return ok(s.links.filter((l) => l.albumId === albumId));
		}
		if (!token && method === 'POST') {
			const request = body();
			log(request);
			const link: ShareLinkResponse = {
				token: `tok${s.links.length + 1}`,
				albumId: request.albumId,
				createdAt: '2026-10-08T00:00:00Z',
				expiresAt: request.expiresAt,
				hasPassword: !!request.password,
				allowDownload: request.allowDownload,
				maxViews: request.maxViews,
				viewCount: 0,
				allowUpload: request.allowUpload,
				uploadCount: 0,
				shareUrl: `/share/tok${s.links.length + 1}`
			};
			s.links.push(link);
			return ok(link);
		}
		const link = s.links.find((l) => l.token === token);
		if (!link)
			return json(404, { error: 'Not found', code: 'share_link_not_found' }).then(() => true);
		if (method === 'PATCH') {
			const update = body();
			log(update);
			Object.assign(link, {
				expiresAt: update.expiresAt,
				maxViews: update.maxViews,
				allowDownload: update.allowDownload,
				allowUpload: update.allowUpload,
				hasPassword: update.password === null ? link.hasPassword : update.password !== ''
			});
			return ok(link);
		}
		if (method === 'DELETE') {
			log();
			s.links = s.links.filter((l) => l !== link);
			return noContent();
		}
	}

	// --- Folders ---
	if (parts[0] === 'folders') {
		const [, id, sub] = parts;
		if (!id) {
			if (method === 'GET') return ok(s.folders.map((f) => ({ ...f, subFolders: [] })));
			const { name, parentFolderId } = body();
			log({ name, parentFolderId });
			const parent = s.folders.find((f) => f.id === parentFolderId);
			const created = folder({
				id: `folder-new-${s.folders.length + 1}`,
				name,
				path: `${parent?.path ?? '/assets/users/ana'}/${name}`,
				parentFolderId: parentFolderId ?? null
			});
			s.folders.push(created);
			s.folderAssets[created.id] = [];
			return ok(created);
		}
		if (id === 'tree') return ok(tree(s.folders, s.folderAssets));
		if (id === 'assets' && sub === 'move') {
			const { sourceFolderId, targetFolderId, assetIds } = body();
			log({ sourceFolderId, targetFolderId, assetIds });
			for (const [key, ids] of Object.entries(s.folderAssets))
				s.folderAssets[key] = ids.filter((a) => !assetIds.includes(a));
			s.folderAssets[targetFolderId] = [...(s.folderAssets[targetFolderId] ?? []), ...assetIds];
			return ok({ moved: assetIds.length, yearBreakdown: [] });
		}
		if (id === 'assets' && sub === 'remove') {
			const { folderId, assetIds } = body();
			log({ folderId, assetIds });
			s.folderAssets[folderId] = s.folderAssets[folderId].filter((a) => !assetIds.includes(a));
			return noContent();
		}
		const found = s.folders.find((f) => f.id === id);
		if (!found)
			return json(404, { error: 'Folder not found', code: 'folder_not_found' }).then(() => true);
		if (!sub) {
			if (method === 'GET') {
				const subFolders = tree(s.folders, s.folderAssets);
				const node = (function find(nodes: FolderResponse[]): FolderResponse | undefined {
					for (const n of nodes) {
						if (n.id === id) return n;
						const below = find(n.subFolders);
						if (below) return below;
					}
				})(subFolders)!;
				return ok({
					...node,
					assetCount: s.folderAssets[id]?.length ?? 0,
					subFolders: node.subFolders.map((child) => ({ ...child, subFolders: [] }))
				});
			}
			if (method === 'PUT') {
				const { name, parentFolderId } = body();
				log({ name, parentFolderId });
				Object.assign(found, { name, parentFolderId });
				return ok(found);
			}
			if (method === 'DELETE') {
				log();
				const gone = new Set([id]);
				for (const f of s.folders)
					if (f.parentFolderId && gone.has(f.parentFolderId)) gone.add(f.id);
				s.folders = s.folders.filter((f) => !gone.has(f.id));
				return noContent();
			}
		}
		if (sub === 'assets') {
			return ok((s.folderAssets[id] ?? []).map((a) => asset(assets.get(a)!)));
		}
		if (sub === 'pin') {
			log();
			found.isPinned = method === 'PUT';
			found.pinnedAt = found.isPinned ? '2026-10-08T00:00:00Z' : null;
			return ok({ folderId: id, isPinned: found.isPinned, pinnedAt: found.pinnedAt });
		}
		if (sub === 'discovery-visibility') {
			const { included } = body();
			log({ included });
			found.excludedFromDiscovery = !included;
			return ok({ folderId: id, excludedFromDiscovery: !included });
		}
		if (sub === 'permissions') {
			if (method === 'GET') return ok([]);
			log(body());
			return ok({});
		}
	}
	return false;
};

export default handle;

/**
 * The core fake (fake-api.ts) answers a few album and folder reads itself
 * (the sidebar's lists, the pickers). This routes every album, folder and
 * link request to this file's stateful fake instead; Playwright tries the
 * most recently added route first.
 */
export async function albumsApi(page: Page, state: Record<string, unknown>) {
	await page.route(/\/api\/(albums|folders)(\/|\?|$)/, async (route: Route) => {
		const request = route.request();
		const path = new URL(request.url()).pathname;
		const context: FakeContext = {
			route,
			request,
			method: request.method(),
			path,
			authorized: request.headers()['authorization'] === 'Bearer token-1',
			json: (status, body) =>
				route.fulfill({
					status,
					contentType: 'application/json',
					body: JSON.stringify(body ?? {})
				}),
			state
		};
		if (!(await handle(context))) await route.fallback();
	});
	return stateOf({ state } as FakeContext);
}
