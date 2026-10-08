import type { GetMapAssetsData, MapClusterResponse, MapPointResponse } from '#lib/api/index.js';
import type { GridAsset } from '#lib/timeline/types.js';

/** What the map shows: its zoom level and the visible box, in degrees. */
export interface MapView {
	zoom: number;
	south: number;
	west: number;
	north: number;
	east: number;
}

export type TileTheme = 'light' | 'dark';

/**
 * CARTO raster tiles: the only map images the server's CSP lets in
 * (img-src https://*.basemaps.cartocdn.com). With the server's tile key
 * (ServerSettings.MapTileApiKey) the tiles come without a watermark.
 */
export function tileUrl(theme: TileTheme, apiKey?: string | null) {
	const variant = theme === 'dark' ? 'dark_all' : 'light_all';
	const base = `https://{s}.basemaps.cartocdn.com/${variant}/{z}/{x}/{y}{r}.png`;
	return apiKey ? `${base}?key=${encodeURIComponent(apiKey)}` : base;
}

const round = (value: number, step: number) => Math.round(value / step) * step;
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/**
 * Query of GET /api/assets/map for a view. The box grows by a margin on each
 * side so short pans stay within what was fetched, and snaps to a grid that
 * depends on the zoom so nearby views share one cache entry. A view that
 * spans (nearly) the whole world asks for everything.
 */
export function clusterQuery(view: MapView): NonNullable<GetMapAssetsData['query']> {
	const zoom = clamp(Math.round(view.zoom), 0, 22);
	const latSpan = view.north - view.south;
	const lngSpan = view.east - view.west;
	if (lngSpan >= 300) return { zoom };

	const step = Math.max(360 / 2 ** (zoom + 3), 0.0005);
	const pad = (span: number) => Math.max(span * 0.25, step);
	return {
		zoom,
		minLat: clamp(round(view.south - pad(latSpan), step), -90, 90),
		maxLat: clamp(round(view.north + pad(latSpan), step), -90, 90),
		minLng: clamp(round(view.west - pad(lngSpan), step), -180, 180),
		maxLng: clamp(round(view.east + pad(lngSpan), step), -180, 180)
	};
}

/** Marker diameter in CSS px: bigger for bigger clusters, on a log scale. */
export function markerSize(count: number) {
	if (count <= 1) return 48;
	return Math.round(Math.min(76, 52 + 8 * Math.log10(count)));
}

/** "8", "999", "1,2 k", "15 k" — short enough for a marker's badge. */
export function compactCount(count: number, locale: string) {
	return new Intl.NumberFormat(locale, {
		notation: 'compact',
		maximumFractionDigits: 1
	}).format(count);
}

/**
 * "jun 2023", or "mar 2021 – jun 2023" for a span of months. Capture times are
 * the camera's wall clock stored as UTC, so they are read in UTC.
 */
export function dateRange(earliest: string, latest: string, locale: string) {
	const format = new Intl.DateTimeFormat(locale, {
		month: 'short',
		year: 'numeric',
		timeZone: 'UTC'
	});
	const from = format.format(new Date(earliest));
	const to = format.format(new Date(latest));
	return from === to ? from : `${from} – ${to}`;
}

/** South-west and north-east corners around every point, or null for none. */
export function pointsBounds(
	points: readonly Pick<MapPointResponse, 'latitude' | 'longitude'>[]
): [[number, number], [number, number]] | null {
	if (points.length === 0) return null;
	let south = 90;
	let north = -90;
	let west = 180;
	let east = -180;
	for (const { latitude, longitude } of points) {
		south = Math.min(south, latitude);
		north = Math.max(north, latitude);
		west = Math.min(west, longitude);
		east = Math.max(east, longitude);
	}
	return [
		[south, west],
		[north, east]
	];
}

/**
 * The photos of a cluster for the side grid, newest first. The map knows
 * where and when each photo is, not its shape or kind: cells are laid out
 * square-ish and the viewer fetches the real asset.
 */
export function clusterItems(
	cluster: Pick<MapClusterResponse, 'assetIds' | 'latestDate'>,
	points: ReadonlyMap<string, Pick<MapPointResponse, 'date'>>
): GridAsset[] {
	return cluster.assetIds
		.map((id) => ({ id, date: points.get(id)?.date ?? cluster.latestDate }))
		.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
		.map(({ id, date }) => ({
			id,
			aspect: null,
			fileName: '',
			capturedAt: date,
			isVideo: false,
			isLivePhoto: false,
			isFavorite: false,
			dominantColor: null,
			thumbnailVersion: null
		}));
}

/** The map's centre and zoom from the URL (?lat=&lng=&z=), when valid. */
export function parseCenter(params: Pick<URLSearchParams, 'get' | 'has'>) {
	const lat = Number(params.get('lat'));
	const lng = Number(params.get('lng'));
	const zoom = Number(params.get('z'));
	if (!params.has('lat') || !params.has('lng') || !params.has('z')) return null;
	if (![lat, lng, zoom].every(Number.isFinite)) return null;
	if (Math.abs(lat) > 90 || Math.abs(lng) > 180 || zoom < 0 || zoom > 22) return null;
	return { lat, lng, zoom };
}

/** The inverse of parseCenter, rounded to what a screen can tell apart. */
export function centerParams(center: { lat: number; lng: number }, zoom: number) {
	const digits = Math.min(6, Math.max(2, Math.ceil(zoom / 3) + 1));
	return {
		lat: center.lat.toFixed(digits),
		lng: center.lng.toFixed(digits),
		z: String(Math.round(zoom))
	};
}
