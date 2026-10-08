import { describe, expect, it } from 'vitest';
import type { ShareLinkResponse } from '#lib/api/index.js';
import {
	absoluteShareUrl,
	createLinkRequest,
	daysFromNow,
	expiryToIso,
	formFromLink,
	linkStatus,
	localDate,
	updateLinkRequest,
	validateLinkForm
} from './share-link.js';

const link = (overrides: Partial<ShareLinkResponse> = {}): ShareLinkResponse => ({
	token: 't',
	albumId: 'a',
	createdAt: '2026-01-01T00:00:00Z',
	expiresAt: null,
	hasPassword: false,
	allowDownload: true,
	maxViews: null,
	viewCount: 0,
	allowUpload: false,
	uploadCount: 0,
	shareUrl: '/share/t',
	...overrides
});

describe('formFromLink', () => {
	it('starts a new link open, downloadable and without a password', () => {
		expect(formFromLink()).toEqual({
			expires: '',
			passwordMode: 'none',
			password: '',
			maxViews: '',
			allowDownload: true,
			allowUpload: false
		});
	});

	it('keeps an existing password unless told otherwise', () => {
		const form = formFromLink(link({ hasPassword: true, maxViews: 5 }));
		expect(form.passwordMode).toBe('keep');
		expect(form.maxViews).toBe('5');
	});
});

describe('requests', () => {
	it('creates with the chosen options', () => {
		const form = { ...formFromLink(), passwordMode: 'set' as const, password: 'x', maxViews: '3' };
		expect(createLinkRequest('a', form)).toEqual({
			albumId: 'a',
			expiresAt: null,
			password: 'x',
			maxViews: 3,
			allowDownload: true,
			allowUpload: false
		});
	});

	it('maps the password choice to the PATCH convention', () => {
		const base = formFromLink(link({ hasPassword: true }));
		expect(updateLinkRequest(base).password).toBeNull();
		expect(updateLinkRequest({ ...base, passwordMode: 'none' }).password).toBe('');
		expect(updateLinkRequest({ ...base, passwordMode: 'set', password: 'new' }).password).toBe(
			'new'
		);
	});

	it('ends the link at the last second of the chosen local day', () => {
		const end = new Date(expiryToIso('2026-12-31'));
		expect(localDate(end)).toBe('2026-12-31');
		expect([end.getHours(), end.getMinutes(), end.getSeconds()]).toEqual([23, 59, 59]);
	});
});

describe('validateLinkForm', () => {
	const now = new Date(2026, 9, 8, 12);
	it('accepts a sensible form', () => {
		expect(validateLinkForm({ ...formFromLink(), maxViews: '10' }, now)).toBeNull();
	});

	it('rejects bad view limits, past dates and empty passwords', () => {
		expect(validateLinkForm({ ...formFromLink(), maxViews: '0' }, now)).toBe('max_views');
		expect(validateLinkForm({ ...formFromLink(), maxViews: '2.5' }, now)).toBe('max_views');
		expect(validateLinkForm({ ...formFromLink(), expires: '2026-10-07' }, now)).toBe(
			'expires_past'
		);
		expect(validateLinkForm({ ...formFromLink(), passwordMode: 'set' }, now)).toBe(
			'password_empty'
		);
	});
});

describe('linkStatus', () => {
	const now = Date.parse('2026-10-08T12:00:00Z');
	it('tells expired and used-up links apart', () => {
		expect(linkStatus(link(), now)).toBe('active');
		expect(linkStatus(link({ expiresAt: '2026-10-01T00:00:00Z' }), now)).toBe('expired');
		expect(linkStatus(link({ maxViews: 3, viewCount: 3 }), now)).toBe('exhausted');
	});
});

describe('helpers', () => {
	it('adds days in local time', () => {
		expect(daysFromNow(7, new Date(2026, 9, 28))).toBe('2026-11-04');
	});

	it('completes a relative share URL with the origin', () => {
		expect(absoluteShareUrl('/share/t', 'https://fotos.example')).toBe(
			'https://fotos.example/share/t'
		);
		expect(absoluteShareUrl('https://x.example/share/t', 'https://y')).toBe(
			'https://x.example/share/t'
		);
	});
});
