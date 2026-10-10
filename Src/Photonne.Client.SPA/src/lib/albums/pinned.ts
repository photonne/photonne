import type { AlbumResponse, FolderResponse } from '#lib/api/index.js';

export type PinnedEntry =
	{ kind: 'album'; item: AlbumResponse } | { kind: 'folder'; item: FolderResponse };

/**
 * The user's pinned albums and folders in one list, the last pinned first,
 * as the native app's Collections shows them (smart albums included).
 */
export function mergePinned(
	albums: readonly AlbumResponse[],
	folders: readonly FolderResponse[]
): PinnedEntry[] {
	const pinnedAt = (entry: PinnedEntry) => Date.parse(entry.item.pinnedAt ?? '') || 0;
	return [
		...albums
			.filter((album) => album.isPinned)
			.map((item): PinnedEntry => ({ kind: 'album', item })),
		...folders
			.filter((folder) => folder.isPinned)
			.map((item): PinnedEntry => ({ kind: 'folder', item }))
	].sort((a, b) => pinnedAt(b) - pinnedAt(a));
}
