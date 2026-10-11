import type { TimelineResponse } from '#lib/api/index.js';
import { ListStore } from './list-store.svelte.js';
import { toGridAsset, type GridAsset } from './types.js';

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
	/** Pages shown, so a refresh brings back as many as there were. */
	#pages = 0;
	#loading = false;
	// Bumped by every start(), so a page fetched before it doesn't land after it.
	#generation = 0;
	#fetch: (cursor: string | null) => Promise<Page | undefined>;

	constructor(
		fetch: (cursor: string | null) => Promise<Page | undefined>,
		grouping: 'month' | 'none' = 'month'
	) {
		this.#fetch = fetch;
		this.store = new ListStore({ grouping, reload: () => this.start() });
	}

	/**
	 * Loads the first page. Once the list is shown, it refetches as many pages
	 * as were loaded instead, and swaps them in at once: the grid stays mounted
	 * (scroll, focus, the viewer's neighbours) while it refreshes.
	 */
	async start() {
		const generation = ++this.#generation;
		const refreshing = this.status === 'ready';
		if (!refreshing) this.status = 'pending';
		const wanted = refreshing ? Math.max(1, this.#pages) : 1;
		this.#loading = true;
		try {
			const items: GridAsset[] = [];
			let cursor: string | null = null;
			let hasMore = false;
			let pages = 0;
			while (pages < wanted) {
				const page = await this.#fetch(cursor);
				if (generation !== this.#generation) return;
				if (!page) {
					// A failed refresh keeps what is on screen.
					if (!refreshing) this.status = 'error';
					return;
				}
				items.push(...page.items.map(toGridAsset));
				hasMore = page.hasMore;
				cursor = page.nextCursor ?? null;
				pages++;
				if (!hasMore) break;
			}
			this.store.items = items;
			this.#cursor = cursor;
			this.#hasMore = hasMore;
			this.#pages = pages;
			this.status = 'ready';
		} finally {
			if (generation === this.#generation) this.#loading = false;
		}
	}

	async more() {
		if (!this.#hasMore || this.#loading) return;
		const generation = this.#generation;
		this.#loading = true;
		try {
			const page = await this.#fetch(this.#cursor);
			// A start() since then replaced the list: this page belongs to the old one.
			if (!page || generation !== this.#generation) return;
			this.#hasMore = page.hasMore;
			this.#cursor = page.nextCursor ?? null;
			this.#pages++;
			this.store.items = [...this.store.items, ...page.items.map(toGridAsset)];
		} finally {
			if (generation === this.#generation) this.#loading = false;
		}
	}
}
