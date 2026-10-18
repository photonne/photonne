import type { VideoSummaryResponse } from '#lib/api/index.js';

/** What a summary video is made from: a calendar year or an album. */
export type SummarySource = { kind: 'year'; year: number } | { kind: 'album'; albumId: string };

/** How often the list is re-read while something renders. */
export const POLL_MS = 3000;

/** The user's summary for this source, if they ever asked for one. */
export function findSummary(
	summaries: readonly VideoSummaryResponse[] | undefined,
	source: SummarySource
): VideoSummaryResponse | undefined {
	return summaries?.find((s) =>
		source.kind === 'year'
			? s.source === 'year' && s.year === source.year
			: s.source === 'album' && s.albumId === source.albumId
	);
}

/** Queued or rendering: the server is still on it. */
export function isActive(summary: VideoSummaryResponse | undefined): boolean {
	return summary?.status === 'pending' || summary?.status === 'running';
}

/** Re-read every few seconds while any summary renders; stop once none does. */
export function pollWhileActive(
	summaries: readonly VideoSummaryResponse[] | undefined
): number | false {
	return summaries?.some(isActive) ? POLL_MS : false;
}

/** The request body for a source. */
export function requestBody(source: SummarySource, includePhotos: boolean) {
	return source.kind === 'year'
		? { source: 'year', year: source.year, albumId: null, includePhotos }
		: { source: 'album', year: null, albumId: source.albumId, includePhotos };
}

/** Whole percent for display, 0–100. */
export function percent(summary: VideoSummaryResponse): number {
	return Math.round(Math.min(1, Math.max(0, summary.progress)) * 100);
}
