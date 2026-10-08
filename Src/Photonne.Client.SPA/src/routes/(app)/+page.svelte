<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import DownloadFormatDialog from '#lib/actions/DownloadFormatDialog.svelte';
	import ShareAssetsDialog from '#lib/actions/ShareAssetsDialog.svelte';
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
	import TimelineTools from '#lib/timeline/TimelineTools.svelte';
	import { TimelineView } from '#lib/timeline/timeline-view.svelte.js';
	import type { GridAsset } from '#lib/timeline/types.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const queryClient = useQueryClient();
	const buckets = createQuery(() => getTimelineBucketsOptions());
	const store = new TimelineStore(queryClient, () => buckets.data);
	const selection = new Selection();
	const batch = new BatchActions(store, selection);

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	// --- Zoom and "go to a date" ------------------------------------------

	const view = new TimelineView(() => store.sections);

	// The year view's sampled photos aren't the timeline's: they can't be
	// selected, and opening one goes to its month instead of the viewer.
	$effect(() => {
		if (view.yearView) selection.clear();
	});

	function yearItemLabel(item: GridAsset) {
		return m.timeline_year_item({
			month: monthTitle(item.capturedAt.slice(0, 7)),
			date: longDate(item.capturedAt)
		});
	}

	async function openMonthOf(item: GridAsset) {
		view.setZoom('small');
		await tick();
		await jumpTo(item.capturedAt.slice(0, 7));
		await tick();
		grid?.focusItem(item.id);
	}

	async function jumpTo(monthKey: string) {
		const shown = grid?.scrollToSection(monthKey);
		if (shown && shown.length === 7) await store.load(shown);
	}

	// --- Viewer (its place in the URL: see ViewerRoute) -----------------

	const viewer = new ViewerRoute();
	let grid = $state<PhotoGrid<GridAsset>>();
	let pickingAlbumFor = $state<string | null>(null);

	const openId = $derived(viewer.openId);
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

	const openViewer = (item: GridAsset) => viewer.open(item.id);
	const navigateViewer = (assetId: string) => viewer.navigate(assetId);

	async function closeViewer() {
		const last = await viewer.close();
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
		} else if (change === 'date' || change === 'added') {
			// The photo may now belong to another month, or a new one (a saved
			// Live Photo frame) joined it: rebuild the skeleton.
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
		<TimelineTools
			{view}
			months={buckets.data ?? []}
			current={() => grid?.currentSection() ?? null}
			onjump={jumpTo}
			paused={!!openId}
		/>
		<div class="grid" bind:clientWidth={view.width}>
			<PhotoGrid
				sections={view.sections}
				{selection}
				label={m.photos_title()}
				sectionTitle={view.sectionTitle}
				sectionSubtitle={view.sectionSubtitle}
				itemLabel={view.yearView ? yearItemLabel : itemLabel}
				onneedsection={(key) => store.load(key)}
				onopen={view.yearView ? openMonthOf : openViewer}
				zoom={view.zoom}
				onzoom={(direction) => view.step(direction)}
				reflowKey={view.yearView ? 'years' : 'months'}
				selectable={!view.yearView}
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
		onshare={() => openId && batch.share([openId])}
		ondownload={() => openId && batch.download([openId])}
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

<ShareAssetsDialog
	assetIds={batch.sharing}
	onclose={() => batch.closeShare()}
	oncreated={() => batch.shared()}
/>
<DownloadFormatDialog downloader={batch.downloader} />

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
