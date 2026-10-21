/**
 * Moves an element to the end of <body> while it is mounted, out of any
 * stacking context or clipping box of the page it is declared in (a photo
 * viewer opened from the map's panel must cover the floating chrome).
 *
 * Use as `{@attach portal}` on an element that is not the first or last node
 * of its block: Svelte finds a block's nodes by walking between them, so the
 * moved element sits inside a wrapper that stays in place.
 */
export function portal(node: HTMLElement) {
	document.body.append(node);
	return () => node.remove();
}
