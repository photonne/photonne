<script lang="ts">
	import { tick, untrack, type Snippet } from 'svelte';
	import { useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import { getAssetDetailQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { longDate, monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import type { ListStore } from './list-store.svelte.js';
	import PhotoGrid from './PhotoGrid.svelte';
	import SelectionBar from './SelectionBar.svelte';
	import { Selection } from './selection.svelte.js';
	import type { GridAsset } from './types.js';

	type Action = 'favorite' | 'album' | 'folder' | 'download' | 'archive' | 'trash';

	interface Props {
		store: ListStore;
		title: string;
		status: 'pending' | 'error' | 'ready';
		emptyText: string;
		/** Month headers (off for an album's own order). */
		headers?: boolean;
		/** Batch actions offered on a selection. */
		available?: readonly Action[];
		/** Replaces the standard batch buttons (e.g. the trash's restore/purge). */
		selectionActions?: Snippet<[Selection, BatchActions]>;
		/** What the viewer offers for the open photo. */
		viewerActions?: readonly ('trash' | 'archive' | 'album')[];
		/** The page's own viewer buttons; receives the open asset id. */
		viewerExtra?: Snippet<[string]>;
		/** Next to the title: page-level buttons (empty trash, edit album…). */
		toolbar?: Snippet;
		/** Above the grid, under the title (album description, filters…). */
		header?: Snippet;
		onnearend?: () => void;
	}

	let {
		store,
		title,
		status,
		emptyText,
		headers = true,
		available,
		selectionActions,
		viewerActions = ['trash', 'archive', 'album'],
		viewerExtra,
		toolbar,
		header,
		onnearend
	}: Props = $props();

	const queryClient = useQueryClient();
	const selection = new Selection();
	// A view keeps the store it was created with for its whole life.
	const batch = new BatchActions(
		untrack(() => store),
		selection
	);
	const viewer = new ViewerRoute();

	let grid = $state<PhotoGrid<GridAsset>>();
	let pickingAlbumFor = $state<string | null>(null);

	const neighbors = $derived(
		viewer.openId ? neighborsIn(store.order, viewer.openId) : { previous: null, next: null }
	);

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	async function close() {
		const last = await viewer.close();
		await tick();
		if (last) grid?.focusItem(last);
	}

	async function removeFromViewer(remove: (ids: readonly string[]) => Promise<void>) {
		const id = viewer.openId;
		if (!id) return;
		const target = neighbors.next ?? neighbors.previous;
		if (target) viewer.navigate(target);
		else await close();
		await remove([id]);
	}

	function changed(assetId: string, change: AssetChange) {
		if (change === 'favorite') {
			const detail = queryClient.getQueryData<AssetDetailResponse>(
				getAssetDetailQueryKey({ path: { assetId } })
			);
			store.update([assetId], (item) => ({
				...item,
				isFavorite: detail?.isFavorite ?? !item.isFavorite
			}));
		} else if (change === 'date') {
			store.reload();
		}
	}
</script>

<div class="page">
	<SelectionBar {selection}>
		{#snippet actions()}
			{#if selectionActions}
				{@render selectionActions(selection, batch)}
			{:else}
				<BatchActionBar actions={batch} {selection} {available} />
			{/if}
		{/snippet}
	</SelectionBar>

	<header class="head">
		<h1>{title}</h1>
		{#if toolbar}<div class="toolbar">{@render toolbar()}</div>{/if}
	</header>
	{@render header?.()}

	{#if status === 'pending'}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if status === 'error'}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if store.items.length === 0}
		<p class="status">{emptyText}</p>
	{:else}
		<div class="grid">
			<PhotoGrid
				sections={store.sections}
				{selection}
				{headers}
				label={title}
				sectionTitle={monthTitle}
				{itemLabel}
				onneedsection={() => {}}
				onopen={(item) => viewer.open(item.id)}
				{onnearend}
				bind:this={grid}
			/>
		</div>
	{/if}
</div>

{#if viewer.openId}
	<AssetViewer
		assetId={viewer.openId}
		thumbnailVersion={store.byId[viewer.openId]?.thumbnailVersion}
		previous={neighbors.previous}
		next={neighbors.next}
		neighborVersion={(id) => store.byId[id]?.thumbnailVersion}
		onnavigate={(id) => viewer.navigate(id)}
		onclose={close}
		onchanged={changed}
		ontrash={viewerActions.includes('trash')
			? () => removeFromViewer((ids) => batch.trash(ids))
			: undefined}
		onarchive={viewerActions.includes('archive')
			? () => removeFromViewer((ids) => batch.archive(ids))
			: undefined}
		onaddtoalbum={viewerActions.includes('album')
			? () => (pickingAlbumFor = viewer.openId)
			: undefined}
	>
		{#snippet actions()}
			{#if viewerExtra && viewer.openId}{@render viewerExtra(viewer.openId)}{/if}
		{/snippet}
	</AssetViewer>
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

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-4) 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.toolbar {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
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
