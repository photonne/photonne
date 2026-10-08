import { describe, expect, it } from 'vitest';
import type { SharedTrashItemResponse } from '#lib/api/index.js';
import { deleters, sharedTrashAsset } from './shared-trash.js';

function item(overrides: Partial<SharedTrashItemResponse>): SharedTrashItemResponse {
	return {
		id: 'a',
		fileName: 'IMG.jpg',
		fullPath: '/assets/shared/IMG.jpg',
		fileSize: 1000,
		type: 'Image',
		extension: '.jpg',
		hasThumbnails: true,
		width: 4000,
		height: 3000,
		deletedAt: '2026-10-01T10:00:00Z',
		deletedByUsername: 'luis',
		deletedFromPath: '/assets/shared',
		deletedFromFolderName: 'shared',
		...overrides
	};
}

describe('shared trash', () => {
	it('turns an item into a grid cell dated by its deletion', () => {
		expect(sharedTrashAsset(item({ type: 'Video' }))).toMatchObject({
			id: 'a',
			aspect: 4000 / 3000,
			capturedAt: '2026-10-01T10:00:00Z',
			isVideo: true
		});
		expect(sharedTrashAsset(item({ width: null, deletedAt: null }))).toMatchObject({
			aspect: null,
			capturedAt: '1970-01-01T00:00:00Z'
		});
	});

	it('counts who deleted what, most first', () => {
		const items = [
			item({ deletedByUsername: 'marta' }),
			item({ deletedByUsername: 'luis' }),
			item({ deletedByUsername: 'marta' }),
			item({ deletedByUsername: null })
		];

		expect(deleters(items)).toEqual([
			{ name: 'marta', count: 2 },
			{ name: '', count: 1 },
			{ name: 'luis', count: 1 }
		]);
	});
});
