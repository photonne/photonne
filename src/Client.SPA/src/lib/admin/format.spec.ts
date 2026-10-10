import { describe, expect, it, vi } from 'vitest';

vi.mock('#lib/paraglide/runtime.js', () => ({ getLocale: () => 'es' }));

const { compact, count, percent, relativeTime, shortMonth } = await import('./format.js');

describe('admin format', () => {
	it('formats numbers in the user language', () => {
		expect(count(12345)).toBe('12.345');
		expect(percent(0.999, 1)).toMatch(/^99,9\s%$/);
		expect(compact(1500)).toMatch(/^1,5\smil$/);
	});

	it('says how long ago something happened', () => {
		const now = Date.parse('2026-10-08T12:00:00Z');

		expect(relativeTime('2026-10-08T11:59:30Z', now)).toBe('ahora');
		expect(relativeTime('2026-10-05T12:00:00Z', now)).toBe('hace 3 días');
		expect(relativeTime('2026-10-08T14:00:00Z', now)).toBe('dentro de 2 horas');
	});

	it('names a growth month briefly', () => {
		expect(shortMonth('2026-09')).toMatch(/sept?\.? 2026/);
	});
});
