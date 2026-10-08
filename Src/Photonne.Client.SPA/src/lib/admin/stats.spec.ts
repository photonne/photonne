import { describe, expect, it } from 'vitest';
import { coverageShare, growthSeries, niceMax } from './stats.js';

describe('stats', () => {
	it('fills the growth months, oldest first, across a year change', () => {
		const series = growthSeries(
			[
				{ year: 2026, month: 1, photos: 5, videos: 1 },
				{ year: 2025, month: 11, photos: 2, videos: 0 },
				{ year: 2020, month: 1, photos: 99, videos: 0 }
			],
			4,
			new Date('2026-02-15T12:00:00Z')
		);

		expect(series).toEqual([
			{ key: '2025-11', photos: 2, videos: 0 },
			{ key: '2025-12', photos: 0, videos: 0 },
			{ key: '2026-01', photos: 5, videos: 1 },
			{ key: '2026-02', photos: 0, videos: 0 }
		]);
	});

	it('rounds the axis up to 1, 2 or 5 times a power of ten', () => {
		expect(niceMax(0)).toBe(1);
		expect(niceMax(7)).toBe(10);
		expect(niceMax(13)).toBe(20);
		expect(niceMax(420)).toBe(500);
		expect(niceMax(1000)).toBe(1000);
	});

	it('never rounds an incomplete coverage up to 100 %', () => {
		expect(coverageShare({ indexed: 0, unindexed: 0 })).toBe(1);
		expect(coverageShare({ indexed: 9999, unindexed: 1 })).toBe(0.999);
		expect(coverageShare({ indexed: 1, unindexed: 1 })).toBe(0.5);
	});
});
