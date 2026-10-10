/**
 * Row-justified photo layout (Google Photos style): cells keep their aspect
 * ratio, every row fills the container width exactly, and the last, partial
 * row keeps the target height instead of being stretched.
 *
 * Same algorithm as the Blazor workspace (JustifiedLayout.cs), so both web
 * clients lay a month out identically.
 */

/** Used when an asset has no known shape yet (no thumbnail, no EXIF). */
export const FALLBACK_ASPECT = 1.5;

// A 6:1 panorama or a vertical strip scan would wreck the row split; they are
// clamped to a sane range for layout purposes only.
const MIN_ASPECT = 0.4;
const MAX_ASPECT = 3;

export interface LayoutRow {
	/** Index of the row's first cell in the input order. */
	start: number;
	count: number;
	height: number;
}

export interface LayoutOptions {
	containerWidth: number;
	targetRowHeight: number;
	spacing: number;
}

export function clampAspect(aspect: number | null | undefined): number {
	if (aspect == null || !Number.isFinite(aspect) || aspect <= 0) return FALLBACK_ASPECT;
	return Math.min(MAX_ASPECT, Math.max(MIN_ASPECT, aspect));
}

export function computeRows(aspects: readonly number[], options: LayoutOptions): LayoutRow[] {
	const { containerWidth, targetRowHeight, spacing } = options;
	const rows: LayoutRow[] = [];
	if (aspects.length === 0 || containerWidth <= 0 || targetRowHeight <= 0) return rows;

	let start = 0;
	let aspectSum = 0;
	for (let i = 0; i < aspects.length; i++) {
		aspectSum += clampAspect(aspects[i]);
		const count = i - start + 1;
		const contentWidth = containerWidth - spacing * (count - 1);
		// The row closes as soon as, at the target height, it overflows the width.
		if (aspectSum * targetRowHeight >= contentWidth) {
			rows.push({ start, count, height: contentWidth / aspectSum });
			start = i + 1;
			aspectSum = 0;
		}
	}
	if (start < aspects.length) {
		rows.push({ start, count: aspects.length - start, height: targetRowHeight });
	}
	return rows;
}

export function rowsHeight(rows: readonly LayoutRow[], spacing: number): number {
	if (rows.length === 0) return 0;
	return rows.reduce((sum, row) => sum + row.height, 0) + spacing * (rows.length - 1);
}

/**
 * Height of a month whose items aren't loaded yet, from its count alone, to
 * reserve stable scroll space. Mirrors computeRows' closing rule: a row closes
 * as soon as it overflows at the target height, so it holds
 * ceil(W / (aspect·target)) cells and ends slightly *below* the target.
 */
export function estimateHeight(
	count: number,
	options: LayoutOptions,
	assumedAspect = FALLBACK_ASPECT
): number {
	const { containerWidth, targetRowHeight, spacing } = options;
	if (count <= 0 || containerWidth <= 0 || targetRowHeight <= 0) return 0;
	const perRow = Math.max(1, Math.ceil(containerWidth / (assumedAspect * targetRowHeight)));
	const rowHeight = (containerWidth - spacing * (perRow - 1)) / (perRow * assumedAspect);
	const rowCount = Math.ceil(count / perRow);
	return rowCount * rowHeight + spacing * Math.max(0, rowCount - 1);
}
