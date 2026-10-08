<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { SvelteMap, SvelteSet } from 'svelte/reactivity';
	import {
		getTimelineBucketItemsOptions,
		getTimelineBucketsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { longDate, monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import PhotoGrid from '#lib/timeline/PhotoGrid.svelte';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { toGridAsset, type GridAsset } from '#lib/timeline/types.js';

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
				onopen={() => {}}
			/>
		</div>
	{/if}
</div>

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
