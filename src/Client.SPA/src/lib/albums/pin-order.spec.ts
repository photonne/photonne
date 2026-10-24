import { describe, expect, it } from 'vitest';
import { movePin } from './pin-order.js';

describe('movePin', () => {
	const list = ['a', 'b', 'c', 'd'];

	it('drops an item before or after another, up or down the list', () => {
		expect(movePin(list, 3, 1)).toEqual(['a', 'd', 'b', 'c']);
		expect(movePin(list, 3, 1, true)).toEqual(['a', 'b', 'd', 'c']);
		expect(movePin(list, 0, 2)).toEqual(['b', 'a', 'c', 'd']);
		expect(movePin(list, 0, 2, true)).toEqual(['b', 'c', 'a', 'd']);
		expect(movePin(list, 0, 3, true)).toEqual(['b', 'c', 'd', 'a']);
	});

	it('moves one step with the keyboard, and stops at the ends', () => {
		expect(movePin(list, 2, 1)).toEqual(['a', 'c', 'b', 'd']);
		expect(movePin(list, 1, 2, true)).toEqual(['a', 'c', 'b', 'd']);
		expect(movePin(list, 0, -1)).toEqual(list);
		expect(movePin(list, 3, 4, true)).toEqual(list);
	});

	it('leaves the list as it is when dropped on itself', () => {
		expect(movePin(list, 1, 1)).toEqual(list);
		expect(movePin(list, 1, 1, true)).toEqual(list);
	});
});
