import { describe, expect, it, vi } from 'vitest';

vi.mock('#lib/paraglide/runtime.js', () => ({ getLocale: () => 'es' }));

const { formatBytes, fromDateTimeLocal, monthTitle, toDateTimeLocal } = await import('./format.js');

describe('format', () => {
	it('titles a bucket month', () => {
		expect(monthTitle('2026-09')).toBe('septiembre de 2026');
		expect(monthTitle('all')).toBe('');
	});

	it('formats sizes', () => {
		expect(formatBytes(512)).toBe('512 B');
		expect(formatBytes(3.25 * 1024 * 1024)).toBe('3,3 MB');
	});

	it('round-trips a capture time through a datetime-local value', () => {
		const iso = '2026-09-03T14:05:00.000Z';

		expect(toDateTimeLocal(iso)).toBe('2026-09-03T14:05');
		expect(fromDateTimeLocal(toDateTimeLocal(iso))).toBe(iso);
	});
});
