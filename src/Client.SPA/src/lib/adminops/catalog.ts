import type { IconName } from '#lib/components/Icon.svelte';
import {
	extractMetadataStream,
	faceClusteringStream,
	generateThumbnailsStream,
	getApiAdminMaintenanceFaceRecognitionPendingCount,
	getApiAdminMaintenanceImageEmbeddingPendingCount,
	getApiAdminMaintenanceMediaRecognitionPendingCount,
	getApiAdminMaintenanceObjectDetectionPendingCount,
	getApiAdminMaintenanceSceneClassificationPendingCount,
	getApiAdminMaintenanceTextRecognitionPendingCount,
	indexAssetsStream,
	maintenanceTaskStream,
	postApiAdminMaintenanceFaceRecognitionBackfill,
	postApiAdminMaintenanceImageEmbeddingBackfill,
	postApiAdminMaintenanceMediaRecognitionBackfill,
	postApiAdminMaintenanceObjectDetectionBackfill,
	postApiAdminMaintenanceSceneClassificationBackfill,
	postApiAdminMaintenanceTextRecognitionBackfill,
	restoreDatesStream,
	type BackfillRequest,
	type BackfillResponse
} from '#lib/api/index.js';
import { m } from '#lib/paraglide/messages.js';
import { icons } from './icons.js';
import type { StreamStarter } from './streams.js';

/**
 * - newPhotos: what to run after adding files, in this order.
 * - ai: the per-asset analysis queues.
 * - memories: the nightly Memories chain on demand, in dependency order
 *   (coordinates → place names → trips → memories).
 * - repair: something is already wrong.
 * - cleanup: reclaims space; the only group that deletes anything.
 */
export type SectionId = 'newPhotos' | 'ai' | 'memories' | 'repair' | 'cleanup';

export const sectionIds: SectionId[] = ['newPhotos', 'ai', 'memories', 'repair', 'cleanup'];

export const sectionTitles: Record<SectionId, { title: () => string; hint: () => string }> = {
	newPhotos: { title: m.ops_mt_section_new, hint: m.ops_mt_section_new_hint },
	ai: { title: m.ops_mt_section_ai, hint: m.ops_mt_section_ai_hint },
	memories: { title: m.ops_mt_section_memories, hint: m.ops_mt_section_memories_hint },
	repair: { title: m.ops_mt_section_repair, hint: m.ops_mt_section_repair_hint },
	cleanup: { title: m.ops_mt_section_cleanup, hint: m.ops_mt_section_cleanup_hint }
};

/** The counters behind an analysis queue (media recognition has no `processing`). */
export interface PendingCounts {
	unprocessed: number;
	inQueue: number;
	completed: number;
	retrying: number;
	failed: number;
	processing?: number;
}

/** One of the per-asset queues fed by `/api/admin/maintenance/{kind}/backfill`. */
export interface BackfillKind {
	/** Path segment, also the key of `DELETE /{kind}/queue`. */
	kind: string;
	pending: (signal: AbortSignal) => Promise<PendingCounts>;
	backfill: (body: BackfillRequest) => Promise<BackfillResponse>;
}

const backfill = (
	kind: string,
	pending: (options: {
		signal: AbortSignal;
		throwOnError: true;
	}) => Promise<{ data: PendingCounts }>,
	run: (options: {
		body: BackfillRequest;
		throwOnError: true;
	}) => Promise<{ data: BackfillResponse }>
): BackfillKind => ({
	kind,
	pending: async (signal) => (await pending({ signal, throwOnError: true })).data,
	backfill: async (body) => (await run({ body, throwOnError: true })).data
});

export type Options = Record<string, boolean>;

export interface TaskOption {
	id: string;
	label: () => string;
	hint?: () => string;
	default: boolean;
	/** Greyed out (and sent as false) unless this holds. */
	enabledWhen?: (options: Options) => boolean;
}

export interface MaintenanceTask {
	id: string;
	section: SectionId;
	icon: { name?: IconName; path?: string };
	title: () => string;
	description: () => string;
	/** What the row says while it runs: a verb, not a number. */
	active: () => string;
	/** `taskKey` of its background task in `/api/tasks`, for the ones that run as one. */
	progressKey?: string;
	start?: (options: Options) => StreamStarter;
	options?: TaskOption[];
	queue?: BackfillKind;
	/** The `AssetEnrichmentType` behind the row: queue summary key and failures filter. */
	enrichmentType?: string;
	/** Asks before running, unless `skip` says this run deletes nothing. */
	confirm?: { title: () => string; message: () => string; skip?: (options: Options) => boolean };
	/** Opens its own page instead of running inline. */
	href?: string;
}

const maintenance =
	(kind: string, dryRun = false): MaintenanceTask['start'] =>
	(options) =>
	(signal) =>
		maintenanceTaskStream({
			path: { kind },
			query: dryRun ? { dryRun: options.dryRun === true } : undefined,
			parseAs: 'stream',
			signal
		});

export const faceClustering: StreamStarter = (signal) =>
	faceClusteringStream({ parseAs: 'stream', signal });

export const catalog: MaintenanceTask[] = [
	{
		id: 'index',
		section: 'newPhotos',
		icon: { name: 'refresh' },
		title: m.ops_mt_index,
		description: m.ops_mt_index_desc,
		active: m.ops_mt_index_active,
		progressKey: 'IndexAssets',
		start: () => (signal) => indexAssetsStream({ parseAs: 'stream', signal })
	},
	{
		id: 'metadata',
		section: 'newPhotos',
		icon: { name: 'info' },
		title: m.ops_mt_metadata,
		description: m.ops_mt_metadata_desc,
		active: m.ops_mt_metadata_active,
		progressKey: 'Metadata',
		enrichmentType: 'Exif',
		options: [
			{
				id: 'overwrite',
				label: m.ops_mt_opt_overwrite,
				hint: m.ops_mt_opt_overwrite_hint,
				default: false
			}
		],
		start: (options) => (signal) =>
			extractMetadataStream({
				query: { overwrite: options.overwrite === true },
				parseAs: 'stream',
				signal
			})
	},
	{
		id: 'thumbnails',
		section: 'newPhotos',
		icon: { path: icons.image },
		title: m.ops_mt_thumbnails,
		description: m.ops_mt_thumbnails_desc,
		active: m.ops_mt_thumbnails_active,
		progressKey: 'Thumbnails',
		enrichmentType: 'Thumbnails',
		options: [
			{
				id: 'regenerate',
				label: m.ops_mt_opt_regenerate,
				hint: m.ops_mt_opt_regenerate_hint,
				default: false
			}
		],
		start: (options) => (signal) =>
			generateThumbnailsStream({
				query: { regenerate: options.regenerate === true },
				parseAs: 'stream',
				signal
			})
	},
	{
		id: 'faces',
		section: 'ai',
		icon: { path: icons.face },
		title: m.ops_mt_faces,
		description: m.ops_mt_faces_desc,
		active: m.ops_mt_faces_active,
		enrichmentType: 'FaceRecognition',
		queue: backfill(
			'face-recognition',
			getApiAdminMaintenanceFaceRecognitionPendingCount,
			postApiAdminMaintenanceFaceRecognitionBackfill
		)
	},
	{
		id: 'objects',
		section: 'ai',
		icon: { path: icons.category },
		title: m.ops_mt_objects,
		description: m.ops_mt_objects_desc,
		active: m.ops_mt_objects_active,
		enrichmentType: 'ObjectDetection',
		queue: backfill(
			'object-detection',
			getApiAdminMaintenanceObjectDetectionPendingCount,
			postApiAdminMaintenanceObjectDetectionBackfill
		)
	},
	{
		id: 'scenes',
		section: 'ai',
		icon: { path: icons.landscape },
		title: m.ops_mt_scenes,
		description: m.ops_mt_scenes_desc,
		active: m.ops_mt_scenes_active,
		enrichmentType: 'SceneClassification',
		queue: backfill(
			'scene-classification',
			getApiAdminMaintenanceSceneClassificationPendingCount,
			postApiAdminMaintenanceSceneClassificationBackfill
		)
	},
	{
		id: 'text',
		section: 'ai',
		icon: { path: icons.text },
		title: m.ops_mt_text,
		description: m.ops_mt_text_desc,
		active: m.ops_mt_text_active,
		enrichmentType: 'TextRecognition',
		queue: backfill(
			'text-recognition',
			getApiAdminMaintenanceTextRecognitionPendingCount,
			postApiAdminMaintenanceTextRecognitionBackfill
		)
	},
	{
		id: 'embedding',
		section: 'ai',
		icon: { path: icons.imageSearch },
		title: m.ops_mt_embedding,
		description: m.ops_mt_embedding_desc,
		active: m.ops_mt_embedding_active,
		enrichmentType: 'ImageEmbedding',
		queue: backfill(
			'image-embedding',
			getApiAdminMaintenanceImageEmbeddingPendingCount,
			postApiAdminMaintenanceImageEmbeddingBackfill
		)
	},
	{
		id: 'interpolate',
		section: 'memories',
		icon: { path: icons.myLocation },
		title: m.ops_mt_interpolate,
		description: m.ops_mt_interpolate_desc,
		active: m.ops_mt_interpolate_active,
		progressKey: 'interpolate-locations',
		start: maintenance('interpolate-locations')
	},
	{
		id: 'geocode',
		section: 'memories',
		icon: { name: 'place' },
		title: m.ops_mt_geocode,
		description: m.ops_mt_geocode_desc,
		active: m.ops_mt_geocode_active,
		progressKey: 'reverse-geocode',
		start: maintenance('reverse-geocode')
	},
	{
		id: 'trips',
		section: 'memories',
		icon: { path: icons.flight },
		title: m.ops_mt_trips,
		description: m.ops_mt_trips_desc,
		active: m.ops_mt_trips_active,
		progressKey: 'detect-trips',
		start: maintenance('detect-trips')
	},
	{
		id: 'memories',
		section: 'memories',
		icon: { path: icons.sparkle },
		title: m.ops_mt_memories,
		description: m.ops_mt_memories_desc,
		active: m.ops_mt_memories_active,
		progressKey: 'generate-memories',
		start: maintenance('generate-memories')
	},
	{
		id: 'dates',
		section: 'repair',
		icon: { path: icons.date },
		title: m.ops_mt_dates,
		description: m.ops_mt_dates_desc,
		active: m.ops_mt_dates_active,
		progressKey: 'DateRestore',
		options: [
			{
				id: 'fromFile',
				label: m.ops_mt_opt_from_file,
				hint: m.ops_mt_opt_from_file_hint,
				default: false
			},
			{
				id: 'inferFromPath',
				label: m.ops_mt_opt_infer_path,
				hint: m.ops_mt_opt_infer_path_hint,
				default: false
			},
			{
				id: 'useFileDate',
				label: m.ops_mt_opt_file_date,
				hint: m.ops_mt_opt_file_date_hint,
				default: false
			},
			{
				id: 'writeToFile',
				label: m.ops_mt_opt_write_file,
				hint: m.ops_mt_opt_write_file_hint,
				// An inferred date kept only in the database is lost on a
				// rebuild; written into the image it survives anything.
				default: true,
				enabledWhen: (options) => options.inferFromPath === true || options.useFileDate === true
			},
			{ id: 'dryRun', label: m.ops_mt_opt_dry_run, hint: m.ops_mt_opt_dry_run_hint, default: false }
		],
		start: (options) => (signal) =>
			restoreDatesStream({
				query: {
					fromFile: options.fromFile === true,
					inferFromPath: options.inferFromPath === true,
					useFileDate: options.useFileDate === true,
					writeToFile:
						(options.inferFromPath === true || options.useFileDate === true) &&
						options.writeToFile === true,
					dryRun: options.dryRun === true
				},
				parseAs: 'stream',
				signal
			})
	},
	{
		id: 'media',
		section: 'repair',
		icon: { name: 'livePhoto' },
		title: m.ops_mt_media,
		description: m.ops_mt_media_desc,
		active: m.ops_mt_media_active,
		enrichmentType: 'MediaRecognition',
		queue: backfill(
			'media-recognition',
			getApiAdminMaintenanceMediaRecognitionPendingCount,
			postApiAdminMaintenanceMediaRecognitionBackfill
		)
	},
	{
		id: 'duplicates',
		section: 'repair',
		icon: { name: 'copy' },
		title: m.ops_mt_duplicates,
		description: m.ops_mt_duplicates_desc,
		active: m.ops_mt_duplicates_active,
		href: '/admin/maintenance/duplicates'
	},
	{
		id: 'missing',
		section: 'repair',
		icon: { name: 'search' },
		title: m.ops_mt_missing,
		description: m.ops_mt_missing_desc,
		active: m.ops_mt_missing_active,
		progressKey: 'missing-files',
		start: maintenance('missing-files')
	},
	{
		id: 'coverage',
		section: 'repair',
		icon: { path: icons.factCheck },
		title: m.ops_mt_coverage,
		description: m.ops_mt_coverage_desc,
		active: m.ops_mt_coverage_active,
		progressKey: 'indexing-coverage',
		start: maintenance('indexing-coverage')
	},
	{
		id: 'sizes',
		section: 'repair',
		icon: { path: icons.ruler },
		title: m.ops_mt_sizes,
		description: m.ops_mt_sizes_desc,
		active: m.ops_mt_sizes_active,
		progressKey: 'recalculate-sizes',
		start: maintenance('recalculate-sizes')
	},
	{
		id: 'orphans',
		section: 'cleanup',
		icon: { path: icons.brokenImage },
		title: m.ops_mt_orphans,
		description: m.ops_mt_orphans_desc,
		active: m.ops_mt_orphans_active,
		progressKey: 'orphan-thumbnails',
		// A thumbnail with no asset behind it can always be regenerated.
		start: maintenance('orphan-thumbnails')
	},
	{
		id: 'emptyTrash',
		section: 'cleanup',
		icon: { path: icons.deleteForever },
		title: m.ops_mt_empty_trash,
		description: m.ops_mt_empty_trash_desc,
		active: m.ops_mt_empty_trash_active,
		progressKey: 'empty-trash',
		start: maintenance('empty-trash'),
		confirm: { title: m.ops_mt_empty_trash_confirm, message: m.ops_mt_empty_trash_confirm_text }
	},
	{
		id: 'purge',
		section: 'cleanup',
		icon: { path: icons.deleteSweep },
		title: m.ops_mt_purge,
		description: m.ops_mt_purge_desc,
		active: m.ops_mt_purge_active,
		progressKey: 'purge-missing',
		options: [
			{
				id: 'dryRun',
				label: m.ops_mt_opt_simulate,
				hint: m.ops_mt_opt_simulate_hint,
				default: false
			}
		],
		start: maintenance('purge-missing', true),
		confirm: {
			title: m.ops_mt_purge_confirm,
			message: m.ops_mt_purge_confirm_text,
			skip: (options) => options.dryRun === true
		}
	}
];

/** Which row a background task belongs to (by `taskKey`). */
export const taskByProgressKey = new Map(
	catalog.filter((task) => task.progressKey).map((task) => [task.progressKey!, task])
);

export function defaultOptions(task: MaintenanceTask): Options {
	return Object.fromEntries((task.options ?? []).map((option) => [option.id, option.default]));
}
