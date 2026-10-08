import type { QueryClient } from '@tanstack/svelte-query';
import {
	postApiFacesByIdAssign,
	postApiPeopleByIdHide,
	postApiPeopleByIdUnhide
} from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import { invalidatePeople } from './cache.js';

/** Runs `call` for each id in turn; how many succeeded before the first failure. */
export async function eachUntilError(
	ids: readonly string[],
	call: (id: string) => Promise<{ error?: unknown }>
) {
	let done = 0;
	for (const id of ids) {
		const { error } = await call(id);
		if (error) break;
		done++;
	}
	return done;
}

/** Hides or shows people, with Undo; true when all of them changed. */
export async function setPeopleHidden(
	queryClient: QueryClient,
	ids: readonly string[],
	hidden: boolean
): Promise<boolean> {
	const apply = hidden ? postApiPeopleByIdHide : postApiPeopleByIdUnhide;
	const revert = hidden ? postApiPeopleByIdUnhide : postApiPeopleByIdHide;
	const done = await eachUntilError(ids, (id) => apply({ path: { id } }));
	await invalidatePeople(queryClient);
	if (done < ids.length) toasts.error(m.action_failed());
	if (done > 0) {
		const changed = ids.slice(0, done);
		toasts.show(
			hidden ? m.people_hidden_toast({ count: done }) : m.people_unhidden_toast({ count: done }),
			{
				action: {
					label: m.action_undo(),
					run: async () => {
						const undone = await eachUntilError(changed, (id) => revert({ path: { id } }));
						if (undone < changed.length) toasts.error(m.action_failed());
						await invalidatePeople(queryClient);
					}
				}
			}
		);
	}
	return done === ids.length;
}

/**
 * Puts faces (back) on a person: Undo of "not this person", "move to" and
 * "not a face", since assigning also clears a rejection.
 */
export async function assignFaces(
	queryClient: QueryClient,
	faceIds: readonly string[],
	personId: string
) {
	const done = await eachUntilError(faceIds, (id) =>
		postApiFacesByIdAssign({ path: { id }, body: { personId, newPersonName: null } })
	);
	if (done < faceIds.length) toasts.error(m.action_failed());
	await invalidatePeople(queryClient);
}
