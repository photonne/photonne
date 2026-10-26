<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { getTimelineBucketsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { longDate, monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import PhotoGrid from '#lib/timeline/PhotoGrid.svelte';
	import { selectionSummary } from '#lib/timeline/selection-summary.js';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { TimelineStore } from '#lib/timeline/timeline-store.svelte.js';
	import type { GridAsset } from '#lib/timeline/types.js';

	/**
	 * Chooses photos from the whole library, for the album or folder that is
	 * open ("Añadir fotos"). The timeline's own grid, months and all: a click
	 * selects instead of opening the viewer, and Shift extends the range.
	 * Mounted only while open, so each time starts with nothing chosen.
	 */
	interface Props {
		title: string;
		/** The confirm button for a number of photos ("Añadir 3"). */
		confirmLabel: (count: number) => string;
		/** Resolves once done; the dialog stays open (and busy) until then. */
		onconfirm: (ids: string[]) => Promise<void>;
		onclose: () => void;
	}

	let { title, confirmLabel, onconfirm, onclose }: Props = $props();

	const queryClient = useQueryClient();
	const buckets = createQuery(() => getTimelineBucketsOptions());
	const store = new TimelineStore(queryClient, () => buckets.data);
	const selection = new Selection();
	let busy = $state(false);

	const summary = $derived(selectionSummary(store.itemsByIds(selection.ids)));

	function itemLabel(item: GridAsset) {
		const date = longDate(item.capturedAt);
		return item.isVideo ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}

	async function confirm() {
		if (busy || !selection.active) return;
		busy = true;
		try {
			await onconfirm([...selection.ids]);
		} finally {
			busy = false;
		}
	}
</script>

<Dialog open {title} onclose={() => !busy && onclose()} width="min(1100px, 100vw)">
	<div class="picker">
		{#if buckets.isPending}
			<Skeleton variant="grid" />
		{:else if store.sections.length === 0}
			<EmptyState icon="photos" title={m.timeline_empty_title()} />
		{:else}
			<PhotoGrid
				sections={store.sections}
				{selection}
				label={title}
				sectionTitle={monthTitle}
				{itemLabel}
				onneedsection={(key) => store.load(key)}
				onloadsections={(keys) => store.loadMany(keys)}
				onopen={(item) => selection.toggle(item.id)}
			/>
		{/if}
	</div>

	{#snippet actions()}
		<p class="status" aria-live="polite">
			{#if selection.active}
				<strong>{m.selection_count({ count: selection.size })}</strong>
				{#if summary}<span>{summary}</span>{/if}
			{:else}
				{m.picker_hint()}
			{/if}
		</p>
		<button type="button" disabled={busy} onclick={onclose}>{m.dialog_cancel()}</button>
		<button type="button" class="primary" disabled={busy || !selection.active} onclick={confirm}>
			{confirmLabel(selection.size)}
		</button>
	{/snippet}
</Dialog>

<style>
	.picker {
		height: min(70dvh, 720px);
		/* The grid's floating pills are positioned against it. */
		position: relative;
	}

	.status {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-3);
		flex: 1;
		min-width: 0;
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.status strong {
		color: var(--color-text);
	}
</style>
