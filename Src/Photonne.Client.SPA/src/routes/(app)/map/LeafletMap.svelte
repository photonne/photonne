<script lang="ts">
	import 'leaflet/dist/leaflet.css';
	import L from 'leaflet';
	import { onMount } from 'svelte';
	import type { MapClusterResponse } from '#lib/api/index.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { compactCount, markerSize, tileUrl, type MapView, type TileTheme } from './map-model.js';

	interface Props {
		clusters: readonly MapClusterResponse[];
		/** The server's tile key; undefined while it's being read (no tiles yet, so none watermarked). */
		tileKey: string | null | undefined;
		/** Where to start; without it the host fits the photos once they load. */
		start: { lat: number; lng: number; zoom: number } | null;
		/** The cluster whose photos are open, drawn highlighted. */
		activeId: string | null;
		label: (cluster: MapClusterResponse) => string;
		onview: (view: MapView, center: { lat: number; lng: number }) => void;
		onpick: (cluster: MapClusterResponse) => void;
	}

	let { clusters, tileKey, start, activeId, label, onview, onpick }: Props = $props();

	const ATTRIBUTION =
		'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>';

	let container = $state<HTMLDivElement>();
	let map = $state<L.Map>();
	let theme = $state<TileTheme>('light');
	// Leaflet's own objects, not UI state: a plain Map that effects don't track.
	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	const markers = new Map<string, { marker: L.Marker; cluster: MapClusterResponse }>();
	let layer: L.LayerGroup | undefined;

	/** Moves the view to show the box; the host calls it for "show all". */
	export function fit(bounds: L.LatLngBoundsExpression) {
		map?.fitBounds(bounds, { padding: [48, 48], maxZoom: 15 });
	}

	function emitView() {
		if (!map) return;
		const bounds = map.getBounds();
		onview(
			{
				zoom: map.getZoom(),
				south: bounds.getSouth(),
				west: bounds.getWest(),
				north: bounds.getNorth(),
				east: bounds.getEast()
			},
			map.getCenter()
		);
	}

	onMount(() => {
		// The user's theme (data-theme on <html>, see lib/theme.ts) wins over
		// the system's; both can change while the map is open.
		const root = document.documentElement;
		const scheme = matchMedia('(prefers-color-scheme: dark)');
		const onScheme = () => {
			const chosen = root.dataset.theme;
			theme = chosen === 'dark' || chosen === 'light' ? chosen : scheme.matches ? 'dark' : 'light';
		};
		onScheme();
		scheme.addEventListener('change', onScheme);
		const chosenTheme = new MutationObserver(onScheme);
		chosenTheme.observe(root, { attributes: true, attributeFilter: ['data-theme'] });

		const instance = L.map(container!, {
			zoomControl: false,
			worldCopyJump: true,
			minZoom: 2,
			maxZoom: 19
		});
		L.control
			.zoom({ position: 'topleft', zoomInTitle: m.map_zoom_in(), zoomOutTitle: m.map_zoom_out() })
			.addTo(instance);
		L.control.scale({ position: 'bottomleft', imperial: false }).addTo(instance);
		instance.attributionControl.setPrefix(false);
		layer = L.layerGroup().addTo(instance);
		instance.on('moveend', emitView);
		if (start) instance.setView([start.lat, start.lng], start.zoom);
		else instance.setView([20, 0], 2);
		map = instance;
		emitView();

		// The container changes size with the side panel and the window.
		const resize = new ResizeObserver(() => instance.invalidateSize());
		resize.observe(container!);

		return () => {
			resize.disconnect();
			scheme.removeEventListener('change', onScheme);
			chosenTheme.disconnect();
			instance.remove();
			markers.clear();
		};
	});

	// Tiles follow the theme; they wait for the key so none come watermarked.
	$effect(() => {
		if (!map || tileKey === undefined) return;
		const tiles = L.tileLayer(tileUrl(theme, tileKey), {
			attribution: ATTRIBUTION,
			subdomains: 'abcd',
			maxZoom: 19,
			detectRetina: false
		}).addTo(map);
		return () => {
			tiles.remove();
		};
	});

	function iconFor(cluster: MapClusterResponse) {
		const size = markerSize(cluster.count);
		const image =
			cluster.hasThumbnail && cluster.firstAssetId
				? `<img src="${thumbnailUrl(cluster.firstAssetId, 'Small')}" alt="" draggable="false">`
				: '';
		const count =
			cluster.count > 1
				? `<span class="count">${compactCount(cluster.count, getLocale())}</span>`
				: '';
		return L.divIcon({
			className: 'photo-marker',
			html: `<span class="ring" style="width:${size}px;height:${size}px">${image}</span>${count}`,
			iconSize: [size, size]
		});
	}

	// Markers are kept by cluster id: a refetch that brings the same clusters
	// leaves them (and keyboard focus on one) untouched.
	$effect(() => {
		if (!map || !layer) return;
		const group = layer;
		const next = new Map(clusters.map((cluster) => [cluster.id, cluster]));
		for (const [id, entry] of markers) {
			if (!next.has(id)) {
				group.removeLayer(entry.marker);
				markers.delete(id);
			}
		}
		for (const cluster of clusters) {
			if (markers.has(cluster.id)) continue;
			const marker = L.marker([cluster.latitude, cluster.longitude], {
				icon: iconFor(cluster),
				keyboard: true,
				riseOnHover: true
			});
			marker.on('click', () => onpick(cluster));
			marker.on('add', () => {
				const element = marker.getElement();
				if (!element) return;
				element.setAttribute('aria-label', label(cluster));
				element.setAttribute('title', label(cluster));
				element.addEventListener('keydown', (event) => {
					if (event.key === 'Enter' || event.key === ' ') {
						event.preventDefault();
						event.stopPropagation();
						onpick(cluster);
					}
				});
			});
			markers.set(cluster.id, { marker, cluster });
			marker.addTo(group);
		}
	});

	// The open cluster stands out (also once its marker is redrawn).
	$effect(() => {
		const active = activeId;
		void clusters;
		if (!map) return;
		for (const [id, { marker }] of markers) {
			marker
				.getElement()
				?.querySelector('.ring')
				?.classList.toggle('active', id === active);
			marker.setZIndexOffset(id === active ? 1000 : 0);
		}
	});

	// Opening a cluster narrows the map (the panel takes its side): keep the
	// cluster in sight once the map has its new size.
	$effect(() => {
		const instance = map;
		const entry = activeId ? markers.get(activeId) : undefined;
		if (!instance || !entry) return;
		const frame = requestAnimationFrame(() => {
			instance.invalidateSize();
			const position = entry.marker.getLatLng();
			if (!instance.getBounds().pad(-0.1).contains(position)) instance.panTo(position);
		});
		return () => cancelAnimationFrame(frame);
	});
</script>

<div class="map" bind:this={container} role="application" aria-label={m.map_label()}></div>

<style>
	.map {
		width: 100%;
		height: 100%;
		background: var(--color-surface);
		font: inherit;
	}

	.map :global(.photo-marker) {
		background: none;
		border: 0;
	}

	.map :global(.photo-marker:focus-visible) {
		outline: none;
	}

	/* A photo in a gold ring, with a light halo so it reads on any tile. */
	.map :global(.photo-marker .ring) {
		display: block;
		overflow: hidden;
		border: 3px solid var(--color-brand);
		border-radius: 50%;
		background: var(--color-brand-tile);
		box-shadow:
			0 0 0 2px rgb(255 255 255 / 0.85),
			0 2px 8px rgb(0 0 0 / 0.35);
		transition: transform var(--duration-fast);
	}

	.map :global(.photo-marker:hover .ring),
	.map :global(.photo-marker:focus-visible .ring) {
		transform: scale(1.08);
	}

	.map :global(.photo-marker:focus-visible .ring) {
		box-shadow:
			0 0 0 3px var(--color-bg),
			0 0 0 6px var(--color-focus);
	}

	.map :global(.photo-marker .ring.active) {
		border-color: var(--color-accent);
		border-width: 4px;
		box-shadow:
			0 0 0 3px var(--color-bg),
			0 0 0 6px var(--color-accent),
			0 2px 12px rgb(0 0 0 / 0.45);
	}

	@media (prefers-reduced-motion: reduce) {
		.map :global(.photo-marker .ring) {
			transition: none;
		}
	}

	.map :global(.photo-marker img) {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.map :global(.photo-marker .count) {
		position: absolute;
		top: -6px;
		right: -8px;
		min-width: 24px;
		padding: 0 6px;
		border: 2px solid var(--color-bg);
		border-radius: 999px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		font-weight: 700;
		line-height: 20px;
		text-align: center;
		white-space: nowrap;
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
	}

	/* Leaflet's controls, in the app's tokens (light and dark). */
	.map :global(.leaflet-control-zoom.leaflet-bar) {
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-control);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
	}

	.map :global(.leaflet-control-zoom a) {
		display: grid;
		place-items: center;
		width: var(--control-h);
		height: var(--control-h);
		border: 0;
		border-radius: 0;
		background: var(--color-surface-raised);
		color: var(--color-text);
		font: 400 var(--font-size-lg) / 1 var(--font-sans);
		transition: background var(--duration-fast);
	}

	.map :global(.leaflet-control-zoom a + a) {
		border-top: 1px solid var(--color-border);
	}

	.map :global(.leaflet-control-zoom a:hover) {
		background: color-mix(in srgb, var(--color-text) 6%, var(--color-surface-raised));
		color: var(--color-text);
	}

	.map :global(.leaflet-control-zoom a:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.map :global(.leaflet-control-zoom a.leaflet-disabled) {
		background: var(--color-surface-raised);
		color: var(--color-text-muted);
		opacity: 0.6;
	}

	.map :global(.leaflet-control-attribution) {
		padding: 2px var(--space-2);
		border-top-left-radius: var(--radius-sm);
		background: color-mix(in srgb, var(--color-surface-raised) 88%, transparent);
		color: var(--color-text-muted);
		font-size: var(--font-size-2xs);
		line-height: 1.5;
	}

	.map :global(.leaflet-control-attribution a) {
		color: var(--color-accent);
	}

	.map :global(.leaflet-control-scale-line) {
		border-color: var(--color-text-muted);
		background: color-mix(in srgb, var(--color-surface-raised) 80%, transparent);
		color: var(--color-text);
		font-size: var(--font-size-2xs);
	}

	.map :global(.leaflet-popup-content-wrapper),
	.map :global(.leaflet-popup-tip) {
		background: var(--color-surface-raised);
		color: var(--color-text);
		box-shadow: var(--shadow-raised);
	}

	.map :global(.leaflet-popup-content-wrapper) {
		border-radius: var(--radius-md);
	}

	.map :global(.leaflet-popup-close-button) {
		color: var(--color-text-muted);
	}

	.map :global(.leaflet-container a.leaflet-popup-close-button:hover) {
		color: var(--color-text);
	}
</style>
