import type { SentShareLinkDto } from '#lib/api/index.js';
import { linkStatus } from '#lib/albums/share-link.js';

/** What a link shows: an album, a single photo, or something that is gone. */
export type LinkSubject =
	| { kind: 'album'; name: string; albumId: string }
	| { kind: 'asset'; name: string; assetId: string }
	| { kind: 'unknown' };

export function linkSubject(link: SentShareLinkDto): LinkSubject {
	if (link.albumId) return { kind: 'album', name: link.albumName ?? '', albumId: link.albumId };
	if (link.assetId) return { kind: 'asset', name: link.assetFileName ?? '', assetId: link.assetId };
	return { kind: 'unknown' };
}

/** App path of what the link shares, to manage it in place. */
export function subjectPath(subject: LinkSubject): string | null {
	if (subject.kind === 'album') return `/albums/${encodeURIComponent(subject.albumId)}`;
	if (subject.kind === 'asset') return `/?asset=${encodeURIComponent(subject.assetId)}`;
	return null;
}

export function linkCover(link: SentShareLinkDto): string | null {
	return link.albumCoverUrl ?? link.assetThumbnailUrl ?? null;
}

export type LinkBadge = 'active' | 'expired' | 'exhausted' | 'password' | 'no_download' | 'upload';

/** The link's state first (one of active/expired/exhausted), then what limits or allows it. */
export function linkBadges(link: SentShareLinkDto, now = Date.now()): LinkBadge[] {
	const badges: LinkBadge[] = [linkStatus(link, now)];
	if (link.hasPassword) badges.push('password');
	if (!link.allowDownload) badges.push('no_download');
	if (link.allowUpload) badges.push('upload');
	return badges;
}

/** Links whose album or file name contains `query` (ignoring case and accents). */
export function filterLinks(links: readonly SentShareLinkDto[], query: string) {
	const needle = fold(query.trim());
	if (!needle) return [...links];
	return links.filter((link) => {
		const subject = linkSubject(link);
		return subject.kind !== 'unknown' && fold(subject.name).includes(needle);
	});
}

function fold(text: string) {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase();
}

/** Totals for the page header: how many links still open, and their views. */
export function linkTotals(links: readonly SentShareLinkDto[], now = Date.now()) {
	return {
		active: links.filter((link) => linkStatus(link, now) === 'active').length,
		views: links.reduce((sum, link) => sum + link.viewCount, 0)
	};
}
