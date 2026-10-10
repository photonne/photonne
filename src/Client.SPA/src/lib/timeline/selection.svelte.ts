import { SvelteSet } from 'svelte/reactivity';

/**
 * Multi-selection of a photo grid, with the file-manager rules of the Blazor
 * workspace: a single toggle sets the anchor, Shift extends from the anchor
 * (which stays put, so successive Shift-clicks re-extend from it), and bulk
 * set/unset serves month checkboxes and drag painting.
 *
 * `has(id)` is reactive per id, so toggling one cell re-renders that cell only.
 */
export class Selection {
	#ids = new SvelteSet<string>();
	#anchor = $state<string | null>(null);

	get size() {
		return this.#ids.size;
	}

	get active() {
		return this.#ids.size > 0;
	}

	get anchor() {
		return this.#anchor;
	}

	get ids(): ReadonlySet<string> {
		return this.#ids;
	}

	has(id: string) {
		return this.#ids.has(id);
	}

	toggle(id: string) {
		if (this.#ids.has(id)) this.#ids.delete(id);
		else this.#ids.add(id);
		this.#anchor = id;
	}

	/**
	 * Selects anchor→target inclusive over `order` (the grid's visual order).
	 * Without a usable anchor it degrades to a toggle that sets the anchor.
	 */
	selectRange(target: string, order: readonly string[]) {
		const anchorIndex = this.#anchor === null ? -1 : order.indexOf(this.#anchor);
		const targetIndex = order.indexOf(target);
		if (anchorIndex < 0 || targetIndex < 0) {
			this.toggle(target);
			return;
		}
		const [lo, hi] =
			anchorIndex < targetIndex ? [anchorIndex, targetIndex] : [targetIndex, anchorIndex];
		for (let i = lo; i <= hi; i++) this.#ids.add(order[i]);
	}

	set(ids: Iterable<string>, selected: boolean) {
		for (const id of ids) {
			if (selected) this.#ids.add(id);
			else this.#ids.delete(id);
		}
	}

	clear() {
		this.#ids.clear();
		this.#anchor = null;
	}

	/** None / some / all of `ids` selected, for a tri-state month checkbox. */
	stateOf(ids: readonly string[]): 'none' | 'some' | 'all' {
		if (ids.length === 0) return 'none';
		let count = 0;
		for (const id of ids) if (this.#ids.has(id)) count++;
		return count === 0 ? 'none' : count === ids.length ? 'all' : 'some';
	}
}
