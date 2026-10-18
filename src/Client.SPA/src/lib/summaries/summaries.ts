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

/** Which material goes in by shape; a portrait summary also comes out portrait. */
export type SummaryOrientation = 'all' | 'landscape' | 'portrait';

export const ORIENTATIONS: readonly SummaryOrientation[] = ['all', 'landscape', 'portrait'];

/** The lengths the user can pick, in minutes. */
export const DURATIONS: readonly number[] = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
export const DEFAULT_MINUTES = 5;

export interface SummaryOptions {
	includePhotos: boolean;
	orientation: SummaryOrientation;
	durationMinutes: number;
}

/** What a new summary starts with: no photos, every shape, five minutes. */
export const DEFAULT_OPTIONS: SummaryOptions = {
	includePhotos: false,
	orientation: 'all',
	durationMinutes: DEFAULT_MINUTES
};

/** The options an earlier request used, to start from them when asking again. */
export function optionsOf(summary: VideoSummaryResponse): SummaryOptions {
	return {
		includePhotos: summary.includePhotos,
		orientation: ORIENTATIONS.includes(summary.orientation as SummaryOrientation)
			? (summary.orientation as SummaryOrientation)
			: 'all',
		durationMinutes: DURATIONS.includes(summary.durationMinutes)
			? summary.durationMinutes
			: DEFAULT_MINUTES
	};
}

/** The request body for a source. */
export function requestBody(source: SummarySource, options: SummaryOptions) {
	const { includePhotos, orientation, durationMinutes } = options;
	return source.kind === 'year'
		? {
				source: 'year',
				year: source.year,
				albumId: null,
				includePhotos,
				orientation,
				durationMinutes
			}
		: {
				source: 'album',
				year: null,
				albumId: source.albumId,
				includePhotos,
				orientation,
				durationMinutes
			};
}

/** Whole percent for display, 0–100. */
export function percent(summary: VideoSummaryResponse): number {
	return Math.round(Math.min(1, Math.max(0, summary.progress)) * 100);
}
