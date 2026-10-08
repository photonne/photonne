import type { AssetDetailResponse, CaptureDateSuggestionResponse } from '#lib/api/index.js';

/** Where a candidate capture date comes from. */
export type DateSource = 'exif' | 'file' | 'fileName' | 'folder';

export interface DateCandidate {
	source: DateSource;
	/** ISO instant (UTC: capture dates are the camera's wall clock stored as UTC). */
	date: string;
	/** The asset already has this date (to the minute). */
	current: boolean;
}

/**
 * Capture dates are wall-clock values the server may send without a zone;
 * read as UTC like the rest of the app, never as the browser's local time.
 */
export function asUtc(iso: string) {
	return /[zZ]|[+-]\d\d:?\d\d$/.test(iso) ? iso : `${iso}Z`;
}

const minute = (iso: string) => new Date(asUtc(iso)).toISOString().slice(0, 16);

/**
 * The dates the server could recover for a photo, EXIF first (the most
 * trustworthy), then the name or folder, then the file's own date. The same
 * date from two sources shows once, under the better source.
 */
export function dateCandidates(
	suggestion: CaptureDateSuggestionResponse | undefined | null
): DateCandidate[] {
	if (!suggestion) return [];
	const found: [DateSource, string | null][] = [
		['exif', suggestion.exifDate],
		[suggestion.inferredOrigin === 'FolderPath' ? 'folder' : 'fileName', suggestion.inferredDate],
		['file', suggestion.fileDate]
	];
	const current = minute(suggestion.currentDate);
	const seen = new Set<string>();
	const candidates: DateCandidate[] = [];
	for (const [source, date] of found) {
		if (!date || Number.isNaN(Date.parse(asUtc(date)))) continue;
		const key = minute(date);
		if (seen.has(key)) continue;
		seen.add(key);
		candidates.push({
			source,
			date: new Date(asUtc(date)).toISOString(),
			current: key === current
		});
	}
	return candidates;
}

/**
 * Writing the date into the file only works on the user's own files outside
 * external libraries (those are read-only); the server would just skip it.
 */
export function canWriteToFile(asset: Pick<AssetDetailResponse, 'canEdit' | 'isReadOnly'>) {
	return asset.canEdit && !asset.isReadOnly;
}
