import { describe, expect, it } from 'vitest';
import {
	companions,
	defaultMergeTarget,
	displayName,
	faceThumbnailUrl,
	initials,
	nextOffset,
	parseSort,
	peopleQuery
} from './people';

describe('peopleQuery', () => {
	it('sends nothing but the page size by default (most faces first)', () => {
		expect(peopleQuery({ sort: 'faces', search: '', includeHidden: false })).toEqual({
			limit: 120
		});
	});

	it('maps the name order to an ascending name sort', () => {
		expect(peopleQuery({ sort: 'name', search: '', includeHidden: false, limit: 10 })).toEqual({
			limit: 10,
			sort: 'name',
			sortDir: 'asc'
		});
	});

	it('puts the unnamed first, trims the search and includes hidden people on demand', () => {
		expect(peopleQuery({ sort: 'unnamed', search: '  José ', includeHidden: true })).toEqual({
			limit: 120,
			unnamedFirst: true,
			search: 'José',
			includeHidden: true
		});
	});
});

describe('parseSort', () => {
	it('accepts the known orders and falls back to most faces', () => {
		expect(parseSort('name')).toBe('name');
		expect(parseSort('unnamed')).toBe('unnamed');
		expect(parseSort('bogus')).toBe('faces');
		expect(parseSort(null)).toBe('faces');
	});
});

describe('displayName', () => {
	it('uses the label for people without a (non-blank) name', () => {
		expect(displayName({ name: 'Ana' }, 'Sin nombre')).toBe('Ana');
		expect(displayName({ name: null }, 'Sin nombre')).toBe('Sin nombre');
		expect(displayName({ name: '  ' }, 'Sin nombre')).toBe('Sin nombre');
	});
});

describe('defaultMergeTarget', () => {
	const person = (id: string, name: string | null, faceCount: number) => ({ id, name, faceCount });

	it('keeps a named person over an unnamed one with more faces', () => {
		expect(defaultMergeTarget([person('a', null, 90), person('b', 'Ana', 3)])?.id).toBe('b');
	});

	it('among named (or unnamed) people keeps the one with most faces, first on a tie', () => {
		expect(
			defaultMergeTarget([person('a', 'Ana', 3), person('b', 'Ana M.', 8), person('c', 'A', 8)])?.id
		).toBe('b');
		expect(defaultMergeTarget([person('a', null, 3), person('b', null, 5)])?.id).toBe('b');
	});

	it('is undefined for nobody', () => {
		expect(defaultMergeTarget([])).toBeUndefined();
	});
});

describe('initials', () => {
	it('takes the first letters of the first and last words', () => {
		expect(initials('ana maría lópez')).toBe('AL');
		expect(initials('Óscar')).toBe('Ó');
		expect(initials('  ')).toBe('');
		expect(initials(null)).toBe('');
	});
});

describe('nextOffset', () => {
	it('continues after the loaded items until the total is reached', () => {
		const page = (n: number) => ({ items: Array.from({ length: n }) });
		expect(nextOffset([page(50)], 120)).toBe(50);
		expect(nextOffset([page(50), page(50)], 120)).toBe(100);
		expect(nextOffset([page(50), page(50), page(20)], 120)).toBeUndefined();
		// A short page that still falls below the total (items vanished) stops too.
		expect(nextOffset([page(50), page(0)], 120)).toBeUndefined();
		expect(nextOffset([], 0)).toBeUndefined();
	});
});

describe('faceThumbnailUrl', () => {
	it('points at the face crop', () => {
		expect(faceThumbnailUrl('f1')).toBe('/api/faces/f1/thumbnail');
	});
});

describe('companions', () => {
	it('keeps the people-together memories in the server order', () => {
		const memories = [
			{ id: 'a', kind: 'PeopleTogether' },
			{ id: 'b', kind: 'PersonThroughYears' },
			{ id: 'c', kind: 'PeopleTogether' }
		];
		expect(companions(memories).map((memory) => memory.id)).toEqual(['a', 'c']);
	});
});
