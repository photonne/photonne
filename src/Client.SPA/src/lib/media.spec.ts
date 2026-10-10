import { describe, expect, it } from 'vitest';
import { thumbnailSizeFor, thumbnailUrl } from './media.js';

describe('thumbnailSizeFor', () => {
	it('picks the smallest size that stays sharp', () => {
		expect(thumbnailSizeFor(200, 1)).toBe('Small');
		expect(thumbnailSizeFor(200, 2)).toBe('Medium');
		expect(thumbnailSizeFor(900, 1)).toBe('Large');
		expect(thumbnailSizeFor(900, 3)).toBe('Large');
	});
});

describe('thumbnailUrl', () => {
	it('versions the URL when the generation time is known', () => {
		expect(thumbnailUrl('a1', 'Small', '2026-10-08T10:00:00Z')).toBe(
			`/api/assets/a1/thumbnail?size=Small&v=${Date.parse('2026-10-08T10:00:00Z')}`
		);
		expect(thumbnailUrl('a1', 'Medium', null)).toBe('/api/assets/a1/thumbnail?size=Medium');
	});
});
