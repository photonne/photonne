import { getLocale } from '#lib/paraglide/runtime.js';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "hace 5 minutos" / "5 minutes ago" for a past instant. */
export function timeAgo(iso: string, now = Date.now()) {
	const elapsed = Math.max(0, now - Date.parse(iso));
	const format = new Intl.RelativeTimeFormat(getLocale(), { numeric: 'auto' });
	if (elapsed < MINUTE) return format.format(0, 'second');
	if (elapsed < HOUR) return format.format(-Math.floor(elapsed / MINUTE), 'minute');
	if (elapsed < DAY) return format.format(-Math.floor(elapsed / HOUR), 'hour');
	return format.format(-Math.floor(elapsed / DAY), 'day');
}

/** "1 h 05 min", "4 min 12 s", "12 s": how long something took. */
export function duration(ms: number) {
	const seconds = Math.max(0, Math.round(ms / 1000));
	const h = Math.floor(seconds / 3600);
	const min = Math.floor((seconds % 3600) / 60);
	const s = seconds % 60;
	if (h > 0) return `${h} h ${String(min).padStart(2, '0')} min`;
	if (min > 0) return `${min} min ${String(s).padStart(2, '0')} s`;
	return `${s} s`;
}
