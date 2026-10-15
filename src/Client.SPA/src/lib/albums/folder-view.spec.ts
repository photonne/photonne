import { describe, expect, it } from 'vitest';
import { parseListView, parseSections } from './folder-view.svelte.js';

describe('parseListView', () => {
	it('keeps the rows and falls back to the tiles', () => {
		expect(parseListView('list')).toBe('list');
		expect(parseListView('grid')).toBe('grid');
		expect(parseListView('masonry')).toBe('grid');
		expect(parseListView(null)).toBe('grid');
	});
});

describe('parseSections', () => {
	it('opens every section unless it was folded', () => {
		expect(parseSections(null)).toEqual({ subfolders: true, photos: true });
		expect(parseSections('{"photos":false}')).toEqual({ subfolders: true, photos: false });
		expect(parseSections('{"subfolders":false,"photos":"no"}')).toEqual({
			subfolders: false,
			photos: true
		});
		expect(parseSections('nope')).toEqual({ subfolders: true, photos: true });
	});
});
