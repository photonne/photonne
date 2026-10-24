import type { AlbumResponse, FolderResponse } from '#lib/api/index.js';

export type PinnedEntry =
	{ kind: 'album'; item: AlbumResponse } | { kind: 'folder'; item: FolderResponse };

/**
 * The user's pinned albums and folders in one list, in the order the user gave
 * them (one order for both kinds; a new pin goes on top), as the native app's
 * Collections shows them (smart albums included).
 */
export function mergePinned(
	albums: readonly AlbumResponse[],
	folders: readonly FolderResponse[]
): PinnedEntry[] {
	const position = (entry: PinnedEntry) => entry.item.pinPosition ?? Number.MAX_SAFE_INTEGER;
	const pinnedAt = (entry: PinnedEntry) => Date.parse(entry.item.pinnedAt ?? '') || 0;
	return [
		...albums
			.filter((album) => album.isPinned)
			.map((item): PinnedEntry => ({ kind: 'album', item })),
		...folders
			.filter((folder) => folder.isPinned)
			.map((item): PinnedEntry => ({ kind: 'folder', item }))
	].sort((a, b) => position(a) - position(b) || pinnedAt(b) - pinnedAt(a));
}
