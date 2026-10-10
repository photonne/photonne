import { neighborsIn } from '#lib/viewer/neighbors.js';
import type { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

/**
 * Runs an action that takes the open photo out of the list (unarchive,
 * restore, delete for good) the way CollectionView does for trash/archive:
 * the viewer moves on to the next photo (or the previous one, or closes)
 * first, so it never shows a photo that's gone.
 */
export async function leaveViewerThen(
	viewer: ViewerRoute,
	order: readonly string[],
	run: (ids: readonly string[]) => Promise<void>
) {
	const id = viewer.openId;
	if (!id) return;
	const { previous, next } = neighborsIn(order, id);
	const target = next ?? previous;
	if (target) await viewer.navigate(target);
	else await viewer.close();
	await run([id]);
}
