import { describe, expect, it } from 'vitest';
import { groupByMonth } from './list-store.svelte.js';
import type { GridAsset } from './types.js';

const asset = (id: string, capturedAt: string): GridAsset => ({
	id,
	aspect: 1,
	fileName: `${id}.jpg`,
	capturedAt,
	isVideo: false,
	isLivePhoto: false,
	isFavorite: false,
	dominantColor: null,
	thumbnailVersion: null
});

describe('groupByMonth', () => {
	it('groups consecutive items of a month', () => {
		const sections = groupByMonth([
			asset('a', '2026-09-20T10:00:00Z'),
			asset('b', '2026-09-01T10:00:00Z'),
			asset('c', '2026-07-03T10:00:00Z')
		]);

		expect(sections.map((s) => [s.key, s.count])).toEqual([
			['2026-09', 2],
			['2026-07', 1]
		]);
	});
});
