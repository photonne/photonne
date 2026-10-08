import { describe, expect, it } from 'vitest';
import { goTo, PHOTO_MS, segmentFill, tick } from './story.js';

describe('story', () => {
	it('moves through a photo, then on to the next', () => {
		let position = goTo(0, 3);
		position = tick(position, 200, 3);
		expect(position.progress).toBeCloseTo(200 / PHOTO_MS);

		for (let i = 0; i < PHOTO_MS / 200; i++) position = tick(position, 200, 3);
		expect(position).toMatchObject({ index: 1, finished: false });
	});

	it('caps a long gap so no photo is skipped', () => {
		const position = tick(goTo(0, 3), 60_000, 3);
		expect(position.index).toBe(0);
		expect(position.progress).toBeCloseTo(250 / PHOTO_MS);
	});

	it('stops at the end of the last photo', () => {
		let position = goTo(2, 3);
		for (let i = 0; i < 100; i++) position = tick(position, 250, 3);
		expect(position).toEqual({ index: 2, progress: 1, finished: true });
		expect(tick(position, 250, 3)).toBe(position);
	});

	it('clamps jumps to the photos there are', () => {
		expect(goTo(-1, 3).index).toBe(0);
		expect(goTo(9, 3).index).toBe(2);
	});

	it('fills the segments behind, the current one partly', () => {
		const position = { index: 1, progress: 0.5, finished: false };
		expect([0, 1, 2].map((s) => segmentFill(s, position))).toEqual([1, 0.5, 0]);
	});
});
