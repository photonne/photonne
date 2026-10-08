import type { SharedTrashItemResponse, TimelineResponse } from '#lib/api/index.js';
import type { Page } from '#lib/timeline/paged-list.svelte.js';

/**
 * PagedList speaks cursors; search pages by offset. The offset of the next
 * page travels as the cursor.
 */
export async function offsetPage(
	cursor: string | null,
	pageSize: number,
	fetch: (offset: number) => Promise<{ items: TimelineResponse[]; hasMore: boolean } | undefined>
): Promise<Page | undefined> {
	const offset = cursor ? Number(cursor) : 0;
	const page = await fetch(offset);
	if (!page) return undefined;
	return {
		items: page.items,
		hasMore: page.hasMore && page.items.length > 0,
		nextCursor: String(offset + Math.min(page.items.length, pageSize))
	};
}

/**
 * The shared trash lists a slimmer shape (who deleted it and from where, no
 * capture date). For the grid it becomes a timeline item dated by its
 * deletion, which is also the order it comes in.
 */
export function sharedTrashToTimeline(item: SharedTrashItemResponse): TimelineResponse {
	const date = item.deletedAt ?? new Date(0).toISOString();
	return {
		id: item.id,
		fileName: item.fileName,
		fullPath: item.fullPath,
		fileSize: item.fileSize,
		fileCreatedAt: date,
		fileModifiedAt: date,
		extension: item.extension,
		scannedAt: date,
		type: item.type,
		checksum: '',
		hasExif: false,
		hasThumbnails: item.hasThumbnails,
		syncStatus: 0,
		width: item.width,
		height: item.height,
		deletedAt: item.deletedAt,
		tags: [],
		isFavorite: false,
		isArchived: false,
		isFileMissing: false,
		dominantColor: null,
		thumbnailsGeneratedAt: null,
		aspectRatio: item.width && item.height ? item.width / item.height : null,
		isReadOnly: false
	};
}
