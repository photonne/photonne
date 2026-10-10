import { describe, expect, it } from 'vitest';
import {
	assetDownloadUrl,
	formatScope,
	needsFormatChoice,
	originalExtension
} from './download-format.js';

describe('needsFormatChoice', () => {
	it('asks only when something can be converted', () => {
		expect(needsFormatChoice({ total: 3, convertibleCount: 1, extensions: ['heic'] })).toBe(true);
		expect(needsFormatChoice({ total: 3, convertibleCount: 0, extensions: [] })).toBe(false);
	});

	it('does not ask when the server could not answer', () => {
		expect(needsFormatChoice(null)).toBe(false);
		expect(needsFormatChoice(undefined)).toBe(false);
	});
});

describe('originalExtension', () => {
	it('names the extension when there is only one', () => {
		expect(originalExtension(['dng'])).toBe('DNG');
	});

	it('stays generic for mixed or no extensions', () => {
		expect(originalExtension(['dng', 'heic'])).toBeNull();
		expect(originalExtension([])).toBeNull();
	});
});

describe('formatScope', () => {
	it('says nothing for a single photo', () => {
		expect(formatScope(1, 1)).toEqual({ kind: 'one' });
	});

	it('tells all from some', () => {
		expect(formatScope(4, 4)).toEqual({ kind: 'all', total: 4 });
		expect(formatScope(4, 1)).toEqual({ kind: 'some', count: 1, total: 4 });
	});
});

describe('assetDownloadUrl', () => {
	it('asks for the original by default', () => {
		expect(assetDownloadUrl('a1')).toBe('/api/assets/a1/content?download=true');
	});

	it('adds the chosen format', () => {
		expect(assetDownloadUrl('a1', 'jpeg')).toBe('/api/assets/a1/content?download=true&format=jpeg');
		expect(assetDownloadUrl('a1', 'original')).toBe(
			'/api/assets/a1/content?download=true&format=original'
		);
	});
});
