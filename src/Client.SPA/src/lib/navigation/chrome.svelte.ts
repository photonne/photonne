import type { Snippet } from 'svelte';

/**
 * The floating chrome's shared state: whether a selection has taken over
 * the navigation, whether the page is scrolling down (immersive), and what
 * the page on screen adds to it (see PageChrome.svelte).
 */
class Chrome {
	/** How many selection bars are showing (each page has at most one). */
	selecting = $state(0);
	/** On a phone, scrolling down hides the chrome; scrolling up brings it back. */
	immersive = $state(false);
	/**
	 * A pane with its own scroll (a photo grid) has gone down: the room kept
	 * for the chrome above and below the page goes to the photos, and the
	 * chrome floats over them until that pane is back at its top.
	 */
	reclaimed = $state(false);
	/**
	 * The page takes the whole window: no room kept above (and, on a phone,
	 * below) for the chrome. Its own scrolling panes keep that room inside,
	 * as padding (--bleed-top, --bleed-bottom), so they pass beneath it.
	 */
	bleed = $state(false);
	/**
	 * With bleed, the page also reaches under the navigation island, to the
	 * window's left edge (the map). What must stay clear of the island keeps
	 * --bleed-left.
	 */
	wide = $state(false);
	/** The page's own buttons, in a capsule of the chrome next to the search. */
	actions = $state<Snippet | null>(null);
}

export const chrome = new Chrome();
