import type { TimelineResponse } from '#lib/api/index.js';
import { ListStore } from './list-store.svelte.js';
import { toGridAsset } from './types.js';

export interface Page {
	items: TimelineResponse[];
	hasMore: boolean;
	nextCursor?: string | null;
}

/**
 * A ListStore filled page by page from a cursor endpoint (favorites, archive,
 * trash…): the first page on start, the next when the grid nears its end.
 */
export class PagedList {
	store: ListStore;
	status = $state<'pending' | 'error' | 'ready'>('pending');
	#hasMore = false;
	#cursor: string | null = null;
	#loading = false;
	#fetch: (cursor: string | null) => Promise<Page | undefined>;

	constructor(
		fetch: (cursor: string | null) => Promise<Page | undefined>,
		grouping: 'month' | 'none' = 'month'
	) {
		this.#fetch = fetch;
		this.store = new ListStore({ grouping, reload: () => this.start() });
	}

	async start() {
		this.#cursor = null;
		this.#hasMore = false;
		this.status = 'pending';
		const page = await this.#load();
		if (!page) {
			this.status = 'error';
			return;
		}
		this.store.items = page.items.map(toGridAsset);
		this.status = 'ready';
	}

	async more() {
		if (!this.#hasMore || this.#loading) return;
		const page = await this.#load();
		if (page) this.store.items = [...this.store.items, ...page.items.map(toGridAsset)];
	}

	async #load() {
		this.#loading = true;
		try {
			const page = await this.#fetch(this.#cursor);
			if (page) {
				this.#hasMore = page.hasMore;
				this.#cursor = page.nextCursor ?? null;
			}
			return page;
		} finally {
			this.#loading = false;
		}
	}
}
