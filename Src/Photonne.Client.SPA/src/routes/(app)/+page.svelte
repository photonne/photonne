<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { SvelteMap, SvelteSet } from 'svelte/reactivity';
	import {
		getAssetDetailQueryKey,
		getTimelineBucketItemsOptions,
		getTimelineBucketsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import { longDate, monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import PhotoGrid from '#lib/timeline/PhotoGrid.svelte';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { toGridAsset, type GridAsset } from '#lib/timeline/types.js';
	import AssetViewer, { type AssetChange } from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';

	const queryClient = useQueryClient();
	const buckets = createQuery(() => getTimelineBucketsOptions());

	// Months are loaded on demand as they approach the viewport; until then
	// they are placeholders sized from their count (the bucket model).
	const months = new SvelteMap<string, GridAsset[]>();
	const requested = new SvelteSet<string>();
	const selection = new Selection();

	const sections = $derived(
		(buckets.data ?? []).map((bucket) => ({
			key: bucket.key,
			count: bucket.count,
			items: months.get(bucket.key) ?? null
		}))
	);

	async function loadMonth(key: string) {
		if (requested.has(key)) return;
		requested.add(key);
		try {
			const items = await queryClient.fetchQuery(
				getTimelineBucketItemsOptions({ path: { yearMonth: key } })
			);
			months.set(key, items.map(toGridAsset));
		} catch {
			// Retried the next time the month scrolls into reach.
			requested.delete(key);
		}
	}

	// --- Viewer ----------------------------------------------------------
	// The open asset lives in the URL (?asset=…): Back closes it, a link opens
	// it, and the grid stays mounted underneath with its scroll intact.

	let grid = $state<PhotoGrid<GridAsset>>();
	let openedHere = false;

	const openId = $derived(page.url.searchParams.get('asset'));
	const order = $derived(sections.flatMap((section) => section.items?.map((i) => i.id) ?? []));
	const byId = $derived(
		new Map(sections.flatMap((section) => section.items?.map((i) => [i.id, i] as const) ?? []))
	);
	const neighbors = $derived(openId ? neighborsIn(order, openId) : { previous: null, next: null });

	// Reaching the last loaded photo pulls in the next month, so the arrow
	// keeps going across month boundaries.
	$effect(() => {
		if (!openId || neighbors.next) return;
		const index = sections.findIndex((s) => s.items?.some((i) => i.id === openId));
		const following = sections.slice(index + 1).find((s) => s.items === null);
		if (index >= 0 && following) loadMonth(following.key);
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

	function assetChanged(assetId: string, change: AssetChange) {
		if (change === 'favorite') {
			const detail = queryClient.getQueryData<AssetDetailResponse>(
				getAssetDetailQueryKey({ path: { assetId } })
			);
			updateItem(assetId, (item) => ({
				...item,
				isFavorite: detail?.isFavorite ?? !item.isFavorite
			}));
		} else if (change === 'date') {
			// The photo may now belong to another month: rebuild the skeleton.
			months.clear();
			requested.clear();
			queryClient.invalidateQueries({ queryKey: getTimelineBucketsOptions().queryKey });
		}
	}

	function updateItem(assetId: string, update: (item: GridAsset) => GridAsset) {
		for (const [key, items] of months) {
			const index = items.findIndex((item) => item.id === assetId);
			if (index >= 0) {
				months.set(key, items.toSpliced(index, 1, update(items[index])));
				return;
			}
		}
	}

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}
</script>

<svelte:head>
	<title>{m.photos_title()} · {m.app_name()}</title>
</svelte:head>

<h1 class="visually-hidden">{m.photos_title()}</h1>

<div class="page">
	<SelectionBar {selection} />

	{#if buckets.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if buckets.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if sections.length === 0}
		<p class="status">{m.photos_empty()}</p>
	{:else}
		<div class="grid">
			<PhotoGrid
				{sections}
				{selection}
				label={m.photos_title()}
				sectionTitle={monthTitle}
				{itemLabel}
				onneedsection={loadMonth}
				onopen={openViewer}
				bind:this={grid}
			/>
		</div>
	{/if}
</div>

{#if openId}
	<AssetViewer
		assetId={openId}
		thumbnailVersion={byId.get(openId)?.thumbnailVersion}
		previous={neighbors.previous}
		next={neighbors.next}
		neighborVersion={(id) => byId.get(id)?.thumbnailVersion}
		onnavigate={navigateViewer}
		onclose={closeViewer}
		onchanged={assetChanged}
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
