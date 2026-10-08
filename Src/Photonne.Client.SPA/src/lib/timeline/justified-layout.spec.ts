import { describe, expect, it } from 'vitest';
import { computeRows, estimateHeight, FALLBACK_ASPECT, rowsHeight } from './justified-layout.js';

const options = { containerWidth: 1000, targetRowHeight: 200, spacing: 4 };

describe('computeRows', () => {
	it('fills each closed row to the exact container width', () => {
		const aspects = [1.5, 1, 0.75, 1.5, 1.5, 1, 2, 1];

		const rows = computeRows(aspects, options);

		for (const row of rows.slice(0, -1)) {
			const widths = aspects.slice(row.start, row.start + row.count).map((a) => a * row.height);
			const total = widths.reduce((a, b) => a + b, 0) + options.spacing * (row.count - 1);
			expect(total).toBeCloseTo(options.containerWidth, 6);
		}
	});

	it('keeps the last partial row at the target height', () => {
		const rows = computeRows([1, 1, 1, 1, 1, 1, 1], options);

		expect(rows.at(-1)!.height).toBe(200);
		expect(rows.reduce((n, r) => n + r.count, 0)).toBe(7);
	});

	it('treats unknown and extreme shapes as sane aspects', () => {
		const rows = computeRows([NaN, 0, -1, 50], options);

		expect(rows.reduce((n, r) => n + r.count, 0)).toBe(4);
		expect(rows.every((r) => Number.isFinite(r.height) && r.height > 0)).toBe(true);
	});

	it('returns nothing for an empty month or a zero-width container', () => {
		expect(computeRows([], options)).toEqual([]);
		expect(computeRows([1], { ...options, containerWidth: 0 })).toEqual([]);
	});
});

describe('estimateHeight', () => {
	it('matches the exact layout when every cell has the assumed shape', () => {
		const aspects = Array<number>(37).fill(FALLBACK_ASPECT);
		const exact = rowsHeight(computeRows(aspects, options), options.spacing);

		// The estimate justifies the last row too, so it can only fall short by
		// less than one row.
		const estimate = estimateHeight(37, options);
		expect(Math.abs(exact - estimate)).toBeLessThan(options.targetRowHeight);
	});

	it('is zero for an empty month', () => {
		expect(estimateHeight(0, options)).toBe(0);
	});
});
