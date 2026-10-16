import { SizePreference } from '#lib/timeline/size-preference.svelte.js';
import { clampSize, MAX_SIZE } from '#lib/timeline/zoom.js';

/** Narrowest, default (size 50) and widest album or folder tile, in px. */
const MIN_WIDTH = 120;
const DEFAULT_WIDTH = 180;
const MAX_WIDTH = 320;

/** How big album and folder tiles are, on every page that shows them. */
export const cardSize = new SizePreference('photonne.cards.size');

/**
 * The narrowest a tile may get at this size (the grid fits as many as it
 * can, then stretches them). Geometric on each side of the default, like
 * the photo sizes.
 */
export function cardMinWidth(size: number): number {
	const t = clampSize(size) / MAX_SIZE;
	return t <= 0.5
		? Math.round(MIN_WIDTH * Math.pow(DEFAULT_WIDTH / MIN_WIDTH, t * 2))
		: Math.round(DEFAULT_WIDTH * Math.pow(MAX_WIDTH / DEFAULT_WIDTH, t * 2 - 1));
}
