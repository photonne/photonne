import { QueryClient, type Query } from '@tanstack/svelte-query';

/**
 * The app's one query cache. It lives here, not in the root layout, so code
 * outside components (session, batch actions, the upload queue) can reach it.
 */
export const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			// Library data changes when the user acts on it, and those actions
			// invalidate what they touch; no need to refetch on every focus.
			staleTime: 60_000,
			refetchOnWindowFocus: false,
			retry: 1
		}
	}
});

// The queries that show which photos exist, where and how: the timeline
// (skeleton and month items), the map, an open photo's detail, album and
// folder lists (counts, covers), stats and memories.
const LIBRARY_QUERIES = [
	'getTimeline',
	'getMapPoints',
	'getAssetDetail',
	'getSameDayAssets',
	'getRecentAssets',
	'getAllAlbums',
	'getAlbumById',
	'getAllFolders',
	'getFolderTree',
	'getMyFolderTree',
	'getFolderById',
	'getTrashStats',
	'getUtilitiesSummary',
	'getMemory',
	'getOrganize'
];

function isLibraryQuery(query: Query) {
	const id = (query.queryKey[0] as { _id?: unknown } | undefined)?._id;
	return typeof id === 'string' && LIBRARY_QUERIES.some((prefix) => id.startsWith(prefix));
}

/**
 * After photos are archived, trashed, restored, moved, edited or added: every
 * cached view of the library is stale, wherever the change was made.
 */
export function invalidateLibrary(client: QueryClient = queryClient) {
	return client.invalidateQueries({ predicate: isLibraryQuery });
}
