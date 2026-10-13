import { parseHidden, type HideableSpace } from './spaces.js';

const KEY = 'photonne.storage.hiddenSpaces';

function read(): HideableSpace[] {
	try {
		return parseHidden(localStorage.getItem(KEY));
	} catch {
		return [];
	}
}

/**
 * The spaces this viewer hid from the storage totals, shared by Ajustes →
 * Almacenamiento and the admin dashboard so one choice holds on both.
 */
class HiddenSpaces {
	#hidden = $state<HideableSpace[]>(read());

	get set(): ReadonlySet<HideableSpace> {
		return new Set(this.#hidden);
	}

	has(space: HideableSpace): boolean {
		return this.#hidden.includes(space);
	}

	toggle(space: HideableSpace) {
		this.#hidden = this.has(space)
			? this.#hidden.filter((s) => s !== space)
			: [...this.#hidden, space];
		try {
			localStorage.setItem(KEY, this.#hidden.join(','));
		} catch {
			// Private mode or blocked storage: the choice just isn't remembered.
		}
	}
}

export const hiddenSpaces = new HiddenSpaces();
