/**
 * The floating chrome's shared state: whether a selection has taken over
 * the navigation, and whether the page is scrolling down (immersive).
 */
class Chrome {
	/** How many selection bars are showing (each page has at most one). */
	selecting = $state(0);
	/** On a phone, scrolling down hides the chrome; scrolling up brings it back. */
	immersive = $state(false);
}

export const chrome = new Chrome();
