/** Seconds per photo the slideshow offers (the native app's three). */
export const SLIDESHOW_INTERVALS = [3, 5, 10] as const;

export type SlideshowInterval = (typeof SLIDESHOW_INTERVALS)[number];

const DEFAULT_INTERVAL: SlideshowInterval = 5;

/** A remembered interval, or the default for anything else. */
export function parseInterval(value: string | number | null | undefined): SlideshowInterval {
	const seconds = Number(value);
	return (SLIDESHOW_INTERVALS as readonly number[]).includes(seconds)
		? (seconds as SlideshowInterval)
		: DEFAULT_INTERVAL;
}

export type SlideshowCommand = 'toggle' | 'next' | 'previous' | 'exit';

/** What a key does while the slideshow runs; null leaves it alone. */
export function slideshowCommand(key: string): SlideshowCommand | null {
	switch (key) {
		case ' ':
		case 'Spacebar':
		case 'k':
			return 'toggle';
		case 'ArrowRight':
		case 'PageDown':
			return 'next';
		case 'ArrowLeft':
		case 'PageUp':
			return 'previous';
		case 'Escape':
			return 'exit';
		default:
			return null;
	}
}

/**
 * When the slideshow moves on by itself: after the interval for a photo; a
 * video plays to its end instead (its `ended` event moves on). At the last
 * photo it waits.
 */
export function autoAdvanceDelay(options: {
	running: boolean;
	isVideo: boolean;
	hasNext: boolean;
	interval: SlideshowInterval;
}) {
	if (!options.running || options.isVideo || !options.hasNext) return null;
	return options.interval * 1000;
}
