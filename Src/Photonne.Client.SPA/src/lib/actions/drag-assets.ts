import { addAssetsToAlbumBatch, moveFolderAssets } from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';

/** dataTransfer type carrying the dragged asset ids (JSON array). */
export const ASSETS_MIME = 'application/x-photonne-assets';

export function startAssetDrag(event: DragEvent, assetIds: readonly string[]) {
	if (!event.dataTransfer) return;
	event.dataTransfer.setData(ASSETS_MIME, JSON.stringify(assetIds));
	event.dataTransfer.effectAllowed = 'copyMove';
}

/** True while the drag carries photos, so a target can light up. */
export function isAssetDrag(event: DragEvent) {
	return event.dataTransfer?.types.includes(ASSETS_MIME) ?? false;
}

function draggedIds(event: DragEvent): string[] {
	try {
		const ids = JSON.parse(event.dataTransfer?.getData(ASSETS_MIME) ?? '[]');
		return Array.isArray(ids) ? ids.filter((id) => typeof id === 'string') : [];
	} catch {
		return [];
	}
}

export async function dropOnAlbum(event: DragEvent, album: { id: string; name: string }) {
	const assetIds = draggedIds(event);
	if (assetIds.length === 0) return;
	const { error } = await addAssetsToAlbumBatch({
		path: { albumId: album.id },
		body: { assetIds }
	});
	if (error) toasts.error(m.action_failed());
	else toasts.show(m.action_added_to_album({ count: assetIds.length, album: album.name }));
}

export async function dropOnFolder(event: DragEvent, folder: { id: string; name: string }) {
	const assetIds = draggedIds(event);
	if (assetIds.length === 0) return;
	const { error } = await moveFolderAssets({
		body: {
			sourceFolderId: null,
			targetFolderId: folder.id,
			assetIds,
			organizeByCaptureYear: false
		}
	});
	if (error) toasts.error(m.action_failed());
	else toasts.show(m.action_moved({ count: assetIds.length, folder: folder.name }));
}
