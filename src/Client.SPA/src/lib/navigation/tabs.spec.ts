import { describe, expect, it } from 'vitest';
import { tabFor } from './tabs.js';

const href = (path: string) => path;

describe('tabFor', () => {
	it('puts the timeline under Fotos', () => {
		expect(tabFor('/', href)).toBe('photos');
	});

	it('puts every collection, and what is inside one, under Colecciones', () => {
		expect(tabFor('/collections', href)).toBe('collections');
		expect(tabFor('/albums', href)).toBe('collections');
		expect(tabFor('/albums/album-1', href)).toBe('collections');
		expect(tabFor('/people/person-ana/faces', href)).toBe('collections');
		expect(tabFor('/trash', href)).toBe('collections');
	});

	it('puts the rest under Más', () => {
		expect(tabFor('/more', href)).toBe('more');
		expect(tabFor('/upload', href)).toBe('more');
		expect(tabFor('/settings/profile', href)).toBe('more');
		expect(tabFor('/admin/users', href)).toBe('more');
	});

	it('does not take a path that only starts like a collection', () => {
		expect(tabFor('/albums-old', href)).toBe('more');
	});

	it('follows a base path', () => {
		const based = (path: string) => `/photos${path === '/' ? '/' : path}`;
		expect(tabFor('/photos/', based)).toBe('photos');
		expect(tabFor('/photos/albums/1', based)).toBe('collections');
	});
});
