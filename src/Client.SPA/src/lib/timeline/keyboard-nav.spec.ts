import { describe, expect, it } from 'vitest';
import { moveFocus } from './keyboard-nav.js';

const rows = [
	['a', 'b', 'c'],
	['d', 'e'],
	['f', 'g', 'h', 'i']
];

describe('moveFocus', () => {
	it('starts at the first cell', () => {
		expect(moveFocus(rows, null, 'right')).toBe('a');
	});

	it('wraps left and right across rows', () => {
		expect(moveFocus(rows, 'c', 'right')).toBe('d');
		expect(moveFocus(rows, 'd', 'left')).toBe('c');
	});

	it('keeps the column moving up and down, clamped to shorter rows', () => {
		expect(moveFocus(rows, 'c', 'down')).toBe('e');
		expect(moveFocus(rows, 'e', 'down')).toBe('g');
		expect(moveFocus(rows, 'i', 'up')).toBe('e');
	});

	it('stays put at the edges', () => {
		expect(moveFocus(rows, 'a', 'up')).toBe('a');
		expect(moveFocus(rows, 'i', 'right')).toBe('i');
	});
});
