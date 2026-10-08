import { describe, expect, it } from 'vitest';
import {
	centerParams,
	clusterItems,
	clusterQuery,
	compactCount,
	dateRange,
	markerSize,
	parseCenter,
	pointsBounds,
	tileUrl
} from './map-model';

describe('tileUrl', () => {
	it('picks the CARTO raster style for the theme and adds the key when there is one', () => {
		expect(tileUrl('light')).toBe('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png');
		expect(tileUrl('dark', 'a b')).toBe(
			'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png?key=a%20b'
		);
	});
});

describe('clusterQuery', () => {
	it('asks for the whole world when the view spans it', () => {
		expect(clusterQuery({ zoom: 2.4, south: -80, west: -200, north: 80, east: 200 })).toEqual({
			zoom: 2
		});
	});

	it('pads the box, snaps it to a zoom grid and stays inside the globe', () => {
		const query = clusterQuery({ zoom: 10, south: 41.3, west: 2.1, north: 41.5, east: 2.3 });
		expect(query.zoom).toBe(10);
		expect(query.minLat!).toBeLessThan(41.3);
		expect(query.maxLat!).toBeGreaterThan(41.5);
		expect(query.minLng!).toBeLessThan(2.1);
		expect(query.maxLng!).toBeGreaterThan(2.3);

		const edge = clusterQuery({ zoom: 4, south: 60, west: 150, north: 89, east: 179 });
		expect(edge.maxLat).toBe(90);
		expect(edge.maxLng).toBe(180);
	});

	it('gives the same query for small pans of the same view', () => {
		const a = clusterQuery({ zoom: 12, south: 40.41, west: -3.71, north: 40.43, east: -3.69 });
		const b = clusterQuery({
			zoom: 12,
			south: 40.4101,
			west: -3.7099,
			north: 40.4301,
			east: -3.6899
		});
		expect(b).toEqual(a);
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
