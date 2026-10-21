import { describe, expect, it } from 'vitest';
import { gridMode, parseGridLayout, parseGridRatio } from './grid-layout-preference.svelte.js';

describe('parseGridLayout', () => {
	it('keeps a known layout and falls back to the masonry', () => {
		expect(parseGridLayout('rows')).toBe('rows');
		expect(parseGridLayout('masonry')).toBe('masonry');
		expect(parseGridLayout('grid')).toBe('masonry');
		expect(parseGridLayout(null)).toBe('masonry');
	});
});

describe('parseGridRatio', () => {
	it('keeps a known ratio and falls back to squares', () => {
		expect(parseGridRatio('original')).toBe('original');
		expect(parseGridRatio('square')).toBe('square');
		expect(parseGridRatio('16:9')).toBe('square');
		expect(parseGridRatio(null)).toBe('square');
	});
});

describe('gridMode', () => {
	it('draws a grid as squares or justified rows, and a masonry whatever the ratio', () => {
		expect(gridMode('rows', 'square')).toBe('square');
		expect(gridMode('rows', 'original')).toBe('rows');
		expect(gridMode('masonry', 'square')).toBe('masonry');
		expect(gridMode('masonry', 'original')).toBe('masonry');
	});
});
