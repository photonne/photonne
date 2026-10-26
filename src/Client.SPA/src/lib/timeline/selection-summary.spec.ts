import { describe, expect, it } from 'vitest';
import { selectionSummary } from './selection-summary.js';
import type { GridAsset } from './types.js';

const asset = (capturedAt: string, fileSize?: number): GridAsset => ({
	id: capturedAt,
	aspect: 1,
	fileName: 'a.jpg',
	capturedAt,
	isVideo: false,
	isLivePhoto: false,
	isFavorite: false,
	dominantColor: null,
	thumbnailVersion: null,
	fileSize
});

describe('selectionSummary', () => {
	it('spans the earliest to the latest capture and adds up the sizes', () => {
		const summary = selectionSummary([
			asset('2024-04-03T10:00:00Z', 1024 * 1024),
			asset('2024-03-12T10:00:00Z', 512 * 1024),
			asset('2024-03-20T10:00:00Z', 512 * 1024)
		]);
		expect(summary).toMatch(/12.*mar.*3.*abr.*2024 · 2 MB$/);
	});

	it('says a single day once and leaves out an unknown size', () => {
		expect(selectionSummary([asset('2024-03-12T08:00:00Z'), asset('2024-03-12T20:00:00Z')])).toBe(
			'12 mar 2024'
		);
	});

	it('is null for nothing selected', () => {
		expect(selectionSummary([])).toBeNull();
	});
});
