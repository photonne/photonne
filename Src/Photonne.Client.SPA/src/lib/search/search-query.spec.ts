import { describe, expect, it } from 'vitest';
import {
	emptySearch,
	filterCount,
	hasCriteria,
	parseSearch,
	searchKey,
	searchParams,
	textSearchRequest,
	toggled
} from './search-query.js';

describe('search query', () => {
	it('reads a search from the URL', () => {
		const query = parseSearch(
			new URLSearchParams(
				'q=+playa+&sem=1&from=2024-01-01&to=2024-12-31&person=p1&person=p2&object=dog&scene=beach&ocr=factura&folder=Viajes'
			)
		);

		expect(query).toEqual({
			q: 'playa',
			semantic: true,
			from: '2024-01-01',
			to: '2024-12-31',
			people: ['p1', 'p2'],
			objects: ['dog'],
			scenes: ['beach'],
			ocr: 'factura',
			folder: 'Viajes'
		});
	});

	it('ignores malformed dates, blanks and repeated values', () => {
		const query = parseSearch(
			new URLSearchParams('from=ayer&to=2024-13-45&object=dog&object=dog&object=+&sem=yes')
		);

		expect(query.from).toBeNull();
		expect(query.to).toBeNull();
		expect(query.objects).toEqual(['dog']);
		expect(query.semantic).toBe(false);
	});

	it('writes a canonical URL that round-trips', () => {
		const query = {
			...emptySearch,
			folder: 'Viajes',
			q: 'perro, gato',
			scenes: ['beach & sea'],
			from: '2024-01-01'
		};

		expect(searchParams(query).toString()).toBe(
			'q=perro%2C+gato&from=2024-01-01&scene=beach+%26+sea&folder=Viajes'
		);
		expect(parseSearch(searchParams(query))).toEqual(query);
		expect(searchKey(emptySearch)).toBe('');
	});

	it('counts filters and knows when there is something to search', () => {
		expect(hasCriteria(emptySearch)).toBe(false);
		expect(hasCriteria({ ...emptySearch, objects: ['dog'] })).toBe(true);
		expect(filterCount({ ...emptySearch, from: '2024-01-01', to: '2024-02-01', ocr: 'x' })).toBe(2);
		// The semantic search reads only the words.
		expect(hasCriteria({ ...emptySearch, semantic: true, objects: ['dog'] })).toBe(false);
		expect(hasCriteria({ ...emptySearch, semantic: true, q: 'perro' })).toBe(true);
	});

	it('builds the text search request', () => {
		const request = textSearchRequest(
			{ ...emptySearch, q: 'playa', to: '2024-12-31', people: ['p1'] },
			100,
			100
		);

		expect(request).toEqual({
			q: 'playa',
			from: undefined,
			to: '2024-12-31T00:00:00Z',
			personId: ['p1'],
			objectLabel: undefined,
			sceneLabel: undefined,
			textQuery: undefined,
			folder: undefined,
			pageSize: 100,
			offset: 100
		});
		expect(textSearchRequest(emptySearch, 0, 50).offset).toBeUndefined();
	});

	it('toggles a value in a list', () => {
		const once = toggled(emptySearch, 'objects', 'dog');
		expect(once.objects).toEqual(['dog']);
		expect(toggled(once, 'objects', 'dog').objects).toEqual([]);
	});
});
