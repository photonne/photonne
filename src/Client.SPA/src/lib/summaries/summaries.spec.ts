import { describe, expect, it } from 'vitest';
import type { VideoSummaryResponse } from '#lib/api/index.js';
import {
	DEFAULT_OPTIONS,
	findSummary,
	isActive,
	optionsOf,
	percent,
	pollWhileActive,
	POLL_MS,
	requestBody
} from './summaries.js';

function summary(overrides: Partial<VideoSummaryResponse>): VideoSummaryResponse {
	return {
		id: 's1',
		source: 'year',
		year: 2024,
		albumId: null,
		title: 'Resumen 2024',
		includePhotos: false,
		orientation: 'all',
		durationMinutes: 5,
		status: 'completed',
		progress: 1,
		error: null,
		assetId: 'a1',
		clipCount: 10,
		requestedAt: '2026-10-18T10:00:00Z',
		completedAt: '2026-10-18T10:05:00Z',
		...overrides
	};
}

describe('findSummary', () => {
	const year = summary({ id: 'y' });
	const album = summary({ id: 'a', source: 'album', year: null, albumId: 'album-1' });

	it('matches a year by its year and an album by its id', () => {
		expect(findSummary([album, year], { kind: 'year', year: 2024 })?.id).toBe('y');
		expect(findSummary([year, album], { kind: 'album', albumId: 'album-1' })?.id).toBe('a');
	});

	it('finds nothing for a source never asked for', () => {
		expect(findSummary([year, album], { kind: 'year', year: 2023 })).toBeUndefined();
		expect(findSummary([year], { kind: 'album', albumId: 'album-2' })).toBeUndefined();
		expect(findSummary(undefined, { kind: 'year', year: 2024 })).toBeUndefined();
	});
});

describe('polling', () => {
	it('polls while something is queued or rendering, and stops after', () => {
		expect(pollWhileActive([summary({}), summary({ status: 'pending' })])).toBe(POLL_MS);
		expect(pollWhileActive([summary({ status: 'running' })])).toBe(POLL_MS);
		expect(pollWhileActive([summary({}), summary({ status: 'failed' })])).toBe(false);
		expect(pollWhileActive(undefined)).toBe(false);
	});

	it('counts only pending and running as active', () => {
		expect(isActive(summary({ status: 'pending' }))).toBe(true);
		expect(isActive(summary({ status: 'completed' }))).toBe(false);
		expect(isActive(undefined)).toBe(false);
	});
});

describe('requestBody', () => {
	it('sends the year or the album, never both, with the chosen options', () => {
		expect(requestBody({ kind: 'year', year: 2024 }, DEFAULT_OPTIONS)).toEqual({
			source: 'year',
			year: 2024,
			albumId: null,
			includePhotos: false,
			orientation: 'all',
			durationMinutes: 5
		});
		expect(
			requestBody(
				{ kind: 'album', albumId: 'x' },
				{ includePhotos: true, orientation: 'portrait', durationMinutes: 10 }
			)
		).toEqual({
			source: 'album',
			year: null,
			albumId: 'x',
			includePhotos: true,
			orientation: 'portrait',
			durationMinutes: 10
		});
	});
});

describe('optionsOf', () => {
	it('starts again from what the earlier request asked for', () => {
		expect(
			optionsOf(summary({ includePhotos: true, orientation: 'landscape', durationMinutes: 3 }))
		).toEqual({ includePhotos: true, orientation: 'landscape', durationMinutes: 3 });
	});

	it('falls back to the defaults for values it does not know', () => {
		expect(optionsOf(summary({ orientation: 'diagonal', durationMinutes: 42 }))).toEqual(
			DEFAULT_OPTIONS
		);
	});
});

describe('percent', () => {
	it('rounds and clamps', () => {
		expect(percent(summary({ progress: 0.456 }))).toBe(46);
		expect(percent(summary({ progress: 1.2 }))).toBe(100);
		expect(percent(summary({ progress: -1 }))).toBe(0);
	});
});
