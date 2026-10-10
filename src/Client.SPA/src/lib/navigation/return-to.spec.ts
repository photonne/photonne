import { describe, expect, it } from 'vitest';
import { safeReturnTo } from './return-to.js';

describe('safeReturnTo', () => {
	it.each(['/', '/albums', '/albums/1?view=grid#top'])('keeps the app path %s', (path) => {
		expect(safeReturnTo(path)).toBe(path);
	});

	it.each([
		null,
		undefined,
		'',
		'albums',
		'//evil.example',
		'/\\evil.example',
		'https://evil.example'
	])('falls back to the home page for %s', (value) => {
		expect(safeReturnTo(value)).toBe('/');
	});
});
