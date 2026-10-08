import type { GridSection } from './grid-model.js';
import type { GridHost } from './grid-host.js';
import type { GridAsset } from './types.js';

/**
 * A GridHost over a list that is already loaded (or loaded page by page):
 * favorites, archive, an album, a folder, search results… Items are shown
 * grouped by capture month, or as one section in their own order (an album
 * keeps its manual order).
 */
export class ListStore implements GridHost {
	items = $state<GridAsset[]>([]);
	#grouping: 'month' | 'none';
	#reload: () => void;

	constructor(options: { grouping: 'month' | 'none'; reload: () => void }) {
		this.#grouping = options.grouping;
		this.#reload = options.reload;
	}

	sections: GridSection<GridAsset>[] = $derived.by(() => {
		if (this.#grouping === 'none') {
			return this.items.length ? [{ key: 'all', count: this.items.length, items: this.items }] : [];
		}
		return groupByMonth(this.items);
	});

	byId = $derived(Object.fromEntries(this.items.map((item) => [item.id, item])));
	order = $derived(this.items.map((item) => item.id));

	itemsByIds(ids: Iterable<string>) {
		return [...ids].flatMap((id) => (this.byId[id] ? [this.byId[id]] : []));
	}

	remove(ids: readonly string[]) {
		const gone = Object.fromEntries(ids.map((id) => [id, true]));
		this.items = this.items.filter((item) => !gone[item.id]);
	}

	update(ids: readonly string[], change: (item: GridAsset) => GridAsset) {
		const targets = Object.fromEntries(ids.map((id) => [id, true]));
		this.items = this.items.map((item) => (targets[item.id] ? change(item) : item));
	}

	reload() {
		this.#reload();
	}
}

/** Consecutive items of the same `yyyy-MM` (items come newest first). */
export function groupByMonth(items: readonly GridAsset[]): GridSection<GridAsset>[] {
	const sections: GridSection<GridAsset>[] = [];
	for (const item of items) {
		const key = item.capturedAt.slice(0, 7);
		const last = sections.at(-1);
		if (last?.key === key) (last.items as GridAsset[]).push(item);
		else sections.push({ key, count: 0, items: [item] });
	}
	for (const section of sections) section.count = section.items!.length;
	return sections;
}
