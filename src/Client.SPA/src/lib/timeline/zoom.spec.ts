import { describe, expect, it } from 'vitest';
import {
	DEFAULT_ZOOM,
	groupingOf,
	MAX_YEAR_SAMPLE,
	parseZoom,
	rowHeightFor,
	stepZoom,
	WheelZoom,
	yearSampleSize,
	zoomLevels
} from './zoom.js';

describe('zoom levels', () => {
	it('grow monotonically at every width, so + always means bigger', () => {
		for (const width of [400, 900, 1600]) {
			const heights = zoomLevels.map((level) => rowHeightFor(level, width));
			expect([...heights].sort((a, b) => a - b)).toEqual(heights);
			expect(new Set(heights).size).toBe(heights.length);
		}
	});

	it('keeps the medium grid the timeline always had', () => {
		expect(DEFAULT_ZOOM).toBe('medium');
		expect([500, 900, 1300].map((w) => rowHeightFor('medium', w))).toEqual([120, 170, 210]);
	});

	it('groups by year, month, then day at the large sizes', () => {
		expect(zoomLevels.map(groupingOf)).toEqual(['year', 'month', 'month', 'day', 'day']);
	});

	it('steps one level and stops at the ends', () => {
		expect(stepZoom('medium', 1)).toBe('large');
		expect(stepZoom('medium', -1)).toBe('small');
		expect(stepZoom('xlarge', 1)).toBe('xlarge');
		expect(stepZoom('year', -1)).toBe('year');
	});

	it('reads a saved level and ignores anything else', () => {
		expect(parseZoom('year')).toBe('year');
		expect(parseZoom('huge')).toBe(DEFAULT_ZOOM);
		expect(parseZoom(null)).toBe(DEFAULT_ZOOM);
	});
});

describe('yearSampleSize', () => {
	it('asks for a few rows of samples, in steps of 12, within the server cap', () => {
		const narrow = yearSampleSize(320);
		const wide = yearSampleSize(1400);
		expect(narrow % 12).toBe(0);
		expect(wide % 12).toBe(0);
		expect(wide).toBeGreaterThan(narrow);
		expect(yearSampleSize(10_000)).toBe(MAX_YEAR_SAMPLE);
		expect(yearSampleSize(0)).toBe(12);
	});
});

describe('WheelZoom', () => {
	it('turns one mouse notch into one step, down being out', () => {
		const wheel = new WheelZoom();
		expect(wheel.push(120, 0)).toBe(-1);
		expect(wheel.push(-120, 1000)).toBe(1);
	});

	it('adds up small trackpad deltas and pauses after a step', () => {
		const wheel = new WheelZoom(60, 200);
		expect(wheel.push(-20, 0)).toBe(0);
		expect(wheel.push(-20, 10)).toBe(0);
		expect(wheel.push(-30, 20)).toBe(1);
		expect(wheel.push(-200, 100)).toBe(0);
		expect(wheel.push(-200, 400)).toBe(1);
	});

	it('starts over when the direction changes', () => {
		const wheel = new WheelZoom(60, 0);
		expect(wheel.push(40, 0)).toBe(0);
		expect(wheel.push(-40, 1)).toBe(0);
		expect(wheel.push(-30, 2)).toBe(1);
	});
});
