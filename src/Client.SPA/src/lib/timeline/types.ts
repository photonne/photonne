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
	/** Bytes on disk; absent where the source doesn't say (map points). */
	fileSize?: number;
	/** When it went to the trash; only trash listings carry it. */
	deletedAt?: string | null;
}

/** A short text drawn on a cell (bottom left) and read with its label. */
export interface CellNote {
	text: string;
	label: string;
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
		thumbnailVersion: asset.thumbnailsGeneratedAt ?? null,
		fileSize: asset.fileSize,
		deletedAt: asset.deletedAt ?? null
	};
}
