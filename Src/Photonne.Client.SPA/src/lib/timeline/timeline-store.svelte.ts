import type { QueryClient } from '@tanstack/svelte-query';
import { SvelteMap, SvelteSet } from 'svelte/reactivity';
import type { TimelineBucketResponse } from '#lib/api/index.js';
import {
	getTimelineBucketItemsOptions,
	getTimelineBucketsOptions
} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import type { GridSection } from './grid-model.js';
import { indexById, withChangedItems, withoutItems } from './collection-ops.js';
import type { GridHost } from './grid-host.js';
import { toGridAsset, type GridAsset } from './types.js';

/**
 * The timeline's data on the bucket model: the month skeleton (counts) from
 * one query, and each month's items fetched on demand as it nears the
 * viewport. Implements GridHost so batch actions can edit it in place.
 */
export class TimelineStore implements GridHost {
	#months = new SvelteMap<string, GridAsset[]>();
	#requested = new SvelteSet<string>();
	#queryClient: QueryClient;
	#buckets: () => readonly TimelineBucketResponse[] | undefined;

	sections: GridSection<GridAsset>[] = $derived.by(() =>
		(this.#buckets() ?? []).map((bucket) => ({
			key: bucket.key,
			count: bucket.count,
			items: this.#months.get(bucket.key) ?? null
		}))
	);

	byId = $derived(indexById(this.sections));

	/** Loaded ids in display order. */
	order = $derived(this.sections.flatMap((s) => s.items?.map((item) => item.id) ?? []));

	constructor(
		queryClient: QueryClient,
		buckets: () => readonly TimelineBucketResponse[] | undefined
	) {
		this.#queryClient = queryClient;
		this.#buckets = buckets;
	}

	async load(key: string) {
		if (this.#requested.has(key)) return;
		this.#requested.add(key);
		try {
			const items = await this.#queryClient.fetchQuery(
				getTimelineBucketItemsOptions({ path: { yearMonth: key } })
			);
			this.#months.set(key, items.map(toGridAsset));
		} catch {
			// Retried the next time the month scrolls into reach.
			this.#requested.delete(key);
		}
	}

	/** The first month after the one holding `assetId` that isn't loaded yet. */
	nextUnloadedAfter(assetId: string) {
		const index = this.sections.findIndex((s) => s.items?.some((item) => item.id === assetId));
		if (index < 0) return null;
		return this.sections.slice(index + 1).find((s) => s.items === null)?.key ?? null;
	}

	itemsByIds(ids: Iterable<string>) {
		const found: GridAsset[] = [];
		for (const id of ids) {
			const item = this.byId.get(id);
			if (item) found.push(item);
		}
		return found;
	}

	remove(ids: readonly string[]) {
		const changed = withoutItems(this.#months, ids);
		for (const { key, items } of changed) this.#months.set(key, items);
		const removed = Object.fromEntries(changed.map((c) => [c.key, c.removed]));
		// Keep the skeleton's counts in step, so months that empty out vanish.
		this.#queryClient.setQueryData<TimelineBucketResponse[]>(
			getTimelineBucketsOptions().queryKey,
			(buckets) =>
				buckets
					?.map((b) => ({ ...b, count: b.count - (removed[b.key] ?? 0) }))
					.filter((b) => b.count > 0)
		);
	}

	update(ids: readonly string[], change: (item: GridAsset) => GridAsset) {
		for (const { key, items } of withChangedItems(this.#months, ids, change)) {
			this.#months.set(key, items);
		}
	}

	/** Drops everything loaded and refetches the skeleton. */
	reload() {
		this.#months.clear();
		this.#requested.clear();
		this.#queryClient.invalidateQueries({ queryKey: getTimelineBucketsOptions().queryKey });
	}
}
