import { describe, expect, it } from 'vitest';
import { chunkRows, columnsOf, targetIndex } from './grid-keys';

describe('chunkRows', () => {
	it('wraps a flat order into rows of the given width', () => {
		expect(chunkRows([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
		expect(chunkRows([1, 2], 0)).toEqual([[1], [2]]);
		expect(chunkRows([], 3)).toEqual([]);
	});
});

describe('columnsOf', () => {
	it('counts the cells on the first row (sub-pixel differences tolerated)', () => {
		expect(columnsOf([10, 10.4, 10, 200, 200])).toBe(3);
		expect(columnsOf([10, 200])).toBe(1);
		expect(columnsOf([])).toBe(1);
	});
});

describe('targetIndex', () => {
	// 0 1 2
	// 3 4 5
	// 6 7
	it('moves by arrows over the grid, clamping at the short last row', () => {
		expect(targetIndex('ArrowRight', 2, 8, 3)).toBe(3);
		expect(targetIndex('ArrowLeft', 3, 8, 3)).toBe(2);
		expect(targetIndex('ArrowDown', 1, 8, 3)).toBe(4);
		expect(targetIndex('ArrowDown', 5, 8, 3)).toBe(7);
		expect(targetIndex('ArrowUp', 7, 8, 3)).toBe(4);
		expect(targetIndex('ArrowUp', 1, 8, 3)).toBe(1);
	});

	it('jumps to the ends and ignores other keys', () => {
		expect(targetIndex('Home', 5, 8, 3)).toBe(0);
		expect(targetIndex('End', 0, 8, 3)).toBe(7);
		expect(targetIndex('a', 0, 8, 3)).toBeNull();
		expect(targetIndex('ArrowDown', 0, 0, 3)).toBeNull();
	});
});
