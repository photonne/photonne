import { describe, expect, it } from 'vitest';
import { singleDay } from './dates.js';

describe('singleDay', () => {
	it('is one day when both ends share the UTC date', () => {
		expect(singleDay('2024-10-08T09:00:00Z', '2024-10-08T21:30:00Z')).toBe(true);
		expect(singleDay('2024-10-08T09:00:00Z', '2024-10-08T09:00:00Z')).toBe(true);
	});

	it('reads an end on midnight as exclusive (a window of whole days)', () => {
		expect(singleDay('2024-10-08T00:00:00Z', '2024-10-09T00:00:00Z')).toBe(true);
	});

	it('spans days otherwise', () => {
		expect(singleDay('2024-10-08T09:00:00Z', '2024-10-09T08:00:00Z')).toBe(false);
	});
});
