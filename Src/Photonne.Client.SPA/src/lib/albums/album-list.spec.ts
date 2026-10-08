import { describe, expect, it } from 'vitest';
import type { AlbumResponse } from '#lib/api/index.js';
import {
	arrangeAlbums,
	defaultListOptions,
	fold,
	parseListOptions,
	scopeCounts
} from './album-list.js';

const album = (overrides: Partial<AlbumResponse>): AlbumResponse => ({
	id: 'a',
	name: 'A',
	description: null,
	createdAt: '2026-01-01T00:00:00Z',
	updatedAt: '2026-01-01T00:00:00Z',
	assetCount: 0,
	coverThumbnailUrl: null,
	previewThumbnailUrls: [],
	isOwner: true,
	isShared: false,
	sharedWithCount: 0,
	canRead: true,
	canWrite: true,
	canDelete: true,
	canManagePermissions: true,
	hasActiveShareLink: false,
	isPinned: false,
	pinnedAt: null,
	kind: 'Manual',
	...overrides
});

const albums = [
	album({ id: '1', name: 'Verano', updatedAt: '2026-08-01T00:00:00Z', assetCount: 30 }),
	album({ id: '2', name: 'álbum 10', updatedAt: '2026-03-01T00:00:00Z', assetCount: 5 }),
	album({ id: '3', name: 'Álbum 9', isOwner: false, updatedAt: '2026-05-01T00:00:00Z' }),
	album({ id: '4', name: 'Perros', kind: 'Smart', isPinned: true }),
	album({ id: '5', name: 'Boda', description: 'La de Lucía y Pablo' })
];

const ids = (list: AlbumResponse[]) => list.map((a) => a.id);

describe('arrangeAlbums', () => {
	it('puts pinned albums apart and sorts the rest by last update, newest first', () => {
		const { pinned, others } = arrangeAlbums(albums, defaultListOptions);
		expect(ids(pinned)).toEqual(['4']);
		expect(ids(others)).toEqual(['1', '3', '2', '5']);
	});

	it('sorts by name naturally and ignoring accents and case', () => {
		const { others } = arrangeAlbums(albums, {
			...defaultListOptions,
			sort: 'name',
			descending: false
		});
		expect(ids(others)).toEqual(['3', '2', '5', '1']);
	});

	it('filters by scope, kind and text', () => {
		expect(ids(arrangeAlbums(albums, { ...defaultListOptions, scope: 'shared' }).others)).toEqual([
			'3'
		]);
		expect(ids(arrangeAlbums(albums, { ...defaultListOptions, kind: 'smart' }).pinned)).toEqual([
			'4'
		]);
		expect(ids(arrangeAlbums(albums, { ...defaultListOptions, query: 'lucia' }).others)).toEqual([
			'5'
		]);
	});

	it('sorts by number of photos', () => {
		const { others } = arrangeAlbums(albums, { ...defaultListOptions, sort: 'count' });
		expect(ids(others).slice(0, 2)).toEqual(['1', '2']);
	});
});

describe('scopeCounts', () => {
	it('counts own and shared albums', () => {
		expect(scopeCounts(albums)).toEqual({ all: 5, mine: 4, shared: 1 });
	});
});

describe('fold', () => {
	it('drops case and accents', () => {
		expect(fold('Álbum Niño')).toBe('album nino');
	});
});

describe('parseListOptions', () => {
	it('keeps valid saved values and drops the rest', () => {
		expect(parseListOptions('{"sort":"name","scope":"bogus","descending":false}')).toEqual({
			...defaultListOptions,
			sort: 'name',
			descending: false
		});
	});

	it('falls back to the defaults on garbage', () => {
		expect(parseListOptions('not json')).toEqual(defaultListOptions);
		expect(parseListOptions(null)).toEqual(defaultListOptions);
	});
});
