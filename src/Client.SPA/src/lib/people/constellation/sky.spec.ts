import { describe, expect, it } from 'vitest';
import {
	brightness,
	buildSky,
	closeness,
	decimalYear,
	laneAt,
	photosUntil,
	yearStep,
	type SkyPerson
} from './sky.js';

const today = new Date(2026, 6, 2); // mid-2026

function person(
	id: string,
	years: Record<number, number>,
	relationshipCategory: string | null = null,
	last?: string
): SkyPerson {
	const list = Object.entries(years).map(([year, assetCount]) => ({
		year: Number(year),
		assetCount
	}));
	return {
		id,
		name: id,
		relationshipCategory,
		firstCapturedAt: `${list[0].year}-03-01T12:00:00`,
		lastCapturedAt: last ?? `${list.at(-1)!.year}-09-01T12:00:00`,
		assetCount: list.reduce((sum, entry) => sum + entry.assetCount, 0),
		years: list
	};
}

const star = (sky: ReturnType<typeof buildSky>, id: string) =>
	sky.stars.find((entry) => entry.id === id)!;

describe('decimalYear', () => {
	it('puts a date within its year', () => {
		expect(decimalYear(new Date(2020, 0, 1))).toBe(2020);
		expect(decimalYear(new Date(2021, 6, 2, 12))).toBeCloseTo(2021.5, 2);
	});
});

describe('buildSky', () => {
	it('fixes a star two years after its last photo and dims it after one', () => {
		const sky = buildSky(
			[
				person('alive', { 2020: 3, 2026: 1 }, null, '2026-02-01T12:00:00'),
				person('fading', { 2020: 3, 2025: 1 }, null, '2025-03-01T12:00:00'),
				person('fixed', { 2010: 3, 2019: 1 })
			],
			today
		);
		expect(star(sky, 'alive')).toMatchObject({ status: 'alive', until: sky.to });
		expect(star(sky, 'fading')).toMatchObject({ status: 'fading', until: sky.to });
		const fixed = star(sky, 'fixed');
		expect(fixed.status).toBe('fixed');
		expect(fixed.until).toBe(fixed.end);
		expect(sky.from).toBeCloseTo(2010.16, 1);
		expect(sky.to).toBeCloseTo(2026.5, 2);
	});

	it('keeps partner and children next to the main line and work far from it', () => {
		const sky = buildSky(
			[
				person('colleague', { 2020: 50, 2026: 50 }, 'work'),
				person('mother', { 2000: 5, 2026: 5 }, 'family'),
				person('partner', { 2015: 9, 2026: 9 }, 'partner'),
				person('child', { 2021: 9, 2026: 9 }, 'children'),
				person('stranger', { 2015: 99, 2026: 99 })
			],
			today
		);
		const distance = (id: string) => Math.abs(star(sky, id).lane);
		expect(distance('partner')).toBe(1);
		expect(distance('child')).toBe(1);
		expect(distance('mother')).toBeGreaterThan(1);
		expect(distance('colleague')).toBeGreaterThan(distance('mother'));
		expect(distance('stranger')).toBeGreaterThan(distance('colleague'));
		// One on each side before a second lane opens.
		expect(star(sky, 'partner').lane).toBe(-star(sky, 'child').lane);
	});

	it('gives the innermost lanes of a band to whoever has more photos', () => {
		const sky = buildSky(
			[
				person('few', { 2000: 1, 2026: 1 }, 'friends'),
				person('many', { 2000: 40, 2026: 40 }, 'friends'),
				person('some', { 2000: 10, 2026: 10 }, 'friends')
			],
			today
		);
		expect(Math.abs(star(sky, 'many').lane)).toBe(1);
		expect(Math.abs(star(sky, 'some').lane)).toBe(1);
		expect(Math.abs(star(sky, 'few').lane)).toBe(2);
		expect(sky.top).toBeLessThan(0);
		expect(sky.bottom).toBeGreaterThan(0);
	});

	it('reuses the lane of a star that stopped for someone who appears later', () => {
		const sky = buildSky(
			[
				person('grandfather', { 1990: 8, 1997: 2 }, 'family'),
				person('grandmother', { 1990: 7, 2004: 2 }, 'family'),
				person('niece', { 2018: 6, 2026: 6 }, 'family')
			],
			today
		);
		expect(Math.abs(star(sky, 'niece').lane)).toBe(1);
	});

	it('never lets a wrong camera date put a star in the future', () => {
		const sky = buildSky([person('ana', { 2020: 1, 2031: 1 })], today);
		expect(star(sky, 'ana').end).toBe(sky.to);
		expect(star(sky, 'ana').status).toBe('alive');
	});

	it('is an empty sky around today without people', () => {
		const sky = buildSky([], today);
		expect(sky).toMatchObject({ stars: [], top: 0, bottom: 0 });
		expect(sky.to - sky.from).toBe(1);
	});
});

describe('a star over time', () => {
	const sky = buildSky([person('ana', { 2010: 2, 2011: 10, 2014: 5 }, 'friends')], today);
	const ana = star(sky, 'ana');

	it('is closest in its best year and away in a year without photos', () => {
		expect(closeness(ana, 2011.5)).toBe(1);
		expect(closeness(ana, 2010.5)).toBeCloseTo(0.2);
		expect(closeness(ana, 2011)).toBeCloseTo(0.6);
		expect(closeness(ana, 2012.5)).toBe(0);
		expect(Math.abs(laneAt(ana, 2011.5))).toBe(1);
		expect(Math.abs(laneAt(ana, 2012.5))).toBeCloseTo(1.3);
	});

	it('stays where its last photo is once fixed', () => {
		expect(laneAt(ana, 2025)).toBe(laneAt(ana, ana.end));
	});

	it('counts the photos up to a year', () => {
		expect(photosUntil(ana, 2009.9)).toBe(0);
		expect(photosUntil(ana, 2011.2)).toBe(12);
		expect(photosUntil(ana, 2026)).toBe(17);
	});

	it('dims after a year without photos, unless it stopped at the last one', () => {
		expect(brightness(ana, 2026)).toBe(1); // fixed: its light is the last photo's
		const fading = star(
			buildSky([person('luis', { 2020: 3, 2024: 1 }, null, '2024-12-31T12:00:00')], today),
			'luis'
		);
		expect(fading.status).toBe('fading');
		expect(brightness(fading, 2025.5)).toBe(1);
		expect(brightness(fading, 2026.5)).toBeCloseTo(0.67, 1);
	});
});

describe('branches', () => {
	const family = (nieceFrom: string | null = 'sister') =>
		buildSky(
			[
				person('mother', { 1990: 9, 2026: 9 }, 'family'),
				person('sister', { 1992: 8, 2026: 8 }, 'family'),
				{
					...person('niece', { 2018: 5, 2019: 5, 2020: 5, 2021: 5, 2026: 5 }, 'family'),
					branchFromPersonId: nieceFrom
				},
				person('friend', { 2000: 30, 2026: 30 }, 'family')
			],
			today
		);

	it('a star is born on the trail it branches from and eases into its own lane', () => {
		const sky = family();
		const niece = star(sky, 'niece');
		const sister = star(sky, 'sister');
		expect(niece.origin).toBe(sister);
		expect(laneAt(niece, niece.start)).toBeCloseTo(laneAt(sister, niece.start));
		const whole = Math.abs(laneAt(sister, niece.start) - niece.lane);
		const halfway = Math.abs(laneAt(niece, niece.start + 1.25) - niece.lane);
		expect(halfway).toBeGreaterThan(0.1);
		expect(halfway).toBeLessThan(whole);
		expect(Math.abs(laneAt(niece, niece.start + 3) - niece.lane)).toBeLessThan(0.31);
	});

	it('settles on the side of the star it is born from', () => {
		const sky = family();
		expect(Math.sign(star(sky, 'niece').lane)).toBe(Math.sign(star(sky, 'sister').lane));
		// Even when the one it is born from has fewer photos and would go later.
		const reversed = buildSky(
			[
				{ ...person('child', { 2018: 90, 2026: 90 }, 'family'), branchFromPersonId: 'parent' },
				person('other', { 2000: 50, 2026: 50 }, 'family'),
				person('parent', { 1990: 2, 2026: 2 }, 'family')
			],
			today
		);
		expect(Math.sign(star(reversed, 'child').lane)).toBe(Math.sign(star(reversed, 'parent').lane));
	});

	it('a child with no one chosen is born from the main line', () => {
		const sky = buildSky([person('daughter', { 2020: 9, 2026: 9 }, 'children')], today);
		const daughter = star(sky, 'daughter');
		expect(daughter.origin).toBe('you');
		expect(laneAt(daughter, daughter.start)).toBe(0);
		expect(Math.abs(laneAt(daughter, 2026))).toBeGreaterThan(0.9);
	});

	it('whoever is born from the person the user is, is born from the main line', () => {
		const people = [
			{ ...person('nephew', { 2015: 4, 2026: 4 }, 'family'), branchFromPersonId: 'me' }
		];
		expect(star(buildSky(people, today), 'nephew').origin).toBeNull();
		const nephew = star(buildSky(people, today, 'me'), 'nephew');
		expect(nephew.origin).toBe('you');
		expect(laneAt(nephew, nephew.start)).toBe(0);
	});

	it('ignores a branch from someone missing, already fixed or in a loop', () => {
		expect(star(family('nobody'), 'niece').origin).toBeNull();
		const stopped = buildSky(
			[
				person('grandfather', { 1990: 5, 1997: 5 }, 'family'),
				{ ...person('niece', { 2018: 5, 2026: 5 }, 'family'), branchFromPersonId: 'grandfather' }
			],
			today
		);
		expect(star(stopped, 'niece').origin).toBeNull();
		const loop = buildSky(
			[
				{ ...person('a', { 2000: 5, 2026: 5 }), branchFromPersonId: 'b' },
				{ ...person('b', { 2000: 5, 2026: 5 }), branchFromPersonId: 'a' }
			],
			today
		);
		expect(star(loop, 'a').origin).toBeNull();
		expect(Number.isFinite(laneAt(star(loop, 'b'), 2010))).toBe(true);
	});
});

describe('yearStep', () => {
	it('spaces the year lines so their labels fit', () => {
		expect(yearStep(60)).toBe(1);
		expect(yearStep(30)).toBe(2);
		expect(yearStep(12)).toBe(5);
		expect(yearStep(3)).toBe(20);
	});
});
