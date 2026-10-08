import { describe, expect, it } from 'vitest';
import { monthsOf, yearsWithPhotos } from './jump.js';

const months = [
	{ key: '2026-09', count: 23 },
	{ key: '2026-06', count: 60 },
	{ key: '2025-12', count: 0 },
	{ key: '2023-03', count: 120 }
];

describe('yearsWithPhotos', () => {
	it('lists the years with photos, newest first', () => {
		expect(yearsWithPhotos(months)).toEqual([2026, 2023]);
	});
});

describe('monthsOf', () => {
	it('gives the twelve months of a year with their counts', () => {
		const grid = monthsOf(months, 2026);
		expect(grid).toHaveLength(12);
		expect(grid[0]).toEqual({ key: '2026-01', month: 1, count: 0 });
		expect(grid[5]).toEqual({ key: '2026-06', month: 6, count: 60 });
		expect(grid[8].count).toBe(23);
	});
});
