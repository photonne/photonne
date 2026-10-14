import { describe, expect, it } from 'vitest';
import { parseGridLayout } from './grid-layout-preference.svelte.js';

describe('parseGridLayout', () => {
	it('keeps a known layout and falls back to the masonry', () => {
		expect(parseGridLayout('rows')).toBe('rows');
		expect(parseGridLayout('masonry')).toBe('masonry');
		expect(parseGridLayout('grid')).toBe('masonry');
		expect(parseGridLayout(null)).toBe('masonry');
	});
});
