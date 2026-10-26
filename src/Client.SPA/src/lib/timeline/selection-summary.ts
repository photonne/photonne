import { dateRange, formatBytes } from '#lib/format.js';
import type { GridAsset } from './types.js';

/**
 * What a selection spans, next to its count: "12 mar – 3 abr 2024 · 1,2 GB".
 * The size counts the photos that say theirs; it's left out when none does
 * (a map's points). Null for an empty selection.
 */
export function selectionSummary(items: readonly GridAsset[]): string | null {
	if (items.length === 0) return null;
	let first = items[0].capturedAt;
	let last = first;
	let bytes = 0;
	let sized = false;
	for (const item of items) {
		if (item.capturedAt < first) first = item.capturedAt;
		if (item.capturedAt > last) last = item.capturedAt;
		if (item.fileSize !== undefined) {
			bytes += item.fileSize;
			sized = true;
		}
	}
	const parts = [dateRange(first, last)];
	if (sized) parts.push(formatBytes(bytes));
	return parts.join(' · ');
}
