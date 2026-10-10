import { describe, expect, it } from 'vitest';
import { clampPan, fitSize, IDENTITY, MAX_SCALE, zoomAt } from './zoom.js';

const stage = { width: 1000, height: 800 };

describe('fitSize', () => {
	it('fits inside the stage keeping the shape, never upscaling', () => {
		expect(fitSize({ width: 4000, height: 3000 }, stage)).toEqual({ width: 1000, height: 750 });
		expect(fitSize({ width: 400, height: 300 }, stage)).toEqual({ width: 400, height: 300 });
	});
});

describe('zoomAt', () => {
	const fitted = { width: 1000, height: 750 };

	it('keeps the point under the pointer still', () => {
		const focus = { x: 200, y: -100 };

		const zoomed = zoomAt(IDENTITY, 2, focus, fitted, stage);

		// The image point under `focus` before (focus / 1) is under it after.
		expect((focus.x - zoomed.x) / zoomed.scale).toBeCloseTo(focus.x, 6);
		expect((focus.y - zoomed.y) / zoomed.scale).toBeCloseTo(focus.y, 6);
	});

	it('stays within the scale limits', () => {
		expect(zoomAt(IDENTITY, 100, { x: 0, y: 0 }, fitted, stage).scale).toBe(MAX_SCALE);
		expect(zoomAt(IDENTITY, 0.1, { x: 0, y: 0 }, fitted, stage)).toEqual(IDENTITY);
	});
});

describe('clampPan', () => {
	it('stops the image edge at the stage edge', () => {
		const clamped = clampPan({ scale: 2, x: 5000, y: -5000 }, { width: 1000, height: 750 }, stage);

		expect(clamped).toEqual({ scale: 2, x: 500, y: -350 });
	});
});
