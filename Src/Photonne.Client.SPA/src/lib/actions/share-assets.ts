import type { ShareLinkResponse } from '#lib/api/index.js';
import { createLinkRequest, type LinkForm } from '#lib/albums/share-link.js';

/** The calls behind "share these photos as a link", injected so the flow can be tested. */
export interface ShareAssetsApi {
	createAlbum(name: string): Promise<{ id: string }>;
	addAssets(albumId: string, assetIds: string[]): Promise<void>;
	createLink(request: ReturnType<typeof createLinkRequest>): Promise<ShareLinkResponse>;
	deleteAlbum(albumId: string): Promise<void>;
}

export interface SharedAssets {
	albumId: string;
	link: ShareLinkResponse;
}

/** Longest album name the share dialog accepts (the server's album name column). */
export const SHARE_NAME_MAX = 200;

export type ShareNameError = 'empty' | 'too_long';

export function validateShareName(name: string): ShareNameError | null {
	const trimmed = name.trim();
	if (!trimmed) return 'empty';
	if (trimmed.length > SHARE_NAME_MAX) return 'too_long';
	return null;
}

/**
 * A public link points at an album, so sharing loose photos wraps them in a
 * new album first (as the native app does): album, photos, link. The API has
 * no single call for it; if a later step fails, the album just created is
 * deleted so a failed share leaves nothing behind.
 */
export async function shareAssetsAsLink(
	api: ShareAssetsApi,
	assetIds: readonly string[],
	name: string,
	form: LinkForm
): Promise<SharedAssets> {
	const album = await api.createAlbum(name.trim());
	try {
		if (assetIds.length > 0) await api.addAssets(album.id, [...assetIds]);
		const link = await api.createLink(createLinkRequest(album.id, form));
		return { albumId: album.id, link };
	} catch (error) {
		await api.deleteAlbum(album.id).catch(() => {});
		throw error;
	}
}
