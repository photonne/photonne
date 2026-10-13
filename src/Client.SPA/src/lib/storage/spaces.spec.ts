import { describe, expect, it } from 'vitest';
import { parseHidden, visibleBytes } from './spaces.js';

describe('visibleBytes', () => {
	const bytes = { personal: 10, shared: 200, library: 3000 };

	it('counts every space when nothing is hidden', () => {
		expect(visibleBytes(bytes, new Set())).toBe(3210);
	});

	it('takes the hidden spaces out but never the personal one', () => {
		expect(visibleBytes(bytes, new Set(['shared']))).toBe(3010);
		expect(visibleBytes(bytes, new Set(['shared', 'library']))).toBe(10);
	});
});

describe('parseHidden', () => {
	it('reads a stored list and drops unknown or personal names', () => {
		expect(parseHidden('shared, library')).toEqual(['shared', 'library']);
		expect(parseHidden('personal,trash,shared')).toEqual(['shared']);
		expect(parseHidden(null)).toEqual([]);
	});
});
