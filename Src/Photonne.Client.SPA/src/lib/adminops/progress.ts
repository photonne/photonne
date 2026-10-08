import type { PhysicalDuplicateGroup } from '#lib/api/index.js';

/**
 * One progress event of a background job, whatever stream it came from.
 * Index, thumbnails, metadata, dates and duplicates send `statistics`;
 * maintenance kinds and face clustering send `processed` / `affected`
 * (their `MaintenanceProgressUpdate` is not in the OpenAPI contract).
 */
export interface ProgressEvent {
	percentage: number;
	message: string;
	isCompleted: boolean;
	taskId: string | null;
	processed: number | null;
	affected: number | null;
	statistics: Record<string, unknown> | null;
	foundGroup: PhysicalDuplicateGroup | null;
}

function numberOr<T>(value: unknown, fallback: T): number | T {
	return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

/** Reads an event defensively: anything that isn't an object is dropped. */
export function toProgressEvent(raw: unknown): ProgressEvent | null {
	if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return null;
	const value = raw as Record<string, unknown>;
	const statistics =
		typeof value.statistics === 'object' && value.statistics !== null
			? (value.statistics as Record<string, unknown>)
			: null;
	const group = value.foundGroup as PhysicalDuplicateGroup | null | undefined;
	return {
		percentage: Math.min(100, Math.max(0, numberOr(value.percentage, 0))),
		message: typeof value.message === 'string' ? value.message : '',
		isCompleted: value.isCompleted === true,
		taskId: typeof value.taskId === 'string' ? value.taskId : null,
		processed: numberOr(value.processed, null),
		affected: numberOr(value.affected, null),
		statistics,
		foundGroup: group && Array.isArray(group.files) ? group : null
	};
}

/** A numeric statistic, or null when the stream didn't send it. */
export function stat(event: ProgressEvent | null, name: string): number | null {
	const value = event?.statistics?.[name];
	return typeof value === 'number' ? value : null;
}
