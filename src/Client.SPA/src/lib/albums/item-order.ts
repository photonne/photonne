/** How an album's photos are shown: its own (manual) order, or by capture date. */
export type ItemOrder = 'album' | 'newest' | 'oldest';

/**
 * `items` (in the album's own order) re-sorted for display. Capture date ties
 * keep the album order, so switching back and forth is stable.
 */
export function orderItems<T>(
	items: readonly T[],
	order: ItemOrder,
	dateOf: (item: T) => string
): T[] {
	if (order === 'album') return [...items];
	const sign = order === 'newest' ? -1 : 1;
	return items
		.map((item, index) => ({ item, index, time: Date.parse(dateOf(item)) }))
		.sort((a, b) => sign * (a.time - b.time) || a.index - b.index)
		.map(({ item }) => item);
}
