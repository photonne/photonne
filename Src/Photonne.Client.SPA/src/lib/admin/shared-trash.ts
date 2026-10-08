import type { SharedTrashItemResponse } from '#lib/api/index.js';
import type { GridAsset } from '#lib/timeline/types.js';

/** A shared-trash item as a grid cell, dated (and grouped) by when it was deleted. */
export function sharedTrashAsset(item: SharedTrashItemResponse): GridAsset {
	return {
		id: item.id,
		aspect: item.width && item.height ? item.width / item.height : null,
		fileName: item.fileName,
		capturedAt: item.deletedAt ?? '1970-01-01T00:00:00Z',
		isVideo: item.type === 'Video',
		isLivePhoto: false,
		isFavorite: false,
		dominantColor: null,
		thumbnailVersion: null
	};
}

/** Who deleted what, most active first, for the filter chips. */
export function deleters(items: readonly SharedTrashItemResponse[]) {
	const counts = new Map<string, number>();
	for (const item of items) {
		const who = item.deletedByUsername ?? '';
		counts.set(who, (counts.get(who) ?? 0) + 1);
	}
	return [...counts]
		.map(([name, count]) => ({ name, count }))
		.sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
}
