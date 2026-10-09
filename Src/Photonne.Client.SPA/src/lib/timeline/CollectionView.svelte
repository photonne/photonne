<script lang="ts">
	import { onDestroy, tick, untrack, type Snippet } from 'svelte';
	import { useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import DownloadFormatDialog from '#lib/actions/DownloadFormatDialog.svelte';
	import ShareAssetsDialog from '#lib/actions/ShareAssetsDialog.svelte';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import { getAssetDetailQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
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

	type Action = 'favorite' | 'album' | 'folder' | 'share' | 'download' | 'archive' | 'trash';

	interface Props {
		store: ListStore;
		title: string;
		status: 'pending' | 'error' | 'ready';
		emptyText: string;
		/** Under the empty text: what to do about it. */
		emptyHint?: string | null;
		/** The way out of the empty state (a .btn link or button). */
		emptyAction?: Snippet;
		/** A short fact next to the title ("31 resultados"). */
		count?: string | null;
		/** Before the title (a person's avatar). */
		leading?: Snippet;
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
		/**
		 * A cover photo: the title and header then sit on it as a banner, which
		 * folds to a slim bar once the grid scrolls.
		 */
		cover?: string | null;
		onnearend?: () => void;
	}

	let {
		store,
		title,
		status,
		emptyText,
		emptyHint = null,
		emptyAction,
		count = null,
		leading,
		headers = true,
		available,
		selectionActions,
		viewerActions = ['trash', 'archive', 'album'],
		viewerExtra,
		toolbar,
		header,
		cover = null,
		onnearend
	}: Props = $props();

	let scrolled = $state(false);
	/** The banner's folded height and its smallest open one (see .banner below). */
	const FOLDED_HEIGHT = 72;
	const MIN_OPEN_HEIGHT = 180;
	/** What the banner gives back when it folds: its height, open, minus the folded one. */
	let bannerHeight = $state(0);
	// While it unfolds again its height is still growing: count at least the minimum.
	const foldGain = $derived(Math.max(bannerHeight, MIN_OPEN_HEIGHT) - FOLDED_HEIGHT);
	// A bigger rendition for the banner when the server has one.
	const coverSrc = $derived(cover?.replace(/([?&]size=)Medium\b/, '$1Large') ?? null);
	let coverFailed = $state(false);

	const queryClient = useQueryClient();
	const selection = new Selection();
	// A view keeps the store it was created with for its whole life.
	const batch = new BatchActions(
		untrack(() => store),
		selection
	);
	// Whatever takes items out (a batch action, a drop on another folder…)
	// takes them out of the selection too.
	onDestroy(untrack(() => store).onremove((ids) => selection.set(ids, false)));
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
		} else if (change === 'date' || change === 'added') {
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

	{#if cover}
		<section
			class="banner"
			class:folded={scrolled}
			aria-labelledby="collection-title"
			bind:clientHeight={bannerHeight}
		>
			<img
				src={coverFailed ? cover : coverSrc}
				alt=""
				decoding="async"
				onerror={() => (coverFailed = true)}
			/>
			<div class="banner-text">
				<h1 id="collection-title">
					{title}{#if count}<span class="count">{count}</span>{/if}
				</h1>
				<div class="banner-header">{@render header?.()}</div>
			</div>
		</section>
		{#if toolbar}<div class="head bar"><div class="toolbar">{@render toolbar()}</div></div>{/if}
	{:else}
		{#if leading}
			<div class="with-leading">
				<div class="leading">{@render leading()}</div>
				<PageHeader {title} {count} actions={toolbar} />
			</div>
		{:else}
			<PageHeader {title} {count} actions={toolbar} />
		{/if}
		{@render header?.()}
	{/if}

	{#if status === 'pending'}
		<Skeleton variant="grid" />
	{:else if status === 'error'}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if store.items.length === 0}
		<EmptyState icon="photos" title={emptyText} hint={emptyHint} action={emptyAction} compact />
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
				onscrolled={(top, max) => {
					// Only folds when the grid would still scroll once it gets the
					// banner's room; otherwise folding would undo the scroll and loop.
					if (top <= 0) scrolled = false;
					else if (top > 24 && max > foldGain) scrolled = true;
				}}
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
		onshare={() => viewer.openId && batch.share([viewer.openId])}
		ondownload={() => viewer.openId && batch.download([viewer.openId])}
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

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--page-gutter) 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.banner {
		position: relative;
		flex: none;
		height: clamp(180px, 28vh, 300px);
		margin: var(--space-3) var(--page-gutter) 0;
		overflow: hidden;
		border-radius: var(--radius-lg);
		background: #1d1d1f;
		color: #fff;
		transition: height var(--duration-normal) ease;
		/* What the header snippet draws (description, badges) reads on the photo. */
		--color-text: #fff;
		--color-text-muted: rgb(255 255 255 / 0.82);
		--color-surface: rgb(255 255 255 / 0.18);
	}

	.banner.folded {
		height: 72px;
	}

	.banner img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.banner::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to top, rgb(0 0 0 / 0.72), rgb(0 0 0 / 0.1) 60%, transparent);
	}

	.banner-text {
		position: absolute;
		inset: auto 0 0;
		z-index: 1;
		display: grid;
		gap: var(--space-1);
		padding: var(--space-4) var(--space-6);
	}

	.banner h1 {
		font-size: var(--font-size-2xl);
		line-height: 1.15;
		text-shadow: 0 1px 8px rgb(0 0 0 / 0.4);
		transition: font-size var(--duration-normal) ease;
	}

	.banner-header :global(.about) {
		padding: 0;
	}

	.folded h1 {
		font-size: var(--font-size-lg);
	}

	.folded .banner-header {
		display: none;
	}

	/* A select among the page's buttons takes their small height. */
	.page :global(.page-header .actions select),
	.toolbar :global(select) {
		min-height: var(--control-h-sm);
	}

	.with-leading {
		display: flex;
		align-items: center;
		padding-left: var(--page-gutter);
	}

	.leading {
		flex: none;
		padding-top: var(--space-3);
	}

	.with-leading > :global(.page-header) {
		flex: 1;
		min-width: 0;
		padding-left: var(--space-4);
	}

	.banner .count {
		margin-left: var(--space-3);
		font-size: var(--font-size-sm);
		font-weight: 500;
		color: var(--color-text-muted);
		text-shadow: none;
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
		margin: 0;
		padding: var(--space-4) var(--page-gutter);
		color: var(--color-text-muted);
	}
</style>
