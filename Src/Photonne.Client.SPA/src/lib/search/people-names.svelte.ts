import { SvelteMap } from 'svelte/reactivity';
import { getApiPeopleById, type PersonDto } from '#lib/api/index.js';

/**
 * Names of the people a page has seen, so a chip for a person picked in an
 * earlier search (or arriving in a link) still says who it is. Unknown ids
 * are looked up once.
 */
export class PeopleNames {
	#names = new SvelteMap<string, string | null>();
	// Bookkeeping only: nothing renders from it.
	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	#asked = new Set<string>();

	learn(people: readonly PersonDto[] | undefined) {
		for (const person of people ?? []) this.#names.set(person.id, person.name ?? null);
	}

	/** The name, null for an unnamed person, undefined while unknown. */
	name(id: string) {
		return this.#names.get(id);
	}

	ensure(ids: readonly string[]) {
		for (const id of ids) {
			if (this.#names.has(id) || this.#asked.has(id)) continue;
			this.#asked.add(id);
			getApiPeopleById({ path: { id } }).then(({ data }) => {
				if (data) this.#names.set(id, data.name ?? null);
			});
		}
	}
}
