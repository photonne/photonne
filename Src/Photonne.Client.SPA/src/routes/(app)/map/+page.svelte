<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery, keepPreviousData } from '@tanstack/svelte-query';
	import { replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import type { MapClusterResponse } from '#lib/api/index.js';
	import {
		getMapAssetsOptions,
		getMapPointsOptions,
		getSettingOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { icons } from '#lib/people/icons.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import ClusterPanel from './ClusterPanel.svelte';
	import LeafletMap from './LeafletMap.svelte';
	import {
		centerParams,
		clusterQuery,
		dateRange,
		parseCenter,
		pointsBounds,
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
	let active = $state<MapClusterResponse | null>(null);
	let map = $state<LeafletMap>();
	let fitted = false;

	const hasPoints = $derived((points.data?.length ?? 0) > 0);
	const pointsById = $derived(new Map((points.data ?? []).map((point) => [point.id, point])));
	const bounds = $derived(pointsBounds(points.data ?? []));

	const clusters = createQuery(() => ({
		...getMapAssetsOptions({ query: view ? clusterQuery(view) : {} }),
		enabled: view !== null && hasPoints,
		// The markers stay while the next view's clusters load.
		placeholderData: keepPreviousData
	}));

	const viewer = new ViewerRoute();

	// The first time the photos arrive, frame them (unless the URL says where to look).
	$effect(() => {
		if (fitted || start || !bounds || !map) return;
		fitted = true;
		map.fit(bounds);
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

	function pick(cluster: MapClusterResponse) {
		active = cluster;
		if (cluster.count === 1 && cluster.assetIds[0]) viewer.open(cluster.assetIds[0]);
	}

	function label(cluster: MapClusterResponse) {
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
	<header class="head">
		<h1>{m.nav_map()}</h1>
		{#if points.data}
			<span class="chip">{m.map_count({ count: points.data.length })}</span>
		{/if}
		{#if clusters.isFetching && hasPoints}
			<span class="updating" role="status">{m.map_updating()}</span>
		{/if}
		<div class="toolbar">
			<button
				type="button"
				class="tool"
				disabled={!bounds}
				onclick={() => bounds && map?.fit(bounds)}
			>
				<Icon path={icons.fitAll} size={18} />
				{m.map_fit()}
			</button>
		</div>
	</header>

	<div class="body">
		<div class="map">
			<LeafletMap
				bind:this={map}
				clusters={hasPoints ? (clusters.data ?? []) : []}
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
				<div class="overlay">
					<Icon path={icons.locationOff} size={40} />
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

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.chip {
		padding: 2px var(--space-3);
		border-radius: 999px;
		background: var(--color-surface);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.updating {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.toolbar {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
	}

	.tool {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.tool:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.tool:disabled {
		opacity: 0.5;
		cursor: default;
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
		background: color-mix(in srgb, var(--color-bg) 82%, transparent);
		color: var(--color-text-muted);
		text-align: center;
	}

	.overlay h2 {
		margin: 0;
		color: var(--color-text);
		font-size: var(--font-size-lg);
	}

	.overlay p {
		margin: 0;
		max-width: 36ch;
	}
</style>
