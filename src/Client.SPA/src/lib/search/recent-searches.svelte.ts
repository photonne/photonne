import { parseRecent, remember, type RecentSearch } from './recent.js';

const KEY = 'photonne.search.recent';

function read(): RecentSearch[] {
	try {
		return parseRecent(localStorage.getItem(KEY));
	} catch {
		return [];
	}
}

/**
 * The searches this viewer ran, newest first, for the search page's
 * "Recientes". Kept in this browser only: a convenience, not a history.
 */
class RecentSearches {
	#list = $state<RecentSearch[]>(read());

	get list(): readonly RecentSearch[] {
		return this.#list;
	}

	add(entry: RecentSearch) {
		this.#save(remember(this.#list, entry));
	}

	clear() {
		this.#save([]);
	}

	#save(list: RecentSearch[]) {
		this.#list = list;
		try {
			if (list.length) localStorage.setItem(KEY, JSON.stringify(list));
			else localStorage.removeItem(KEY);
		} catch {
			// Private mode or blocked storage: the list lives for this visit only.
		}
	}
}

export const recentSearches = new RecentSearches();
