import { describe, expect, it } from 'vitest';
import type { SentShareLinkDto } from '#lib/api/index.js';
import {
	filterLinks,
	linkBadges,
	linkCover,
	linkSubject,
	linkTotals,
	subjectPath
} from './links-model.js';

const NOW = Date.parse('2026-10-08T12:00:00Z');

function link(overrides: Partial<SentShareLinkDto> = {}): SentShareLinkDto {
	return {
		token: 'tok',
		createdAt: '2026-10-01T10:00:00Z',
		expiresAt: null,
		hasPassword: false,
		allowDownload: true,
		maxViews: null,
		viewCount: 0,
		allowUpload: false,
		uploadCount: 0,
		assetId: null,
		assetFileName: null,
		assetType: null,
		assetThumbnailUrl: null,
		albumId: 'album-1',
		albumName: 'Vacaciones',
		albumCoverUrl: '/api/assets/a1/thumbnail?size=Medium',
		shareUrl: 'https://photos.example/share/tok',
		...overrides
	};
}

const assetLink = () =>
	link({
		albumId: null,
		albumName: null,
		albumCoverUrl: null,
		assetId: 'asset-9',
		assetFileName: 'IMG_0009.HEIC',
		assetThumbnailUrl: '/api/assets/asset-9/thumbnail?size=Medium'
	});

describe('linkSubject', () => {
	it('prefers the album', () => {
		expect(linkSubject(link())).toEqual({ kind: 'album', name: 'Vacaciones', albumId: 'album-1' });
	});

	it('falls back to the single photo', () => {
		expect(linkSubject(assetLink())).toEqual({
			kind: 'asset',
			name: 'IMG_0009.HEIC',
			assetId: 'asset-9'
		});
	});

	it('knows when neither is left', () => {
		expect(linkSubject(link({ albumId: null, albumName: null }))).toEqual({ kind: 'unknown' });
	});
});

describe('subjectPath', () => {
	it('opens the album, or the photo in the viewer', () => {
		expect(subjectPath(linkSubject(link()))).toBe('/albums/album-1');
		expect(subjectPath(linkSubject(assetLink()))).toBe('/?asset=asset-9');
		expect(subjectPath({ kind: 'unknown' })).toBeNull();
	});
});

describe('linkCover', () => {
	it('uses the album cover, else the photo', () => {
		expect(linkCover(link())).toBe('/api/assets/a1/thumbnail?size=Medium');
		expect(linkCover(assetLink())).toBe('/api/assets/asset-9/thumbnail?size=Medium');
		expect(linkCover(link({ albumCoverUrl: null }))).toBeNull();
	});
});

describe('linkBadges', () => {
	it('starts with the state', () => {
		expect(linkBadges(link(), NOW)).toEqual(['active']);
		expect(linkBadges(link({ expiresAt: '2026-10-01T00:00:00Z' }), NOW)).toEqual(['expired']);
		expect(linkBadges(link({ maxViews: 3, viewCount: 3 }), NOW)).toEqual(['exhausted']);
	});

	it('adds password, downloads and uploads', () => {
		expect(
			linkBadges(link({ hasPassword: true, allowDownload: false, allowUpload: true }), NOW)
		).toEqual(['active', 'password', 'no_download', 'upload']);
	});
});

describe('filterLinks', () => {
	const links = [link(), link({ token: 't2', albumName: 'Cumpleaños' }), assetLink()];

	it('keeps everything without a query', () => {
		expect(filterLinks(links, '  ')).toHaveLength(3);
	});

	it('matches names ignoring case and accents', () => {
		expect(filterLinks(links, 'cumpleanos').map((l) => l.token)).toEqual(['t2']);
		expect(filterLinks(links, 'heic').map((l) => l.assetId)).toEqual(['asset-9']);
	});
});

describe('linkTotals', () => {
	it('counts open links and all views', () => {
		const links = [
			link({ viewCount: 4 }),
			link({ token: 't2', maxViews: 2, viewCount: 2 }),
			link({ token: 't3', viewCount: 1 })
		];
		expect(linkTotals(links, NOW)).toEqual({ active: 2, views: 7 });
	});
});
