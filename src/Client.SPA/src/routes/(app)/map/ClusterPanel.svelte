<script lang="ts">
	import { untrack } from 'svelte';
	import type { MapPointResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { ListStore } from '#lib/timeline/list-store.svelte.js';
	import { clusterItems, dateRange, type MapCluster } from './map-model.js';

	interface Props {
		cluster: MapCluster;
		points: ReadonlyMap<string, MapPointResponse>;
		onclose: () => void;
	}

	let { cluster, points, onclose }: Props = $props();

	// One store per open cluster (the page keys this panel by cluster id).
	const store = new ListStore({
		grouping: 'month',
		reload: () => (store.items = clusterItems(cluster, points))
	});
	store.items = untrack(() => clusterItems(cluster, points));

	const range = $derived(dateRange(cluster.earliestDate, cluster.latestDate, getLocale()));
</script>

<aside class="panel" aria-label={m.map_cluster_title({ count: cluster.count })}>
	<CollectionView
		{store}
		title={m.map_cluster_title({ count: store.items.length })}
		status="ready"
		emptyText={m.map_cluster_empty()}
		sizeShortcuts={false}
		chrome="none"
	>
		{#snippet toolbar()}
			<button
				type="button"
				class="icon-btn"
				title={m.map_cluster_close()}
				aria-label={m.map_cluster_close()}
				onclick={onclose}
			>
				<Icon name="close" />
			</button>
		{/snippet}
		{#snippet header()}
			<p class="range">{range}</p>
		{/snippet}
	</CollectionView>
</aside>

<style>
	/* A floating island at the right, under the chrome, over the map. */
	.panel {
		/* Its grid is no page: only its own rounded corners to keep clear of. */
		--bleed-top: var(--space-4);
		--bleed-bottom: var(--space-2);
		position: absolute;
		top: calc(var(--chrome-top, 0px) + var(--space-1));
		right: var(--float-margin);
		bottom: var(--float-margin);
		z-index: 1000;
		width: min(440px, 45%);
		min-width: 300px;
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-xl);
		background: var(--color-bg);
		box-shadow: var(--shadow-nav);
	}

	/* Narrow screens: a sheet over the lower part of the map, not a column
	   that would leave the map a sliver; above the bottom bar. */
	@media (max-width: 700px) {
		.panel {
			top: auto;
			left: var(--float-margin);
			bottom: calc(var(--nav-room, 0px) + var(--space-1));
			width: auto;
			min-width: 0;
			height: 62%;
		}
	}

	.panel :global(h1) {
		font-size: var(--font-size-lg);
	}

	.range {
		margin: 0;
		padding: 0 var(--page-gutter) var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
