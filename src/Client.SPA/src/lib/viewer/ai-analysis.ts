import type { EnrichmentTaskDto } from '#lib/api/index.js';

/** The five per-photo AI passes, by the task type name the server uses. */
export const AI_ANALYSES = [
	{ key: 'faces', taskType: 'FaceRecognition' },
	{ key: 'objects', taskType: 'ObjectDetection' },
	{ key: 'scenes', taskType: 'SceneClassification' },
	{ key: 'text', taskType: 'TextRecognition' },
	{ key: 'embeddings', taskType: 'ImageEmbedding' }
] as const;

export type AiAnalysis = (typeof AI_ANALYSES)[number];

/** What the latest attempt of one analysis is doing. */
export type AiStatus =
	| { kind: 'never' }
	| { kind: 'queued' }
	| { kind: 'running' }
	| { kind: 'done'; at: number | null }
	/** `retryAt` while the worker still plans another attempt. */
	| { kind: 'failed'; message: string | null; retryAt: number | null }
	| { kind: 'suppressed' };

export interface AiAnalysisRow {
	analysis: AiAnalysis;
	status: AiStatus;
}

/** How often the panel re-reads the tasks while one is queued or running. */
export const AI_POLL_MS = 3000;

const time = (iso: string | null | undefined) => {
	if (!iso) return null;
	const value = Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}Z`);
	return Number.isFinite(value) ? value : null;
};

function statusOf(task: EnrichmentTaskDto | undefined): AiStatus {
	switch (task?.status.toLowerCase()) {
		case 'pending':
			return { kind: 'queued' };
		case 'processing':
			return { kind: 'running' };
		case 'completed':
			return { kind: 'done', at: time(task.completedAt) };
		case 'failed':
			return {
				kind: 'failed',
				message: task.errorMessage?.trim() || null,
				retryAt: time(task.nextRetryAt)
			};
		case 'suppressed':
			return { kind: 'suppressed' };
		default:
			return { kind: 'never' };
	}
}

/**
 * One row per analysis. An asset keeps a task row per attempt ever made, so
 * each analysis is read off its latest one; a type the server never queued
 * shows as never analysed. Other task types (EXIF, thumbnails) are left out.
 */
export function aiAnalysisRows(tasks: readonly EnrichmentTaskDto[]): AiAnalysisRow[] {
	return AI_ANALYSES.map((analysis) => {
		let latest: EnrichmentTaskDto | undefined;
		for (const task of tasks) {
			if (task.taskType.toLowerCase() !== analysis.taskType.toLowerCase()) continue;
			if (!latest || (time(task.createdAt) ?? 0) > (time(latest.createdAt) ?? 0)) latest = task;
		}
		return { analysis, status: statusOf(latest) };
	});
}

/** Queued or running: the panel keeps polling and shows progress instead of a button. */
export function isBusy(status: AiStatus) {
	return status.kind === 'queued' || status.kind === 'running';
}

/** The task types of the rows that are busy. */
export function busyTypes(rows: readonly AiAnalysisRow[]) {
	return rows.filter((row) => isBusy(row.status)).map((row) => row.analysis.taskType);
}

/** Some analysis that was busy no longer is: what it produced is now stale on screen. */
export function someFinished(before: readonly string[], now: readonly string[]) {
	return before.some((type) => !now.includes(type));
}

/** "hace 5 min", "en 2 h": the distance from `nowMs`, in the largest unit that fits. */
export function relativeTime(atMs: number, nowMs: number, locale: string) {
	const seconds = Math.round((atMs - nowMs) / 1000);
	const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto', style: 'short' });
	const abs = Math.abs(seconds);
	if (abs < 60) return format.format(seconds, 'second');
	if (abs < 3600) return format.format(Math.round(seconds / 60), 'minute');
	if (abs < 86400) return format.format(Math.round(seconds / 3600), 'hour');
	return format.format(Math.round(seconds / 86400), 'day');
}
