/** Where a story (a memory played as a slideshow) is: which photo, how far into it. */
export interface StoryPosition {
	index: number;
	/** 0–1 through the current photo. */
	progress: number;
	/** Played to the end of the last photo. */
	finished: boolean;
}

/** How long a photo stays on screen. */
export const PHOTO_MS = 5000;

/**
 * Moves the story on by `deltaMs`. A long gap (a background tab, a debugger)
 * is capped, so coming back never skips photos.
 */
export function tick(position: StoryPosition, deltaMs: number, count: number): StoryPosition {
	if (position.finished || count === 0) return position;
	const progress = position.progress + Math.min(deltaMs, 250) / PHOTO_MS;
	if (progress < 1) return { ...position, progress };
	return position.index < count - 1
		? { index: position.index + 1, progress: 0, finished: false }
		: { index: position.index, progress: 1, finished: true };
}

/** To photo `index` (clamped), from its start. */
export function goTo(index: number, count: number): StoryPosition {
	return { index: Math.max(0, Math.min(count - 1, index)), progress: 0, finished: false };
}

/** How far each segment of the progress bar is filled (done, current, to come). */
export function segmentFill(segment: number, position: StoryPosition) {
	if (segment < position.index) return 1;
	if (segment > position.index) return 0;
	return position.progress;
}
