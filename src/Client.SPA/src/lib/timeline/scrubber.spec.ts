import { describe, expect, it } from 'vitest';
import { rowYearMarkers, spreadMarkers, yearMarkers } from './scrubber.js';

describe('yearMarkers', () => {
	it('marks where each year starts, as a fraction of the height', () => {
		const tops = new Map([
			['2026-09', 0],
			['2026-01', 200],
			['2025-12', 500],
			['2024-06', 900]
		]);

		expect(yearMarkers(tops, 1000)).toEqual([
			{ label: '2026', fraction: 0 },
			{ label: '2025', fraction: 0.5 },
			{ label: '2024', fraction: 0.9 }
		]);
	});

	it('is empty without content', () => {
		expect(yearMarkers(new Map(), 0)).toEqual([]);
	});
});

describe('rowYearMarkers', () => {
	it('marks the first row of each year, newest or oldest first', () => {
		const rows = [
			{ top: 0, year: '2026' },
			{ top: 100, year: '2026' },
			{ top: 200, year: '2025' },
			{ top: 700, year: '2023' }
		];

		expect(rowYearMarkers(rows, 1000)).toEqual([
			{ label: '2026', fraction: 0 },
			{ label: '2025', fraction: 0.2 },
			{ label: '2023', fraction: 0.7 }
		]);
		expect(rowYearMarkers([...rows].reverse(), 1000).map((m) => m.label)).toEqual([
			'2023',
			'2025',
			'2026'
		]);
	});

	it('gives nothing for rows whose years come and go', () => {
		const rows = [
			{ top: 0, year: '2026' },
			{ top: 100, year: '2024' },
			{ top: 200, year: '2026' }
		];

		expect(rowYearMarkers(rows, 1000)).toEqual([]);
		expect(rowYearMarkers(rows, 0)).toEqual([]);
	});
});

describe('spreadMarkers', () => {
	it('drops labels that would overlap', () => {
		const markers = [
			{ label: '2026', fraction: 0 },
			{ label: '2025', fraction: 0.01 },
			{ label: '2024', fraction: 0.5 }
		];

		expect(spreadMarkers(markers, 500, 20).map((m) => m.label)).toEqual(['2026', '2024']);
	});
});
