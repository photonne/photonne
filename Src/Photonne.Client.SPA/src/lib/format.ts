import { getLocale } from '#lib/paraglide/runtime.js';

const cache = new Map<string, Intl.DateTimeFormat>();

function formatter(options: Intl.DateTimeFormatOptions) {
	const locale = getLocale();
	const key = locale + JSON.stringify(options);
	let format = cache.get(key);
	if (!format) {
		format = new Intl.DateTimeFormat(locale, options);
		cache.set(key, format);
	}
	return format;
}

/** "septiembre de 2026" for the bucket key "2026-09"; '' for any other key. */
export function monthTitle(key: string) {
	if (!/^\d{4}-\d{2}$/.test(key)) return '';
	const [year, month] = key.split('-').map(Number);
	return formatter({ month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
		new Date(Date.UTC(year, month - 1, 1))
	);
}

/** "jueves, 3 de septiembre" for the day key "2026-09-03"; '' for any other key. */
export function dayTitle(key: string) {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return '';
	const [year, month, day] = key.split('-').map(Number);
	return formatter({ weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(
		new Date(Date.UTC(year, month - 1, day))
	);
}

/**
 * "3 de septiembre de 2026". Capture times are stored as UTC instants of the
 * camera's wall clock, so they are shown in UTC to keep the day the photo says.
 */
export function longDate(iso: string) {
	return formatter({ day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
		new Date(iso)
	);
}

/** "3,2 MB" in the user's language. */
export function formatBytes(bytes: number) {
	const units = ['B', 'KB', 'MB', 'GB', 'TB'];
	let value = bytes;
	let unit = 0;
	while (value >= 1024 && unit < units.length - 1) {
		value /= 1024;
		unit++;
	}
	const digits = unit === 0 || value >= 100 ? 0 : 1;
	return `${new Intl.NumberFormat(getLocale(), { maximumFractionDigits: digits }).format(value)} ${units[unit]}`;
}

/** "3 sept 2026, 14:05" — the capture time as the camera recorded it (UTC wall clock). */
export function dateTime(iso: string) {
	return formatter({ dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(
		new Date(iso)
	);
}

/** "2026-09-03T14:05" for a datetime-local input, from a UTC wall-clock instant. */
export function toDateTimeLocal(iso: string) {
	return new Date(iso).toISOString().slice(0, 16);
}

/** The inverse of toDateTimeLocal: the input's wall clock as a UTC instant. */
export function fromDateTimeLocal(value: string) {
	return new Date(`${value}:00Z`).toISOString();
}

/** "202610081430" for file names, from the current local time. */
export function fileStamp(now = Date.now()) {
	const d = new Date(now);
	const pad = (n: number) => String(n).padStart(2, '0');
	return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}`;
}
