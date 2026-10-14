import { describe, expect, it } from 'vitest';
import { deckScales } from './explore-deck.js';

describe('deckScales', () => {
	it('puts the nearest card in front, full size', () => {
		const cards = deckScales([1.2, 0.1, 0.9]);
		expect(cards.map((c) => c.centred)).toEqual([false, true, false]);
		expect(cards[1].scale).toBeCloseTo(0.986, 2);
		expect(cards[1].depth).toBeGreaterThan(cards[2].depth);
		expect(cards[2].depth).toBeGreaterThan(cards[0].depth);
	});

	it('shrinks with distance down to a floor', () => {
		const [near, far, farther] = deckScales([0, 1.6, 5]);
		expect(near.scale).toBe(1);
		expect(far.scale).toBeCloseTo(0.78, 5);
		expect(farther.scale).toBeCloseTo(0.78, 5);
	});

	it('breaks a tie by the first card', () => {
		expect(deckScales([0.5, 0.5]).map((c) => c.centred)).toEqual([true, false]);
	});

	it('is empty for no cards', () => {
		expect(deckScales([])).toEqual([]);
	});
});
