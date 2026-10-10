/** Keeps a pane width within its bounds, rounded to whole pixels. */
export function clampWidth(width: number, min: number, max: number) {
	return Math.round(Math.min(max, Math.max(min, width)));
}

/** The saved width, or the default when none (or storage is unavailable). */
export function savedWidth(key: string, fallback: number, min: number, max: number) {
	try {
		const value = Number(localStorage.getItem(key));
		return value > 0 ? clampWidth(value, min, max) : fallback;
	} catch {
		return fallback;
	}
}

export function saveWidth(key: string, width: number) {
	try {
		localStorage.setItem(key, String(width));
	} catch {
		// Kept for this page only.
	}
}
