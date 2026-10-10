import type { BackgroundTaskResponse } from '#lib/api/index.js';
import type { ProgressEvent } from './progress.js';

/** `BackgroundTaskEntry.Status` on the server. */
export type TaskStatus = 'Running' | 'Completed' | 'Cancelled' | 'Failed';

export function isRunning(task: Pick<BackgroundTaskResponse, 'status'>) {
	return task.status === 'Running';
}

/**
 * What identifies a task for the screens: every maintenance kind registers
 * under the type "Maintenance", so the kind (in `parameters`) is what tells
 * one from another; everything else is its type.
 */
export function taskKey(task: Pick<BackgroundTaskResponse, 'type' | 'parameters'>) {
	return task.parameters?.kind ?? task.type;
}

/** Running first, then newest first. */
export function sortTasks(tasks: readonly BackgroundTaskResponse[]) {
	return [...tasks].sort(
		(a, b) =>
			Number(isRunning(b)) - Number(isRunning(a)) ||
			Date.parse(b.startedAt) - Date.parse(a.startedAt)
	);
}

/** The most recent task per key (running or not). */
export function latestByKey(tasks: readonly BackgroundTaskResponse[]) {
	const latest = new Map<string, BackgroundTaskResponse>();
	for (const task of tasks) {
		const key = taskKey(task);
		const known = latest.get(key);
		if (!known || Date.parse(task.startedAt) > Date.parse(known.startedAt)) latest.set(key, task);
	}
	return latest;
}

/**
 * A polled task with the live stream's news laid over it. The resume stream
 * replays from the start, so the bar never goes back below what polling saw.
 */
export function withLive(
	task: BackgroundTaskResponse,
	live: ProgressEvent | undefined
): BackgroundTaskResponse {
	if (!live || !isRunning(task)) return task;
	return {
		...task,
		percentage: Math.max(task.percentage, live.percentage),
		lastMessage: live.message || task.lastMessage
	};
}

/** How long a task ran (or has been running). */
export function elapsedMs(
	task: Pick<BackgroundTaskResponse, 'startedAt' | 'finishedAt'>,
	now = Date.now()
) {
	const end = task.finishedAt ? Date.parse(task.finishedAt) : now;
	return Math.max(0, end - Date.parse(task.startedAt));
}

/**
 * Rough time left from the progress so far, or null while there is too
 * little to go on (under 2 % or under ten seconds).
 */
export function remainingMs(
	task: Pick<BackgroundTaskResponse, 'startedAt' | 'percentage'>,
	now = Date.now()
) {
	const spent = now - Date.parse(task.startedAt);
	if (task.percentage < 2 || task.percentage >= 100 || spent < 10_000) return null;
	return (spent * (100 - task.percentage)) / task.percentage;
}
