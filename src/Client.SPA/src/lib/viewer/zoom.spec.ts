import { describe, expect, it } from 'vitest';
import { centerOn, clampPan, fitSize, IDENTITY, MAX_SCALE, visibleRect, zoomAt } from './zoom.js';

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

describe('visibleRect', () => {
	const fitted = { width: 1000, height: 750 };

	it('is the whole image when not zoomed', () => {
		expect(visibleRect(IDENTITY, fitted, stage)).toEqual({ x: 0, y: 0, width: 1, height: 1 });
	});

	it('frames the centre half at 2× without a pan', () => {
		// 2000×1500 shown in 1000×800: half the width, 800/1500 of the height.
		const rect = visibleRect({ scale: 2, x: 0, y: 0 }, fitted, stage);
		expect(rect.x).toBeCloseTo(0.25, 6);
		expect(rect.width).toBeCloseTo(0.5, 6);
		expect(rect.height).toBeCloseTo(800 / 1500, 6);
		expect(rect.y).toBeCloseTo((1 - 800 / 1500) / 2, 6);
	});

	it('moves to the left edge when panned all the way right', () => {
		const rect = visibleRect({ scale: 2, x: 500, y: 0 }, fitted, stage);
		expect(rect.x).toBeCloseTo(0, 6);
		expect(rect.width).toBeCloseTo(0.5, 6);
	});
});

describe('centerOn', () => {
	const fitted = { width: 1000, height: 750 };

	it('brings a point of the image to the centre, and back to the frame it names', () => {
		const state = centerOn({ scale: 4, x: 0, y: 0 }, { x: 0.3, y: 0.6 }, fitted, stage);
		const rect = visibleRect(state, fitted, stage);
		expect(rect.x + rect.width / 2).toBeCloseTo(0.3, 6);
		expect(rect.y + rect.height / 2).toBeCloseTo(0.6, 6);
	});

	it('stops at the edges', () => {
		const state = centerOn({ scale: 2, x: 0, y: 0 }, { x: 0, y: 1 }, fitted, stage);
		expect(state).toEqual(clampPan({ scale: 2, x: 5000, y: -5000 }, fitted, stage));
	});
});
