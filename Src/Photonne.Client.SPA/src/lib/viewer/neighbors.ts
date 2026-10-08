/** Previous and next id around `id` in a display order, or null at the ends. */
export function neighborsIn(order: readonly string[], id: string) {
	const index = order.indexOf(id);
	if (index < 0) return { previous: null, next: null };
	return {
		previous: index > 0 ? order[index - 1] : null,
		next: index < order.length - 1 ? order[index + 1] : null
	};
}
