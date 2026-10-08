import type { Page } from '@playwright/test';
import type {
	AdminEnrichmentFailureDto,
	BackgroundTaskResponse,
	PendingCountResponse,
	QueueCounts
} from '../../src/lib/api/generated/types.gen';
import type { FakeContext, FakeHandler } from '../fake-api';

/**
 * Administration operations: background tasks, queues, failures, maintenance
 * streams and server settings. Tests read and seed `state.adminops`.
 */
export interface AdminOpsState {
	tasks: BackgroundTaskResponse[];
	queues: Record<string, QueueCounts>;
	pending: Record<string, PendingCountResponse>;
	failures: AdminEnrichmentFailureDto[];
	settings: Record<string, string>;
	saved: [string, string][];
	started: string[];
	cancelled: string[];
	backfills: string[];
	emptied: string[];
	retried: string[];
	suppressed: string[];
	deletedFiles: string[];
	trashCleaned: number;
	notificationsPurged: number;
	demo: boolean;
}

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

let nextId = 100;
const uuid = () => `00000000-0000-4000-8000-${String(nextId++).padStart(12, '0')}`;

function seed(): AdminOpsState {
	return {
		tasks: [
			{
				id: '00000000-0000-4000-8000-000000000001',
				type: 'Thumbnails',
				status: 'Running',
				percentage: 42,
				lastMessage: 'Procesando 420 de 1000 (IMG_0420.jpg)',
				startedAt: minutesAgo(3),
				finishedAt: null,
				parameters: { regenerate: 'False' }
			},
			{
				id: '00000000-0000-4000-8000-000000000002',
				type: 'Maintenance',
				status: 'Completed',
				percentage: 100,
				lastMessage: 'Eliminadas 12 miniaturas huérfanas.',
				startedAt: minutesAgo(40),
				finishedAt: minutesAgo(38),
				parameters: { kind: 'orphan-thumbnails', dryRun: 'False' }
			},
			{
				id: '00000000-0000-4000-8000-000000000003',
				type: 'IndexAssets',
				status: 'Failed',
				percentage: 10,
				lastMessage: 'Error: El directorio interno no existe: /assets',
				startedAt: minutesAgo(55),
				finishedAt: minutesAgo(54),
				parameters: {}
			}
		],
		queues: {
			Exif: { inQueue: 0, processing: 0, retrying: 0, failed: 2 },
			Thumbnails: { inQueue: 580, processing: 2, retrying: 0, failed: 0 },
			FaceRecognition: { inQueue: 120, processing: 1, retrying: 3, failed: 1 },
			ObjectDetection: { inQueue: 0, processing: 0, retrying: 0, failed: 0 }
		},
		pending: {
			'face-recognition': pending(340, 120, 5200),
			'object-detection': pending(0, 0, 5660),
			'scene-classification': pending(25, 0, 5635),
			'text-recognition': pending(800, 0, 4860),
			'image-embedding': pending(0, 0, 5660),
			'media-recognition': pending(4, 0, 5656)
		},
		failures: [
			failure(1, 'Exif', 'Permanent', 'Unsupported file format', 'ana'),
			failure(2, 'Exif', 'Permanent', 'File is truncated', 'luis'),
			failure(
				3,
				'FaceRecognition',
				'Transient',
				'ML service unreachable (timeout after 30s)',
				'ana'
			),
			failure(4, 'TextRecognition', 'NeedsAction', 'Text recognition is disabled', 'ana', true)
		],
		settings: {
			'ServerSettings.PublicUrl': 'https://fotos.example.com',
			'ServerSettings.SessionTimeoutMinutes': '1440',
			'FaceRecognition.ClusteringThreshold': '0.42',
			'NightlyTaskSettings.Enabled': 'true',
			'NightlyTaskSettings.LastRunDate': '2026-10-07'
		},
		saved: [],
		started: [],
		cancelled: [],
		backfills: [],
		emptied: [],
		retried: [],
		suppressed: [],
		deletedFiles: [],
		trashCleaned: 0,
		notificationsPurged: 0,
		demo: false
	};
}

function pending(unprocessed: number, inQueue: number, completed: number): PendingCountResponse {
	return {
		unprocessed,
		inQueue,
		completed,
		retrying: 0,
		failed: 0,
		processing: inQueue > 0 ? 1 : 0,
		lastCompletedAt: minutesAgo(1),
		completedLastMinute: inQueue > 0 ? 12 : 0
	};
}

function failure(
	n: number,
	taskType: string,
	failureKind: string,
	errorMessage: string,
	owner: string,
	suppressed = false
): AdminEnrichmentFailureDto {
	return {
		taskId: `00000000-0000-4000-9000-00000000000${n}`,
		assetId: `2026-09-${n}`,
		fileName: `IMG_20260${n}.jpg`,
		fileCreatedAt: '2026-09-12T10:00:00Z',
		ownerId: null,
		ownerName: owner,
		taskType,
		status: suppressed ? 'Suppressed' : 'Failed',
		errorMessage,
		attemptCount: 3,
		isPermanent: true,
		lastAttemptAt: minutesAgo(30 * n),
		failureKind,
		failureCode: failureKind === 'Permanent' ? 'unreadable_file' : null
	};
}

export function adminState(state: Record<string, unknown>) {
	return (state.adminops ??= seed()) as AdminOpsState;
}

/** The signed-in fake user, made an administrator (registered after fakeApi, so it wins). */
export async function asAdmin(page: Page) {
	await page.route('**/api/users/me', (route) => {
		if (route.request().headers()['authorization'] !== 'Bearer token-1') {
			return route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
		}
		return route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({
				id: '00000000-0000-0000-0000-000000000001',
				username: 'ana',
				email: 'ana@photonne.test',
				role: 'Admin',
				isActive: true,
				isPrimaryAdmin: true
			})
		});
	});
}

/** A background task as the server registers it when a stream starts it. */
function register(state: AdminOpsState, type: string, parameters: Record<string, string> = {}) {
	const task: BackgroundTaskResponse = {
		id: uuid(),
		type,
		status: 'Running',
		percentage: 0,
		lastMessage: 'Iniciando…',
		startedAt: new Date().toISOString(),
		finishedAt: null,
		parameters
	};
	state.tasks.push(task);
	return task;
}

function stream(context: FakeContext, events: unknown[], ndjson = false) {
	return context.route.fulfill({
		status: 200,
		contentType: ndjson ? 'application/x-ndjson' : 'application/json',
		body: ndjson
			? events.map((event) => JSON.stringify(event)).join('\n') + '\n'
			: JSON.stringify(events)
	});
}

const STARTERS: Record<string, string> = {
	'/api/assets/index/stream': 'IndexAssets',
	'/api/assets/thumbnails/stream': 'Thumbnails',
	'/api/assets/metadata/stream': 'Metadata',
	'/api/assets/dates/restore/stream': 'DateRestore'
};

const handle: FakeHandler = async (context) => {
	const { method, path, request, json } = context;
	const isAdminPath =
		path.startsWith('/api/admin/') ||
		path.startsWith('/api/tasks') ||
		path.startsWith('/api/settings') ||
		path in STARTERS ||
		path.startsWith('/api/assets/duplicates/');
	if (!isAdminPath) return false;
	// The demo info is public: the login page reads it too.
	if (!context.authorized && path !== '/api/admin/demo-info') {
		await json(401);
		return true;
	}
	const state = adminState(context.state);
	const url = new URL(request.url());
	const query = url.searchParams;
	let match: RegExpMatchArray | null;

	// Background tasks
	if (method === 'GET' && path === '/api/tasks') {
		await json(200, state.tasks);
		return true;
	}
	if ((match = path.match(/^\/api\/tasks\/([^/]+)\/stream$/))) {
		const task = state.tasks.find((t) => t.id === match![1]);
		await stream(
			context,
			task
				? [
						{
							percentage: task.percentage + 1,
							message: task.lastMessage,
							isCompleted: false,
							taskId: task.id
						}
					]
				: []
		);
		return true;
	}
	if (method === 'DELETE' && (match = path.match(/^\/api\/tasks\/([^/]+)$/))) {
		const task = state.tasks.find((t) => t.id === match![1]);
		if (!task) {
			await json(404, { error: 'Task not found', code: 'task_not_found' });
			return true;
		}
		state.cancelled.push(task.id);
		Object.assign(task, {
			status: 'Cancelled',
			finishedAt: new Date().toISOString(),
			lastMessage: 'Proceso cancelado.'
		});
		await context.route.fulfill({ status: 204 });
		return true;
	}

	// Streams that start a job
	if (method === 'GET' && path in STARTERS) {
		const type = STARTERS[path];
		state.started.push(`${type}${url.search}`);
		const task = register(state, type);
		await stream(context, [
			{
				message: 'Iniciando…',
				percentage: 0,
				statistics: null,
				isCompleted: false,
				taskId: task.id
			}
		]);
		return true;
	}
	if (method === 'GET' && (match = path.match(/^\/api\/admin\/maintenance\/([a-z-]+)\/stream$/))) {
		const kind = match[1];
		if (kind === 'face-clustering') {
			state.started.push('FaceClustering');
			const task = register(state, 'FaceClustering');
			await stream(context, [{ message: 'Iniciando…', percentage: 0, taskId: task.id }], true);
			return true;
		}
		state.started.push(`${kind}${url.search}`);
		const task = register(state, 'Maintenance', {
			kind,
			dryRun: query.get('dryRun') === 'true' ? 'True' : 'False'
		});
		await stream(
			context,
			[
				{
					message: 'Iniciando…',
					percentage: 0,
					processed: 0,
					affected: 0,
					isCompleted: false,
					taskId: task.id
				}
			],
			true
		);
		return true;
	}

	// Queues
	if (method === 'GET' && path === '/api/admin/enrichment/queue-summary') {
		await json(200, { types: state.queues });
		return true;
	}
	if (method === 'GET' && path === '/api/admin/maintenance/ml-pending-total') {
		await json(200, { count: 1169 });
		return true;
	}
	if (method === 'GET' && path === '/api/admin/maintenance/reverse-geocode/pending-count') {
		await json(200, { pending: 37, datasetAvailable: true, cities: 140_000 });
		return true;
	}
	if (method === 'GET' && path === '/api/admin/indexing-coverage') {
		await json(200, {
			hasResult: true,
			verifiedAtUtc: minutesAgo(120),
			totalFiles: 5702,
			indexed: 5690,
			unsupported: 10,
			unindexed: 2,
			unindexedPaths: [
				'/assets/users/ana/Camera/IMG_9999.heic',
				'/assets/users/luis/raw/DSC_0001.nef'
			],
			unindexedTruncated: false,
			offlineLibraries: 0
		});
		return true;
	}
	if ((match = path.match(/^\/api\/admin\/maintenance\/([a-z-]+)\/pending-count$/))) {
		const counts = state.pending[match[1]];
		if (!counts) return false;
		await json(200, counts);
		return true;
	}
	if (
		method === 'POST' &&
		(match = path.match(/^\/api\/admin\/maintenance\/([a-z-]+)\/backfill$/))
	) {
		const kind = match[1];
		if (kind === 'text-recognition') {
			await json(409, {
				error: 'El reconocimiento de texto está desactivado en Ajustes.',
				code: 'ml_disabled'
			});
			return true;
		}
		const counts = state.pending[kind];
		state.backfills.push(kind);
		const enqueued = counts.unprocessed;
		counts.inQueue += enqueued;
		counts.unprocessed = 0;
		await json(200, { enqueued, total: enqueued, elapsedMs: 1200 });
		return true;
	}
	if (
		method === 'DELETE' &&
		(match = path.match(/^\/api\/admin\/maintenance\/([a-z-]+)\/queue$/))
	) {
		const kind = match[1];
		state.emptied.push(kind);
		const counts = state.pending[kind];
		const deleted = counts?.inQueue ?? 0;
		if (counts) counts.inQueue = 0;
		if (kind === 'face-recognition') state.queues.FaceRecognition.inQueue = 0;
		await json(200, { deleted, stillProcessing: 1 });
		return true;
	}

	// Enrichment failures
	if (method === 'GET' && path === '/api/admin/enrichment/failures') {
		const type = query.get('type');
		const kind = query.get('kind');
		const open = state.failures;
		const definitive = open.filter((f) => f.status === 'Failed');
		const countBy = (key: 'taskType' | 'failureKind') =>
			definitive.reduce<Record<string, number>>((acc, f) => {
				acc[f[key]] = (acc[f[key]] ?? 0) + 1;
				return acc;
			}, {});
		const filtered = open.filter(
			(f) => (!type || f.taskType === type) && (!kind || f.failureKind === kind)
		);
		await json(200, {
			items: filtered,
			nextCursor: null,
			total: filtered.filter((f) => f.status === 'Failed').length,
			countsByType: countBy('taskType'),
			countsByKind: countBy('failureKind'),
			retrying: 0,
			suppressed: filtered.filter((f) => f.status === 'Suppressed').length
		});
		return true;
	}
	if (method === 'POST' && path === '/api/admin/enrichment/failures/retry-all') {
		const type = query.get('type');
		const retried = state.failures.filter(
			(f) => f.status === 'Failed' && (!type || f.taskType === type)
		);
		state.retried.push(...retried.map((f) => f.taskId));
		state.failures = state.failures.filter((f) => !retried.includes(f));
		await json(200, { retried: retried.length });
		return true;
	}
	if (
		method === 'POST' &&
		(match = path.match(/^\/api\/admin\/enrichment\/failures\/([^/]+)\/(retry|suppress)$/))
	) {
		const [, taskId, action] = match;
		const item = state.failures.find((f) => f.taskId === taskId);
		if (!item) {
			await json(404, { error: 'Enrichment task not found', code: 'enrichment_task_not_found' });
			return true;
		}
		if (action === 'retry') {
			state.retried.push(taskId);
			state.failures = state.failures.filter((f) => f !== item);
		} else {
			state.suppressed.push(taskId);
			item.status = 'Suppressed';
		}
		await json(200, { taskId, status: action === 'retry' ? 'Pending' : 'Suppressed' });
		return true;
	}

	// Duplicates
	if (method === 'GET' && path === '/api/assets/duplicates/stream') {
		const physical = query.get('physical') === 'true';
		state.started.push(`duplicates${url.search}`);
		const file = (name: string, dir: string, indexed: boolean, days: number) => ({
			physicalPath: `/data/${dir}/${name}`,
			virtualPath: `/assets/users/ana/${dir}/${name}`,
			fileName: name,
			directory: dir,
			fileSize: 2_400_000,
			fileModifiedAt: new Date(Date.now() - days * 86_400_000).toISOString(),
			isIndexed: indexed,
			assetId: indexed ? `2026-09-${days}` : null,
			ownerUsername: 'ana'
		});
		const groups = physical
			? [
					{
						hash: 'a1b2c3d4e5f6a7b8c9d0',
						files: [
							file('IMG_0001.jpg', 'Camera', true, 3),
							file('IMG_0001 (1).jpg', 'Descargas', false, 1)
						]
					},
					{
						hash: 'ffeeddccbbaa99887766',
						files: [
							file('IMG_0002.jpg', 'Camera', true, 5),
							file('IMG_0002.jpg', 'Backup', true, 2),
							file('copia.jpg', 'Escritorio', false, 9)
						]
					}
				]
			: [];
		const events = [
			{
				message: 'Calculando hashes…',
				percentage: 40,
				statistics: null,
				isCompleted: false,
				foundGroup: null
			},
			...groups.map((group) => ({
				message: 'Grupo encontrado',
				percentage: 70,
				statistics: null,
				isCompleted: false,
				foundGroup: group
			})),
			{
				message: physical ? 'Encontrados 2 grupos en disco.' : 'Encontrados 3 grupos duplicados.',
				percentage: 100,
				isCompleted: true,
				foundGroup: null,
				statistics: {
					totalAssets: 5702,
					duplicateGroups: physical ? 2 : 3,
					duplicateAssets: physical ? 3 : 4,
					removed: query.get('cleanup') === 'true' ? 4 : 0,
					bytesReclaimed: 7_200_000,
					unindexedFiles: physical ? 2 : 0
				}
			}
		];
		await stream(context, events);
		return true;
	}
	if (method === 'POST' && path === '/api/assets/duplicates/physical/delete') {
		const body = request.postDataJSON() as { physicalPath: string }[];
		state.deletedFiles.push(...body.map((file) => file.physicalPath));
		await json(200, { deleted: body.length, errors: [] });
		return true;
	}

	// Settings
	if (method === 'GET' && path === '/api/settings') {
		const key = query.get('key') ?? '';
		// The trash settings belong to the library fake.
		const trashKey =
			key.startsWith('TrashSettings.') || key.startsWith('NightlyTaskSettings.Trash');
		if (trashKey && !(key in state.settings)) return false;
		await json(200, { key, value: state.settings[key] ?? '' });
		return true;
	}
	if (method === 'POST' && path === '/api/settings') {
		const { key, value } = request.postDataJSON() as { key: string; value: string };
		state.saved.push([key, value]);
		state.settings[key] = value;
		await json(200, { message: 'Setting saved successfully' });
		return true;
	}
	if (method === 'GET' && path === '/api/settings/server-info') {
		await json(200, { processorCount: 8 });
		return true;
	}
	if (method === 'GET' && path === '/api/admin/demo-info') {
		await json(200, {
			enabled: state.demo,
			demoUsername: null,
			demoPassword: null,
			resetIntervalHours: null,
			nextResetAt: null
		});
		return true;
	}
	if (method === 'GET' && path === '/api/admin/trash/stats') {
		await json(200, {
			totalItems: 48,
			totalBytes: 512_000_000,
			expiredItems: 6,
			retentionDays: 30,
			maxQuotaMb: 0,
			overQuotaUsers: 1,
			overQuotaBytes: 12_000_000,
			perUser: [
				{
					userId: 'u1',
					username: 'ana',
					items: 40,
					bytes: 480_000_000,
					expiredItems: 6,
					overQuota: true
				},
				{
					userId: 'u2',
					username: 'luis',
					items: 8,
					bytes: 32_000_000,
					expiredItems: 0,
					overQuota: false
				}
			]
		});
		return true;
	}
	if (method === 'POST' && path === '/api/admin/trash/cleanup-expired') {
		state.trashCleaned++;
		await json(200, { success: true, message: 'ok', deleted: 6 });
		return true;
	}
	if (method === 'GET' && path === '/api/admin/notifications/stats') {
		await json(200, { total: 230, unread: 12, oldestAt: '2026-08-01T09:00:00Z' });
		return true;
	}
	if (method === 'POST' && path === '/api/admin/notifications/purge') {
		state.notificationsPurged++;
		await json(200, { deleted: 140 });
		return true;
	}
	return false;
};

export default handle;
