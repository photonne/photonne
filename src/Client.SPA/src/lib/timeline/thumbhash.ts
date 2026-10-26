import { thumbHashToDataURL } from 'thumbhash';

// Decoding is cheap (~0.1 ms) but a grid repaints the same cells while it
// scrolls: each hash is decoded once. Bounded so a long session can't grow
// it without end; the oldest go first.
const MAX = 3000;
const cache = new Map<string, string | null>();

/**
 * The blurred sketch of a photo (its ThumbHash, base64) as an image URL for
 * a placeholder, or null when there is none or it can't be read.
 */
export function thumbHashUrl(hash: string | null | undefined): string | null {
	if (!hash) return null;
	const known = cache.get(hash);
	if (known !== undefined) return known;
	let url: string | null;
	try {
		const bytes = Uint8Array.from(atob(hash), (char) => char.charCodeAt(0));
		url = thumbHashToDataURL(bytes);
	} catch {
		url = null;
	}
	if (cache.size >= MAX) cache.delete(cache.keys().next().value!);
	cache.set(hash, url);
	return url;
}
