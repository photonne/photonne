import { describe, expect, it } from 'vitest';
import type { SharedContentResponse } from '#lib/api/index.js';
import { aspectOf, outcomeOf, shareMediaUrl } from './share-api.js';

const content: SharedContentResponse = {
	token: 't',
	requiresPassword: false,
	wrongPassword: false,
	allowDownload: true,
	allowUpload: false,
	album: { name: 'Boda', description: null, assetCount: 0, coverThumbnailUrl: null },
	assets: [],
	expiresAt: null
};

describe('outcomeOf', () => {
	it('shows the content of an album link', () => {
		expect(outcomeOf(200, content, undefined)).toEqual({ kind: 'content', content });
	});

	it('asks for the password, saying when the last one was wrong', () => {
		const gate = { ...content, requiresPassword: true, album: null, assets: null };
		expect(outcomeOf(200, gate, undefined)).toEqual({ kind: 'password', wrong: false });
		expect(outcomeOf(200, { ...gate, wrongPassword: true }, undefined)).toEqual({
			kind: 'password',
			wrong: true
		});
	});

	it.each([
		[410, { error: 'x', code: 'share_link_expired' }, 'expired'],
		[410, { error: 'x', code: 'share_link_max_views' }, 'maxViews'],
		[404, { error: 'x', code: 'share_link_not_found' }, 'notFound'],
		[500, null, 'unreachable'],
		[undefined, null, 'unreachable']
	])('status %s → %s', (status, error, kind) => {
		expect(outcomeOf(status, undefined, error).kind).toBe(kind);
	});

	it('treats a link without album content as not found', () => {
		expect(outcomeOf(200, { ...content, album: null }, undefined).kind).toBe('notFound');
	});
});

describe('shareMediaUrl', () => {
	it('builds thumbnail and content URLs with the password', () => {
		expect(shareMediaUrl('to ken', 'a1', { thumbnail: 'Small' })).toBe(
			'/api/share/to%20ken/asset/a1/thumbnail?size=Small'
		);
		expect(shareMediaUrl('t', 'a1', { content: true, download: true }, 'p&w')).toBe(
			'/api/share/t/asset/a1/content?download=true&pw=p%26w'
		);
		expect(shareMediaUrl('t', 'a1', { content: true })).toBe('/api/share/t/asset/a1/content');
	});
});

describe('aspectOf', () => {
	it('is null without dimensions', () => {
		const asset = {
			id: 'a',
			fileName: 'a.jpg',
			type: 'Image',
			fileCreatedAt: '2026-01-01T00:00:00Z',
			fileSize: 1,
			width: null,
			height: 3,
			thumbnailUrl: '',
			contentUrl: ''
		};
		expect(aspectOf(asset)).toBeNull();
		expect(aspectOf({ ...asset, width: 6 })).toBe(2);
	});
});
