export interface ScrubberMarker {
	label: string;
	/** Position on the rail, 0 (top) to 1 (bottom). */
	fraction: number;
}

/**
 * One marker per year, where its newest month starts. `sections` come newest
 * first with `yyyy-MM` keys; positions follow the laid-out heights, so the
 * rail matches what scrolling actually shows.
 */
export function yearMarkers(
	sectionTops: ReadonlyMap<string, number>,
	totalHeight: number
): ScrubberMarker[] {
	if (totalHeight <= 0) return [];
	const markers: ScrubberMarker[] = [];
	let lastYear: string | null = null;
	for (const [key, top] of sectionTops) {
		const year = key.slice(0, 4);
		if (year === lastYear) continue;
		lastYear = year;
		markers.push({ label: year, fraction: top / totalHeight });
	}
	return markers;
}

/**
 * One marker per year for a grid without dated sections (an album sorted by
 * date), where the year's first row starts. `rows` come in display order
 * with the year of their first photo. Years that don't run one way (an
 * album in its own order mixes them) give no markers: a year would start
 * over and over, and the rail would say nothing.
 */
export function rowYearMarkers(
	rows: readonly { top: number; year: string }[],
	totalHeight: number
): ScrubberMarker[] {
	if (totalHeight <= 0) return [];
	const markers: ScrubberMarker[] = [];
	let direction = 0;
	for (const row of rows) {
		const last = markers.at(-1);
		if (last?.label === row.year) continue;
		if (last) {
			const step = row.year > last.label ? 1 : -1;
			if (direction !== 0 && step !== direction) return [];
			direction = step;
		}
		markers.push({ label: row.year, fraction: row.top / totalHeight });
	}
	return markers;
}

/**
 * Drops markers that would overlap on a rail `railHeight` px tall, keeping
 * the earlier (newer) one, so labels stay legible on long libraries.
 */
export function spreadMarkers(
	markers: readonly ScrubberMarker[],
	railHeight: number,
	minGapPx: number
): ScrubberMarker[] {
	const kept: ScrubberMarker[] = [];
	for (const marker of markers) {
		const last = kept.at(-1);
		if (!last || (marker.fraction - last.fraction) * railHeight >= minGapPx) kept.push(marker);
	}
	return kept;
}
