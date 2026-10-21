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
	import PageChrome from '#lib/components/PageChrome.svelte';
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

<!-- The map takes the whole window; the title and "show all" float over it. -->
{#snippet fitAll()}
	<button
		type="button"
		class="icon-btn"
		aria-label={m.map_fit()}
		title={m.map_fit()}
		disabled={!bounds}
		onclick={() => bounds && map?.fit(bounds)}
	>
		<Icon path={icons.fitAll} />
	</button>
{/snippet}
<PageChrome bleed wide actions={fitAll} />

<div class="page">
	<div class="title glass">
		<h1>{m.nav_map()}</h1>
		{#if points.data}<span class="count">{m.map_count({ count: points.data.length })}</span>{/if}
	</div>

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
	/* Leaflet's panes and controls stack up to z-index 1000: kept inside
	   the page, under the floating chrome and the navigation island. */
	.page {
		position: relative;
		height: 100%;
		isolation: isolate;
	}

	.body {
		position: absolute;
		inset: 0;
	}

	.map {
		position: absolute;
		inset: 0;
	}

	/* Under the chrome, at the left: what the page is, over the map. */
	.title {
		--glass-tint: 82%;
		position: absolute;
		top: calc(var(--bleed-top) + var(--space-1));
		left: calc(var(--bleed-left) + var(--float-margin));
		z-index: 900;
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		max-width: calc(100% - var(--bleed-left) - 2 * var(--float-margin));
		padding: var(--space-2) var(--space-4);
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
	}

	.title h1 {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 650;
	}

	.count {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		white-space: nowrap;
	}

	/* Leaflet's controls keep clear of the chrome, the title and, on a
	   phone, the bottom bar. */
	.map :global(.leaflet-top) {
		top: calc(var(--bleed-top) + 52px);
	}

	.map :global(.leaflet-left) {
		left: var(--bleed-left);
	}

	.map :global(.leaflet-bottom) {
		bottom: var(--bleed-bottom);
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
