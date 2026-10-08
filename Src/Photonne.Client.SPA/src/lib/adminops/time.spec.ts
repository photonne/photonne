import { describe, expect, it, vi } from 'vitest';

vi.mock('#lib/paraglide/runtime.js', () => ({ getLocale: () => 'es' }));

const { duration, timeAgo } = await import('./time.js');
const { parseCollapsed } = await import('./collapsed.js');

describe('time', () => {
	it('says how long ago', () => {
		const now = Date.parse('2026-10-08T12:00:00Z');
		expect(timeAgo('2026-10-08T11:59:30Z', now)).toBe('ahora');
		expect(timeAgo('2026-10-08T11:55:00Z', now)).toBe('hace 5 minutos');
		expect(timeAgo('2026-10-08T09:00:00Z', now)).toBe('hace 3 horas');
		expect(timeAgo('2026-10-06T12:00:00Z', now)).toBe('anteayer');
	});

	it('formats durations', () => {
		expect(duration(12_000)).toBe('12 s');
		expect(duration(252_000)).toBe('4 min 12 s');
		expect(duration(3_900_000)).toBe('1 h 05 min');
	});
});

describe('collapsed groups', () => {
	it('keeps the known groups and drops the rest', () => {
		expect(parseCollapsed(null)).toEqual([]);
		expect(parseCollapsed('ai, cleanup,renamed')).toEqual(['ai', 'cleanup']);
	});
});
