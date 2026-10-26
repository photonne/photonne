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
	import type { IconName } from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { longDate, monthTitle } from '#lib/format.js';
	import { leaveViewerThen } from '#lib/library/viewer-flow.js';
	import { m } from '#lib/paraglide/messages.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import { gridLayout } from './grid-layout-preference.svelte.js';
	import type { ListStore } from './list-store.svelte.js';
	import PhotoGrid from './PhotoGrid.svelte';
	import SelectionBar from './SelectionBar.svelte';
	import { collectionSize } from './size-preference.svelte.js';
	import PageChrome from '#lib/components/PageChrome.svelte';
	import ViewOptions from './ViewOptions.svelte';
	import { Selection } from './selection.svelte.js';
	import type { CellNote, GridAsset } from './types.js';
	import { sizeKeyDirection, WHEEL_STEP } from './zoom.js';

	type Action = 'favorite' | 'album' | 'folder' | 'share' | 'download' | 'archive' | 'trash';

	interface Props {
		store: ListStore;
		title: string;
		status: 'pending' | 'error' | 'ready';
		emptyText: string;
		/** The empty state's icon: an Icon.svelte name or a 24×24 path from icons.ts. */
		emptyIcon?: IconName | { path: string };
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
		/** Page-level buttons (empty trash, edit album…): in the floating chrome. */
		toolbar?: Snippet;
		/** Above the grid, under the title (album description, filters…). */
		header?: Snippet;
		/**
		 * The page's own view options (an album's order, a folder's
		 * subfolders), in the view options menu before the layout and size:
		 * ViewOptionsGroup blocks.
		 */
		viewOptionsExtra?: Snippet;
		/**
		 * A cover photo: the title and header then sit on it as a banner, which
		 * folds to a slim bar once the grid scrolls.
		 */
		cover?: string | null;
		/**
		 * The grid put away (a folder showing only its subfolders): nothing
		 * under the header, which then has the room.
		 */
		collapsed?: boolean;
		onnearend?: () => void;
		/** + and − resize the grid; off where they mean something else (the map's zoom). */
		sizeShortcuts?: boolean;
		/**
		 * The size control in the page's toolbar. Off when the page puts it
		 * next to the photos itself (a folder's Fotos section).
		 */
		sizeInToolbar?: boolean;
		/**
		 * The page's own selection, when it chooses photos itself (a proposed
		 * batch in Organizar) or keeps it across views.
		 */
		selection?: Selection;
		/**
		 * How it sits in the window. 'page': the whole of it, the header
		 * scrolling away under the floating chrome, which carries the view
		 * options. 'inline': under the page's own bars (search, admin), the
		 * options still in the chrome. 'none': a part of another page (the
		 * map's panel), the options among its own buttons.
		 */
		chrome?: 'page' | 'inline' | 'none';
		/** A short note on each cell (the trash's days left). */
		cellNote?: (item: GridAsset) => CellNote | null;
	}

	let {
		store,
		title,
		status,
		emptyText,
		emptyIcon = 'photos',
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
		viewOptionsExtra,
		cover = null,
		collapsed = false,
		onnearend,
		sizeShortcuts = true,
		sizeInToolbar = true,
		selection: given,
		chrome = 'page',
		cellNote
	}: Props = $props();

	// A bigger rendition for the banner when the server has one.
	const coverSrc = $derived(cover?.replace(/([?&]size=)Medium\b/, '$1Large') ?? null);
	let coverFailed = $state(false);

	const queryClient = useQueryClient();
	const selection = untrack(() => given) ?? new Selection();
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

	/** Scrolls to a photo and focuses it, once the grid shows it. */
	export async function reveal(id: string) {
		await tick();
		await grid?.focusItem(id);
	}

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	function close() {
		viewer.close();
	}

	/** The grid is on screen, so its size can change. */
	const sizable = $derived(!collapsed && status === 'ready' && store.items.length > 0);
	const sizeInBar = $derived(sizable && sizeInToolbar);
	/** The view options menu: the photos' size, or the page's own options. */
	const hasOptions = $derived(sizeInBar || !!viewOptionsExtra);
	const hasTools = $derived(!!toolbar || hasOptions);
	/** In the header only when it is part of another page; else in the chrome. */
	const tools = $derived(chrome === 'none' && hasTools ? ownTools : undefined);

	// + / − resize the grid, like on the timeline, while nothing covers it.
	function onkeydown(event: KeyboardEvent) {
		if (!sizeShortcuts || !sizable || viewer.openId || pickingAlbumFor !== null) return;
		const direction = sizeKeyDirection(event);
		if (!direction) return;
		event.preventDefault();
		collectionSize.step(direction);
	}

	// However the viewer closes (its button, Escape, the browser's Back),
	// focus goes back to the cell of the last photo shown.
	let lastOpen: string | null = null;
	$effect(() => {
		const id = viewer.openId;
		const before = lastOpen;
		lastOpen = id;
		if (id || !before) return;
		tick().then(() => grid?.focusItem(before));
	});

	function removeFromViewer(remove: (ids: readonly string[]) => Promise<void>) {
		return leaveViewerThen(viewer, store.order, remove);
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

<svelte:window {onkeydown} />

{#snippet viewOptions()}
	<ViewOptions
		size={sizeInBar ? collectionSize.value : null}
		onsize={(size) => collectionSize.set(size)}
	>
		{@render viewOptionsExtra?.()}
	</ViewOptions>
{/snippet}

<!-- The page's buttons, then its view options; inside another page they
     come first, its buttons (a close) last. -->
{#snippet ownTools()}
	{#if hasOptions && chrome === 'none'}{@render viewOptions()}{/if}
	{@render toolbar?.()}
	{#if hasOptions && chrome !== 'none'}{@render viewOptions()}{/if}
{/snippet}

<!-- The whole window: the header scrolls away with the photos, and the
     page's buttons wait in the floating chrome, always at hand. -->
{#if chrome !== 'none'}
	<PageChrome bleed={chrome === 'page'} actions={hasTools ? ownTools : undefined} />
{/if}

{#snippet heading()}
	{#if cover}
		<section class="banner dark-surface" aria-labelledby="collection-title">
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
		{#if tools}<div class="head bar">
				<div class="toolbar">{@render tools()}</div>
			</div>{/if}
	{:else}
		{#if leading}
			<div class="with-leading">
				<div class="leading">{@render leading()}</div>
				<PageHeader {title} {count} actions={tools} float={false} />
			</div>
		{:else}
			<PageHeader {title} {count} actions={tools} float={false} />
		{/if}
		{@render header?.()}
	{/if}
{/snippet}

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

	{#if collapsed || status !== 'ready' || store.items.length === 0}
		<div class="scroll">
			{@render heading()}
			{#if collapsed}
				<!-- The host folded the grid away. -->
			{:else if status === 'pending'}
				<Skeleton variant="grid" />
			{:else if status === 'error'}
				<p class="status" role="alert">{m.error_loading()}</p>
			{:else}
				<!-- Full size like any page's; small only inside another page's panel. -->
				<EmptyState
					icon={typeof emptyIcon === 'string' ? emptyIcon : undefined}
					iconPath={typeof emptyIcon === 'string' ? undefined : emptyIcon.path}
					title={emptyText}
					hint={emptyHint}
					action={emptyAction}
					compact={chrome === 'none'}
				/>
			{/if}
		</div>
	{:else}
		<div class="grid">
			<PhotoGrid
				sections={store.sections}
				{selection}
				{headers}
				layout={gridLayout.mode}
				size={collectionSize.value}
				onzoom={(direction) => collectionSize.step(direction, WHEEL_STEP)}
				label={title}
				sectionTitle={(key) => monthTitle(key.slice(0, 7))}
				{itemLabel}
				{cellNote}
				onneedsection={() => {}}
				onopen={(item) => viewer.open(item.id)}
				{onnearend}
				lead={heading}
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

	/* Under the banner, the toolbar keeps the page header's room below it:
	   the grid scrolls under that margin, not up against the buttons. */
	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--page-gutter) var(--space-3);
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
		border-radius: var(--radius-xl);
		background: var(--color-surface-raised);
		color: #fff;
		/* What the header snippet draws (description, badges) reads on the photo. */
		--color-text: #fff;
		--color-text-muted: rgb(255 255 255 / 0.82);
		--color-surface: rgb(255 255 255 / 0.18);
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
	}

	.banner-header :global(.about) {
		padding: 0;
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
		color: var(--color-text-muted);
	}
</style>
