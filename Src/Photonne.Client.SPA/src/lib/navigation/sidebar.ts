const KEY = 'photonne.sidebar';

/** Below this width the sidebar starts as a rail unless the user chose otherwise. */
export const RAIL_BELOW = 1100;

/**
 * Whether the sidebar starts collapsed: the user's last choice, else
 * collapsed on a narrow window.
 */
export function initialCollapsed(width: number): boolean {
	try {
		const saved = localStorage.getItem(KEY);
		if (saved === 'rail') return true;
		if (saved === 'full') return false;
	} catch {
		// Storage unavailable: fall back to the window width.
	}
	return width <= RAIL_BELOW;
}

export function saveCollapsed(collapsed: boolean) {
	try {
		localStorage.setItem(KEY, collapsed ? 'rail' : 'full');
	} catch {
		// Kept for this page only.
	}
}
