/**
 * What the timeline shows and in which order: every photo or those of a day
 * or a range of days, newest or oldest first. Applied on the client, over
 * the month skeleton and each month's items (the bucket model already has
 * the whole skeleton).
 */
export type TimelineOrder = 'newest' | 'oldest';

/** Inclusive days, `yyyy-MM-dd`; one day when both are the same. */
export interface DateRange {
	from: string;
	to: string;
}

export interface TimelineFilter {
	order: TimelineOrder;
	range: DateRange | null;
}

export const NO_FILTER: TimelineFilter = { order: 'newest', range: null };

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** A range from two days in any order; null if either isn't a day. */
export function dateRange(from: string, to: string): DateRange | null {
	if (!DAY.test(from) || !DAY.test(to)) return null;
	return from <= to ? { from, to } : { from: to, to: from };
}

/** The month (`yyyy-MM`) has days in the range. */
export function monthInRange(month: string, range: DateRange): boolean {
	return month >= range.from.slice(0, 7) && month <= range.to.slice(0, 7);
}

/** The year (`yyyy`) has days in the range. */
export function yearInRange(year: string, range: DateRange): boolean {
	return year >= range.from.slice(0, 4) && year <= range.to.slice(0, 4);
}

/** A capture instant (ISO, UTC like the months) falls on a day of the range. */
export function inRange(capturedAt: string, range: DateRange): boolean {
	const day = capturedAt.slice(0, 10);
	return day >= range.from && day <= range.to;
}

/** The items to show of a loaded month or year: in range, in order. */
export function arrangeItems<T extends { capturedAt: string }>(
	items: readonly T[],
	filter: TimelineFilter
): T[] {
	const kept = filter.range
		? items.filter((item) => inRange(item.capturedAt, filter.range!))
		: [...items];
	return filter.order === 'oldest' ? kept.reverse() : kept;
}
