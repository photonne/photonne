import { describe, expect, it } from 'vitest';
import { arrangeItems, dateRange, inRange, monthInRange, yearInRange } from './timeline-filter.js';

const item = (capturedAt: string) => ({ capturedAt });

describe('timeline filter', () => {
	it('builds a range from two days in any order', () => {
		expect(dateRange('2024-07-20', '2024-07-02')).toEqual({ from: '2024-07-02', to: '2024-07-20' });
		expect(dateRange('2024-07-02', '')).toBeNull();
	});

	it('keeps the months and years with days in the range', () => {
		const range = dateRange('2023-12-30', '2024-02-01')!;
		expect(monthInRange('2023-12', range)).toBe(true);
		expect(monthInRange('2024-02', range)).toBe(true);
		expect(monthInRange('2024-03', range)).toBe(false);
		expect(yearInRange('2023', range)).toBe(true);
		expect(yearInRange('2022', range)).toBe(false);
	});

	it('keeps the photos of the days in the range, ends included', () => {
		const range = dateRange('2024-07-02', '2024-07-03')!;
		expect(inRange('2024-07-02T00:00:00Z', range)).toBe(true);
		expect(inRange('2024-07-03T23:59:59Z', range)).toBe(true);
		expect(inRange('2024-07-04T00:00:00Z', range)).toBe(false);
	});

	it('puts the oldest first on request', () => {
		const items = [
			item('2024-07-03T10:00:00Z'),
			item('2024-07-02T10:00:00Z'),
			item('2024-06-30T10:00:00Z')
		];
		expect(arrangeItems(items, { order: 'oldest', range: null }).map((i) => i.capturedAt)).toEqual([
			'2024-06-30T10:00:00Z',
			'2024-07-02T10:00:00Z',
			'2024-07-03T10:00:00Z'
		]);
		expect(
			arrangeItems(items, { order: 'newest', range: dateRange('2024-07-01', '2024-07-31') }).length
		).toBe(2);
	});
});
