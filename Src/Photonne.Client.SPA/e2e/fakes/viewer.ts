import type {
	AssetDetailResponse,
	ClassifiedSceneDto,
	DetectedObjectDto,
	EnrichmentTaskDto,
	FaceDto,
	PersonAssetDto,
	RecognizedTextLineDto
} from '../../src/lib/api/generated/types.gen';
import type { FakeHandler } from '../fake-api';
import { library } from '../fake-api';
import { peopleState } from './people';

/** What the viewer fake holds for one test; tests read it to assert. */
export interface ViewerState {
	/** Enrichment tasks by asset id (only the ones a test touched). */
	tasks: Record<string, EnrichmentTaskDto[]>;
	retried: string[];
	dates: { assetId: string; dateTaken: string; writeToFile: boolean }[];
	savedFrames: number[];
}

/** A Live Photo whose frames can be saved (the base fake's detail never allows it). */
export const LIVE_ID = 'live-2026-09-7';

/** The photo with recognised text and two faces: Ana's and an unknown one. */
export const TEXT_PHOTO = '2026-09-3';
const UNKNOWN_FACE = 'face-viewer-unknown';

const photos = library.buckets.flatMap((bucket) => library.items(bucket.key));
const byId = new Map(photos.map((item) => [item.id, item]));

export function viewerState(state: Record<string, unknown>): ViewerState {
	if (!state.viewer) {
		// One more face on the text photo, next to the one the people fake put there.
		peopleState(state).faces.push({
			id: UNKNOWN_FACE,
			assetId: TEXT_PHOTO,
			personId: null,
			suggestedPersonId: null,
			rejected: false
		});
		state.viewer = { tasks: {}, retried: [], dates: [], savedFrames: [] } satisfies ViewerState;
	}
	return state.viewer as ViewerState;
}

const at = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60_000).toISOString();

function task(taskType: string, status: string, extra: Partial<EnrichmentTaskDto> = {}) {
	return {
		taskType,
		status,
		errorMessage: null,
		attemptCount: 1,
		createdAt: at(60),
		startedAt: at(60),
		completedAt: status === 'Completed' ? at(59) : null,
		nextRetryAt: null,
		...extra
	} satisfies EnrichmentTaskDto;
}

function initialTasks(): EnrichmentTaskDto[] {
	return [
		task('Exif', 'Completed'),
		task('FaceRecognition', 'Completed'),
		task('ObjectDetection', 'Failed', { errorMessage: 'ML service unavailable' }),
		task('SceneClassification', 'Completed'),
		task('TextRecognition', 'Completed')
	];
}

/** Each read moves a queued task on: queued → running → done. */
function advance(tasks: EnrichmentTaskDto[]) {
	for (const t of tasks) {
		if (t.status === 'Processing') Object.assign(t, { status: 'Completed', completedAt: at(0) });
		else if (t.status === 'Pending') Object.assign(t, { status: 'Processing', startedAt: at(0) });
	}
}

function asPersonAsset(id: string): PersonAssetDto | null {
	const item = byId.get(id);
	if (!item) return null;
	return {
		id: item.id,
		fileName: item.fileName,
		type: item.type,
		fileCreatedAt: item.fileCreatedAt,
		hasThumbnails: true,
		dominantColor: null
	};
}

function liveDetail(): AssetDetailResponse {
	const item = byId.get('2026-09-7')!;
	return {
		id: LIVE_ID,
		fileName: 'IMG_LIVE.HEIC',
		fullPath: '/assets/users/ana/IMG_LIVE.HEIC',
		fileSize: 2_100_000,
		fileCreatedAt: item.fileCreatedAt,
		fileModifiedAt: item.fileCreatedAt,
		capturedAt: item.fileCreatedAt,
		extension: '.heic',
		scannedAt: item.fileCreatedAt,
		type: 'Image',
		checksum: 'sum-live',
		hasExif: true,
		hasThumbnails: true,
		folderId: null,
		folderPath: '/assets/users/ana/Camera',
		exif: null,
		thumbnails: [],
		tags: ['LivePhoto'],
		userTags: [],
		autoTags: [],
		syncStatus: 'Synced',
		isFavorite: false,
		isArchived: false,
		isFileMissing: false,
		caption: null,
		aiDescription: null,
		isReadOnly: false,
		isOwner: true,
		canEdit: true,
		canSaveMotionFrame: true
	};
}

const frameSvg = (index: number) =>
	`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="hsl(${index * 12} 50% 50%)"/><text x="200" y="160" font-size="64" text-anchor="middle" fill="#fff">${index}</text></svg>`;

const handle: FakeHandler = async ({ method, path, request, authorized, json, route, state }) => {
	const searchPeople = path.match(/^\/api\/search\/people\/([^/]+)\/assets$/);
	const match = path.match(/^\/api\/assets\/([^/]+)(\/.*)?$/);
	if (!searchPeople && !match) return false;
	const done = (body: unknown) => json(200, body).then(() => true);

	if (searchPeople) {
		if (!authorized) return json(401).then(() => true);
		const ids = new Set(
			peopleState(state)
				.faces.filter((f) => f.personId === searchPeople[1] && !f.rejected)
				.map((f) => f.assetId)
		);
		const items = [...ids].map(asPersonAsset).filter((item) => item !== null);
		return done({ total: items.length, items });
	}

	const [, id, rest = ''] = match!;
	// Media of the fake Live Photo: the base fake serves only thumbnails and content.
	if (id === LIVE_ID && method === 'GET' && rest === '/motion/frames') {
		return done({ frameCount: 30 });
	}
	const frame = rest.match(/^\/motion\/frames\/(\d+)$/);
	if (frame && method === 'GET') {
		return route
			.fulfill({ status: 200, contentType: 'image/svg+xml', body: frameSvg(Number(frame[1])) })
			.then(() => true);
	}
	if (!authorized) return false;
	const s = viewerState(state);

	if (id === LIVE_ID && rest === '' && method === 'GET') return done(liveDetail());
	const save = rest.match(/^\/motion\/frames\/(\d+)\/save$/);
	if (save && method === 'POST') {
		s.savedFrames.push(Number(save[1]));
		return done({ assetId: '2026-09-1' });
	}

	switch (`${method} ${rest}`) {
		case 'GET /faces': {
			const faces = peopleState(state).faces.filter((f) => f.assetId === id);
			return done(
				faces.map((f, index): FaceDto => ({
					id: f.id,
					assetId: f.assetId,
					personId: f.personId,
					boundingBoxX: 0.15 + index * 0.4,
					boundingBoxY: 0.25,
					boundingBoxW: 0.18,
					boundingBoxH: 0.3,
					confidence: 0.97,
					isManuallyAssigned: false,
					isRejected: f.rejected,
					suggestedPersonId: f.suggestedPersonId,
					suggestedDistance: f.suggestedPersonId ? 0.4 : null
				}))
			);
		}
		case 'GET /enrichment': {
			s.tasks[id] ??= initialTasks();
			const tasks = s.tasks[id];
			const snapshot = structuredClone(tasks);
			advance(tasks);
			return done({ assetId: id, fileName: byId.get(id)?.fileName ?? id, tasks: snapshot });
		}
		case 'POST /enrichment/retry': {
			const taskType = new URL(request.url()).searchParams.get('taskType')!;
			s.tasks[id] ??= initialTasks();
			s.tasks[id].push(task(taskType, 'Pending', { createdAt: at(0), startedAt: null }));
			s.retried.push(taskType);
			return done({ assetId: id, taskType, status: 'Pending' });
		}
		case 'POST /enrichment/retry-all': {
			s.tasks[id] ??= initialTasks();
			const failed = s.tasks[id].filter((t) => t.status === 'Failed');
			for (const t of failed) Object.assign(t, { status: 'Pending', errorMessage: null });
			s.retried.push(...failed.map((t) => t.taskType));
			return done({ assetId: id, retried: failed.length });
		}
		case 'GET /text': {
			const lines = id === TEXT_PHOTO ? ['Café Central', 'Menú del día 12 €'] : [];
			return done(
				lines.map((text, lineIndex): RecognizedTextLineDto => ({
					id: `line-${lineIndex}`,
					text,
					confidence: 0.95,
					bBoxX: 0.1,
					bBoxY: 0.1 + lineIndex * 0.1,
					bBoxWidth: 0.5,
					bBoxHeight: 0.05,
					lineIndex
				}))
			);
		}
		case 'GET /objects':
			return done(
				['dog', 'person', 'dog'].map((label, i): DetectedObjectDto => ({
					id: `object-${i}`,
					label,
					classId: i,
					confidence: 0.9 - i * 0.1,
					boundingBoxX: 0,
					boundingBoxY: 0,
					boundingBoxW: 0.2,
					boundingBoxH: 0.2
				}))
			);
		case 'GET /scenes':
			return done([
				{ id: 'scene-1', label: 'beach', classId: 1, confidence: 0.8, rank: 1 }
			] satisfies ClassifiedSceneDto[]);
		case 'GET /same-day': {
			const key = id.split('-').slice(0, 2).join('-');
			const items = library
				.items(key)
				.slice(0, 6)
				.map((item) => asPersonAsset(item.id)!);
			return done({ total: items.length, items });
		}
		case 'GET /date/suggestion': {
			const captured = byId.get(id)?.fileCreatedAt ?? '2026-09-01T10:00:00Z';
			return done({
				currentDate: captured.replace('Z', ''),
				currentSource: 'FileSystem',
				exifDate: '2019-07-09T05:36:00',
				inferredDate: '2019-07-09T05:36:00',
				inferredOrigin: 'FileName',
				fileDate: captured
			});
		}
		case 'PATCH /date': {
			const body = request.postDataJSON() as { dateTaken: string; writeToFile: boolean };
			s.dates.push({ assetId: id, ...body });
			return done({
				dateTaken: body.dateTaken,
				capturedAt: body.dateTaken,
				fileWritten: body.writeToFile,
				reason: null
			});
		}
	}
	return false;
};

export default handle;
