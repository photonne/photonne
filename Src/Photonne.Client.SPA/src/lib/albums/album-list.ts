import type { AlbumResponse } from '#lib/api/index.js';

/** Whose albums: all, the user's own, or the ones others shared with them. */
export type AlbumScope = 'all' | 'mine' | 'shared';
export type AlbumKindFilter = 'all' | 'manual' | 'smart';
export type AlbumSort = 'updated' | 'created' | 'name' | 'count';
/** Cover tiles or compact rows (shared with the folders page). */
export type ListView = 'grid' | 'list';

export interface AlbumListOptions {
	scope: AlbumScope;
	kind: AlbumKindFilter;
	sort: AlbumSort;
	descending: boolean;
	view: ListView;
	/** Albums under a header per creation year, as in the native app. */
	groupByYear: boolean;
	/** Free text matched against name and description, accent- and case-insensitive. */
	query: string;
}

export const defaultListOptions: AlbumListOptions = {
	scope: 'all',
	kind: 'all',
	sort: 'updated',
	descending: true,
	view: 'grid',
	groupByYear: false,
	query: ''
};

export const isSmart = (album: Pick<AlbumResponse, 'kind'>) => album.kind === 'Smart';

/** Lower case without diacritics: "Álbum" matches "album". */
export function fold(text: string) {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase();
}

export function matchesQuery(album: AlbumResponse, query: string) {
	const needle = fold(query.trim());
	if (!needle) return true;
	return fold(`${album.name} ${album.description ?? ''}`).includes(needle);
}

function inScope(album: AlbumResponse, scope: AlbumScope) {
	if (scope === 'mine') return album.isOwner;
	if (scope === 'shared') return !album.isOwner;
	return true;
}

function ofKind(album: AlbumResponse, kind: AlbumKindFilter) {
	if (kind === 'smart') return isSmart(album);
	if (kind === 'manual') return !isSmart(album);
	return true;
}

export function compareAlbums(sort: AlbumSort, locale: string) {
	const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true });
	const byName = (a: AlbumResponse, b: AlbumResponse) => collator.compare(a.name, b.name);
	const keyed: Record<AlbumSort, (a: AlbumResponse, b: AlbumResponse) => number> = {
		name: byName,
		updated: (a, b) => Date.parse(a.updatedAt) - Date.parse(b.updatedAt),
		created: (a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt),
		count: (a, b) => a.assetCount - b.assetCount
	};
	// Ties fall back to the name so the order is stable between refreshes.
	return (a: AlbumResponse, b: AlbumResponse) => keyed[sort](a, b) || byName(a, b);
}

/**
 * The albums page's two groups: the user's pinned albums first, then the
 * rest, both filtered and sorted the same way.
 */
export function arrangeAlbums(
	albums: readonly AlbumResponse[],
	options: AlbumListOptions,
	locale = 'es'
) {
	const compare = compareAlbums(options.sort, locale);
	const sorted = albums
		.filter(
			(album) =>
				inScope(album, options.scope) &&
				ofKind(album, options.kind) &&
				matchesQuery(album, options.query)
		)
		.sort((a, b) => (options.descending ? compare(b, a) : compare(a, b)));
	return {
		pinned: sorted.filter((album) => album.isPinned),
		others: sorted.filter((album) => !album.isPinned)
	};
}

/** How many albums each scope would show, for the scope tabs. */
export function scopeCounts(albums: readonly AlbumResponse[]) {
	const mine = albums.filter((album) => album.isOwner).length;
	return { all: albums.length, mine, shared: albums.length - mine };
}

/** Reads list options saved by an earlier visit, ignoring anything malformed. */
export function parseListOptions(raw: string | null): AlbumListOptions {
	try {
		const saved = JSON.parse(raw ?? '{}') as Partial<AlbumListOptions>;
		const pick = <T extends string>(value: unknown, allowed: readonly T[], fallback: T): T =>
			allowed.includes(value as T) ? (value as T) : fallback;
		return {
			scope: pick(saved.scope, ['all', 'mine', 'shared'], defaultListOptions.scope),
			kind: pick(saved.kind, ['all', 'manual', 'smart'], defaultListOptions.kind),
			sort: pick(saved.sort, ['updated', 'created', 'name', 'count'], defaultListOptions.sort),
			descending:
				typeof saved.descending === 'boolean' ? saved.descending : defaultListOptions.descending,
			view: pick(saved.view, ['grid', 'list'], defaultListOptions.view),
			groupByYear: saved.groupByYear === true,
			query: ''
		};
	} catch {
		return { ...defaultListOptions };
	}
}

/** Albums by the year they were created, newest year first, keeping their order inside. */
export function groupByYear(albums: readonly AlbumResponse[]) {
	const groups: { year: number; albums: AlbumResponse[] }[] = [];
	for (const album of albums) {
		const year = new Date(album.createdAt).getUTCFullYear();
		const group = groups.find((candidate) => candidate.year === year);
		if (group) group.albums.push(album);
		else groups.push({ year, albums: [album] });
	}
	return groups.sort((a, b) => b.year - a.year);
}

/**
 * What a selection of albums allows, as in the native app: an action is
 * offered only if it works for every selected album, so a batch never fails
 * halfway on permissions known in advance. Leaving is for albums others
 * shared with me; deleting needs ownership or the delete grant.
 */
export function albumSelectionActions(selected: readonly AlbumResponse[]) {
	return {
		canDelete: selected.length > 0 && selected.every((album) => album.isOwner || album.canDelete),
		canLeave: selected.length > 0 && selected.every((album) => !album.isOwner)
	};
}
