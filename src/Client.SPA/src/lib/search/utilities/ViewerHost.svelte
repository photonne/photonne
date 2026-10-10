<script lang="ts">
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import AssetViewer from '#lib/viewer/AssetViewer.svelte';
	import type { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	/**
	 * The photo viewer over a list that isn't a grid (duplicate groups, the
	 * large files table): its place is the URL's ?asset=, like everywhere.
	 */
	interface Props {
		viewer: ViewerRoute;
		/** The ids in the order the arrows walk them. */
		order: readonly string[];
		versionOf: (id: string) => string | null | undefined;
		/** Offered when the host can take the photo out of its list. */
		ontrash?: (id: string) => void;
		/** Where focus goes back to after closing. */
		onclosed?: (id: string) => void;
	}

	let { viewer, order, versionOf, ontrash, onclosed }: Props = $props();

	const neighbors = $derived(
		viewer.openId ? neighborsIn(order, viewer.openId) : { previous: null, next: null }
	);

	async function close() {
		const last = await viewer.close();
		if (last) onclosed?.(last);
	}
</script>

{#if viewer.openId}
	<AssetViewer
		assetId={viewer.openId}
		thumbnailVersion={versionOf(viewer.openId)}
		previous={neighbors.previous}
		next={neighbors.next}
		neighborVersion={versionOf}
		onnavigate={(id) => viewer.navigate(id)}
		onclose={close}
		onchanged={() => {}}
		ontrash={ontrash
			? () => {
					const id = viewer.openId;
					if (!id) return;
					const target = neighbors.next ?? neighbors.previous;
					if (target) viewer.navigate(target);
					else close();
					ontrash(id);
				}
			: undefined}
	/>
{/if}
