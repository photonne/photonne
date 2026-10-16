import { describe, expect, it } from 'vitest';
import { cardMinWidth } from './card-size.svelte.js';

describe('cardMinWidth', () => {
	it('keeps the tiles the albums always had at the default size', () => {
		expect(cardMinWidth(50)).toBe(180);
	});

	it('grows with the size, within its ends', () => {
		expect(cardMinWidth(0)).toBe(120);
		expect(cardMinWidth(100)).toBe(320);
		expect(cardMinWidth(25)).toBeGreaterThan(120);
		expect(cardMinWidth(25)).toBeLessThan(180);
		expect(cardMinWidth(-5)).toBe(120);
	});
});
