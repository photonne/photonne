import { describe, expect, it } from 'vitest';
import type { TimelineResponse } from '#lib/api/index.js';
import {
	keepInAll,
	keepOnly,
	largest,
	markedBytes,
	oldest,
	recoverableBytes,
	toggleCopy,
	withoutAssets
} from './duplicates.js';

const asset = (id: string, fileCreatedAt: string, fileSize: number) =>
	({ id, fileCreatedAt, fileSize }) as TimelineResponse;

const groups = [
	{
		hash: 'h1',
		totalSize: 600,
		assets: [
			asset('a1', '2024-05-02T10:00:00Z', 200),
			asset('a2', '2024-05-01T10:00:00Z', 200),
			asset('a3', '2024-05-03T10:00:00Z', 200)
		]
	},
	{
		hash: 'h2',
		totalSize: 1500,
		assets: [asset('b1', '2023-01-01T00:00:00Z', 500), asset('b2', '2023-02-01T00:00:00Z', 1000)]
	}
];

describe('duplicates review', () => {
	it('picks the oldest and the largest copy', () => {
		expect(oldest(groups[0].assets).id).toBe('a2');
		expect(largest(groups[1].assets).id).toBe('b2');
		// Equal sizes: the oldest of them.
		expect(largest(groups[0].assets).id).toBe('a2');
	});

	it('keeps one copy per group and marks the rest', () => {
		expect([...keepInAll(groups, oldest)].sort()).toEqual(['a1', 'a3', 'b2']);
		expect([...keepInAll(groups, largest)].sort()).toEqual(['a1', 'a3', 'b1']);
		expect([...keepOnly(new Set(['a2', 'b1']), groups[0], 'a3')].sort()).toEqual([
			'a1',
			'a2',
			'b1'
		]);
	});

	it('never marks the last copy of a group', () => {
		let marked = toggleCopy(new Set(), groups[1], 'b1');
		expect([...marked]).toEqual(['b1']);
		marked = toggleCopy(marked, groups[1], 'b2');
		expect([...marked]).toEqual(['b1']);
		expect([...toggleCopy(marked, groups[1], 'b1')]).toEqual([]);
	});

	it('adds up sizes', () => {
		expect(markedBytes(groups, new Set(['a1', 'b2']))).toBe(1200);
		expect(recoverableBytes(groups)).toBe(400 + 500);
	});

	it('drops trashed copies and groups that are no longer duplicates', () => {
		const left = withoutAssets(groups, ['a1', 'b1']);
		expect(left.map((g) => g.hash)).toEqual(['h1']);
		expect(left[0].assets.map((a) => a.id)).toEqual(['a2', 'a3']);
		expect(left[0].totalSize).toBe(400);
	});
});
