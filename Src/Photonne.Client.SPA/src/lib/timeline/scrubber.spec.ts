import { describe, expect, it } from 'vitest';
import { spreadMarkers, yearMarkers } from './scrubber.js';

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
