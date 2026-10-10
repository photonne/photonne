import {
	apiErrorCode,
	getShareLink,
	type SharedAssetDto,
	type SharedContentResponse
} from '#lib/api/index.js';
import type { ThumbnailSize } from '#lib/media.js';

/** What opening a link can turn out to be, one screen each. */
export type ShareOutcome =
	| { kind: 'content'; content: SharedContentResponse }
	| { kind: 'password'; wrong: boolean }
	| { kind: 'expired' }
	| { kind: 'maxViews' }
	| { kind: 'notFound' }
	| { kind: 'unreachable' };

export function outcomeOf(
	status: number | undefined,
	data: SharedContentResponse | undefined,
	error: unknown
): ShareOutcome {
	if (status === undefined) return { kind: 'unreachable' };
	if (data) {
		if (data.requiresPassword) return { kind: 'password', wrong: data.wrongPassword };
		// Only album links carry content today (an asset-only link has none).
		if (!data.album || !data.assets) return { kind: 'notFound' };
		return { kind: 'content', content: data };
	}
	const code = apiErrorCode(error);
	if (code === 'share_link_expired') return { kind: 'expired' };
	if (code === 'share_link_max_views') return { kind: 'maxViews' };
	if (status === 404 || status === 410) return { kind: 'notFound' };
	return { kind: 'unreachable' };
}

/**
 * Opens a link (no session needed). Every successful open counts as a view
 * on the server, so callers should not refetch it needlessly.
 */
export async function openShare(token: string, password?: string): Promise<ShareOutcome> {
	try {
		const { data, error, response } = await getShareLink({
			path: { token },
			query: password ? { pw: password } : undefined
		});
		return outcomeOf(response?.status, data, error);
	} catch {
		return { kind: 'unreachable' };
	}
}

/**
 * Media of a shared asset. The password (when the link has one) goes along
 * on every request, as the server checks it on the media endpoints too.
 */
export function shareMediaUrl(
	token: string,
	assetId: string,
	media: { thumbnail: ThumbnailSize } | { content: true; download?: boolean },
	password?: string | null
): string {
	const params = new URLSearchParams();
	let kind: string;
	if ('thumbnail' in media) {
		kind = 'thumbnail';
		params.set('size', media.thumbnail);
	} else {
		kind = 'content';
		if (media.download) params.set('download', 'true');
	}
	if (password) params.set('pw', password);
	const query = params.toString();
	return `/api/share/${encodeURIComponent(token)}/asset/${encodeURIComponent(assetId)}/${kind}${query ? `?${query}` : ''}`;
}

export function isVideo(asset: SharedAssetDto) {
	return asset.type === 'Video';
}

/** Width / height when known; the grid falls back to a default shape. */
export function aspectOf(asset: SharedAssetDto): number | null {
	return asset.width && asset.height ? asset.width / asset.height : null;
}

// The password of an open link, for this tab only: a reload shouldn't ask
// again, a new visit should.
const PASSWORD_KEY = (token: string) => `photonne.share.${token}`;

export function rememberedPassword(token: string): string | null {
	try {
		return sessionStorage.getItem(PASSWORD_KEY(token));
	} catch {
		return null;
	}
}

export function rememberPassword(token: string, password: string | null) {
	try {
		if (password) sessionStorage.setItem(PASSWORD_KEY(token), password);
		else sessionStorage.removeItem(PASSWORD_KEY(token));
	} catch {
		// Not remembered: the visitor types it again after a reload.
	}
}
