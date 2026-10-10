import type { TimelineResponse } from '#lib/api/index.js';
import type { GridItem } from './grid-model.js';

/** What a grid cell needs to draw one asset. */
export interface GridAsset extends GridItem {
	fileName: string;
	capturedAt: string;
	isVideo: boolean;
	isLivePhoto: boolean;
	isFavorite: boolean;
	dominantColor: string | null;
	thumbnailVersion: string | null;
}

export function toGridAsset(asset: TimelineResponse): GridAsset {
	return {
		id: asset.id,
		// The oriented thumbnail's shape; the stored pixel size is the fallback
		// for assets whose thumbnails aren't generated yet.
		aspect: asset.aspectRatio ?? (asset.width && asset.height ? asset.width / asset.height : null),
		fileName: asset.fileName,
		capturedAt: asset.fileCreatedAt,
		isVideo: asset.type === 'Video',
		isLivePhoto: asset.tags.includes('LivePhoto'),
		isFavorite: asset.isFavorite,
		dominantColor: asset.dominantColor ?? null,
		thumbnailVersion: asset.thumbnailsGeneratedAt ?? null
	};
}
