/**
 * Thumbnail size, from 0 (the smallest) to 100 (the largest), and, for the
 * timeline, how photos are grouped. The two are independent: a slider sets
 * the size and a menu the grouping.
 */
export const MIN_SIZE = 0;
export const MAX_SIZE = 100;
/**
 * Five sizes, as the native app's zoom levels: the slider stops on them, and
 * +, − and Ctrl + wheel (or a pinch) go from one to the next.
 */
export const SIZE_STEP = 25;
export const SIZE_LEVELS = [0, 25, 50, 75, 100] as const;
/** One step of Ctrl + wheel: a whole level, like + and −. */
export const WHEEL_STEP = SIZE_STEP;
/** The grid as it always was: medium rows. */
export const DEFAULT_SIZE = 50;

/** What the headers are: years (sampled), months, or months split into days. */
export const groupings = ['year', 'month', 'day'] as const;
export type Grouping = (typeof groupings)[number];
export const DEFAULT_GROUPING: Grouping = 'month';

// Row heights at evenly spaced sizes (0, 25, 50, 75, 100) for a narrow
// (<600px), medium (<1100px) and wide grid; sizes in between interpolate.
// 50 keeps the heights the grid had before zoom existed, and 25, 75 and 100
// the old small, large and extra large levels.
const ROW_HEIGHTS: [number, number, number][] = [
	[60, 72, 84],
	[84, 104, 124],
	[120, 170, 210],
	[170, 240, 290],
	[240, 330, 400]
];

// The year view's sampled rows at the default size; they scale with it.
const YEAR_ROW_HEIGHTS: [number, number, number] = [48, 56, 64];
const MIN_YEAR_ROW_HEIGHT = 32;

/** Within the range, on the nearest of the five levels. */
export function clampSize(size: number): number {
	const clamped = Math.min(MAX_SIZE, Math.max(MIN_SIZE, size));
	return Math.round(clamped / SIZE_STEP) * SIZE_STEP;
}

/** Which of the five levels a size is, 0 (the smallest) to 4. */
export function sizeLevel(size: number): number {
	return clampSize(size) / SIZE_STEP;
}

function bucket(containerWidth: number): 0 | 1 | 2 {
	return containerWidth < 600 ? 0 : containerWidth < 1100 ? 1 : 2;
}

/**
 * Target row height for a size. Between two anchors it grows geometrically,
 * so each step of the slider feels the same at both ends.
 */
export function rowHeightFor(size: number, containerWidth: number): number {
	const column = bucket(containerWidth);
	const position = (clampSize(size) / MAX_SIZE) * (ROW_HEIGHTS.length - 1);
	const index = Math.min(ROW_HEIGHTS.length - 2, Math.floor(position));
	const from = ROW_HEIGHTS[index][column];
	const to = ROW_HEIGHTS[index + 1][column];
	return Math.round(from * Math.pow(to / from, position - index));
}

/** Row height of the year view: its own small rows, scaled like the rest. */
export function yearRowHeightFor(size: number, containerWidth: number): number {
	const scale = rowHeightFor(size, containerWidth) / rowHeightFor(DEFAULT_SIZE, containerWidth);
	return Math.max(
		MIN_YEAR_ROW_HEIGHT,
		Math.round(YEAR_ROW_HEIGHTS[bucket(containerWidth)] * scale)
	);
}

/** One step bigger (+1) or smaller (-1), within the range. */
export function stepSize(size: number, direction: 1 | -1, step = SIZE_STEP): number {
	return clampSize(size + direction * step);
}

export function parseSize(raw: string | null | undefined): number {
	const size = raw === null || raw === undefined || raw.trim() === '' ? NaN : Number(raw);
	return Number.isFinite(size) ? clampSize(size) : DEFAULT_SIZE;
}

export function parseGrouping(raw: string | null | undefined): Grouping {
	return groupings.includes(raw as Grouping) ? (raw as Grouping) : DEFAULT_GROUPING;
}

/**
 * The timeline used to save one of five levels that set both the size and
 * the grouping; this reads one into the new pair, or null if it isn't one.
 */
export function migrateZoomLevel(
	raw: string | null | undefined
): { size: number; grouping: Grouping } | null {
	switch (raw) {
		case 'year':
			return { size: DEFAULT_SIZE, grouping: 'year' };
		case 'small':
			return { size: 25, grouping: 'month' };
		case 'medium':
			return { size: 50, grouping: 'month' };
		case 'large':
			return { size: 75, grouping: 'day' };
		case 'xlarge':
			return { size: 100, grouping: 'day' };
		default:
			return null;
	}
}

/** The server never samples more than this per year (TimelineYearsEndpoint.MaxSample). */
export const MAX_YEAR_SAMPLE = 100;
/** How many rows of samples each year shows. */
export const YEAR_ROWS = 3;

/**
 * Photos per year to ask the server for, enough to fill YEAR_ROWS rows at
 * this width and size. Rounded up to a multiple of 12 so resizing the window
 * or dragging the slider doesn't refetch on every pixel.
 */
export function yearSampleSize(
	containerWidth: number,
	size = DEFAULT_SIZE,
	assumedAspect = 1.5
): number {
	const rowHeight = yearRowHeightFor(size, containerWidth);
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

/**
 * A key press nobody else wants, free for a page shortcut: not typed into a
 * text field or a dialog, and without a modifier (Ctrl + + is the browser's own
 * zoom).
 */
export function isFreeKeyPress(event: KeyboardEvent): boolean {
	if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) return false;
	const target = event.target as HTMLElement | null;
	// A slider doesn't type: + and − on it are still the page's.
	return !target?.closest?.(
		'input:not([type="range"]), textarea, select, dialog, [role="dialog"], [contenteditable]'
	);
}

/** + bigger, − smaller, from a free key press (isFreeKeyPress); null otherwise. */
export function sizeKeyDirection(event: KeyboardEvent): 1 | -1 | null {
	if (!isFreeKeyPress(event)) return null;
	if (event.key === '+' || event.key === '=') return 1;
	if (event.key === '-' || event.key === '_') return -1;
	return null;
}
