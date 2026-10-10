import { appHref } from '#lib/navigation/href.js';

export type LabelKind = 'scenes' | 'objects';

/** The photos of a scene or object label. */
export function labelHref(kind: LabelKind, label: string) {
	return appHref(`/explore/${kind}/${encodeURIComponent(label)}`);
}

/** The photos carrying one of the user's tags. */
export function tagHref(tag: string) {
	return appHref(`/explore/tags/${encodeURIComponent(tag)}`);
}

/** The photos whose recognised text matches. */
export function textHref(query: string) {
	return `${appHref('/explore/text')}?q=${encodeURIComponent(query)}`;
}
