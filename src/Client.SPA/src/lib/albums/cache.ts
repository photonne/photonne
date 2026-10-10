import type { QueryClient } from '@tanstack/svelte-query';
import {
	pinAlbum,
	pinFolder,
	unpinAlbum,
	unpinFolder,
	type AlbumResponse,
	type FolderResponse
} from '#lib/api/index.js';
import {
	getAlbumByIdQueryKey,
	getAllAlbumsQueryKey,
	getAllFoldersQueryKey,
	getFolderByIdQueryKey,
	getFolderTreeQueryKey,
	getMyFolderTreeQueryKey
} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';

/** Refetches the album lists (page, sidebar pins, pickers) and, if given, one album. */
export function invalidateAlbums(queryClient: QueryClient, albumId?: string) {
	queryClient.invalidateQueries({ queryKey: getAllAlbumsQueryKey() });
	if (albumId)
		queryClient.invalidateQueries({ queryKey: getAlbumByIdQueryKey({ path: { albumId } }) });
}

/** Refetches every folder list (tree, sidebar pins, pickers) and, if given, one folder. */
export function invalidateFolders(queryClient: QueryClient, folderId?: string) {
	queryClient.invalidateQueries({ queryKey: getFolderTreeQueryKey() });
	queryClient.invalidateQueries({ queryKey: getAllFoldersQueryKey() });
	queryClient.invalidateQueries({ queryKey: getMyFolderTreeQueryKey() });
	if (folderId)
		queryClient.invalidateQueries({ queryKey: getFolderByIdQueryKey({ path: { folderId } }) });
}

/** Pins or unpins an album for this user (it shows in the sidebar's "Pinned"). */
export async function toggleAlbumPin(
	queryClient: QueryClient,
	album: Pick<AlbumResponse, 'id' | 'name' | 'isPinned'>
) {
	const options = { path: { albumId: album.id } };
	const { error } = album.isPinned ? await unpinAlbum(options) : await pinAlbum(options);
	if (error) {
		toasts.error(m.action_failed());
		return;
	}
	invalidateAlbums(queryClient, album.id);
	toasts.show(
		album.isPinned ? m.albums_unpinned({ name: album.name }) : m.albums_pinned({ name: album.name })
	);
}

export async function toggleFolderPin(
	queryClient: QueryClient,
	folder: Pick<FolderResponse, 'id' | 'name' | 'isPinned'>
) {
	const options = { path: { folderId: folder.id } };
	const { error } = folder.isPinned ? await unpinFolder(options) : await pinFolder(options);
	if (error) {
		toasts.error(m.action_failed());
		return;
	}
	invalidateFolders(queryClient, folder.id);
	toasts.show(
		folder.isPinned
			? m.albums_unpinned({ name: folder.name })
			: m.albums_pinned({ name: folder.name })
	);
}
