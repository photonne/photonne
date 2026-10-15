import type { ListView } from './album-list.js';

const VIEW_KEY = 'photonne.folders.view';
/** Where the folders index kept the view before it had its own key. */
const LEGACY_LIST_KEY = 'photonne.folders.list';
const SECTIONS_KEY = 'photonne.folders.sections';

export interface FolderSections {
	/** A folder's subfolders, shown or folded away. */
	subfolders: boolean;
	/** A folder's own photos, when it has subfolders too. */
	photos: boolean;
}

const OPEN: FolderSections = { subfolders: true, photos: true };

/**
 * How folders are shown, on the index and inside a folder alike: cover
 * tiles or compact rows, and which of a folder's two sections (subfolders,
 * photos) are open. Kept in this browser, like the albums' view.
 */
class FolderViewPreference {
	#view = $state<ListView>(readView());
	sections = $state<FolderSections>(readSections());

	/** Settable, so a toggle can bind to it; every change is kept. */
	get value() {
		return this.#view;
	}

	set value(view: ListView) {
		this.#view = view;
		save(VIEW_KEY, view);
	}

	toggle(section: keyof FolderSections) {
		this.sections = { ...this.sections, [section]: !this.sections[section] };
		save(SECTIONS_KEY, JSON.stringify(this.sections));
	}
}

export const folderView = new FolderViewPreference();

export function parseListView(raw: string | null | undefined): ListView {
	return raw === 'list' ? 'list' : 'grid';
}

export function parseSections(raw: string | null | undefined): FolderSections {
	try {
		const saved = JSON.parse(raw ?? '{}') as Partial<FolderSections> | null;
		return {
			subfolders: saved?.subfolders !== false,
			photos: saved?.photos !== false
		};
	} catch {
		return { ...OPEN };
	}
}

function readView(): ListView {
	try {
		const own = localStorage.getItem(VIEW_KEY);
		if (own !== null) return parseListView(own);
		const legacy = JSON.parse(localStorage.getItem(LEGACY_LIST_KEY) ?? '{}') as { view?: unknown };
		return parseListView(typeof legacy?.view === 'string' ? legacy.view : null);
	} catch {
		return 'grid';
	}
}

function readSections(): FolderSections {
	try {
		return parseSections(localStorage.getItem(SECTIONS_KEY));
	} catch {
		return { ...OPEN };
	}
}

function save(key: string, value: string) {
	try {
		localStorage.setItem(key, value);
	} catch {
		// Storage may be unavailable (private mode); the choice lasts the visit.
	}
}
