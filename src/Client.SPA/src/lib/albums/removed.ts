import type { RemovedAlbumAssetResponse } from '#lib/api/index.js';

/**
 * Taking out this many photos or more at once asks first. Fewer just go,
 * with Undo, like the other actions that can be undone: a dialog every time
 * would be in the way, and "Quitadas recientemente" keeps the rest.
 */
export const CONFIRM_REMOVE_FROM = 20;

export function removalNeedsConfirm(count: number) {
	return count >= CONFIRM_REMOVE_FROM;
}

export interface RemovalGroup {
	key: string;
	removedAt: string;
	/** Null once that user's account is gone. */
	removedBy: string | null;
	items: RemovedAlbumAssetResponse[];
}

/**
 * The album's removals (the server lists them newest first) grouped by the
 * action that made them: one request stamps all its photos with the same
 * moment, so the same time and the same person is one removal.
 */
export function groupRemovals(items: readonly RemovedAlbumAssetResponse[]): RemovalGroup[] {
	const groups: RemovalGroup[] = [];
	for (const item of items) {
		const key = `${item.removedAt}|${item.removedByUserId ?? ''}`;
		const last = groups.at(-1);
		if (last?.key === key) last.items.push(item);
		else
			groups.push({
				key,
				removedAt: item.removedAt,
				removedBy: item.removedByUsername,
				items: [item]
			});
	}
	return groups;
}
