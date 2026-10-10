import type { GetApiPeopleData, MemoryResponse, PersonDto } from '#lib/api/index.js';

/**
 * The "most photos together with" row of a person's page: their memories of
 * kind PeopleTogether, in the server's order (most photos first), as the
 * native app shows them. "Through the years" is left to Recuerdos.
 */
export function companions<T extends Pick<MemoryResponse, 'kind'>>(memories: readonly T[]): T[] {
	return memories.filter((memory) => memory.kind === 'PeopleTogether');
}

/** How the people grid is ordered (the native app offers the same three). */
export type PeopleSort = 'faces' | 'name' | 'unnamed';

export const PEOPLE_SORTS: readonly PeopleSort[] = ['faces', 'name', 'unnamed'];

/** Page size of the people grid: big enough to fill a desktop screen at once. */
export const PEOPLE_PAGE_SIZE = 120;

export function parseSort(value: string | null | undefined): PeopleSort {
	return PEOPLE_SORTS.includes(value as PeopleSort) ? (value as PeopleSort) : 'faces';
}

/**
 * Query of GET /api/people for the grid's controls. Only the flags that are
 * on are sent (the server treats a missing flag as false); a blank search is
 * no search.
 */
export function peopleQuery(options: {
	sort: PeopleSort;
	search: string;
	includeHidden: boolean;
	limit?: number;
}): NonNullable<GetApiPeopleData['query']> {
	const query: NonNullable<GetApiPeopleData['query']> = {
		limit: options.limit ?? PEOPLE_PAGE_SIZE
	};
	if (options.sort === 'name') {
		query.sort = 'name';
		query.sortDir = 'asc';
	} else if (options.sort === 'unnamed') {
		query.unnamedFirst = true;
	}
	const search = options.search.trim();
	if (search) query.search = search;
	if (options.includeHidden) query.includeHidden = true;
	return query;
}

/** The person's name, or the "unnamed" label when it has none (or only spaces). */
export function displayName(person: Pick<PersonDto, 'name'>, unnamed: string) {
	return person.name?.trim() || unnamed;
}

export function hasName(person: Pick<PersonDto, 'name'>) {
	return !!person.name?.trim();
}

/** The face crop (220×220 JPEG); loads with the media cookie like any thumbnail. */
export function faceThumbnailUrl(faceId: string) {
	return `/api/faces/${faceId}/thumbnail`;
}

/**
 * Which of several people a merge keeps by default: a named one before an
 * unnamed one (the name is what the user cared to type), then the one with
 * the most faces, then the first.
 */
export function defaultMergeTarget<T extends Pick<PersonDto, 'id' | 'name' | 'faceCount'>>(
	people: readonly T[]
): T | undefined {
	let best: T | undefined;
	for (const person of people) {
		if (!best) {
			best = person;
			continue;
		}
		const named = hasName(person);
		const bestNamed = hasName(best);
		if (named !== bestNamed ? named : person.faceCount > best.faceCount) best = person;
	}
	return best;
}

/** One or two letters for an avatar without a face crop. */
export function initials(name: string | null | undefined) {
	const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
	if (words.length === 0) return '';
	const first = [...words[0]][0] ?? '';
	const last = words.length > 1 ? ([...words[words.length - 1]][0] ?? '') : '';
	return (first + last).toUpperCase();
}

/**
 * Pagination over an offset endpoint: the next offset, or undefined when the
 * pages already hold `total` items (TanStack's getNextPageParam contract).
 */
export function nextOffset(pages: readonly { items: readonly unknown[] }[], total: number) {
	const loaded = pages.reduce((sum, page) => sum + page.items.length, 0);
	const last = pages.at(-1);
	if (!last || last.items.length === 0 || loaded >= total) return undefined;
	return loaded;
}
