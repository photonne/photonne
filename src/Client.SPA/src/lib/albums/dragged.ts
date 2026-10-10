import { ASSETS_MIME } from '#lib/actions/drag-assets.js';

/** dataTransfer type carrying a dragged folder's id (tree → tree). */
export const FOLDER_MIME = 'application/x-photonne-folder';

/** Asset ids carried by a photo drag (see startAssetDrag), or none. */
export function draggedAssetIds(data: Pick<DataTransfer, 'getData'> | null): string[] {
	try {
		const ids: unknown = JSON.parse(data?.getData(ASSETS_MIME) || '[]');
		return Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [];
	} catch {
		return [];
	}
}

export function isFolderDrag(event: DragEvent) {
	return event.dataTransfer?.types.includes(FOLDER_MIME) ?? false;
}
