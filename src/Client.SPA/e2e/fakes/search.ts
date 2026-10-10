import type { Page } from '@playwright/test';
import type { FakeContext, FakeHandler } from '../fake-api';
import { library } from '../fake-api';

/**
 * Search, organize and utilities. Tests steer it through `state`
 * (`semanticOff`: the semantic search answers 503, as without ML) and read
 * back what was sent from it (`searches`, `excludes`, `moves`, `ruleMoves`,
 * `deletedUnsupported`).
 */

type Item = ReturnType<typeof library.items>[number];
type State = FakeContext['state'];

/** A timeline item with the fields the search endpoints add. */
function full(item: Item, extra: Record<string, unknown> = {}) {
	return {
		...item,
		fullPath: `/assets/users/ana/Camera/${item.fileName}`,
		fileSize: 3_400_000,
		fileModifiedAt: item.fileCreatedAt,
		extension: '.jpg',
		scannedAt: item.fileCreatedAt,
		checksum: `sum-${item.id}`,
		hasExif: true,
		hasThumbnails: true,
		syncStatus: 0,
		deletedAt: null,
		isArchived: false,
		isFileMissing: false,
		isReadOnly: false,
		...extra
	};
}

const all = () => library.buckets.flatMap((bucket) => library.items(bucket.key));
const byId = (id: string) => all().find((item) => item.id === id)!;
const yearOf = (id: string) => Number(id.slice(0, 4));

export const people = [
	{ id: '11111111-0000-0000-0000-000000000001', name: 'Lucía', faceCount: 120 },
	{ id: '11111111-0000-0000-0000-000000000002', name: 'Marc', faceCount: 80 },
	{ id: '11111111-0000-0000-0000-000000000003', name: null, faceCount: 12 }
].map((person) => ({
	...person,
	coverFaceId: null,
	isHidden: false,
	createdAt: '2026-01-01T00:00:00Z',
	updatedAt: '2026-01-01T00:00:00Z',
	pendingSuggestionsCount: 0
}));

export const objectLabels = [
	{ label: 'dog', assetCount: 42, coverAssetId: '2026-09-0' },
	{ label: 'bicycle', assetCount: 7, coverAssetId: '2026-08-1' }
];

export const sceneLabels = [
	{ label: 'beach', assetCount: 60, coverAssetId: '2026-06-2' },
	{ label: 'mountain_snowy', assetCount: 18, coverAssetId: '2024-07-3' }
];

// --- Search ----------------------------------------------------------

/** Deterministic matches for each filter, so a test knows what to expect. */
function textSearch(params: URLSearchParams) {
	let items = all();
	const q = params.get('q')?.toLowerCase();
	if (q) items = items.filter((item) => item.fileName.toLowerCase().includes(q));
	const from = params.get('from');
	const to = params.get('to');
	if (from) items = items.filter((item) => item.fileCreatedAt >= from);
	if (to) {
		const end = new Date(Date.parse(to) + 86_400_000).toISOString();
		items = items.filter((item) => item.fileCreatedAt < end);
	}
	if (params.getAll('objectLabel').includes('dog')) items = items.filter((_, i) => i % 3 === 0);
	if (params.getAll('sceneLabel').includes('beach'))
		items = items.filter((item) => item.id.startsWith('2026-06'));
	if (params.getAll('personId').length) items = items.filter((_, i) => i % 2 === 0);
	if (params.get('textQuery')) items = items.slice(0, 3);
	if (params.get('folder')) items = items.slice(0, 5);
	const pageSize = Number(params.get('pageSize') ?? 100);
	const offset = Number(params.get('offset') ?? 0);
	return {
		items: items.slice(offset, offset + pageSize).map((item) => full(item)),
		hasMore: offset + pageSize < items.length
	};
}

// --- Organize --------------------------------------------------------

interface Organize {
	inbox: Set<string>;
	excluded: Set<string>;
}

/** The inbox: September and August 2026, minus what tests move or set aside. */
function organize(state: State): Organize {
	return (state.organize ??= {
		inbox: new Set([...library.items('2026-09'), ...library.items('2026-08')].map((i) => i.id)),
		excluded: new Set<string>()
	}) as Organize;
}

const page = (ids: Iterable<string>) => ({
	// Newest first, like the server.
	items: [...ids]
		.map((id) => full(byId(id)))
		.sort((a, b) => b.fileCreatedAt.localeCompare(a.fileCreatedAt) || a.id.localeCompare(b.id)),
	hasMore: false,
	nextCursor: null
});

function yearGroups(ids: readonly string[]) {
	const years = new Map<number, string[]>();
	for (const id of ids) years.set(yearOf(id), [...(years.get(yearOf(id)) ?? []), id]);
	return [...years].map(([year, assetIds]) => ({ year, assetIds }));
}

const yearCounts = (ids: readonly string[]) =>
	yearGroups(ids).map(({ year, assetIds }) => ({ year, count: assetIds.length }));

interface Rule {
	type?: string;
	op?: string;
	value?: boolean;
	mediaType?: string;
	conditions?: Rule[];
}

/** Favourite and media-type conditions filter; anything else matches all. */
function ruleMatches(state: State, rule: Rule | null) {
	const leaves = rule?.conditions ?? (rule ? [rule] : []);
	return [...organize(state).inbox].filter((id) => {
		const item = byId(id);
		return leaves.every((leaf) => {
			if (leaf.type === 'favorite') return item.isFavorite === (leaf.value ?? true);
			if (leaf.type === 'mediaType')
				return (item.type === 'Video') === (leaf.mediaType === 'Video');
			return true;
		});
	});
}

function suggestions(state: State) {
	const inbox = [...organize(state).inbox];
	const trip = [
		'2026-08-0',
		'2026-08-1',
		'2026-08-2',
		'2026-08-3',
		'2026-08-4',
		'2026-08-5'
	].filter((id) => inbox.includes(id));
	const month = inbox.filter((id) => id.startsWith('2026-09'));
	return [
		...(trip.length
			? [
					{
						kind: 'trip',
						key: 'trip:Roma',
						title: 'Roma',
						from: '2026-08',
						to: '2026-08',
						count: trip.length,
						coverAssetId: trip[0],
						assetIds: trip
					}
				]
			: []),
		...(month.length
			? [
					{
						kind: 'month',
						key: 'month:2026-09',
						title: '2026-09',
						from: '2026-09',
						to: '2026-09',
						count: month.length,
						coverAssetId: month[0],
						assetIds: month
					}
				]
			: [])
	];
}

// --- Utilities -------------------------------------------------------

function duplicates() {
	// Copies of one photo: the same file in several folders, each its own asset.
	const copies = (ids: string[], folders: string[], size: number) =>
		ids.map((id, i) => {
			const original = byId(ids[0]);
			return full(byId(id), {
				fileName: original.fileName,
				fullPath: `/assets/users/ana/${folders[i]}/${original.fileName}`,
				fileSize: size,
				fileCreatedAt: `2026-0${i + 1}-15T10:00:00Z`
			});
		});
	return [
		{
			hash: 'hash-1',
			totalSize: 3 * 2_000_000,
			assets: copies(
				['2026-09-0', '2026-09-20', '2026-09-21'],
				['Camera', 'Viajes', 'Copia'],
				2_000_000
			)
		},
		{
			hash: 'hash-2',
			totalSize: 2 * 5_000_000,
			assets: copies(['2026-06-1', '2026-06-50'], ['Camera', 'Backup'], 5_000_000)
		}
	];
}

function largeFiles(count: number) {
	return all()
		.slice(0, count)
		.map((item, i) =>
			full(item, { fileSize: 900_000_000 - i * 7_000_000, type: i < 3 ? 'Video' : item.type })
		);
}

const folder = (
	id: string,
	path: string,
	assetCount: number,
	subFolders: unknown[] = [],
	extra = {}
) => ({
	id,
	path,
	name: path.split('/').at(-1),
	parentFolderId: null,
	createdAt: '2026-01-01T00:00:00Z',
	assetCount,
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
	subFolders,
	...extra
});

const folderTree = [
	folder('folder-ana', '/assets/users/ana', 120, [
		folder('folder-viajes', '/assets/users/ana/Viajes', 70, [
			folder('folder-roma', '/assets/users/ana/Viajes/Roma', 30),
			folder('folder-lisboa', '/assets/users/ana/Viajes/Lisboa', 25)
		]),
		folder('folder-camera', '/assets/users/ana/Camera', 40)
	]),
	folder('folder-familia', '/assets/shared/Familia', 15, [], { isShared: true, isOwner: false })
];

function unsupported(state: State) {
	const deleted = (state.deletedUnsupported ??= []) as string[];
	return [
		{ id: 'file-1', fileName: 'notas.txt', extension: '.txt', fileSize: 2_048, canDelete: true },
		{
			id: 'file-2',
			fileName: 'factura.pdf',
			extension: '.pdf',
			fileSize: 340_000,
			canDelete: true
		},
		{
			id: 'file-3',
			fileName: 'proyecto.psd',
			extension: '.psd',
			fileSize: 48_000_000,
			canDelete: false
		}
	]
		.filter((file) => !deleted.includes(file.id))
		.map((file) => ({
			...file,
			fullPath: `/assets/users/ana/Varios/${file.fileName}`,
			fileCreatedAt: '2025-03-01T10:00:00Z',
			discoveredAt: '2026-09-01T10:00:00Z'
		}));
}

function record(state: State, key: string, value: unknown) {
	((state[key] ??= []) as unknown[]).push(value);
}

const handle: FakeHandler = async (context) => {
	const { method, path, request, json, state } = context;
	const params = new URL(request.url()).searchParams;
	const body = () => request.postDataJSON();
	const send = async (status: number, payload?: unknown) => {
		await json(status, payload);
		return true;
	};

	// --- Search ------------------------------------------------------
	if (method === 'GET' && path === '/api/assets/search') {
		record(state, 'searches', params.toString());
		return send(200, textSearch(params));
	}
	if (method === 'GET' && path === '/api/assets/search/semantic') {
		record(state, 'semanticSearches', params.get('q'));
		if (state.semanticOff)
			return send(503, {
				error: 'Semantic search unavailable',
				code: 'semantic_search_unavailable'
			});
		if ((params.get('q') ?? '').length < 2)
			return send(400, { error: 'query must be 2-200 chars', code: 'invalid_query' });
		const items = all()
			.slice(0, 7)
			.map((item, i) => ({ score: 0.9 - i * 0.05, asset: full(item) }));
		return send(200, { items });
	}
	if (method === 'GET' && path === '/api/people') {
		const search = params.get('search')?.toLowerCase();
		const items = people.filter((p) => !search || p.name?.toLowerCase().includes(search));
		return send(200, { total: items.length, items });
	}
	if (method === 'GET' && /^\/api\/people\/[^/]+$/.test(path)) {
		const person = people.find((p) => p.id === path.split('/')[3]);
		return person ? send(200, person) : send(404, { error: 'Not found', code: 'not_found' });
	}
	if (method === 'GET' && path === '/api/objects/labels') return send(200, objectLabels);
	if (method === 'GET' && path === '/api/scenes/labels') return send(200, sceneLabels);

	// --- Organize ----------------------------------------------------
	if (method === 'GET' && path === '/api/organize/inbox/count') {
		const { inbox, excluded } = organize(state);
		const dates = [...inbox].map((id) => byId(id).fileCreatedAt).sort();
		return send(200, {
			count: inbox.size,
			oldest: dates[0] ?? null,
			newest: dates.at(-1) ?? null,
			excludedCount: excluded.size
		});
	}
	if (method === 'GET' && path === '/api/organize/inbox')
		return send(200, page(organize(state).inbox));
	if (method === 'GET' && path === '/api/organize/excluded')
		return send(200, page(organize(state).excluded));
	if (method === 'GET' && path === '/api/organize/suggestions')
		return send(200, suggestions(state));
	if (method === 'POST' && path === '/api/organize/exclude') {
		const { assetIds, excluded } = body() as { assetIds: string[]; excluded: boolean };
		record(state, 'excludes', { assetIds, excluded });
		const { inbox, excluded: aside } = organize(state);
		for (const id of assetIds) {
			(excluded ? inbox : aside).delete(id);
			(excluded ? aside : inbox).add(id);
		}
		return send(200, { updated: assetIds.length });
	}
	if (method === 'POST' && path === '/api/organize/rule/preview') {
		const { rule, sampleSize } = body() as { rule: Rule; sampleSize?: number };
		const ids = ruleMatches(state, rule);
		return send(200, {
			count: ids.length,
			sampleAssetIds: ids.slice(0, sampleSize ?? 24),
			yearBreakdown: yearCounts(ids)
		});
	}
	if (method === 'POST' && path === '/api/organize/rule/review') {
		return send(200, { groups: yearGroups(ruleMatches(state, (body() as { rule: Rule }).rule)) });
	}
	if (method === 'POST' && path === '/api/organize/rule/move') {
		const request = body() as { rule: Rule; organizeByCaptureYear?: boolean };
		record(state, 'ruleMoves', request);
		const ids = ruleMatches(state, request.rule);
		for (const id of ids) organize(state).inbox.delete(id);
		return send(200, {
			moved: ids.length,
			yearBreakdown: request.organizeByCaptureYear ? yearCounts(ids) : []
		});
	}
	if (method === 'POST' && path === '/api/folders/assets/move') {
		const request = body() as { assetIds: string[]; organizeByCaptureYear?: boolean };
		record(state, 'moves', request);
		for (const id of request.assetIds) organize(state).inbox.delete(id);
		return send(200, {
			moved: request.assetIds.length,
			yearBreakdown: request.organizeByCaptureYear ? yearCounts(request.assetIds) : []
		});
	}
	if (method === 'POST' && path === '/api/assets/year-breakdown') {
		return send(200, { groups: yearGroups((body() as { assetIds: string[] }).assetIds) });
	}

	// --- Utilities ---------------------------------------------------
	if (method === 'GET' && path === '/api/utilities/summary') {
		return send(200, {
			duplicateGroups: 2,
			duplicateAssets: 5,
			duplicateRecoverableBytes: 9_000_000,
			largeFilesCount: 12,
			largeFilesBytes: 8_400_000_000,
			unsupportedCount: unsupported(state).length
		});
	}
	if (method === 'GET' && path === '/api/utilities/duplicates') return send(200, duplicates());
	if (method === 'GET' && path === '/api/utilities/large-files')
		return send(200, largeFiles(Number(params.get('count') ?? 50)));
	if (method === 'GET' && path === '/api/utilities/folders/tree') return send(200, folderTree);
	if (method === 'GET' && path === '/api/unsupported-files')
		return send(200, { items: unsupported(state), hasMore: false, nextCursor: null });
	if (method === 'GET' && /^\/api\/unsupported-files\/[^/]+\/content$/.test(path)) {
		await context.route.fulfill({
			status: 200,
			contentType: 'application/octet-stream',
			body: 'hola'
		});
		return true;
	}
	if (method === 'DELETE' && /^\/api\/unsupported-files\/[^/]+$/.test(path)) {
		record(state, 'deletedUnsupported', path.split('/')[3]);
		await context.route.fulfill({ status: 204 });
		return true;
	}

	return false;
};

export default handle;

/**
 * Puts this area's fake in front of every other one for a test, so an area
 * that fakes a shared endpoint (people, labels, folder moves…) with other
 * data can't change what these tests see. Call after `fakeApi`.
 */
export async function pinSearchFakes(page: Page, state: State) {
	await page.route('**/api/**', async (route) => {
		const request = route.request();
		const context: FakeContext = {
			route,
			request,
			method: request.method(),
			path: new URL(request.url()).pathname,
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
}
