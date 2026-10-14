import type { GridLayoutMode } from './grid-model.js';

const STORAGE_KEY = 'photonne.grid.layout';
const DEFAULT_LAYOUT: GridLayoutMode = 'masonry';

/**
 * How collections (albums, searches, people…) lay their photos out: a
 * masonry of columns or justified rows. Chosen in Settings › Appearance and
 * kept in this browser, like the theme.
 */
class GridLayoutPreference {
	value = $state<GridLayoutMode>(readSaved());

	set(layout: GridLayoutMode) {
		this.value = layout;
		try {
			localStorage.setItem(STORAGE_KEY, layout);
		} catch {
			// Storage may be unavailable (private mode); the choice lasts the visit.
		}
	}
}

export const gridLayout = new GridLayoutPreference();

export function parseGridLayout(raw: string | null | undefined): GridLayoutMode {
	return raw === 'rows' || raw === 'masonry' ? raw : DEFAULT_LAYOUT;
}

function readSaved(): GridLayoutMode {
	try {
		return parseGridLayout(localStorage.getItem(STORAGE_KEY));
	} catch {
		return DEFAULT_LAYOUT;
	}
}
