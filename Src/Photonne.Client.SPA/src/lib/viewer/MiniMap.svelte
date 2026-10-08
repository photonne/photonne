<script lang="ts">
	import 'leaflet/dist/leaflet.css';
	import L from 'leaflet';
	import { onMount } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { getSettingOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { m } from '#lib/paraglide/messages.js';
	import { tileUrl } from '../../routes/(app)/map/map-model.js';
	import { PLACE_ZOOM } from './location.js';

	let { lat, lng }: { lat: number; lng: number } = $props();

	const TILE_KEY = 'ServerSettings.MapTileApiKey';
	const ATTRIBUTION =
		'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>';

	// Same query (and cache entry) as the map page: tiles wait for the key so
	// none come watermarked.
	const tileSetting = createQuery(() => ({
		...getSettingOptions({ query: { key: TILE_KEY } }),
		staleTime: Infinity,
		retry: false
	}));
	const tileKey = $derived(tileSetting.isPending ? undefined : tileSetting.data?.value || null);

	let container = $state<HTMLDivElement>();
	let map = $state<L.Map>();
	let marker: L.CircleMarker | undefined;

	onMount(() => {
		// A picture of the place, not a map to explore: the link opens the real one.
		const instance = L.map(container!, {
			zoomControl: false,
			attributionControl: true,
			dragging: false,
			scrollWheelZoom: false,
			doubleClickZoom: false,
			boxZoom: false,
			keyboard: false,
			touchZoom: false
		});
		instance.attributionControl.setPrefix(false);
		map = instance;
		return () => {
			instance.remove();
			map = undefined;
		};
	});

	$effect(() => {
		if (!map) return;
		map.setView([lat, lng], PLACE_ZOOM, { animate: false });
		marker?.remove();
		marker = L.circleMarker([lat, lng], {
			radius: 7,
			weight: 3,
			color: '#fff',
			fillColor: '#60a5fa',
			fillOpacity: 1,
			interactive: false
		}).addTo(map);
	});

	// The viewer is always dark, so its map is too.
	$effect(() => {
		if (!map || tileKey === undefined) return;
		const tiles = L.tileLayer(tileUrl('dark', tileKey), {
			attribution: ATTRIBUTION,
			subdomains: 'abcd',
			maxZoom: 19
		}).addTo(map);
		return () => {
			tiles.remove();
		};
	});
</script>

<div class="mini-map" bind:this={container} role="group" aria-label={m.viewer_map_label()}></div>

<style>
	.mini-map {
		height: 160px;
		border-radius: var(--radius-md);
		overflow: hidden;
		background: var(--color-surface);
		font: inherit;
		/* Above nothing: Leaflet's panes have z-indexes of their own. */
		isolation: isolate;
	}

	.mini-map :global(.leaflet-control-attribution) {
		background: rgb(0 0 0 / 0.55);
		color: var(--color-text-muted);
		font-size: 10px;
	}

	.mini-map :global(.leaflet-control-attribution a) {
		color: var(--color-accent);
	}
</style>
