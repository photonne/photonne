import { getLocale } from '#lib/paraglide/runtime.js';

/**
 * "3–9 sept 2024" / "28 ago – 2 sept 2024" / "3 sept 2024" for a memory's
 * window. Like capture times, windows are UTC wall clock, so shown in UTC; the
 * end is exclusive when it falls on midnight (a window of whole days).
 */
export function dateRange(startIso: string, endIso: string) {
	const format = new Intl.DateTimeFormat(getLocale(), {
		day: 'numeric',
		month: 'short',
		year: 'numeric',
		timeZone: 'UTC'
	});
	const start = new Date(startIso);
	let end = new Date(endIso);
	if (end.getTime() > start.getTime() && end.toISOString().endsWith('T00:00:00.000Z')) {
		end = new Date(end.getTime() - 1);
	}
	if (end.getTime() <= start.getTime()) return format.format(start);
	return format.formatRange(start, end);
}
