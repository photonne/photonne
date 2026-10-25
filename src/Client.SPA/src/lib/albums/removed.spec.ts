import { describe, expect, it } from 'vitest';
import type { RemovedAlbumAssetResponse } from '#lib/api/index.js';
import { groupRemovals, removalNeedsConfirm } from './removed.js';

function removal(
	id: string,
	removedAt: string,
	user: string | null = 'u1'
): RemovedAlbumAssetResponse {
	return {
		asset: { id } as RemovedAlbumAssetResponse['asset'],
		removedAt,
		removedByUserId: user,
		removedByUsername: user && `name-${user}`
	};
}

describe('groupRemovals', () => {
	it('groups the photos one removal took out together', () => {
		const groups = groupRemovals([
			removal('a', '2026-10-25T10:00:00Z'),
			removal('b', '2026-10-25T10:00:00Z'),
			removal('c', '2026-10-25T09:00:00Z'),
			removal('d', '2026-10-25T09:00:00Z', 'u2'),
			removal('e', '2026-10-25T09:00:00Z', null)
		]);

		expect(groups.map((g) => g.items.map((i) => i.asset.id))).toEqual([
			['a', 'b'],
			['c'],
			['d'],
			['e']
		]);
		expect(groups[0]).toMatchObject({ removedAt: '2026-10-25T10:00:00Z', removedBy: 'name-u1' });
		expect(groups[3].removedBy).toBeNull();
	});

	it('has nothing to group when nothing was removed', () => {
		expect(groupRemovals([])).toEqual([]);
	});
});

describe('removalNeedsConfirm', () => {
	it('asks only for large removals', () => {
		expect(removalNeedsConfirm(1)).toBe(false);
		expect(removalNeedsConfirm(19)).toBe(false);
		expect(removalNeedsConfirm(20)).toBe(true);
	});
});
