import type { MapPointResponse } from '#lib/api/index.js';
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

/** Zoom of the first view: the newest photo's city and suburbs, as in the app. */
export const START_ZOOM = 12;

/** Pixels between markers' centres below which they merge into one. */
export const CLUSTER_RADIUS = 80;

/** A group of nearby photos, drawn as one marker. */
export interface MapCluster {
	id: string;
	latitude: number;
	longitude: number;
	count: number;
	/** Newest first. */
	assetIds: string[];
	earliestDate: string;
	latestDate: string;
	/** The photo on the marker: the newest one with a thumbnail. */
	firstAssetId: string;
	hasThumbnail: boolean;
}

const TILE = 256;
const MAX_LAT = 85.051128;
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/** Web Mercator, in pixels of the whole world at a zoom (Leaflet's own). */
function project(lat: number, lng: number, size: number) {
	const sin = Math.sin((clamp(lat, -MAX_LAT, MAX_LAT) * Math.PI) / 180);
	return {
		x: ((lng + 180) / 360) * size,
		y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * size
	};
}

function unproject(x: number, y: number, size: number) {
	return {
		latitude: (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / size))) * 180) / Math.PI,
		longitude: (x / size) * 360 - 180
	};
}

/**
 * Groups the photos into markers for a zoom, in the browser: every photo
 * within [radius] screen pixels of a group's first (newest) photo joins it.
 * A grid of [radius]-wide cells keeps each search to the 3×3 cells around,
 * so it runs in about linear time. The whole library is grouped at once,
 * so panning at the same zoom changes nothing.
 */
export function clusterPoints(
	points: readonly MapPointResponse[],
	zoom: number,
	radius = CLUSTER_RADIUS
): MapCluster[] {
	const size = TILE * 2 ** zoom;
	const sorted = [...points].sort((a, b) =>
		a.date !== b.date ? (a.date < b.date ? 1 : -1) : a.id < b.id ? -1 : 1
	);
	const xs = new Float64Array(sorted.length);
	const ys = new Float64Array(sorted.length);
	const grid = new Map<string, number[]>();
	sorted.forEach((point, i) => {
		const { x, y } = project(point.latitude, point.longitude, size);
		xs[i] = x;
		ys[i] = y;
		const key = `${Math.floor(x / radius)}:${Math.floor(y / radius)}`;
		const cell = grid.get(key);
		if (cell) cell.push(i);
		else grid.set(key, [i]);
	});

	const taken = new Uint8Array(sorted.length);
	const clusters: MapCluster[] = [];
	const reach = radius * radius;
	for (let seed = 0; seed < sorted.length; seed++) {
		if (taken[seed]) continue;
		taken[seed] = 1;
		const members = [seed];
		const cx = Math.floor(xs[seed] / radius);
		const cy = Math.floor(ys[seed] / radius);
		for (let dx = -1; dx <= 1; dx++) {
			for (let dy = -1; dy <= 1; dy++) {
				for (const i of grid.get(`${cx + dx}:${cy + dy}`) ?? []) {
					if (taken[i]) continue;
					const ddx = xs[i] - xs[seed];
					const ddy = ys[i] - ys[seed];
					if (ddx * ddx + ddy * ddy > reach) continue;
					taken[i] = 1;
					members.push(i);
				}
			}
		}
		// Indexes follow the newest-first order.
		members.sort((a, b) => a - b);
		let sumX = 0;
		let sumY = 0;
		for (const i of members) {
			sumX += xs[i];
			sumY += ys[i];
		}
		const photos = members.map((i) => sorted[i]);
		const cover = photos.find((point) => point.hasThumbnail) ?? photos[0];
		clusters.push({
			id: `${zoom}:${photos[0].id}:${photos.length}`,
			...unproject(sumX / members.length, sumY / members.length, size),
			count: photos.length,
			assetIds: photos.map((point) => point.id),
			earliestDate: photos[photos.length - 1].date,
			latestDate: photos[0].date,
			firstAssetId: cover.id,
			hasThumbnail: cover.hasThumbnail
		});
	}
	return clusters;
}

/**
 * The clusters worth drawing for a view: those in it, plus a margin on each
 * side so a short pan shows markers already in place.
 */
export function visibleClusters(clusters: readonly MapCluster[], view: MapView) {
	const lngSpan = view.east - view.west;
	if (lngSpan >= 360) return clusters;
	const latPad = (view.north - view.south) * 0.25;
	const lngPad = lngSpan * 0.25;
	const inside = (lng: number) => lng >= view.west - lngPad && lng <= view.east + lngPad;
	return clusters.filter(
		(cluster) =>
			cluster.latitude >= view.south - latPad &&
			cluster.latitude <= view.north + latPad &&
			// Past the antimeridian the view's longitudes run beyond ±180.
			(inside(cluster.longitude) ||
				inside(cluster.longitude - 360) ||
				inside(cluster.longitude + 360))
	);
}

/** The newest photo, where the map opens; null for none. */
export function latestPoint<T extends Pick<MapPointResponse, 'date'>>(points: readonly T[]) {
	let latest: T | null = null;
	for (const point of points) if (!latest || point.date > latest.date) latest = point;
	return latest;
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
	cluster: Pick<MapCluster, 'assetIds' | 'latestDate'>,
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
