import { describe, expect, it } from 'vitest';
import { applyScanUpdate, cronOf, scheduleOf, startScan } from './libraries.js';

describe('libraries', () => {
	it('reads the schedules the server understands', () => {
		expect(scheduleOf(null)).toBe('manual');
		expect(scheduleOf('  ')).toBe('manual');
		expect(scheduleOf('@daily')).toBe('daily');
		expect(scheduleOf('@Weekly')).toBe('weekly');
		expect(scheduleOf('0 * * * *')).toBe('hourly');
		expect(scheduleOf('0 0 1 * *')).toBe('monthly');
		expect(scheduleOf('*/5 * * * *')).toBe('unknown');
	});

	it('writes schedules as @names', () => {
		expect(cronOf('manual')).toBeNull();
		expect(cronOf('weekly')).toBe('@weekly');
	});

	it('folds streamed updates into the scan state', () => {
		let state = startScan('lib-1');
		state = applyScanUpdate(state, {
			message: 'Escaneando…',
			percentage: 40,
			assetsFound: 10,
			assetsIndexed: 4,
			assetsMarkedMissing: 0,
			isCompleted: false,
			taskId: 'task-1'
		});
		state = applyScanUpdate(state, { percentage: 140, isCompleted: true, error: null });

		expect(state).toMatchObject({
			taskId: 'task-1',
			message: 'Escaneando…',
			percentage: 100,
			found: 10,
			indexed: 4,
			done: true,
			error: null
		});
	});
});
