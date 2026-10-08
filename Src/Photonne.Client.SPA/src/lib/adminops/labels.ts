import type { BackgroundTaskResponse } from '#lib/api/index.js';
import { m } from '#lib/paraglide/messages.js';
import { taskByProgressKey } from './catalog.js';
import { taskKey } from './tasks.js';

/** The name of a background task, from its type (or maintenance kind). */
export function taskTitle(task: Pick<BackgroundTaskResponse, 'type' | 'parameters'>) {
	const key = taskKey(task);
	const known = taskByProgressKey.get(key);
	if (known) return known.title();
	switch (key) {
		case 'LibraryScan':
			return m.ops_task_library_scan();
		case 'FaceClustering':
			return m.ops_task_face_clustering();
		case 'Maintenance':
			return m.ops_task_maintenance();
		default:
			return key;
	}
}

export function statusLabel(status: string) {
	switch (status) {
		case 'Running':
			return m.ops_status_running();
		case 'Completed':
			return m.ops_status_completed();
		case 'Cancelled':
			return m.ops_status_cancelled();
		case 'Failed':
			return m.ops_status_failed();
		default:
			return status;
	}
}

/** Server `AssetEnrichmentType` names, in pipeline order. */
export const enrichmentTypes = [
	'Exif',
	'Thumbnails',
	'MediaRecognition',
	'FaceRecognition',
	'ObjectDetection',
	'SceneClassification',
	'TextRecognition',
	'ImageEmbedding'
] as const;

export function enrichmentLabel(type: string) {
	switch (type) {
		case 'Exif':
			return m.ops_type_exif();
		case 'Thumbnails':
			return m.ops_type_thumbnails();
		case 'MediaRecognition':
			return m.ops_type_media();
		case 'FaceRecognition':
			return m.ops_type_faces();
		case 'ObjectDetection':
			return m.ops_type_objects();
		case 'SceneClassification':
			return m.ops_type_scenes();
		case 'TextRecognition':
			return m.ops_type_text();
		case 'ImageEmbedding':
			return m.ops_type_embedding();
		default:
			return type;
	}
}

/** The `DELETE /api/admin/maintenance/{kind}/queue` kind of a queue that can be emptied. */
export const queueKinds: Record<string, string> = {
	MediaRecognition: 'media-recognition',
	FaceRecognition: 'face-recognition',
	ObjectDetection: 'object-detection',
	SceneClassification: 'scene-classification',
	TextRecognition: 'text-recognition',
	ImageEmbedding: 'image-embedding'
};

/** Server `EnrichmentFailureKind` names: why a task failed. */
export const failureKinds = ['Transient', 'Permanent', 'NeedsAction', 'Unknown'] as const;

export function failureKindLabel(kind: string) {
	switch (kind) {
		case 'Transient':
			return m.ops_kind_transient();
		case 'Permanent':
			return m.ops_kind_permanent();
		case 'NeedsAction':
			return m.ops_kind_needs_action();
		default:
			return m.ops_kind_unknown();
	}
}

export function failureKindHint(kind: string) {
	switch (kind) {
		case 'Transient':
			return m.ops_kind_transient_hint();
		case 'Permanent':
			return m.ops_kind_permanent_hint();
		case 'NeedsAction':
			return m.ops_kind_needs_action_hint();
		default:
			return m.ops_kind_unknown_hint();
	}
}
