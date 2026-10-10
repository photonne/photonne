import { describe, expect, it, vi } from 'vitest';
import type { ShareLinkResponse } from '#lib/api/index.js';
import { formFromLink } from '#lib/albums/share-link.js';
import {
	SHARE_NAME_MAX,
	shareAssetsAsLink,
	validateShareName,
	type ShareAssetsApi
} from './share-assets.js';

const link: ShareLinkResponse = {
	token: 'tok',
	albumId: 'new-album',
	createdAt: '2026-10-08T10:00:00Z',
	expiresAt: null,
	hasPassword: false,
	allowDownload: true,
	maxViews: null,
	viewCount: 0,
	allowUpload: false,
	uploadCount: 0,
	shareUrl: '/share/tok'
};

function fakeApi(overrides: Partial<ShareAssetsApi> = {}) {
	return {
		createAlbum: vi.fn(async () => ({ id: 'new-album' })),
		addAssets: vi.fn(async () => {}),
		createLink: vi.fn(async () => link),
		deleteAlbum: vi.fn(async () => {}),
		...overrides
	} satisfies ShareAssetsApi;
}

describe('shareAssetsAsLink', () => {
	it('creates the album, adds the photos and links it', async () => {
		const api = fakeApi();
		const form = { ...formFromLink(), maxViews: '5', allowDownload: false };

		const result = await shareAssetsAsLink(api, ['a', 'b'], '  Playa  ', form);

		expect(result).toEqual({ albumId: 'new-album', link });
		expect(api.createAlbum).toHaveBeenCalledWith('Playa');
		expect(api.addAssets).toHaveBeenCalledWith('new-album', ['a', 'b']);
		expect(api.createLink).toHaveBeenCalledWith(
			expect.objectContaining({ albumId: 'new-album', maxViews: 5, allowDownload: false })
		);
		expect(api.deleteAlbum).not.toHaveBeenCalled();
	});

	it('removes the new album when the link cannot be created', async () => {
		const api = fakeApi({ createLink: vi.fn(async () => Promise.reject(new Error('boom'))) });

		await expect(shareAssetsAsLink(api, ['a'], 'Playa', formFromLink())).rejects.toThrow('boom');
		expect(api.deleteAlbum).toHaveBeenCalledWith('new-album');
	});

	it('removes the new album when the photos cannot be added', async () => {
		const api = fakeApi({ addAssets: vi.fn(async () => Promise.reject(new Error('nope'))) });

		await expect(shareAssetsAsLink(api, ['a'], 'Playa', formFromLink())).rejects.toThrow('nope');
		expect(api.createLink).not.toHaveBeenCalled();
		expect(api.deleteAlbum).toHaveBeenCalledWith('new-album');
	});

	it('reports the original failure even if the clean-up fails too', async () => {
		const api = fakeApi({
			createLink: vi.fn(async () => Promise.reject(new Error('boom'))),
			deleteAlbum: vi.fn(async () => Promise.reject(new Error('gone')))
		});

		await expect(shareAssetsAsLink(api, ['a'], 'Playa', formFromLink())).rejects.toThrow('boom');
	});
});

describe('validateShareName', () => {
	it('needs a name', () => {
		expect(validateShareName('')).toBe('empty');
		expect(validateShareName('   ')).toBe('empty');
		expect(validateShareName('Playa')).toBeNull();
	});

	it('fits the album name limit', () => {
		expect(validateShareName('a'.repeat(SHARE_NAME_MAX))).toBeNull();
		expect(validateShareName('a'.repeat(SHARE_NAME_MAX + 1))).toBe('too_long');
	});
});
