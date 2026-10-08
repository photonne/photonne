import type { QueryClient } from '@tanstack/svelte-query';
import { apiErrorCode, moveFolderAssets, updateFolder } from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import { invalidateFolders } from './cache.js';
import { yearSummary } from './folder-tree.js';

export interface AssetsMoved {
	assetIds: readonly string[];
	/** The folder they left, when known (a folder page's own photos). */
	from: string | null;
	to: string;
}

type Listener = (event: AssetsMoved) => void;
const listeners = new Set<Listener>();

/**
 * Photos can leave a folder from outside its page (dropped on the tree in
 * the folders layout); the page listens to take them off its grid.
 */
export function onAssetsMoved(listener: Listener) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

function emit(event: AssetsMoved) {
	for (const listener of listeners) listener(event);
}

/**
 * Moves photos into a folder, tells the open folder page, and offers Undo
 * (moving them back) when they came from a known folder and weren't spread
 * into year subfolders.
 */
export async function moveAssets(
	queryClient: QueryClient,
	options: {
		assetIds: readonly string[];
		from: { id: string; name: string } | null;
		to: { id: string; name: string };
		byYear?: boolean;
	}
) {
	const { assetIds, from, to, byYear = false } = options;
	if (assetIds.length === 0 || from?.id === to.id) return false;
	const { data, error } = await moveFolderAssets({
		body: {
			sourceFolderId: from?.id ?? null,
			targetFolderId: to.id,
			assetIds: [...assetIds],
			organizeByCaptureYear: byYear
		}
	});
	if (error || !data) {
		toasts.error(m.action_failed());
		return false;
	}
	emit({ assetIds, from: from?.id ?? null, to: to.id });
	invalidateFolders(queryClient);
	const message = byYear
		? m.folders_moved_by_year({
				count: data.moved,
				folder: to.name,
				years: yearSummary(data.yearBreakdown)
			})
		: m.action_moved({ count: data.moved, folder: to.name });
	toasts.show(
		message,
		from && !byYear
			? {
					action: {
						label: m.action_undo(),
						run: () => void moveAssets(queryClient, { assetIds, from: to, to: from })
					}
				}
			: {}
	);
	return true;
}

/** Moves a folder (and its subtree) under another one, from a drag in the tree. */
export async function moveFolder(
	queryClient: QueryClient,
	folder: { id: string; name: string },
	parent: { id: string; name: string }
) {
	// The server renames and re-parents in one call; the name is sent unchanged.
	const { error } = await updateFolder({
		path: { folderId: folder.id },
		body: { name: folder.name, parentFolderId: parent.id }
	});
	if (error) {
		const code = apiErrorCode(error);
		toasts.error(
			code === 'folder_already_exists'
				? m.folders_error_exists()
				: code === 'invalid_parent_folder'
					? m.folders_error_parent()
					: m.action_failed()
		);
		return false;
	}
	invalidateFolders(queryClient, folder.id);
	toasts.show(m.folders_folder_moved({ folder: folder.name, parent: parent.name }));
	return true;
}
