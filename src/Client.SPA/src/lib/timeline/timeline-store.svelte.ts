import type { QueryClient } from '@tanstack/svelte-query';
import { SvelteMap, SvelteSet } from 'svelte/reactivity';
import type { TimelineBucketResponse } from '#lib/api/index.js';
import {
	getTimelineBucketItemsOptions,
	getTimelineBucketItemsQueryKey,
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

	// The request of each month in flight or done, so a caller that needs the
	// items (jumping into a month) can wait for the one already running.
	#loading: Record<string, Promise<void>> = {};
	// Bumped by reload(), so a month fetched before it doesn't land after it.
	#generation = 0;

	load(key: string): Promise<void> {
		if (this.#requested.has(key)) return this.#loading[key] ?? Promise.resolve();
		this.#requested.add(key);
		const loading = this.#fetch(key);
		this.#loading[key] = loading;
		return loading;
	}

	async #fetch(key: string) {
		const generation = this.#generation;
		try {
			const items = await this.#queryClient.fetchQuery(
				getTimelineBucketItemsOptions({ path: { yearMonth: key } })
			);
			if (generation === this.#generation) this.#months.set(key, items.map(toGridAsset));
		} catch {
			if (generation !== this.#generation) return;
			// Retried the next time the month scrolls into reach.
			this.#requested.delete(key);
			delete this.#loading[key];
		}
	}

	/**
	 * The cached items of these months no longer match what is shown: the
	 * next visit (or reload) fetches them again instead of reusing them.
	 */
	#staleMonths(keys: Iterable<string>) {
		for (const yearMonth of keys) {
			this.#queryClient.invalidateQueries({
				queryKey: getTimelineBucketItemsQueryKey({ path: { yearMonth } }),
				refetchType: 'none'
			});
		}
	}

	/** The first month after the one holding `assetId` that isn't loaded yet. */
	nextUnloadedAfter(assetId: string) {
		const index = this.sections.findIndex((s) => s.items?.some((item) => item.id === assetId));
		if (index < 0) return null;
		return this.sections.slice(index + 1).find((s) => s.items === null)?.key ?? null;
	}

	/** The nearest month before the one holding `assetId` that isn't loaded yet. */
	previousUnloadedBefore(assetId: string) {
		const index = this.sections.findIndex((s) => s.items?.some((item) => item.id === assetId));
		if (index < 0) return null;
		return this.sections.slice(0, index).findLast((s) => s.items === null)?.key ?? null;
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
		this.#staleMonths(changed.map((c) => c.key));
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
		const changed = withChangedItems(this.#months, ids, change);
		for (const { key, items } of changed) this.#months.set(key, items);
		this.#staleMonths(changed.map((c) => c.key));
	}

	/** Drops everything loaded and refetches the skeleton and each month. */
	reload() {
		this.#generation++;
		this.#months.clear();
		this.#requested.clear();
		this.#loading = {};
		this.#queryClient.invalidateQueries({
			queryKey: [{ _id: 'getTimelineBucketItems' }],
			refetchType: 'none'
		});
		this.#queryClient.invalidateQueries({ queryKey: getTimelineBucketsOptions().queryKey });
	}
}
