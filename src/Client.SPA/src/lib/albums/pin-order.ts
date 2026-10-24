import type { QueryClient } from '@tanstack/svelte-query';
import { reorderPins, type AlbumResponse, type FolderResponse } from '#lib/api/index.js';
import {
	getAllAlbumsQueryKey,
	getAllFoldersQueryKey
} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import { invalidateAlbums, invalidateFolders } from './cache.js';

export interface PinKey {
	kind: 'album' | 'folder';
	id: string;
}

/** dataTransfer type of a pin dragged within "Pinned" (its kind:id), apart from dragged photos. */
export const PIN_MIME = 'application/x-photonne-pin';

export function isPinDrag(event: DragEvent) {
	return event.dataTransfer?.types.includes(PIN_MIME) ?? false;
}

/**
 * The list with the item at `from` moved to just before (or, with `after`,
 * just after) the item at `target`. Indexes are those of the list as it is.
 */
export function movePin<T>(list: readonly T[], from: number, target: number, after = false): T[] {
	if (from === target || !(from in list) || !(target in list)) return [...list];
	const rest = list.filter((_, index) => index !== from);
	const at = target - (from < target ? 1 : 0) + (after ? 1 : 0);
	return [...rest.slice(0, at), list[from], ...rest.slice(at)];
}

/**
 * Saves the new order of "Pinned". The sidebar and the lists show it at once;
 * if the server refuses, they go back to what it has.
 */
export async function savePinOrder(queryClient: QueryClient, order: readonly PinKey[]) {
	const position = new Map(order.map((pin, index) => [`${pin.kind}:${pin.id}`, index]));
	const reposition =
		<T extends AlbumResponse | FolderResponse>(kind: PinKey['kind']) =>
		(items: T[] | undefined) =>
			items?.map((item) => {
				const at = position.get(`${kind}:${item.id}`);
				return at === undefined ? item : { ...item, pinPosition: at };
			});
	await Promise.all([
		queryClient.cancelQueries({ queryKey: getAllAlbumsQueryKey() }),
		queryClient.cancelQueries({ queryKey: getAllFoldersQueryKey() })
	]);
	queryClient.setQueriesData<AlbumResponse[]>(
		{ queryKey: getAllAlbumsQueryKey() },
		reposition('album')
	);
	queryClient.setQueriesData<FolderResponse[]>(
		{ queryKey: getAllFoldersQueryKey() },
		reposition('folder')
	);

	const { error } = await reorderPins({
		body: { pins: order.map(({ kind, id }) => ({ kind, id })) }
	});
	if (error) {
		toasts.error(m.action_failed());
		invalidateAlbums(queryClient);
		invalidateFolders(queryClient);
	}
}
