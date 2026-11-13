import { relationshipCategory, type RelationshipCategory } from '../relationship.js';

/** What the server gives per person (GET /api/people/constellation). */
export interface SkyPerson {
	id: string;
	name: string;
	relationshipCategory?: string | null;
	relationshipLabel?: string | null;
	/** Whose trail this person is born from (the niece, from the sister). */
	branchFromPersonId?: string | null;
	firstCapturedAt: string;
	lastCapturedAt: string;
	assetCount: number;
	years: readonly { year: number; assetCount: number }[];
}

/**
 * alive: photos within the last year. fading: none for a year. fixed: none
 * for two, so the star stays where its last photo is.
 */
export type StarStatus = 'alive' | 'fading' | 'fixed';

export interface Star {
	id: string;
	name: string;
	category: RelationshipCategory | null;
	label: string | null;
	/** First and last photo, as decimal years (2014.5 is mid-2014). */
	start: number;
	end: number;
	status: StarStatus;
	/** Where the trail stops: the last photo if fixed, today otherwise. */
	until: number;
	/** Signed distance to the main line, in lanes: negative above it. */
	lane: number;
	assetCount: number;
	years: readonly { year: number; assetCount: number }[];
	/** Most photos in one year. */
	peak: number;
	/**
	 * Whose trail it is born from: another star, or the main line ('you': a
	 * child with no one chosen). Null when it appears on its own.
	 */
	origin: Star | 'you' | null;
}

export interface Sky {
	stars: Star[];
	/** The time shown: first photo of anyone, to today. */
	from: number;
	to: number;
	/** Outermost lanes in use, above (negative) and below the main line. */
	top: number;
	bottom: number;
}

/** Years without photos before a star dims, and before it stays behind. */
export const FADE_AFTER = 1;
export const FIXED_AFTER = 2;

/** How far a star drifts from its lane in a year without photos, in lanes. */
const DRIFT = 0.3;
/** Space between two relationship bands, in lanes. */
const BAND_GAP = 0.4;
/** Years a star takes to leave the trail it is born from for its own lane. */
export const BRANCH_YEARS = 2.5;
/** A lane is free for someone else this long after its star stopped, in years. */
const LANE_REST = 1.5;

// From the main line outwards. Partner and children share the first band.
const BANDS: readonly (readonly (RelationshipCategory | null)[])[] = [
	['partner', 'children'],
	['family'],
	['friends'],
	['pet'],
	['work'],
	['other'],
	[null]
];

export function decimalYear(date: Date): number {
	const year = date.getFullYear();
	const start = new Date(year, 0, 1).getTime();
	const next = new Date(year + 1, 0, 1).getTime();
	return year + (date.getTime() - start) / (next - start);
}

function statusOf(end: number, today: number): StarStatus {
	const gap = today - end;
	return gap >= FIXED_AFTER ? 'fixed' : gap >= FADE_AFTER ? 'fading' : 'alive';
}

/** Places every person in the sky as it is `today`. */
export function buildSky(people: readonly SkyPerson[], today: Date): Sky {
	const to = decimalYear(today);
	const stars: Star[] = people.map((person) => {
		// A camera with the wrong date can't put a photo in the future.
		const end = Math.min(decimalYear(new Date(person.lastCapturedAt)), to);
		const start = Math.min(decimalYear(new Date(person.firstCapturedAt)), end);
		const status = statusOf(end, to);
		return {
			id: person.id,
			name: person.name,
			category: relationshipCategory(person.relationshipCategory),
			label: person.relationshipLabel?.trim() || null,
			start,
			end,
			status,
			until: status === 'fixed' ? end : to,
			lane: 0,
			assetCount: person.assetCount,
			years: person.years,
			peak: Math.max(1, ...person.years.map((y) => y.assetCount)),
			origin: null
		};
	});

	// Branches: only from a star that is in the sky and still moving when this
	// one appears. A loop (the server refuses them) leaves its stars on their own.
	const byId = new Map(stars.map((star) => [star.id, star]));
	for (const [index, person] of people.entries()) {
		const star = stars[index];
		const parent = person.branchFromPersonId ? byId.get(person.branchFromPersonId) : undefined;
		if (parent && parent !== star && parent.start <= star.start && star.start <= parent.until) {
			star.origin = parent;
		} else if (!person.branchFromPersonId && star.category === 'children') {
			star.origin = 'you';
		}
	}
	for (const star of stars) {
		const seen = new Set<Star>([star]);
		for (let up = star.origin; up && up !== 'you'; up = up.origin) {
			if (seen.has(up)) {
				star.origin = null;
				break;
			}
			seen.add(up);
		}
	}

	// Each band takes the same distance on both sides of the main line, so a
	// colleague is never nearer than family. Within a band, whoever has more
	// photos goes first and gets the innermost lane; a lane whose star stopped
	// is reused by someone who appears later.
	let offset = 0;
	const weight = { [-1]: 0, [1]: 0 };
	for (const band of BANDS) {
		const members = stars
			.filter((star) => band.includes(star.category))
			.sort(
				(a, b) =>
					band.indexOf(a.category) - band.indexOf(b.category) ||
					b.assetCount - a.assetCount ||
					a.start - b.start
			);
		if (members.length === 0) continue;
		const lanes: Record<-1 | 1, Star[][]> = { [-1]: [], [1]: [] };
		while (members.length > 0) {
			// Whoever it is born from goes first, so it can settle on their side.
			const next = members.findIndex(
				(star) => !(star.origin && star.origin !== 'you' && members.includes(star.origin))
			);
			const star = members.splice(Math.max(next, 0), 1)[0];
			const near = star.origin && star.origin !== 'you' ? Math.sign(star.origin.lane) : 0;
			const slot = ([-1, 1] as const)
				.map((side) => {
					const free = lanes[side].findIndex((lane) =>
						lane.every(
							(other) =>
								star.start > other.until + LANE_REST || other.start > star.until + LANE_REST
						)
					);
					return { side, index: free === -1 ? lanes[side].length : free };
				})
				.sort(
					(a, b) =>
						Number(b.side === near) - Number(a.side === near) ||
						a.index - b.index ||
						weight[a.side] - weight[b.side] ||
						a.side - b.side
				)[0];
			(lanes[slot.side][slot.index] ??= []).push(star);
			weight[slot.side]++;
			star.lane = slot.side * (offset + slot.index + 1);
		}
		offset += Math.max(lanes[-1].length, lanes[1].length) + BAND_GAP;
	}

	return {
		stars: stars.sort((a, b) => a.start - b.start),
		from: stars.length > 0 ? Math.min(...stars.map((star) => star.start)) : to - 1,
		to,
		top: Math.min(0, ...stars.map((star) => star.lane)),
		bottom: Math.max(0, ...stars.map((star) => star.lane))
	};
}

/**
 * How present the person is at `t`, from 0 to 1: their photos that year
 * against their best year. A year's count sits at its middle, with a straight
 * line to the next.
 */
export function closeness(star: Star, t: number): number {
	const x = t - 0.5;
	const year = Math.floor(x);
	const at = (y: number) => star.years.find((entry) => entry.year === y)?.assetCount ?? 0;
	const mix = x - year;
	return (at(year) * (1 - mix) + at(year + 1) * mix) / star.peak;
}

/**
 * Where the star is at `t`: its lane, further out the fewer photos it has
 * then. One born from another trail leaves it little by little.
 */
export function laneAt(star: Star, t: number): number {
	const time = Math.max(star.start, Math.min(t, star.until));
	const own = star.lane + Math.sign(star.lane) * DRIFT * (1 - closeness(star, time));
	if (!star.origin) return own;
	const left = Math.min(1, (time - star.start) / BRANCH_YEARS);
	const ease = left * left * (3 - 2 * left);
	const from = star.origin === 'you' ? 0 : laneAt(star.origin, time);
	return from + (own - from) * ease;
}

/** 1 while there are recent photos; down to 0.35 as a year without any goes by. */
export function brightness(star: Star, t: number): number {
	const gap = Math.min(t, star.until) - star.end;
	if (gap <= FADE_AFTER) return 1;
	return 1 - 0.65 * Math.min(1, (gap - FADE_AFTER) / (FIXED_AFTER - FADE_AFTER));
}

/** Photos of the person up to the year of `t`, that one included. */
export function photosUntil(star: Star, t: number): number {
	const year = Math.floor(t);
	return star.years.reduce((sum, entry) => (entry.year <= year ? sum + entry.assetCount : sum), 0);
}

/** A step between year lines that keeps their labels `minGap` pixels apart. */
export function yearStep(pixelsPerYear: number, minGap = 48): number {
	return [1, 2, 5, 10, 20, 50].find((step) => step * pixelsPerYear >= minGap) ?? 100;
}
