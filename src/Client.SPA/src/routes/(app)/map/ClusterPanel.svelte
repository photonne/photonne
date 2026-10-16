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
	.panel {
		width: min(440px, 45%);
		flex: none;
		min-width: 300px;
		border-left: 1px solid var(--color-border);
		background: var(--color-bg);
	}

	/* Narrow screens: a sheet over the lower part of the map, not a column
	   that would leave the map a sliver. Above Leaflet's panes and controls. */
	@media (max-width: 700px) {
		.panel {
			position: absolute;
			inset: auto 0 0;
			z-index: 1000;
			width: auto;
			min-width: 0;
			height: 70%;
			border-left: 0;
			border-top: 1px solid var(--color-border);
			border-radius: var(--radius-lg) var(--radius-lg) 0 0;
			box-shadow: var(--shadow-raised);
			overflow: hidden;
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
