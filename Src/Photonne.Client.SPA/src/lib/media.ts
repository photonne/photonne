export type ThumbnailSize = 'Small' | 'Medium' | 'Large';

// Longest side of each generated thumbnail (ThumbnailGeneratorService).
const SIZES: [ThumbnailSize, number][] = [
	['Small', 220],
	['Medium', 640],
	['Large', 1280]
];

/** The smallest thumbnail that stays sharp for a box `cssPixels` long on this screen. */
export function thumbnailSizeFor(
	cssPixels: number,
	devicePixelRatio = globalThis.devicePixelRatio ?? 1
) {
	const needed = cssPixels * devicePixelRatio;
	return SIZES.find(([, px]) => px >= needed)?.[0] ?? 'Large';
}

/**
 * Thumbnail URL. With `version` (the timeline's `thumbnailsGeneratedAt`) the
 * server answers as immutable for a year, and a regeneration changes the URL.
 * Loads authenticate with the media cookie, so this works in `<img src>`.
 */
export function thumbnailUrl(assetId: string, size: ThumbnailSize, version?: string | null) {
	const v = version ? `&v=${Date.parse(version)}` : '';
	return `/api/assets/${assetId}/thumbnail?size=${size}${v}`;
}
