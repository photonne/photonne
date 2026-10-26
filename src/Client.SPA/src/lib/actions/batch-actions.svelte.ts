import {
	addAssetsToAlbumBatch,
	archiveAssets,
	deleteAssets,
	moveFolderAssets,
	restoreAssets,
	setFavorites,
	unarchiveAssets,
	type AssetDetailResponse
} from '#lib/api/index.js';
import { getAssetDetailQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { invalidateLibrary, queryClient } from '#lib/api/query-client.js';
import { announceAssetsMoved } from '#lib/albums/folder-moves.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { returnToRestored } from '#lib/library/viewer-flow.js';
import { m } from '#lib/paraglide/messages.js';
import type { GridHost } from '#lib/timeline/grid-host.js';
import type { Selection } from '#lib/timeline/selection.svelte.js';
import { AssetDownloader } from './asset-downloader.svelte.js';

/**
 * Actions on the selected photos, shared by every grid (see GridHost).
 * Removing actions (archive, trash) take the photos out of view at once and
 * offer Undo. One action runs at a time; the ones asked for meanwhile (a
 * second Delete in the viewer) wait their turn instead of being dropped.
 */
export class BatchActions {
	busy = $state(false);
	/** Downloads, with the "original or JPEG?" question (DownloadFormatDialog). */
	readonly downloader = new AssetDownloader();
	/**
	 * The photos the share dialog is open for. The host renders
	 * ShareAssetsDialog, not the bar: the dialog outlives the selection (it
	 * clears once the link exists) and the viewer opens it too.
	 */
	sharing = $state<string[] | null>(null);
	#sharingSelection = false;
	#queue: Promise<void> = Promise.resolve();
	#queued = 0;
	#host: GridHost;
	#selection: Selection;

	constructor(host: GridHost, selection: Selection) {
		this.#host = host;
		this.#selection = selection;
	}

	/** Explicit ids (the viewer's single photo) or the current selection. */
	#targets(ids?: readonly string[]) {
		return ids ? [...ids] : [...this.#selection.ids];
	}

	#run(action: () => Promise<void>) {
		this.#queued++;
		this.busy = true;
		const run = this.#queue.then(async () => {
			try {
				await action();
			} catch {
				toasts.error(m.action_failed());
			} finally {
				if (--this.#queued === 0) this.busy = false;
			}
		});
		this.#queue = run;
		return run;
	}

	/**
	 * Marks the ones missing a heart; only when all have it, clears them all.
	 * The server answers with the ids that now have the asked state: the ones
	 * it left out (gone, or no longer readable) keep theirs.
	 */
	toggleFavorites(ids?: readonly string[]) {
		const targets = this.#targets(ids);
		return this.#run(async () => {
			const items = this.#host.itemsByIds(targets);
			if (items.length === 0) return;
			const marking = items.some((item) => !item.isFavorite);
			const changing = items.filter((item) => item.isFavorite !== marking).map((item) => item.id);
			const { data, error } = await setFavorites({
				body: { assetIds: changing, isFavorite: marking }
			});
			if (error || !data) throw error;
			this.#markFavorite(data.assetIds, marking);
			toasts.show(
				marking
					? m.action_favorited({ count: data.assetIds.length })
					: m.action_unfavorited({ count: data.assetIds.length })
			);
		});
	}

	#markFavorite(ids: readonly string[], isFavorite: boolean) {
		if (ids.length === 0) return;
		this.#host.update(ids, (item) => ({ ...item, isFavorite }));
		// The viewer reads the heart from the photo's detail.
		for (const assetId of ids) {
			queryClient.setQueryData<AssetDetailResponse>(
				getAssetDetailQueryKey({ path: { assetId } }),
				(detail) => detail && { ...detail, isFavorite }
			);
		}
		invalidateLibrary();
	}

	archive(ids?: readonly string[]) {
		return this.#removing(ids, archiveAssets, unarchiveAssets, m.action_archived);
	}

	trash(ids?: readonly string[]) {
		return this.#removing(ids, deleteAssets, restoreAssets, m.action_trashed);
	}

	#removing(
		ids: readonly string[] | undefined,
		apply: (options: { body: { assetIds: string[] } }) => Promise<{ error?: unknown }>,
		revert: (options: { body: { assetIds: string[] } }) => Promise<{ error?: unknown }>,
		message: (inputs: { count: number }) => string
	) {
		const assetIds = this.#targets(ids);
		return this.#run(async () => {
			if (assetIds.length === 0) return;
			const { error } = await apply({ body: { assetIds } });
			if (error) throw error;
			this.#host.remove(assetIds);
			this.#selection.set(assetIds, false);
			invalidateLibrary();
			toasts.show(message({ count: assetIds.length }), {
				action: {
					label: m.action_undo(),
					run: async () => {
						const { error } = await revert({ body: { assetIds } });
						if (error) toasts.error(m.action_failed());
						else {
							invalidateLibrary();
							this.#host.reload();
							// Culling in the viewer: back to the photo it moved past.
							await returnToRestored(assetIds);
						}
					}
				}
			});
		});
	}

	addToAlbum(album: { id: string; name: string }, ids?: readonly string[]) {
		const assetIds = this.#targets(ids);
		return this.#run(async () => {
			const { error } = await addAssetsToAlbumBatch({
				path: { albumId: album.id },
				body: { assetIds }
			});
			if (error) throw error;
			this.#selection.clear();
			invalidateLibrary();
			toasts.show(m.action_added_to_album({ count: assetIds.length, album: album.name }));
		});
	}

	moveToFolder(
		folder: { id: string; name: string },
		organizeByCaptureYear: boolean,
		ids?: readonly string[]
	) {
		const assetIds = this.#targets(ids);
		return this.#run(async () => {
			const { data, error } = await moveFolderAssets({
				body: { sourceFolderId: null, targetFolderId: folder.id, assetIds, organizeByCaptureYear }
			});
			if (error) throw error;
			this.#selection.clear();
			announceAssetsMoved({
				assetIds,
				from: null,
				to: folder.id,
				exact: data?.moved === assetIds.length
			});
			invalidateLibrary();
			toasts.show(m.action_moved({ count: assetIds.length, folder: folder.name }));
		});
	}

	/** Opens the share dialog for `ids` (the viewer's photo) or the selection. */
	share(ids?: readonly string[]) {
		const assetIds = this.#targets(ids);
		if (assetIds.length === 0) return;
		this.#sharingSelection = !ids;
		this.sharing = assetIds;
	}

	closeShare() {
		this.sharing = null;
	}

	/** The link exists: a selection that was shared is done with. */
	shared() {
		if (this.#sharingSelection) this.#selection.clear();
	}

	download(ids?: readonly string[]) {
		return this.downloader.start(this.#targets(ids));
	}
}
