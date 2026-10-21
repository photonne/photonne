import { describe, expect, it } from 'vitest';
import {
	DEFAULT_GROUPING,
	DEFAULT_SIZE,
	isFreeKeyPress,
	MAX_SIZE,
	MAX_YEAR_SAMPLE,
	migrateZoomLevel,
	MIN_SIZE,
	parseGrouping,
	parseSize,
	rowHeightFor,
	sizeKeyDirection,
	stepSize,
	clampSize,
	sizeLevel,
	WheelZoom,
	yearRowHeightFor,
	yearSampleSize
} from './zoom.js';

describe('thumbnail size', () => {
	it('grows with the size at every width, so + always means bigger', () => {
		for (const width of [400, 900, 1600]) {
			let previous = 0;
			for (let size = MIN_SIZE; size <= MAX_SIZE; size++) {
				const height = rowHeightFor(size, width);
				expect(height).toBeGreaterThanOrEqual(previous);
				previous = height;
			}
			expect(rowHeightFor(MAX_SIZE, width)).toBeGreaterThan(rowHeightFor(MIN_SIZE, width) * 3);
		}
	});

	it('keeps the medium grid the timeline always had, and the old levels', () => {
		expect(DEFAULT_SIZE).toBe(50);
		expect([500, 900, 1300].map((w) => rowHeightFor(50, w))).toEqual([120, 170, 210]);
		expect([500, 900, 1300].map((w) => rowHeightFor(25, w))).toEqual([84, 104, 124]);
		expect([500, 900, 1300].map((w) => rowHeightFor(100, w))).toEqual([240, 330, 400]);
	});

	it('goes below the old smallest level', () => {
		expect(rowHeightFor(0, 1300)).toBeLessThan(rowHeightFor(25, 1300));
	});

	it('clamps sizes out of range', () => {
		expect(rowHeightFor(-20, 900)).toBe(rowHeightFor(0, 900));
		expect(rowHeightFor(500, 900)).toBe(rowHeightFor(100, 900));
	});

	it('scales the year rows with the size, never too small to see', () => {
		expect(yearRowHeightFor(DEFAULT_SIZE, 1300)).toBe(64);
		expect(yearRowHeightFor(100, 1300)).toBeGreaterThan(64);
		expect(yearRowHeightFor(0, 400)).toBeGreaterThanOrEqual(32);
	});

	it('steps from level to level and stops at both ends', () => {
		expect(stepSize(50, 1)).toBe(75);
		expect(stepSize(50, -1)).toBe(25);
		expect(stepSize(100, 1)).toBe(MAX_SIZE);
		expect(stepSize(0, -1)).toBe(MIN_SIZE);
	});

	it('puts any size on the nearest of the five levels', () => {
		expect(clampSize(60)).toBe(50);
		expect(clampSize(63)).toBe(75);
		expect(clampSize(-20)).toBe(0);
		expect(sizeLevel(75)).toBe(3);
	});

	it('reads a saved size and ignores anything else', () => {
		expect(parseSize('30')).toBe(25);
		expect(parseSize('130')).toBe(MAX_SIZE);
		expect(parseSize('huge')).toBe(DEFAULT_SIZE);
		expect(parseSize('')).toBe(DEFAULT_SIZE);
		expect(parseSize(null)).toBe(DEFAULT_SIZE);
	});
});

describe('grouping', () => {
	it('reads a saved grouping and ignores anything else', () => {
		expect(parseGrouping('day')).toBe('day');
		expect(parseGrouping('week')).toBe(DEFAULT_GROUPING);
		expect(parseGrouping(null)).toBe('month');
	});

	it('carries the old zoom levels over to a size and a grouping', () => {
		expect(migrateZoomLevel('year')).toEqual({ size: DEFAULT_SIZE, grouping: 'year' });
		expect(migrateZoomLevel('small')).toEqual({ size: 25, grouping: 'month' });
		expect(migrateZoomLevel('medium')).toEqual({ size: 50, grouping: 'month' });
		expect(migrateZoomLevel('large')).toEqual({ size: 75, grouping: 'day' });
		expect(migrateZoomLevel('xlarge')).toEqual({ size: 100, grouping: 'day' });
		expect(migrateZoomLevel('huge')).toBeNull();
		expect(migrateZoomLevel(null)).toBeNull();
	});
});

describe('size keys', () => {
	// A stand-in for a KeyboardEvent (these tests run in Node): only what's read.
	const press = (key: string, init: Partial<KeyboardEvent> = {}, inField = false) =>
		({
			key,
			defaultPrevented: false,
			ctrlKey: false,
			metaKey: false,
			altKey: false,
			target: { closest: () => (inField ? {} : null) },
			...init
		}) as unknown as KeyboardEvent;

	it('reads + and − (and their unshifted keys)', () => {
		expect(sizeKeyDirection(press('+'))).toBe(1);
		expect(sizeKeyDirection(press('='))).toBe(1);
		expect(sizeKeyDirection(press('-'))).toBe(-1);
		expect(sizeKeyDirection(press('a'))).toBeNull();
	});

	it('leaves modified keys and typing alone', () => {
		expect(sizeKeyDirection(press('+', { ctrlKey: true }))).toBeNull();
		expect(isFreeKeyPress(press('-', {}, true))).toBe(false);
		expect(isFreeKeyPress(press('-'))).toBe(true);
		expect(isFreeKeyPress(press('-', { defaultPrevented: true }))).toBe(false);
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

	it('asks for more at smaller sizes', () => {
		expect(yearSampleSize(1000, 0)).toBeGreaterThan(yearSampleSize(1000, 100));
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
