import type { DownloadOptionsResponse } from '#lib/api/index.js';

/** What a RAW or HEIC/HEIF leaves as: the file itself, or a JPEG the server converts. */
export type DownloadFormat = 'original' | 'jpeg';

/** Whether there is anything to ask: only RAW and HEIC/HEIF have a JPEG to offer. */
export function needsFormatChoice(
	options: DownloadOptionsResponse | null | undefined
): options is DownloadOptionsResponse {
	return !!options && options.convertibleCount > 0;
}

/** "DNG" when every convertible file shares one extension, so "Original" can say which. */
export function originalExtension(extensions: readonly string[]): string | null {
	return extensions.length === 1 ? extensions[0].toUpperCase() : null;
}

/**
 * How much of the selection the choice affects: a single photo needs no
 * explanation; otherwise say whether it applies to all of them or to some.
 */
export function formatScope(
	total: number,
	convertibleCount: number
):
	| { kind: 'one' }
	| { kind: 'all'; total: number }
	| { kind: 'some'; count: number; total: number } {
	if (total <= 1) return { kind: 'one' };
	if (convertibleCount >= total) return { kind: 'all', total };
	return { kind: 'some', count: convertibleCount, total };
}

/**
 * The file of one asset as a download. Without a format the server sends the
 * original, as it always did.
 */
export function assetDownloadUrl(assetId: string, format?: DownloadFormat | null) {
	const query = new URLSearchParams({ download: 'true' });
	if (format) query.set('format', format);
	return `/api/assets/${encodeURIComponent(assetId)}/content?${query}`;
}
