import { describe, expect, it } from 'vitest';
import type { PendingAssetDto, PendingEnrichmentResponse } from '#lib/api/index.js';
import { pendingItems, pendingSummary, retryable } from './analysis.js';

const item = (assetId: string, overrides: Partial<PendingAssetDto> = {}): PendingAssetDto => ({
	assetId,
	fileName: `${assetId}.jpg`,
	fileCreatedAt: '2026-09-01T00:00:00Z',
	pending: 1,
	processing: 0,
	failed: 0,
	failedTaskTypes: [],
	...overrides
});

const page = (
	items: PendingAssetDto[],
	counts: Partial<PendingEnrichmentResponse> = {}
): PendingEnrichmentResponse => ({
	items,
	nextCursor: null,
	totalAssets: 3,
	inFlightAssets: 2,
	failedAssets: 1,
	...counts
});

describe('pendingItems', () => {
	it('lists each photo once across pages, in order', () => {
		const items = pendingItems([page([item('a'), item('b')]), page([item('b'), item('c')])]);
		expect(items.map((i) => i.assetId)).toEqual(['a', 'b', 'c']);
	});
});

describe('retryable', () => {
	it('keeps the photos with a failed task', () => {
		const items = [
			item('a'),
			item('b', { failed: 2, failedTaskTypes: ['Exif', 'Thumbnails'] }),
			item('c', { failedTaskTypes: ['FaceRecognition'] })
		];
		expect(retryable(items).map((i) => i.assetId)).toEqual(['b', 'c']);
	});
});

describe('pendingSummary', () => {
	it('reads the counts of the first page, and zero without one', () => {
		expect(pendingSummary(page([]))).toEqual({ total: 3, inFlight: 2, failed: 1 });
		expect(pendingSummary(undefined)).toEqual({ total: 0, inFlight: 0, failed: 0 });
	});
});
