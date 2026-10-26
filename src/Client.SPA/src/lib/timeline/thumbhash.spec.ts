import { describe, expect, it } from 'vitest';
import { thumbHashUrl } from './thumbhash.js';

describe('thumbHashUrl', () => {
	it('turns a ThumbHash into an image URL, and nothing into nothing', () => {
		expect(thumbHashUrl('ofcFJYRA7JqD6iiwmzaaOQl4iMB/')).toMatch(/^data:image\/png;base64,/);
		expect(thumbHashUrl(null)).toBeNull();
		expect(thumbHashUrl('')).toBeNull();
	});

	it('gives the same URL for the same hash', () => {
		const hash = 'HxiCFAQI2SB0OuWUewQcZrgPa4jMPbTGBA==';
		expect(thumbHashUrl(hash)).toBe(thumbHashUrl(hash));
	});
});
