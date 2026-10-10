import type { PendingAssetDto, PendingEnrichmentResponse } from '#lib/api/index.js';

/**
 * The pending list over its pages, once per photo: the list moves under the
 * cursor while workers finish tasks, so a later page can repeat a photo.
 */
export function pendingItems(pages: readonly PendingEnrichmentResponse[]): PendingAssetDto[] {
	const seen = new Set<string>();
	const items: PendingAssetDto[] = [];
	for (const item of pages.flatMap((page) => page.items)) {
		if (seen.has(item.assetId)) continue;
		seen.add(item.assetId);
		items.push(item);
	}
	return items;
}

/** The photos a "retry everything" would touch: those with a failed task. */
export function retryable(items: readonly PendingAssetDto[]): PendingAssetDto[] {
	return items.filter((item) => item.failed > 0 || item.failedTaskTypes.length > 0);
}

/** The page's headline counts, from the first page (they cover the whole list). */
export function pendingSummary(first: PendingEnrichmentResponse | undefined) {
	if (!first) return { total: 0, inFlight: 0, failed: 0 };
	return { total: first.totalAssets, inFlight: first.inFlightAssets, failed: first.failedAssets };
}
