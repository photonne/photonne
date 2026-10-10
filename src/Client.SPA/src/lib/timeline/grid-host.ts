import type { GridAsset } from './types.js';

/**
 * What batch actions need from whatever grid they act on (timeline, album,
 * folder, favorites, search…), so they are written once for all of them.
 */
export interface GridHost {
	/** The loaded items among `ids`. */
	itemsByIds(ids: Iterable<string>): GridAsset[];
	/** Takes items out of view (archived, trashed). */
	remove(ids: readonly string[]): void;
	update(ids: readonly string[], change: (item: GridAsset) => GridAsset): void;
	/** Brings back what an undo restored. */
	reload(): void;
}
