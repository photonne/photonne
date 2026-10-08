import { describe, expect, it } from 'vitest';
import { toHex } from './checksum.js';
import { extensionOf, isMediaFile } from './files.js';
import { appendDates, failureOf, isDuplicateMessage } from './transport.js';

describe('failureOf', () => {
	it.each([
		[0, null, 'network'],
		[413, { error: 'x', code: 'upload_too_large' }, 'tooLarge'],
		[409, { error: 'x', code: 'storage_quota_exceeded' }, 'quota'],
		[401, { error: 'x', code: 'invalid_password' }, 'password'],
		[403, null, 'forbidden'],
		[410, { error: 'x', code: 'share_link_expired' }, 'gone'],
		[404, null, 'gone'],
		[500, null, 'server']
	])('status %i → %s', (status, body, failure) => {
		expect(failureOf({ status, body })).toBe(failure);
	});
});

describe('appendDates', () => {
	it('sends the modification time as epoch millis', () => {
		const form = new FormData();
		appendDates(form, new File([], 'a.jpg', { lastModified: 1_700_000_000_123 }));
		expect(form.get('fileCreatedAt')).toBe('1700000000123');
		expect(form.get('fileModifiedAt')).toBe('1700000000123');
	});
});

describe('isDuplicateMessage', () => {
	it('recognises the server answer for a file it already had', () => {
		expect(isDuplicateMessage({ message: 'Asset already exists', assetId: 'x' })).toBe(true);
		expect(isDuplicateMessage({ message: 'Asset uploaded', assetId: 'x' })).toBe(false);
		expect(isDuplicateMessage(null)).toBe(false);
	});
});

describe('files', () => {
	it('reads extensions case-insensitively', () => {
		expect(extensionOf('IMG_1.HEIC')).toBe('heic');
		expect(extensionOf('README')).toBe('');
		expect(extensionOf('.hidden')).toBe('');
	});

	it.each([
		['IMG_1.JPG', true],
		['clip.mov', true],
		['raw.CR3', true],
		['notes.txt', false],
		['._IMG_1.jpg', false],
		['Thumbs.db', false]
	])('%s is media: %s', (name, expected) => {
		expect(isMediaFile({ name })).toBe(expected);
	});

	it('hex-encodes digests', () => {
		expect(toHex(new Uint8Array([0, 15, 255]).buffer)).toBe('000fff');
	});
});
