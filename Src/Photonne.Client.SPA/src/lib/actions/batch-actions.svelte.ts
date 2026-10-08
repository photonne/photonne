import {
	addAssetsToAlbumBatch,
	archiveAssets,
	deleteAssets,
	downloadAssetsZip,
	moveFolderAssets,
	restoreAssets,
	toggleFavorite,
	unarchiveAssets
} from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { fileStamp } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import type { GridHost } from '#lib/timeline/grid-host.js';
import type { Selection } from '#lib/timeline/selection.svelte.js';

/**
 * Actions on the selected photos, shared by every grid (see GridHost).
 * Removing actions (archive, trash) take the photos out of view at once and
 * offer Undo; one action runs at a time.
 */
export class BatchActions {
	busy = $state(false);
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

	async #run(action: () => Promise<void>) {
		if (this.busy) return;
		this.busy = true;
		try {
			await action();
		} catch {
			toasts.error(m.action_failed());
		} finally {
			this.busy = false;
		}
	}

	/**
	 * Marks the ones missing a heart; only when all have it, clears them all.
	 * The endpoint toggles one photo at a time, so only those that change are
	 * called.
	 */
	toggleFavorites(ids?: readonly string[]) {
		return this.#run(async () => {
			const items = this.#host.itemsByIds(this.#targets(ids));
			if (items.length === 0) return;
			const marking = items.some((item) => !item.isFavorite);
			const changing = items.filter((item) => item.isFavorite !== marking).map((item) => item.id);
			for (const id of changing) {
				const { error } = await toggleFavorite({ path: { assetId: id } });
				if (error) throw error;
			}
			this.#host.update(changing, (item) => ({ ...item, isFavorite: marking }));
			toasts.show(
				marking
					? m.action_favorited({ count: changing.length })
					: m.action_unfavorited({ count: changing.length })
			);
		});
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
		return this.#run(async () => {
			const assetIds = this.#targets(ids);
			if (assetIds.length === 0) return;
			const { error } = await apply({ body: { assetIds } });
			if (error) throw error;
			this.#host.remove(assetIds);
			this.#selection.set(assetIds, false);
			toasts.show(message({ count: assetIds.length }), {
				action: {
					label: m.action_undo(),
					run: async () => {
						const { error } = await revert({ body: { assetIds } });
						if (error) toasts.error(m.action_failed());
						else this.#host.reload();
					}
				}
			});
		});
	}

	addToAlbum(album: { id: string; name: string }, ids?: readonly string[]) {
		return this.#run(async () => {
			const assetIds = this.#targets(ids);
			const { error } = await addAssetsToAlbumBatch({
				path: { albumId: album.id },
				body: { assetIds }
			});
			if (error) throw error;
			this.#selection.clear();
			toasts.show(m.action_added_to_album({ count: assetIds.length, album: album.name }));
		});
	}

	moveToFolder(
		folder: { id: string; name: string },
		organizeByCaptureYear: boolean,
		ids?: readonly string[]
	) {
		return this.#run(async () => {
			const assetIds = this.#targets(ids);
			const { error } = await moveFolderAssets({
				body: { sourceFolderId: null, targetFolderId: folder.id, assetIds, organizeByCaptureYear }
			});
			if (error) throw error;
			this.#selection.clear();
			toasts.show(m.action_moved({ count: assetIds.length, folder: folder.name }));
		});
	}

	downloadZip(ids?: readonly string[]) {
		return this.#run(async () => {
			const assetIds = this.#targets(ids);
			toasts.show(m.action_zip_preparing());
			const { data, error } = await downloadAssetsZip({ body: { assetIds }, parseAs: 'blob' });
			if (error || !(data instanceof Blob)) throw error;
			saveBlob(data, `photonne-${fileStamp()}.zip`);
		});
	}
}

function saveBlob(blob: Blob, fileName: string) {
	const url = URL.createObjectURL(blob);
	const link = Object.assign(document.createElement('a'), { href: url, download: fileName });
	link.click();
	setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
