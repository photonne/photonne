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
