<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import {
		getMapPointsOptions,
		getSettingOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { icons } from '#lib/people/icons.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import ClusterPanel from './ClusterPanel.svelte';
	import LeafletMap from './LeafletMap.svelte';
	import {
		centerParams,
		clusterPoints,
		dateRange,
		latestPoint,
		parseCenter,
		pointsBounds,
		START_ZOOM,
		visibleClusters,
		type MapCluster,
		type MapView
	} from './map-model.js';

	const TILE_KEY = 'ServerSettings.MapTileApiKey';

	const points = createQuery(() => getMapPointsOptions());
	const tileSetting = createQuery(() => ({
		...getSettingOptions({ query: { key: TILE_KEY } }),
		staleTime: Infinity,
		retry: false
	}));
	// Without a readable key the map still works (watermarked tiles).
	const tileKey = $derived(tileSetting.isPending ? undefined : tileSetting.data?.value || null);

	const start = untrack(() => parseCenter(page.url.searchParams));
	let view = $state<MapView | null>(null);
	let active = $state<MapCluster | null>(null);
	let map = $state<LeafletMap>();
	let placed = false;

	const pointsById = $derived(new Map((points.data ?? []).map((point) => [point.id, point])));
	const bounds = $derived(pointsBounds(points.data ?? []));

	// Grouped here, from the points already loaded: a new zoom regroups them
	// (panning keeps the zoom, so it doesn't), and only what's near the view
	// is drawn.
	const zoom = $derived(view ? Math.round(view.zoom) : null);
	const clusters = $derived(zoom === null ? [] : clusterPoints(points.data ?? [], zoom));
	const shown = $derived(view ? visibleClusters(clusters, view) : []);

	const viewer = new ViewerRoute();

	// The first time the photos arrive, open on the newest one, as the app
	// does (unless the URL says where to look): a city's worth of markers
	// rather than the whole world. "Show all" frames everything.
	$effect(() => {
		const latest = latestPoint(points.data ?? []);
		if (placed || start || !latest || !map) return;
		placed = true;
		map.show(latest.latitude, latest.longitude, START_ZOOM);
	});

	function onview(next: MapView, center: { lat: number; lng: number }) {
		view = next;
		// The URL keeps the place, so a reload or a shared link opens here.
		const url = new URL(page.url.href);
		for (const [key, value] of Object.entries(centerParams(center, next.zoom))) {
			url.searchParams.set(key, value);
		}
		if (url.href !== page.url.href) replaceState(url, page.state);
	}

	function pick(cluster: MapCluster) {
		active = cluster;
		if (cluster.count === 1 && cluster.assetIds[0]) viewer.open(cluster.assetIds[0]);
	}

	function label(cluster: MapCluster) {
		return m.map_cluster({
			count: cluster.count,
			range: dateRange(cluster.earliestDate, cluster.latestDate, getLocale())
		});
	}
</script>

<svelte:head>
	<title>{m.nav_map()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader
		title={m.nav_map()}
		count={points.data ? m.map_count({ count: points.data.length }) : null}
	>
		{#snippet actions()}
			<button
				type="button"
				class="btn sm"
				disabled={!bounds}
				onclick={() => bounds && map?.fit(bounds)}
			>
				<Icon path={icons.fitAll} size={18} />
				{m.map_fit()}
			</button>
		{/snippet}
	</PageHeader>

	<div class="body">
		<div class="map">
			<LeafletMap
				bind:this={map}
				clusters={shown}
				{tileKey}
				{start}
				activeId={active?.id ?? null}
				{label}
				{onview}
				onpick={pick}
			/>
			{#if points.isError}
				<div class="overlay" role="alert">
					<p>{m.error_loading()}</p>
				</div>
			{:else if points.data && points.data.length === 0}
				<!-- EmptyState's look, with a heading: the page's only content. -->
				<div class="overlay">
					<span class="mark" aria-hidden="true"><Icon path={icons.locationOff} size={32} /></span>
					<h2>{m.map_empty_title()}</h2>
					<p>{m.map_empty_body()}</p>
				</div>
			{/if}
		</div>
		{#if active}
			{#key active.id}
				<ClusterPanel cluster={active} points={pointsById} onclose={() => (active = null)} />
			{/key}
		{/if}
	</div>
</div>

<style>
	.page {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.body {
		flex: 1;
		min-height: 0;
		display: flex;
		border-top: 1px solid var(--color-border);
	}

	.map {
		position: relative;
		flex: 1;
		min-width: 0;
	}

	.overlay {
		position: absolute;
		inset: 0;
		z-index: 500;
		display: grid;
		place-content: center;
		justify-items: center;
		gap: var(--space-2);
		padding: var(--space-6);
		background: color-mix(in srgb, var(--color-bg) 85%, transparent);
		color: var(--color-text-muted);
		text-align: center;
	}

	.mark {
		display: grid;
		place-items: center;
		width: 72px;
		height: 72px;
		margin-bottom: var(--space-2);
		border-radius: 50%;
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.overlay h2 {
		margin: 0;
		color: var(--color-text);
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.overlay p {
		margin: 0;
		max-width: 46ch;
		font-size: var(--font-size-sm);
	}
</style>
