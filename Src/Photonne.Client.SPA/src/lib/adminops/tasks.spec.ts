import { describe, expect, it } from 'vitest';
import type { BackgroundTaskResponse } from '#lib/api/index.js';
import { toProgressEvent } from './progress.js';
import { queueProgress } from './queue-progress.js';
import { elapsedMs, latestByKey, remainingMs, sortTasks, taskKey, withLive } from './tasks.js';

function task(over: Partial<BackgroundTaskResponse>): BackgroundTaskResponse {
	return {
		id: 'id',
		type: 'Thumbnails',
		status: 'Completed',
		percentage: 100,
		lastMessage: '',
		startedAt: '2026-10-08T10:00:00Z',
		finishedAt: '2026-10-08T10:05:00Z',
		parameters: {},
		...over
	};
}

describe('tasks', () => {
	it('keys maintenance tasks by kind and the rest by type', () => {
		expect(taskKey(task({ type: 'Maintenance', parameters: { kind: 'empty-trash' } }))).toBe(
			'empty-trash'
		);
		expect(taskKey(task({ type: 'IndexAssets' }))).toBe('IndexAssets');
	});

	it('sorts running first, then newest', () => {
		const old = task({ id: 'old', startedAt: '2026-10-08T09:00:00Z' });
		const recent = task({ id: 'recent', startedAt: '2026-10-08T11:00:00Z' });
		const running = task({ id: 'running', status: 'Running', startedAt: '2026-10-08T08:00:00Z' });
		expect(sortTasks([old, recent, running]).map((t) => t.id)).toEqual([
			'running',
			'recent',
			'old'
		]);
	});

	it('keeps the latest task per key', () => {
		const a = task({ id: 'a', startedAt: '2026-10-08T09:00:00Z' });
		const b = task({ id: 'b', startedAt: '2026-10-08T10:00:00Z' });
		expect(latestByKey([b, a]).get('Thumbnails')?.id).toBe('b');
	});

	it('lays live progress over a running task without going backwards', () => {
		const running = task({ status: 'Running', percentage: 40, lastMessage: 'polled' });
		const live = toProgressEvent({ percentage: 30, message: 'live' })!;
		expect(withLive(running, live)).toMatchObject({ percentage: 40, lastMessage: 'live' });
		expect(withLive(running, { ...live, percentage: 55 }).percentage).toBe(55);
		expect(withLive(task({}), live).lastMessage).toBe('');
	});

	it('measures elapsed and estimates what is left', () => {
		const now = Date.parse('2026-10-08T10:10:00Z');
		const running = task({ status: 'Running', finishedAt: null, percentage: 50 });
		expect(elapsedMs(running, now)).toBe(600_000);
		expect(elapsedMs(task({}), now)).toBe(300_000);
		expect(remainingMs(running, now)).toBe(600_000);
		expect(remainingMs({ ...running, percentage: 1 }, now)).toBeNull();
	});
});

describe('progress events', () => {
	it('reads what each stream sends and clamps the percentage', () => {
		expect(
			toProgressEvent({
				percentage: 140,
				message: 'x',
				isCompleted: true,
				taskId: 't',
				affected: 3
			})
		).toMatchObject({
			percentage: 100,
			isCompleted: true,
			taskId: 't',
			affected: 3,
			processed: null
		});
		expect(toProgressEvent([1])).toBeNull();
		expect(toProgressEvent({ foundGroup: { hash: 'h' } })?.foundGroup).toBeNull();
	});
});

describe('queueProgress', () => {
	it('measures the run since it was queued', () => {
		expect(queueProgress({ completed: 150, inQueue: 50, processing: 0 }, 100)).toBe(50);
		expect(queueProgress({ completed: 100, inQueue: 0, processing: 0 }, 100)).toBeNull();
		expect(queueProgress({ completed: 150, inQueue: 50, processing: 0 }, null)).toBeNull();
	});
});
