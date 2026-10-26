<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import { appHref } from '#lib/navigation/href.js';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import DownloadFormatDialog from '#lib/actions/DownloadFormatDialog.svelte';
	import ShareAssetsDialog from '#lib/actions/ShareAssetsDialog.svelte';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import {
		getAssetDetailOptions,
		getAssetDetailQueryKey,
		getTimelineBucketsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageChrome from '#lib/components/PageChrome.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { longDate, monthTitle } from '#lib/format.js';
	import Icon from '#lib/components/Icon.svelte';
	import { leaveViewerThen } from '#lib/library/viewer-flow.js';
	import { m } from '#lib/paraglide/messages.js';
	import { gridLayout } from '#lib/timeline/grid-layout-preference.svelte.js';
	import PhotoGrid, { type GridPosition } from '#lib/timeline/PhotoGrid.svelte';
	import { selectionSummary } from '#lib/timeline/selection-summary.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { TimelineStore } from '#lib/timeline/timeline-store.svelte.js';
	import type { TimelineFilter } from '#lib/timeline/timeline-filter.js';
	import TimelineTools from '#lib/timeline/TimelineTools.svelte';
	import { WHEEL_STEP } from '#lib/timeline/zoom.js';
	import { TimelineView } from '#lib/timeline/timeline-view.svelte.js';
	import type { GridAsset } from '#lib/timeline/types.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import type { Snapshot } from './$types';

	const queryClient = useQueryClient();
	const buckets = createQuery(() => getTimelineBucketsOptions());
	// The filters (order, a day or a range) live in the view, made below.
	const store: TimelineStore = new TimelineStore(
		queryClient,
		() => buckets.data,
		(): TimelineFilter => view.filter
	);
	const selection = new Selection();
	const batch = new BatchActions(store, selection);

	// The header's facts: how many items, and the years they span (or the
	// days chosen in the filters).
	const total = $derived(store.sections.reduce((sum, month) => sum + month.count, 0));
	const span = $derived.by(() => {
		const range = view.range;
		if (range)
			return range.from === range.to
				? m.filters_active_day({ date: longDate(range.from) })
				: m.filters_active_range({ from: longDate(range.from), to: longDate(range.to) });
		const years = store.sections
			.filter((month) => month.count > 0)
			.map((month) => Number(month.key.slice(0, 4)));
		if (years.length === 0) return null;
		const [from, to] = [Math.min(...years), Math.max(...years)];
		return from === to ? String(from) : m.timeline_range({ from, to });
	});

	// A range's first and last months only count the right days once
	// loaded: load them now, so the header says how many there are.
	$effect(() => {
		if (!view.range) return;
		const keys = store.sections.map((section) => section.key);
		const ends = [keys[0], keys.at(-1)].filter((key): key is string => !!key);
		untrack(() => store.loadMany(ends));
	});
	const facts = $derived(
		[m.timeline_year_items({ count: total }), span].filter(Boolean).join(' · ')
	);

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	// --- Zoom and "go to a date" ------------------------------------------

	const view: TimelineView = new TimelineView(() => store.sections);

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
		view.setGrouping('month');
		await tick();
		await jumpTo(item.capturedAt.slice(0, 7));
		await tick();
		grid?.focusItem(item.id);
	}

	async function jumpTo(monthKey: string) {
		const shown = grid?.scrollToSection(monthKey);
		if (shown && shown.length === 7) await store.load(shown);
	}

	// Back from another page lands where the timeline was, not at the top.
	let restoring = $state<GridPosition | null>(null);

	export const snapshot: Snapshot<GridPosition | null> = {
		capture: () => grid?.position() ?? null,
		restore: (value) => (restoring = value)
	};

	$effect(() => {
		if (!grid || !restoring) return;
		grid.restorePosition(restoring);
		restoring = null;
	});

	// A link to a month (?month=yyyy-MM, the viewer's "Ver en Fotos"): scroll
	// there once the grid is up, then drop it so a reload doesn't jump again.
	let jumpedTo: string | null = null;
	$effect(() => {
		const month = page.url.searchParams.get('month');
		if (!month || !/^\d{4}-\d{2}$/.test(month) || jumpedTo === month) return;
		if (!grid || store.sections.length === 0) return;
		jumpedTo = month;
		untrack(() => void jumpTo(month));
		const url = new URL(page.url.href);
		url.searchParams.delete('month');
		replaceState(url, page.state);
	});

	// --- Viewer (its place in the URL: see ViewerRoute) -----------------

	const viewer = new ViewerRoute();
	let grid = $state<PhotoGrid<GridAsset>>();
	let pickingAlbumFor = $state<string | null>(null);

	const openId = $derived(viewer.openId);
	const neighbors = $derived(
		openId ? neighborsIn(store.order, openId) : { previous: null, next: null }
	);

	// Reaching the last (or first) loaded photo pulls in the next (or
	// previous) month, so the arrows keep going across month boundaries.
	$effect(() => {
		if (!openId || neighbors.next) return;
		const following = store.nextUnloadedAfter(openId);
		if (following) store.load(following);
	});

	$effect(() => {
		if (!openId || neighbors.previous) return;
		const preceding = store.previousUnloadedBefore(openId);
		if (preceding) store.load(preceding);
	});

	// A link to a photo (?asset=…) whose month isn't loaded: load that month,
	// found from the photo's capture date, so it gets its neighbours.
	let linkedMonthFor: string | null = null;
	$effect(() => {
		const id = openId;
		if (!id || !buckets.data || store.byId.has(id) || linkedMonthFor === id) return;
		linkedMonthFor = id;
		queryClient
			.fetchQuery(getAssetDetailOptions({ path: { assetId: id } }))
			.then((detail) => store.load(detail.capturedAt.slice(0, 7)))
			.catch(() => {
				// The viewer shows its own error for a photo that can't be read.
			});
	});

	const openViewer = (item: GridAsset) => viewer.open(item.id);
	const navigateViewer = (assetId: string) => viewer.navigate(assetId);

	function closeViewer() {
		viewer.close();
	}

	// However the viewer closes (its button, Escape, the browser's Back),
	// focus goes back to the cell of the last photo shown.
	let lastOpen: string | null = null;
	$effect(() => {
		const id = openId;
		const before = lastOpen;
		lastOpen = id;
		if (id || !before) return;
		tick().then(() => grid?.focusItem(before));
	});

	/** After the shown photo leaves the timeline: next, else previous, else close. */
	function removeFromViewer(remove: (ids: readonly string[]) => Promise<void>) {
		return leaveViewerThen(viewer, store.order, remove);
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

{#snippet tools()}
	<TimelineTools
		{view}
		months={buckets.data ?? []}
		current={() => grid?.currentSection() ?? null}
		onjump={jumpTo}
		paused={!!openId}
	/>
{/snippet}

<!-- The whole window: the title scrolls away with the photos and the
     filters and view options wait in the floating chrome. -->
<PageChrome bleed actions={(buckets.data?.length ?? 0) > 0 ? tools : undefined} />

{#snippet heading()}
	<PageHeader title={m.photos_title()} count={store.sections.length > 0 ? facts : null} />
{/snippet}

<div class="page">
	<SelectionBar {selection} summary={selectionSummary(store.itemsByIds(selection.ids))}>
		{#snippet actions()}
			<BatchActionBar actions={batch} {selection} />
		{/snippet}
	</SelectionBar>

	{#if buckets.isPending || buckets.isError || store.sections.length === 0}
		<div class="scroll">
			{@render heading()}
			{#if buckets.isPending}
				<Skeleton variant="grid" />
			{:else if buckets.isError}
				<p class="status" role="alert">{m.error_loading()}</p>
			{:else if (buckets.data?.length ?? 0) > 0}
				<!-- The filters leave nothing. -->
				<EmptyState icon="photos" title={m.filters_none_in_range()}>
					{#snippet action()}
						<button
							type="button"
							class="btn"
							onclick={() => {
								view.setRange(null);
								view.setOrder('newest');
							}}>{m.filters_clear()}</button
						>
					{/snippet}
				</EmptyState>
			{:else}
				<EmptyState icon="photos" title={m.timeline_empty_title()} hint={m.timeline_empty_hint()}>
					{#snippet action()}
						<a class="btn primary" href={appHref('/upload')}>
							<Icon name="upload" size={18} />
							{m.timeline_upload()}
						</a>
					{/snippet}
				</EmptyState>
			{/if}
		</div>
	{:else}
		<div class="grid" bind:clientWidth={view.width}>
			<PhotoGrid
				sections={view.sections}
				{selection}
				label={m.photos_title()}
				sectionTitle={view.sectionTitle}
				sectionSubtitle={view.sectionSubtitle}
				itemLabel={view.yearView ? yearItemLabel : itemLabel}
				onneedsection={(key) => store.load(key)}
				onloadsections={(keys) => store.loadMany(keys)}
				onopen={view.yearView ? openMonthOf : openViewer}
				size={view.size}
				grouping={view.yearView ? 'year' : view.grouping === 'day' ? 'day' : 'month'}
				layout={view.yearView ? 'rows' : gridLayout.mode}
				onzoom={(direction) => view.step(direction, WHEEL_STEP)}
				reflowKey={`${view.yearView ? 'years' : 'months'}|${view.order}|${view.range?.from}|${view.range?.to}`}
				selectable={!view.yearView}
				lead={heading}
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

	/* Without the grid (loading, empty…): the header and the rest scroll
	   together, clear of the floating chrome. */
	.scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding-top: var(--bleed-top, 0px);
		padding-bottom: var(--bleed-bottom, 0px);
	}

	.status {
		margin: 0;
		padding: var(--space-4) var(--page-gutter);
		color: var(--color-danger);
	}
</style>
