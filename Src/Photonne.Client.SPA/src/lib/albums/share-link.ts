import type { CreateShareRequest, ShareLinkResponse, UpdateShareRequest } from '#lib/api/index.js';

/** The public-link form; text fields stay strings until they are sent. */
export interface LinkForm {
	/** Last valid day (yyyy-MM-dd, local), or '' for a link that never expires. */
	expires: string;
	/** keep: leave the current password; set: use `password`; none: no password. */
	passwordMode: 'keep' | 'set' | 'none';
	password: string;
	/** Positive whole number, or '' for unlimited views. */
	maxViews: string;
	allowDownload: boolean;
	allowUpload: boolean;
}

export type LinkFormError = 'max_views' | 'expires_past' | 'password_empty';

const pad = (n: number) => String(n).padStart(2, '0');

/** yyyy-MM-dd of a Date in local time (what a date input shows). */
export function localDate(date: Date) {
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** A link stays valid through the whole chosen day: it ends at its last second, local time. */
export function expiryToIso(day: string) {
	const [year, month, date] = day.split('-').map(Number);
	return new Date(year, month - 1, date, 23, 59, 59).toISOString();
}

/** The date `days` from `now`, for the quick expiry choices. */
export function daysFromNow(days: number, now = new Date()) {
	const date = new Date(now);
	date.setDate(date.getDate() + days);
	return localDate(date);
}

export function formFromLink(link?: ShareLinkResponse | null): LinkForm {
	return {
		expires: link?.expiresAt ? localDate(new Date(link.expiresAt)) : '',
		passwordMode: link ? (link.hasPassword ? 'keep' : 'none') : 'none',
		password: '',
		maxViews: link?.maxViews ? String(link.maxViews) : '',
		allowDownload: link?.allowDownload ?? true,
		allowUpload: link?.allowUpload ?? false
	};
}

export function validateLinkForm(form: LinkForm, now = new Date()): LinkFormError | null {
	const views = form.maxViews.trim();
	if (views && !/^[1-9]\d*$/.test(views)) return 'max_views';
	if (form.expires && form.expires < localDate(now)) return 'expires_past';
	if (form.passwordMode === 'set' && !form.password) return 'password_empty';
	return null;
}

const maxViewsOf = (form: LinkForm) => (form.maxViews.trim() ? Number(form.maxViews) : null);
const expiresOf = (form: LinkForm) => (form.expires ? expiryToIso(form.expires) : null);

export function createLinkRequest(albumId: string, form: LinkForm): CreateShareRequest {
	return {
		albumId,
		expiresAt: expiresOf(form),
		password: form.passwordMode === 'set' ? form.password : null,
		maxViews: maxViewsOf(form),
		allowDownload: form.allowDownload,
		allowUpload: form.allowUpload
	};
}

/** PATCH semantics of the password: null keeps it, '' removes it, a value replaces it. */
export function updateLinkRequest(form: LinkForm): UpdateShareRequest {
	return {
		expiresAt: expiresOf(form),
		password:
			form.passwordMode === 'keep' ? null : form.passwordMode === 'set' ? form.password : '',
		maxViews: maxViewsOf(form),
		allowDownload: form.allowDownload,
		allowUpload: form.allowUpload
	};
}

/** Whether a link still opens: the server stops serving it past its date or its views. */
export function linkStatus(
	link: Pick<ShareLinkResponse, 'expiresAt' | 'maxViews' | 'viewCount'>,
	now = Date.now()
): 'active' | 'expired' | 'exhausted' {
	if (link.expiresAt && Date.parse(link.expiresAt) <= now) return 'expired';
	if (link.maxViews !== null && link.viewCount >= link.maxViews) return 'exhausted';
	return 'active';
}

/**
 * The link to hand out. The server builds it from its public URL setting, or
 * returns a path when it has none; a path is completed with this origin.
 */
export function absoluteShareUrl(shareUrl: string, origin: string) {
	return /^https?:\/\//.test(shareUrl) ? shareUrl : `${origin}${shareUrl}`;
}
