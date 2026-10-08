<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import {
		getAssetDetailQueryKey,
		getTimelineBucketsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { longDate, monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import PhotoGrid from '#lib/timeline/PhotoGrid.svelte';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { TimelineStore } from '#lib/timeline/timeline-store.svelte.js';
	import type { GridAsset } from '#lib/timeline/types.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';

	const queryClient = useQueryClient();
	const buckets = createQuery(() => getTimelineBucketsOptions());
	const store = new TimelineStore(queryClient, () => buckets.data);
	const selection = new Selection();
	const batch = new BatchActions(store, selection);

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	// --- Viewer ----------------------------------------------------------
	// The open asset lives in the URL (?asset=…): Back closes it, a link opens
	// it, and the grid stays mounted underneath with its scroll intact.

	let grid = $state<PhotoGrid<GridAsset>>();
	let openedHere = false;
	let pickingAlbumFor = $state<string | null>(null);

	const openId = $derived(page.url.searchParams.get('asset'));
	const neighbors = $derived(
		openId ? neighborsIn(store.order, openId) : { previous: null, next: null }
	);

	// Reaching the last loaded photo pulls in the next month, so the arrow
	// keeps going across month boundaries.
	$effect(() => {
		if (!openId || neighbors.next) return;
		const following = store.nextUnloadedAfter(openId);
		if (following) store.load(following);
	});

	function viewerUrl(assetId: string | null) {
		const url = new URL(page.url.href);
		if (assetId) url.searchParams.set('asset', assetId);
		else url.searchParams.delete('asset');
		return url.pathname + url.search;
	}

	function openViewer(item: GridAsset) {
		openedHere = true;
		goto(viewerUrl(item.id), { reset: false });
	}

	function navigateViewer(assetId: string) {
		goto(viewerUrl(assetId), { replace: true, reset: false });
	}

	async function closeViewer() {
		const last = openId;
		if (openedHere) history.back();
		else await goto(viewerUrl(null), { replace: true, reset: false });
		openedHere = false;
		await tick();
		if (last) grid?.focusItem(last);
	}

	/** After the shown photo leaves the timeline: next, else previous, else close. */
	function leaveViewer() {
		const target = neighbors.next ?? neighbors.previous;
		if (target) navigateViewer(target);
		else closeViewer();
	}

	async function removeFromViewer(remove: (ids: readonly string[]) => Promise<void>) {
		if (!openId) return;
		const id = openId;
		leaveViewer();
		await remove([id]);
	}

	function assetChanged(assetId: string, change: AssetChange) {
		if (change === 'favorite') {
			const detail = queryClient.getQueryData<AssetDetailResponse>(
				getAssetDetailQueryKey({ path: { assetId } })
			);
			store.update([assetId], (item) => ({
				...item,
				isFavorite: detail?.isFavorite ?? !item.isFavorite
			}));
		} else if (change === 'date') {
			// The photo may now belong to another month: rebuild the skeleton.
			store.reload();
		}
	}
</script>

<svelte:head>
	<title>{m.photos_title()} · {m.app_name()}</title>
</svelte:head>

<h1 class="visually-hidden">{m.photos_title()}</h1>

<div class="page">
	<SelectionBar {selection}>
		{#snippet actions()}
			<BatchActionBar actions={batch} {selection} />
		{/snippet}
	</SelectionBar>

	{#if buckets.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if buckets.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if store.sections.length === 0}
		<p class="status">{m.photos_empty()}</p>
	{:else}
		<div class="grid">
			<PhotoGrid
				sections={store.sections}
				{selection}
				label={m.photos_title()}
				sectionTitle={monthTitle}
				{itemLabel}
				onneedsection={(key) => store.load(key)}
				onopen={openViewer}
				bind:this={grid}
			/>
		</div>
	{/if}
</div>

{#if openId}
	<AssetViewer
		assetId={openId}
		thumbnailVersion={store.byId.get(openId)?.thumbnailVersion}
		previous={neighbors.previous}
		next={neighbors.next}
		neighborVersion={(id) => store.byId.get(id)?.thumbnailVersion}
		onnavigate={navigateViewer}
		onclose={closeViewer}
		onchanged={assetChanged}
		ontrash={() => removeFromViewer((ids) => batch.trash(ids))}
		onarchive={() => removeFromViewer((ids) => batch.archive(ids))}
		onaddtoalbum={() => (pickingAlbumFor = openId)}
	/>
	<AlbumPickerDialog
		open={pickingAlbumFor !== null}
		onclose={() => (pickingAlbumFor = null)}
		onpick={(album) => {
			const id = pickingAlbumFor;
			pickingAlbumFor = null;
			if (id) batch.addToAlbum(album, [id]);
		}}
	/>
{/if}

<style>
	.page {
		position: relative;
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.grid {
		flex: 1;
		min-height: 0;
	}

	.status {
		padding: var(--space-6);
		color: var(--color-text-muted);
	}
</style>
