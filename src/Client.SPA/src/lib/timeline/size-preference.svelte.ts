import { clampSize, DEFAULT_SIZE, parseSize, stepSize } from './zoom.js';

/**
 * A thumbnail size (zoom.ts) kept in this browser. The timeline, the
 * collections and the album cards each keep their own, so making one
 * smaller doesn't change the others.
 */
export class SizePreference {
	value = $state(DEFAULT_SIZE);
	#key: string;

	constructor(key: string, fallback?: () => number | null) {
		this.#key = key;
		this.value = read(key) ?? fallback?.() ?? DEFAULT_SIZE;
	}

	set(size: number) {
		this.value = clampSize(size);
		try {
			localStorage.setItem(this.#key, String(this.value));
		} catch {
			// Storage may be unavailable (private mode); the size lasts the visit.
		}
	}

	step(direction: 1 | -1, step?: number) {
		this.set(stepSize(this.value, direction, step));
	}
}

function read(key: string): number | null {
	try {
		const raw = localStorage.getItem(key);
		return raw === null ? null : parseSize(raw);
	} catch {
		return null;
	}
}

/** Albums, folders, favorites, search, Organize…: every grid but the timeline. */
export const collectionSize = new SizePreference('photonne.collection.size');
