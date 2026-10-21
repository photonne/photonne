import type { GridLayoutMode } from './grid-model.js';

const STORAGE_KEY = 'photonne.grid.layout';
const RATIO_KEY = 'photonne.grid.ratio';

/** What the user picks: a masonry or a grid of rows. */
export type GridLayoutChoice = 'masonry' | 'rows';
/** A grid's cells: equal squares (1:1) or each photo's own shape. */
export type GridRatio = 'square' | 'original';

const DEFAULT_LAYOUT: GridLayoutChoice = 'masonry';
const DEFAULT_RATIO: GridRatio = 'square';

/**
 * How every photo grid (the timeline, albums, searches, people…) lays its
 * photos out: a masonry of columns or a grid, of squares or of each photo's
 * shape. One choice for the whole app, made in Settings › Appearance or in
 * a grid's view options, and kept in this browser, like the theme.
 */
class GridLayoutPreference {
	value = $state<GridLayoutChoice>(read(STORAGE_KEY, parseGridLayout));
	ratio = $state<GridRatio>(read(RATIO_KEY, parseGridRatio));

	/** What the grid draws. */
	get mode(): GridLayoutMode {
		return gridMode(this.value, this.ratio);
	}

	set(layout: GridLayoutChoice) {
		this.value = layout;
		save(STORAGE_KEY, layout);
	}

	setRatio(ratio: GridRatio) {
		this.ratio = ratio;
		save(RATIO_KEY, ratio);
	}
}

export const gridLayout = new GridLayoutPreference();

export function gridMode(layout: GridLayoutChoice, ratio: GridRatio): GridLayoutMode {
	if (layout === 'masonry') return 'masonry';
	return ratio === 'square' ? 'square' : 'rows';
}

export function parseGridLayout(raw: string | null | undefined): GridLayoutChoice {
	return raw === 'rows' || raw === 'masonry' ? raw : DEFAULT_LAYOUT;
}

export function parseGridRatio(raw: string | null | undefined): GridRatio {
	return raw === 'square' || raw === 'original' ? raw : DEFAULT_RATIO;
}

function read<T>(key: string, parse: (raw: string | null) => T): T {
	try {
		return parse(localStorage.getItem(key));
	} catch {
		return parse(null);
	}
}

function save(key: string, value: string) {
	try {
		localStorage.setItem(key, value);
	} catch {
		// Storage may be unavailable (private mode); the choice lasts the visit.
	}
}
