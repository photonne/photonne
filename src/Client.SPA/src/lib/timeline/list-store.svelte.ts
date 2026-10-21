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
	#removed: ((ids: readonly string[]) => void)[] = [];

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
		for (const listener of this.#removed) listener(ids);
	}

	/** Runs after items are taken out (so a selection can drop them). */
	onremove(listener: (ids: readonly string[]) => void) {
		this.#removed.push(listener);
		return () => {
			this.#removed = this.#removed.filter((other) => other !== listener);
		};
	}

	update(ids: readonly string[], change: (item: GridAsset) => GridAsset) {
		const targets = Object.fromEntries(ids.map((id) => [id, true]));
		this.items = this.items.map((item) => (targets[item.id] ? change(item) : item));
	}

	reload() {
		this.#reload();
	}
}

/**
 * Consecutive items of the same `yyyy-MM` (items come newest first). A list
 * in another order (the trash, by deletion) can come back to a month after
 * another: that run is a section of its own, keyed `yyyy-MM~2`, `~3`… so
 * every key stays unique. The first seven characters are always the month.
 */
export function groupByMonth(items: readonly GridAsset[]): GridSection<GridAsset>[] {
	const sections: GridSection<GridAsset>[] = [];
	const runs: Record<string, number> = {};
	for (const item of items) {
		const month = item.capturedAt.slice(0, 7);
		const last = sections.at(-1);
		if (last?.key.slice(0, 7) === month) {
			(last.items as GridAsset[]).push(item);
			continue;
		}
		const run = (runs[month] ?? 0) + 1;
		runs[month] = run;
		sections.push({ key: run === 1 ? month : `${month}~${run}`, count: 0, items: [item] });
	}
	for (const section of sections) section.count = section.items!.length;
	return sections;
}
