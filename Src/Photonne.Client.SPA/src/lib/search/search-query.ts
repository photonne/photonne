import type { SearchAssetsData } from '#lib/api/index.js';

/**
 * Everything a search is, as it lives in the URL (`/search?q=…&person=…`), so
 * a search can be linked, reloaded and stepped through with Back.
 *
 * Dates are calendar days (`yyyy-MM-dd`); lists repeat their parameter
 * (`object=dog&object=cat`) so a label can hold any character.
 */
export interface SearchQuery {
	q: string;
	/** Natural-language (CLIP) search instead of the text search. */
	semantic: boolean;
	from: string | null;
	to: string | null;
	people: string[];
	objects: string[];
	scenes: string[];
	/** Text inside the image (OCR). */
	ocr: string;
	/** Substring of the path, e.g. `/assets/users/ana/Viajes`. */
	folder: string;
}

export const emptySearch: SearchQuery = {
	q: '',
	semantic: false,
	from: null,
	to: null,
	people: [],
	objects: [],
	scenes: [],
	ocr: '',
	folder: ''
};

const DAY = /^\d{4}-\d{2}-\d{2}$/;

function day(value: string | null) {
	return value && DAY.test(value) && !Number.isNaN(Date.parse(value)) ? value : null;
}

function list(params: URLSearchParams, key: string) {
	return [...new Set(params.getAll(key).map((value) => value.trim()))].filter(Boolean);
}

/** Reads a search from the URL; anything malformed is ignored, never thrown. */
export function parseSearch(params: URLSearchParams): SearchQuery {
	return {
		q: params.get('q')?.trim() ?? '',
		semantic: params.get('sem') === '1',
		from: day(params.get('from')),
		to: day(params.get('to')),
		people: list(params, 'person'),
		objects: list(params, 'object'),
		scenes: list(params, 'scene'),
		ocr: params.get('ocr')?.trim() ?? '',
		folder: params.get('folder')?.trim() ?? ''
	};
}

/**
 * The search as URL parameters in a fixed order with empties left out, so
 * two equal searches give the same string (it keys the results).
 */
export function searchParams(query: SearchQuery): URLSearchParams {
	const params = new URLSearchParams();
	const text = (key: string, value: string | null) => {
		if (value?.trim()) params.set(key, value.trim());
	};
	text('q', query.q);
	if (query.semantic) params.set('sem', '1');
	text('from', query.from);
	text('to', query.to);
	for (const id of query.people) params.append('person', id);
	for (const label of query.objects) params.append('object', label);
	for (const label of query.scenes) params.append('scene', label);
	text('ocr', query.ocr);
	text('folder', query.folder);
	return params;
}

export function searchKey(query: SearchQuery) {
	return searchParams(query).toString();
}

/** How many filters (beyond the words) narrow the text search. */
export function filterCount(query: SearchQuery) {
	return [
		query.from || query.to,
		query.people.length,
		query.objects.length,
		query.scenes.length,
		query.ocr,
		query.folder
	].filter(Boolean).length;
}

/**
 * Whether there is something to search for. The semantic search only reads
 * the words, so leftover filters alone don't make a semantic search.
 */
export function hasCriteria(query: SearchQuery) {
	return query.semantic ? query.q.length > 0 : query.q.length > 0 || filterCount(query) > 0;
}

/** The text search's request for one page. */
export function textSearchRequest(
	query: SearchQuery,
	offset: number,
	pageSize: number
): NonNullable<SearchAssetsData['query']> {
	return {
		q: query.q || undefined,
		from: query.from ? `${query.from}T00:00:00Z` : undefined,
		// The server includes the whole `to` day.
		to: query.to ? `${query.to}T00:00:00Z` : undefined,
		personId: query.people.length ? query.people : undefined,
		objectLabel: query.objects.length ? query.objects : undefined,
		sceneLabel: query.scenes.length ? query.scenes : undefined,
		textQuery: query.ocr || undefined,
		folder: query.folder || undefined,
		pageSize,
		offset: offset || undefined
	};
}

/** `query` with one list value added or taken away. */
export function toggled(
	query: SearchQuery,
	key: 'people' | 'objects' | 'scenes',
	value: string
): SearchQuery {
	const current = query[key];
	return {
		...query,
		[key]: current.includes(value) ? current.filter((v) => v !== value) : [...current, value]
	};
}
