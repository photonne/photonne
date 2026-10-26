import type { SearchAssetsData } from '#lib/api/index.js';

/**
 * Everything a search is, as it lives in the URL (`/search?q=…&person=…`), so
 * a search can be linked, reloaded and stepped through with Back.
 *
 * Dates are calendar days (`yyyy-MM-dd`); lists repeat their parameter
 * (`object=dog&object=cat`) so a label can hold any character.
 *
 * The words go to the semantic (AI) search unless `sem=0` says otherwise.
 * A URL with filters and no `sem` is a text search: the semantic search
 * ignores filters, so a link to a person or a label (Discover, older links)
 * keeps meaning what it says.
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
	/** Only photos or only videos; null is both. */
	type: MediaType | null;
	/** A whole tag name (a user tag or a structural one). */
	tag: string;
	/** The EXIF camera model, and its make when known. */
	camera: string;
	make: string;
	/** A GeoNames place id, with the name to show (it isn't looked up). */
	place: string;
	placeName: string;
}

export type MediaType = 'image' | 'video';

export const emptySearch: SearchQuery = {
	q: '',
	semantic: true,
	from: null,
	to: null,
	people: [],
	objects: [],
	scenes: [],
	ocr: '',
	folder: '',
	type: null,
	tag: '',
	camera: '',
	make: '',
	place: '',
	placeName: ''
};

const DAY = /^\d{4}-\d{2}-\d{2}$/;

function day(value: string | null) {
	return value && DAY.test(value) && !Number.isNaN(Date.parse(value)) ? value : null;
}

function list(params: URLSearchParams, key: string) {
	return [...new Set(params.getAll(key).map((value) => value.trim()))].filter(Boolean);
}

function mediaType(value: string | null): MediaType | null {
	return value === 'image' || value === 'video' ? value : null;
}

/** Reads a search from the URL; anything malformed is ignored, never thrown. */
export function parseSearch(params: URLSearchParams): SearchQuery {
	const query: SearchQuery = {
		q: params.get('q')?.trim() ?? '',
		semantic: false,
		from: day(params.get('from')),
		to: day(params.get('to')),
		people: list(params, 'person'),
		objects: list(params, 'object'),
		scenes: list(params, 'scene'),
		ocr: params.get('ocr')?.trim() ?? '',
		folder: params.get('folder')?.trim() ?? '',
		type: mediaType(params.get('type')),
		tag: params.get('tag')?.trim() ?? '',
		camera: params.get('camera')?.trim() ?? '',
		make: params.get('make')?.trim() ?? '',
		place: params.get('place')?.trim() ?? '',
		placeName: params.get('placeName')?.trim() ?? ''
	};
	const sem = params.get('sem');
	query.semantic = sem === '1' || (sem !== '0' && filterCount(query) === 0);
	return query;
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
	// Only what the default (see parseSearch) wouldn't say on its own.
	const filtered = filterCount(query) > 0;
	if (query.semantic && filtered) params.set('sem', '1');
	if (!query.semantic && !filtered) params.set('sem', '0');
	text('from', query.from);
	text('to', query.to);
	for (const id of query.people) params.append('person', id);
	for (const label of query.objects) params.append('object', label);
	for (const label of query.scenes) params.append('scene', label);
	text('ocr', query.ocr);
	text('folder', query.folder);
	text('type', query.type);
	text('tag', query.tag);
	text('camera', query.camera);
	if (query.camera) text('make', query.make);
	text('place', query.place);
	if (query.place) text('placeName', query.placeName);
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
		query.folder,
		query.type,
		query.tag,
		query.camera,
		query.place
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
		type: query.type ?? undefined,
		tag: query.tag || undefined,
		cameraModel: query.camera || undefined,
		cameraMake: (query.camera && query.make) || undefined,
		placeId: query.place || undefined,
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
