import type { GridItem, GridSection } from './grid-model.js';

/** Loaded items of the sections by id. */
export function indexById<T extends GridItem>(sections: readonly GridSection<T>[]) {
	return new Map(sections.flatMap((s) => s.items?.map((item) => [item.id, item] as const) ?? []));
}

/**
 * `months` without the items in `ids`: the months that changed, with their
 * remaining items and how many were taken out.
 */
export function withoutItems<T extends { id: string }>(
	months: Iterable<[string, readonly T[]]>,
	ids: readonly string[]
) {
	const gone = new Set(ids);
	const changed: { key: string; items: T[]; removed: number }[] = [];
	for (const [key, items] of months) {
		const kept = items.filter((item) => !gone.has(item.id));
		if (kept.length !== items.length)
			changed.push({ key, items: kept, removed: items.length - kept.length });
	}
	return changed;
}

/** The months holding any of `ids`, with `change` applied to those items. */
export function withChangedItems<T extends { id: string }>(
	months: Iterable<[string, readonly T[]]>,
	ids: readonly string[],
	change: (item: T) => T
) {
	const targets = new Set(ids);
	const changed: { key: string; items: T[] }[] = [];
	for (const [key, items] of months) {
		if (items.some((item) => targets.has(item.id))) {
			changed.push({
				key,
				items: items.map((item) => (targets.has(item.id) ? change(item) : item))
			});
		}
	}
	return changed;
}
