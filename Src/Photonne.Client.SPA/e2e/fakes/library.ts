import type {
	MemoryDetailResponse,
	MemoryResponse,
	ObjectLabelDto,
	SceneLabelDto,
	SharedTrashItemResponse,
	TimelineResponse
} from '../../src/lib/api/generated/types.gen';
import type { FakeContext, FakeHandler } from '../fake-api';
import { library } from '../fake-api';

/**
 * Archive, trash, memories and explore. The data lives in `context.state`
 * (key `library`), so a test can tweak it before loading the page and check
 * what the page sent.
 */
export interface LibraryState {
	archived: TimelineResponse[];
	trash: TimelineResponse[];
	sharedTrash: SharedTrashItemResponse[];
	settings: Record<string, string>;
	memories: MemoryResponse[];
	onThisDay: TimelineResponse[];
	scenes: SceneLabelDto[];
	objects: ObjectLabelDto[];
	/** Every search the page ran, as its query string. */
	searches: URLSearchParams[];
	purged: string[];
	calls: string[];
}

/** A full timeline item for one of the fake library's assets. */
export function timelineItem(id: string, extra: Partial<TimelineResponse> = {}): TimelineResponse {
	const [year, month] = id.split('-');
	const item = library.items(`${year}-${month}`).find((candidate) => candidate.id === id)!;
	return {
		...item,
		fullPath: `/assets/users/ana/Camera/${item.fileName}`,
		fileSize: 3_400_000,
		fileModifiedAt: item.fileCreatedAt,
		extension: '.jpg',
		scannedAt: item.fileCreatedAt,
		checksum: `sum-${id}`,
		hasExif: true,
		hasThumbnails: true,
		syncStatus: 2,
		deletedAt: null,
		isArchived: false,
		isFileMissing: false,
		isReadOnly: false,
		...extra
	};
}

const ids = (bucket: string, from: number, count: number) =>
	library
		.items(bucket)
		.slice(from, from + count)
		.map((item) => item.id);

function memory(
	id: string,
	kind: string,
	title: string,
	extra: Partial<MemoryResponse> = {}
): MemoryResponse {
	return {
		id,
		kind,
		title,
		subtitle: null,
		themeKey: '',
		groupTitle: '',
		cardLabel: null,
		coverAssetId: `2024-07-${id.length}`,
		assetCount: 6,
		companionPersonId: null,
		companionName: null,
		windowStart: '2024-10-08T00:00:00Z',
		windowEnd: '2024-10-09T00:00:00Z',
		...extra
	};
}

function initial(): LibraryState {
	return {
		archived: ids('2024-07', 0, 12).map((id) => timelineItem(id, { isArchived: true })),
		trash: ids('2023-03', 0, 10).map((id) =>
			timelineItem(id, { deletedAt: '2026-10-01T10:00:00Z' })
		),
		sharedTrash: [],
		settings: {
			'TrashSettings.Enabled': 'true',
			'TrashSettings.RetentionDays': '30',
			'TrashSettings.MaxQuotaMb': '0',
			'NightlyTaskSettings.TrashCleanup.Enabled': 'true'
		},
		memories: [
			memory('mem-today-1', 'OnThisDay', 'Hace 2 años', {
				subtitle: '8 de octubre de 2024',
				windowStart: '2024-10-08T00:00:00Z',
				windowEnd: '2024-10-09T00:00:00Z'
			}),
			memory('mem-today-2', 'OnThisDay', 'Hace 3 años', {
				windowStart: '2023-10-08T00:00:00Z',
				windowEnd: '2023-10-09T00:00:00Z'
			}),
			memory('mem-month', 'ThisMonth', 'Octubre de 2022', {
				windowStart: '2022-10-01T00:00:00Z',
				windowEnd: '2022-11-01T00:00:00Z'
			}),
			memory('mem-person', 'PersonThroughYears', 'Martina a lo largo de los años', {
				cardLabel: 'Martina'
			}),
			memory('mem-beach-24', 'CuratedScene', 'Días de playa de 2024', {
				themeKey: 'scene:beach',
				groupTitle: 'Días de playa',
				cardLabel: '2024',
				windowStart: '2024-07-01T00:00:00Z',
				windowEnd: '2024-08-01T00:00:00Z'
			}),
			memory('mem-beach-23', 'CuratedScene', 'Días de playa de 2023', {
				themeKey: 'scene:beach',
				groupTitle: 'Días de playa',
				cardLabel: '2023',
				windowStart: '2023-07-01T00:00:00Z',
				windowEnd: '2023-08-01T00:00:00Z'
			}),
			memory('mem-trip', 'Trip', 'Viaje a Lisboa', {
				themeKey: 'trips',
				groupTitle: 'Viajes',
				cardLabel: 'Lisboa',
				subtitle: 'Junio de 2025'
			})
		],
		onThisDay: ids('2025-12', 0, 5).map((id) => timelineItem(id)),
		scenes: [
			{ label: 'beach', assetCount: 42, coverAssetId: '2024-07-3' },
			{ label: 'mountain', assetCount: 17, coverAssetId: '2023-03-8' },
			{ label: 'forest', assetCount: 25, coverAssetId: null }
		],
		objects: [
			{ label: 'dog', assetCount: 31, coverAssetId: '2026-06-2' },
			{ label: 'bicycle', assetCount: 4, coverAssetId: '2026-06-9' }
		],
		searches: [],
		purged: [],
		calls: []
	};
}

export function libraryState(state: Record<string, unknown>) {
	return (state.library ??= initial()) as LibraryState;
}

const without = <T extends { id: string }>(items: T[], gone: string[]) =>
	items.filter((item) => !gone.includes(item.id));

const routes: Record<string, (context: FakeContext, data: LibraryState) => Promise<void>> = {
	'GET /api/assets/archived': async ({ json }, data) =>
		json(200, { items: data.archived, hasMore: false, nextCursor: null }),
	'POST /api/assets/archive/unarchive-all': async ({ route }, data) => {
		data.calls.push('unarchive-all');
		data.archived = [];
		await route.fulfill({ status: 204 });
	},
	'GET /api/assets/trash': async ({ json }, data) =>
		json(200, { items: data.trash, hasMore: false, nextCursor: null }),
	'POST /api/assets/purge': async ({ route, request }, data) => {
		const gone: string[] = request.postDataJSON().assetIds;
		data.purged.push(...gone);
		data.trash = without(data.trash, gone);
		await route.fulfill({ status: 204 });
	},
	'POST /api/assets/trash/restore-all': async ({ route }, data) => {
		data.calls.push('restore-all');
		data.trash = [];
		await route.fulfill({ status: 204 });
	},
	'POST /api/assets/trash/empty': async ({ route }, data) => {
		data.calls.push('empty');
		data.trash = [];
		await route.fulfill({ status: 204 });
	},
	'GET /api/assets/shared-trash': async ({ json }, data) =>
		json(200, { items: data.sharedTrash, hasMore: false, nextCursor: null }),
	'POST /api/assets/shared-trash/restore': async ({ route, request }, data) => {
		data.calls.push('shared-restore');
		data.sharedTrash = without(data.sharedTrash, request.postDataJSON().assetIds);
		await route.fulfill({ status: 204 });
	},
	'POST /api/assets/shared-trash/purge': async ({ route, request }, data) => {
		const gone: string[] = request.postDataJSON().assetIds;
		data.purged.push(...gone);
		data.sharedTrash = without(data.sharedTrash, gone);
		await route.fulfill({ status: 204 });
	},
	'GET /api/memories': async ({ json }, data) => json(200, data.memories),
	'GET /api/assets/memories': async ({ json }, data) => json(200, data.onThisDay),
	'GET /api/scenes/labels': async ({ json, request }, data) =>
		json(200, filterLabels(data.scenes, new URL(request.url()).searchParams)),
	'GET /api/objects/labels': async ({ json, request }, data) =>
		json(200, filterLabels(data.objects, new URL(request.url()).searchParams)),
	'GET /api/assets/search': async ({ json, request }, data) => {
		const params = new URL(request.url()).searchParams;
		data.searches.push(params);
		const found = params.get('textQuery') === 'nada' ? [] : ids('2026-06', 0, 14);
		json(200, { items: found.map((id) => timelineItem(id)), hasMore: false });
	}
};

function filterLabels<T extends { label: string }>(labels: T[], params: URLSearchParams) {
	const q = params.get('q')?.toLowerCase();
	return labels.filter((label) => !q || label.label.includes(q));
}

const handle: FakeHandler = async (context) => {
	const { method, path, authorized, json, request } = context;
	if (!path.startsWith('/api/')) return false;
	const data = libraryState(context.state);

	if (method === 'GET' && path === '/api/settings') {
		const key = new URL(request.url()).searchParams.get('key') ?? '';
		if (!(key in data.settings)) return false;
		if (!authorized) return json(401).then(() => true);
		await json(200, { key, value: data.settings[key] });
		return true;
	}

	const detail = /^\/api\/memories\/([^/]+)$/.exec(path);
	if (method === 'GET' && detail) {
		if (!authorized) return json(401).then(() => true);
		const found = data.memories.find((candidate) => candidate.id === detail[1]);
		if (!found) {
			await json(404, { error: 'Memory not found', code: 'memory_not_found' });
			return true;
		}
		const body: MemoryDetailResponse = {
			...found,
			assets: ids('2024-07', 0, found.assetCount).map((id) => timelineItem(id))
		};
		await json(200, body);
		return true;
	}

	const route = routes[`${method} ${path}`];
	if (!route) return false;
	if (!authorized) return json(401).then(() => true);
	await route(context, data);
	return true;
};

export default handle;
