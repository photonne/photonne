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

/** "septiembre de 2026" for the bucket key "2026-09". */
export function monthTitle(key: string) {
	const [year, month] = key.split('-').map(Number);
	return formatter({ month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
		new Date(Date.UTC(year, month - 1, 1))
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
