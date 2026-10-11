import { describe, expect, it } from 'vitest';
import type { TimelineResponse } from '#lib/api/index.js';
import { PagedList, type Page } from './paged-list.svelte.js';

const asset = (id: string) =>
	({
		id,
		fileName: `${id}.jpg`,
		fileCreatedAt: '2026-09-01T10:00:00Z',
		type: 'Image',
		tags: [],
		isFavorite: false
	}) as unknown as TimelineResponse;

/** Pages of two items over `ids`, the cursor being the next offset. */
function pages(ids: string[]) {
	return async (cursor: string | null): Promise<Page> => {
		const offset = Number(cursor ?? 0);
		const items = ids.slice(offset, offset + 2).map(asset);
		const next = offset + items.length;
		return { items, hasMore: next < ids.length, nextCursor: String(next) };
	};
}

describe('PagedList', () => {
	it('refreshes as many pages as were loaded without going back to pending', async () => {
		let ids = ['a', 'b', 'c', 'd', 'e'];
		const list = new PagedList((cursor) => pages(ids)(cursor));
		await list.start();
		await list.more();
		expect(list.store.items.map((i) => i.id)).toEqual(['a', 'b', 'c', 'd']);

		ids = ['a', 'c', 'd', 'e', 'f'];
		const refresh = list.start();
		expect(list.status).toBe('ready');
		await refresh;
		expect(list.store.items.map((i) => i.id)).toEqual(['a', 'c', 'd', 'e']);
	});

	it('drops a page that arrives after a refresh started', async () => {
		const ids = ['a', 'b', 'c', 'd'];
		let release: () => void = () => {};
		let hold = false;
		const list = new PagedList(async (cursor) => {
			if (hold) await new Promise<void>((resolve) => (release = resolve));
			return pages(ids)(cursor);
		});
		await list.start();

		hold = true;
		const more = list.more();
		const pendingRelease = release;
		hold = false;
		await list.start();
		pendingRelease();
		await more;

		expect(list.store.items.map((i) => i.id)).toEqual(['a', 'b']);
	});
});
