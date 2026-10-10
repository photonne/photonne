import { describe, expect, it } from 'vitest';
import {
	centerParams,
	clusterItems,
	clusterPoints,
	compactCount,
	dateRange,
	latestPoint,
	markerSize,
	parseCenter,
	pointsBounds,
	tileUrl,
	visibleClusters
} from './map-model';

describe('tileUrl', () => {
	it('picks the CARTO raster style for the theme and adds the key when there is one', () => {
		expect(tileUrl('light')).toBe('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png');
		expect(tileUrl('dark', 'a b')).toBe(
			'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png?key=a%20b'
		);
	});
});

const point = (
	id: string,
	latitude: number,
	longitude: number,
	date: string,
	hasThumbnail = true
) => ({
	id,
	latitude,
	longitude,
	date,
	hasThumbnail
});

// Two photos a street apart in Barcelona, one in Madrid.
const library = [
	point('bcn-old', 41.3874, 2.1686, '2021-03-01T10:00:00Z'),
	point('bcn-new', 41.3884, 2.1696, '2023-06-01T10:00:00Z', false),
	point('mad', 40.4168, -3.7038, '2022-01-01T10:00:00Z')
];

describe('clusterPoints', () => {
	it('groups photos a street apart and keeps cities apart at a city zoom', () => {
		const clusters = clusterPoints(library, 12);
		expect(clusters.map((c) => c.count)).toEqual([2, 1]);

		const [bcn] = clusters;
		expect(bcn.assetIds).toEqual(['bcn-new', 'bcn-old']);
		expect(bcn.latestDate).toBe('2023-06-01T10:00:00Z');
		expect(bcn.earliestDate).toBe('2021-03-01T10:00:00Z');
		// The newest photo with a thumbnail goes on the marker.
		expect(bcn.firstAssetId).toBe('bcn-old');
		expect(bcn.hasThumbnail).toBe(true);
		expect(bcn.latitude).toBeCloseTo(41.3879, 3);
		expect(bcn.longitude).toBeCloseTo(2.1691, 3);
	});

	it('merges cities from far out and splits a street up close', () => {
		expect(clusterPoints(library, 2).map((c) => c.count)).toEqual([3]);
		expect(clusterPoints(library, 18).map((c) => c.count)).toEqual([1, 1, 1]);
	});

	it('gives the same markers for the same zoom, whatever the order of the photos', () => {
		expect(clusterPoints([...library].reverse(), 12)).toEqual(clusterPoints(library, 12));
		expect(clusterPoints([], 12)).toEqual([]);
	});

	it('groups a big library quickly', () => {
		const many = Array.from({ length: 50_000 }, (_, i) =>
			point(`p${i}`, 36 + (i % 223) * 0.04, -9 + (i % 389) * 0.05, '2024-01-01T00:00:00Z')
		);
		const begin = performance.now();
		const clusters = clusterPoints(many, 6);
		expect(performance.now() - begin).toBeLessThan(1000);
		expect(clusters.reduce((sum, c) => sum + c.count, 0)).toBe(50_000);
	});
});

describe('visibleClusters', () => {
	const clusters = clusterPoints(library, 12);

	it('keeps what is in the view or just around it', () => {
		const bcnView = { zoom: 12, south: 41.3, west: 2.1, north: 41.45, east: 2.25 };
		expect(visibleClusters(clusters, bcnView).map((c) => c.count)).toEqual([2]);
	});

	it('keeps everything when the view spans the world', () => {
		const world = { zoom: 1, south: -80, west: -200, north: 80, east: 200 };
		expect(visibleClusters(clusters, world)).toHaveLength(2);
	});

	it('finds markers past the antimeridian', () => {
		const fiji = clusterPoints([point('suva', -18.14, 178.44, '2024-01-01T00:00:00Z')], 8);
		const view = { zoom: 8, south: -19, north: -17 };
		expect(visibleClusters([...fiji], { ...view, west: 178, east: 180 })).toHaveLength(1);
		expect(visibleClusters([...fiji], { ...view, west: -182, east: -180 })).toHaveLength(1);
		expect(visibleClusters([...fiji], { ...view, west: 0, east: 2 })).toHaveLength(0);
	});
});

describe('latestPoint', () => {
	it('picks the newest photo, or null for none', () => {
		expect(latestPoint(library)?.id).toBe('bcn-new');
		expect(latestPoint([])).toBeNull();
	});
});

describe('markerSize', () => {
	it('grows with the count and stays within bounds', () => {
		expect(markerSize(1)).toBe(48);
		expect(markerSize(10)).toBeGreaterThan(markerSize(2));
		expect(markerSize(1000)).toBeGreaterThan(markerSize(10));
		expect(markerSize(10_000_000)).toBe(76);
	});
});

describe('compactCount', () => {
	it('shortens big numbers in the user language', () => {
		expect(compactCount(8, 'es')).toBe('8');
		expect(compactCount(1250, 'en')).toBe('1.3K');
	});
});

describe('dateRange', () => {
	it('shows one month or a span, read in UTC', () => {
		expect(dateRange('2023-06-01T00:30:00Z', '2023-06-30T23:00:00Z', 'en')).toBe('Jun 2023');
		expect(dateRange('2021-03-01T00:00:00Z', '2023-06-30T00:00:00Z', 'en')).toBe(
			'Mar 2021 – Jun 2023'
		);
	});
});

describe('pointsBounds', () => {
	it('wraps every point, or nothing for no points', () => {
		expect(
			pointsBounds([
				{ latitude: 41.4, longitude: 2.17 },
				{ latitude: 40.4, longitude: -3.7 },
				{ latitude: 43.3, longitude: -2 }
			])
		).toEqual([
			[40.4, -3.7],
			[43.3, 2.17]
		]);
		expect(pointsBounds([])).toBeNull();
	});
});

describe('clusterItems', () => {
	it('orders the photos newest first, dating unknown ones by the cluster', () => {
		const points = new Map([
			['a', { date: '2020-01-01T00:00:00Z' }],
			['b', { date: '2024-05-01T00:00:00Z' }]
		]);
		const items = clusterItems(
			{ assetIds: ['a', 'b', 'c'], latestDate: '2022-01-01T00:00:00Z' },
			points
		);
		expect(items.map((item) => item.id)).toEqual(['b', 'c', 'a']);
		expect(items[1].capturedAt).toBe('2022-01-01T00:00:00Z');
		expect(items[0].aspect).toBeNull();
	});
});

describe('parseCenter / centerParams', () => {
	it('round-trips a valid view and rejects broken ones', () => {
		const params = new URLSearchParams(centerParams({ lat: 41.38879, lng: 2.15899 }, 12));
		expect(params.get('z')).toBe('12');
		expect(parseCenter(params)).toEqual({ lat: 41.38879, lng: 2.15899, zoom: 12 });
		expect(parseCenter(new URLSearchParams('lat=91&lng=0&z=3'))).toBeNull();
		expect(parseCenter(new URLSearchParams('lat=1&lng=x&z=3'))).toBeNull();
		expect(parseCenter(new URLSearchParams('lat=1&lng=1'))).toBeNull();
	});
});
