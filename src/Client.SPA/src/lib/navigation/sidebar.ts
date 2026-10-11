const KEY = 'photonne.sidebar';

/** Below this width the sidebar starts as a rail unless the user chose otherwise. */
export const RAIL_BELOW = 1100;

/** Media query for the windows where the sidebar is a rail by default. */
export const RAIL_QUERY = `(max-width: ${RAIL_BELOW}px)`;

/** The user's last choice (true: rail), or null if they never chose. */
export function savedCollapsed(): boolean | null {
	try {
		const saved = localStorage.getItem(KEY);
		if (saved === 'rail') return true;
		if (saved === 'full') return false;
	} catch {
		// Storage unavailable: no choice to keep.
	}
	return null;
}

/**
 * Whether the sidebar starts collapsed: the user's last choice, else
 * collapsed on a narrow window.
 */
export function initialCollapsed(width: number): boolean {
	return savedCollapsed() ?? width <= RAIL_BELOW;
}

export function saveCollapsed(collapsed: boolean) {
	try {
		localStorage.setItem(KEY, collapsed ? 'rail' : 'full');
	} catch {
		// Kept for this page only.
	}
}
