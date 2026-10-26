import { neighborsIn } from '#lib/viewer/neighbors.js';
import type { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

// Photos on their way out (their action queued or running): a quick second
// Delete moves past them instead of landing on one that's about to go.
const leaving = new Set<string>();

// The last photo the viewer moved past to take it out: an undo brings the
// viewer back to it, so culling with Delete never loses your place.
let lastLeft: { viewer: Pick<ViewerRoute, 'openId' | 'navigate'>; id: string } | null = null;

/**
 * After an undo put [ids] back: if one of them is the photo the open viewer
 * just moved past, shows it again. Nothing when the viewer has closed since.
 */
export function returnToRestored(ids: readonly string[]) {
	const left = lastLeft;
	if (!left || !ids.includes(left.id) || !left.viewer.openId) return;
	lastLeft = null;
	return left.viewer.navigate(left.id);
}

/**
 * Runs an action that takes the open photo out of the list (archive, trash,
 * unarchive, restore, delete for good): the viewer moves on to the next
 * photo (or the previous one, or closes) first, so it never shows a photo
 * that's gone.
 */
export async function leaveViewerThen(
	viewer: ViewerRoute,
	order: readonly string[],
	run: (ids: readonly string[]) => Promise<void>
) {
	const id = viewer.openId;
	if (!id) return;
	const { previous, next } = neighborsIn(
		order.filter((other) => other === id || !leaving.has(other)),
		id
	);
	const target = next ?? previous;
	leaving.add(id);
	lastLeft = { viewer, id };
	try {
		if (target) await viewer.navigate(target);
		else await viewer.close();
		await run([id]);
	} finally {
		leaving.delete(id);
	}
}
