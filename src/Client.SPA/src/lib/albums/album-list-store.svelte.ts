import type { TimelineResponse } from '#lib/api/index.js';
import { ListStore } from '#lib/timeline/list-store.svelte.js';
import { toGridAsset } from '#lib/timeline/types.js';

/**
 * Loads a whole album (the endpoint doesn't page) as one section in its own
 * order. Unlike PagedList, a reload keeps the grid on screen instead of
 * going back to "loading".
 */
export class AlbumList {
	store: ListStore;
	status = $state<'pending' | 'error' | 'ready'>('pending');
	#fetch: () => Promise<TimelineResponse[] | undefined>;

	constructor(fetch: () => Promise<TimelineResponse[] | undefined>) {
		this.#fetch = fetch;
		this.store = new ListStore({ grouping: 'none', reload: () => this.start() });
	}

	async start() {
		const items = await this.#fetch();
		if (!items) {
			this.status = 'error';
			return;
		}
		this.store.items = items.map(toGridAsset);
		this.status = 'ready';
	}
}
