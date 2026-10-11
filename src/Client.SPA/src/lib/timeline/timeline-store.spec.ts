import { QueryClient } from '@tanstack/svelte-query';
import { describe, expect, it } from 'vitest';
import type { TimelineResponse } from '#lib/api/index.js';
import { TimelineStore } from './timeline-store.svelte.js';

const asset = (id: string) =>
	({
		id,
		fileName: `${id}.jpg`,
		fileCreatedAt: '2026-09-01T10:00:00Z',
		type: 'Image',
		tags: [],
		isFavorite: false
	}) as unknown as TimelineResponse;

function storeWith(months: Record<string, string[]>) {
	const client = new QueryClient();
	client.fetchQuery = (async (options: { queryKey: [{ path: { yearMonth: string } }] }) =>
		months[options.queryKey[0].path.yearMonth].map(asset)) as unknown as QueryClient['fetchQuery'];
	const buckets = Object.entries(months).map(([key, ids]) => ({ key, count: ids.length }));
	return new TimelineStore(client, () => buckets);
}

describe('TimelineStore', () => {
	it('finds the nearest unloaded month on either side of a photo', async () => {
		const store = storeWith({ '2026-09': ['a'], '2026-08': ['b'], '2026-07': ['c'] });
		await store.load('2026-08');

		expect(store.previousUnloadedBefore('b')).toBe('2026-09');
		expect(store.nextUnloadedAfter('b')).toBe('2026-07');

		await store.load('2026-09');
		expect(store.previousUnloadedBefore('b')).toBeNull();
	});
});
