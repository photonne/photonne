import { getLocale } from '#lib/paraglide/runtime.js';

/**
 * Formatting for server-side timestamps (last login, scans, backups…). Unlike
 * capture times, these are real instants, so they are shown in the viewer's
 * time zone.
 */

/** "1.234" in the user's language. */
export function count(value: number) {
	return new Intl.NumberFormat(getLocale()).format(value);
}

/** "42 %" (or "99,9 %") for a 0–1 share. */
export function percent(share: number, digits = 0) {
	return new Intl.NumberFormat(getLocale(), {
		style: 'percent',
		maximumFractionDigits: digits
	}).format(share);
}

/** "8 oct 2026, 14:05" in local time. */
export function localDateTime(iso: string) {
	return new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium', timeStyle: 'short' }).format(
		new Date(iso)
	);
}

/** "8 oct 2026" in local time. */
export function localDate(iso: string) {
	return new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium' }).format(new Date(iso));
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
	['year', 365 * 24 * 3600],
	['month', 30 * 24 * 3600],
	['week', 7 * 24 * 3600],
	['day', 24 * 3600],
	['hour', 3600],
	['minute', 60]
];

/** "hace 3 días" / "in 2 hours"; "ahora" under a minute. */
export function relativeTime(iso: string, now = Date.now()) {
	const seconds = (Date.parse(iso) - now) / 1000;
	const format = new Intl.RelativeTimeFormat(getLocale(), { numeric: 'auto' });
	for (const [unit, size] of UNITS) {
		if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit);
	}
	return format.format(0, 'second');
}

/** "sep 2026" for a growth month key `yyyy-MM`. */
export function shortMonth(key: string) {
	const [year, month] = key.split('-').map(Number);
	return new Intl.DateTimeFormat(getLocale(), {
		month: 'short',
		year: 'numeric',
		timeZone: 'UTC'
	}).format(new Date(Date.UTC(year, month - 1, 1)));
}

/** "1,2 k" style compact numbers for chart axes and tiles. */
export function compact(value: number) {
	return new Intl.NumberFormat(getLocale(), {
		notation: 'compact',
		maximumFractionDigits: 1
	}).format(value);
}
