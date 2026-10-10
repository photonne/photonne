/**
 * Timeline zoom levels, from the most zoomed out (a few sampled rows per
 * year) to the largest thumbnails. Like the native app's TimelineZoomLevel,
 * the order is the order + and - step through, so row heights only grow.
 */
export const zoomLevels = ['year', 'small', 'medium', 'large', 'xlarge'] as const;
export type ZoomLevel = (typeof zoomLevels)[number];

/** What a level's headers are: years (sampled), months, or months split into days. */
export type ZoomGrouping = 'year' | 'month' | 'day';

/** The grid as it always was: month headers, medium rows. */
export const DEFAULT_ZOOM: ZoomLevel = 'medium';

// Target row height per level for a narrow (<600px), medium (<1100px) and
// wide grid. `medium` keeps the heights the grid had before zoom existed.
const ROW_HEIGHTS: Record<ZoomLevel, [number, number, number]> = {
	year: [48, 56, 64],
	small: [84, 104, 124],
	medium: [120, 170, 210],
	large: [170, 240, 290],
	xlarge: [240, 330, 400]
};

export function groupingOf(level: ZoomLevel): ZoomGrouping {
	if (level === 'year') return 'year';
	// Day headers only where rows are big enough for a day to read as a group;
	// at small sizes they would cut every row short.
	return level === 'large' || level === 'xlarge' ? 'day' : 'month';
}

export function rowHeightFor(level: ZoomLevel, containerWidth: number): number {
	const [narrow, medium, wide] = ROW_HEIGHTS[level];
	return containerWidth < 600 ? narrow : containerWidth < 1100 ? medium : wide;
}

/** The next level: +1 zooms in (bigger thumbnails), -1 out. Stops at both ends. */
export function stepZoom(level: ZoomLevel, direction: 1 | -1): ZoomLevel {
	const index = zoomLevels.indexOf(level) + direction;
	return zoomLevels[Math.min(zoomLevels.length - 1, Math.max(0, index))];
}

export function parseZoom(raw: string | null | undefined): ZoomLevel {
	return zoomLevels.includes(raw as ZoomLevel) ? (raw as ZoomLevel) : DEFAULT_ZOOM;
}

/** The server never samples more than this per year (TimelineYearsEndpoint.MaxSample). */
export const MAX_YEAR_SAMPLE = 100;
/** How many rows of samples each year shows. */
export const YEAR_ROWS = 3;

/**
 * Photos per year to ask the server for, enough to fill YEAR_ROWS rows at
 * this width. Rounded up to a multiple of 12 so resizing the window doesn't
 * refetch on every pixel.
 */
export function yearSampleSize(containerWidth: number, assumedAspect = 1.5): number {
	const rowHeight = rowHeightFor('year', containerWidth);
	const perRow = Math.max(1, Math.ceil(containerWidth / (rowHeight * assumedAspect)));
	const wanted = Math.ceil((perRow * YEAR_ROWS) / 12) * 12;
	return Math.min(MAX_YEAR_SAMPLE, Math.max(12, wanted));
}

/**
 * Accumulates Ctrl + wheel deltas into zoom steps. A trackpad pinch sends a
 * stream of small deltas and a mouse wheel a few big ones; both should step
 * once per deliberate gesture, not once per event.
 */
export class WheelZoom {
	#total = 0;
	#lastStep = Number.NEGATIVE_INFINITY;
	#threshold: number;
	#cooldownMs: number;

	constructor(threshold = 60, cooldownMs = 220) {
		this.#threshold = threshold;
		this.#cooldownMs = cooldownMs;
	}

	/** A wheel delta (positive = scroll down = zoom out); returns a step or 0. */
	push(deltaY: number, now: number): 1 | -1 | 0 {
		if (now - this.#lastStep < this.#cooldownMs) return 0;
		// A change of direction starts over.
		if (Math.sign(deltaY) !== Math.sign(this.#total)) this.#total = 0;
		this.#total += deltaY;
		if (Math.abs(this.#total) < this.#threshold) return 0;
		const step = this.#total > 0 ? -1 : 1;
		this.#total = 0;
		this.#lastStep = now;
		return step;
	}
}
