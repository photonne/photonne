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
	/**
	 * Who it hangs from, drawn or not: what keeps a family together. A star
	 * and everyone hanging from it, however far down, are a pack.
	 */
	parent: Star | null;
}

/** A star and those who hang from it, on lanes of their own next to each other. */
export interface Pack {
	stars: Star[];
	/** Innermost and outermost lane, both on the same side of the main line. */
	inner: number;
	outer: number;
	/** From the first photo of any of them to where the last trail stops. */
	from: number;
	until: number;
}

export interface Sky {
	stars: Star[];
	/** The time shown: first photo of anyone, to today. */
	from: number;
	to: number;
	/** Outermost lanes in use, above (negative) and below the main line. */
	top: number;
	bottom: number;
	/** The packs of more than one star, to draw each as a group. */
	packs: Pack[];
}

/** Years without photos before a star dims, and before it stays behind. */
export const FADE_AFTER = 1;
export const FIXED_AFTER = 2;

/** How far a star drifts from its lane in a year without photos, in lanes. */
const DRIFT = 0.3;
/** Space around a pack, in lanes: sets it apart from its neighbours. */
const PACK_GAP = 0.35;
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

/**
 * Places every person in the sky as it is `today`. `selfId` is the person the
 * user is (not among `people`): whoever is born from them is born from the
 * main line.
 */
export function buildSky(
	people: readonly SkyPerson[],
	today: Date,
	selfId: string | null = null
): Sky {
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
			origin: null,
			parent: null
		};
	});

	// Who hangs from whom. A loop (the server refuses them) leaves its stars
	// on their own.
	const byId = new Map(stars.map((star) => [star.id, star]));
	for (const [index, person] of people.entries()) {
		const parent = person.branchFromPersonId ? byId.get(person.branchFromPersonId) : undefined;
		if (parent && parent !== stars[index]) stars[index].parent = parent;
	}
	for (const star of stars) {
		const seen = new Set<Star>([star]);
		for (let up = star.parent; up; up = up.parent) {
			if (seen.has(up)) {
				star.parent = null;
				break;
			}
			seen.add(up);
		}
	}

	// Branches are drawn only from a star that is still moving when this one
	// appears; from the main line, for whoever hangs from the user and for a
	// child with no one chosen.
	for (const [index, person] of people.entries()) {
		const star = stars[index];
		const parent = star.parent;
		if (parent && parent.start <= star.start && star.start <= parent.until) {
			star.origin = parent;
		} else if (
			person.branchFromPersonId
				? person.branchFromPersonId === selfId
				: star.category === 'children'
		) {
			star.origin = 'you';
		}
	}

	// Packs: each star with no one above it, followed by those who hang from
	// it, each right before their own.
	const children = new Map<Star, Star[]>();
	for (const star of stars) {
		if (star.parent) children.set(star.parent, [...(children.get(star.parent) ?? []), star]);
	}
	const family = (star: Star): Star[] => [
		star,
		...(children.get(star) ?? []).sort((a, b) => a.start - b.start).flatMap(family)
	];
	const units = stars.filter((star) => !star.parent).map(family);
	const photos = (unit: Star[]) => unit.reduce((sum, star) => sum + star.assetCount, 0);
	const apart = (a: Star, b: Star) =>
		a.start > b.until + LANE_REST || b.start > a.until + LANE_REST;

	// Each band takes the same distance on both sides of the main line, so a
	// colleague is never nearer than family. A pack goes whole in the band of
	// the star it starts from. Within a band, whoever has more photos goes
	// first and gets the innermost lanes; a lane whose star stopped is reused
	// by someone who appears later, but never one inside somebody else's pack.
	const packs: Pack[] = [];
	let offset = 0;
	const weight = { [-1]: 0, [1]: 0 };
	for (const band of BANDS) {
		const members = units
			.filter((unit) => band.includes(unit[0].category))
			.sort(
				(a, b) =>
					band.indexOf(a[0].category) - band.indexOf(b[0].category) ||
					photos(b) - photos(a) ||
					a[0].start - b[0].start
			);
		if (members.length === 0) continue;
		// Lanes that can be shared, and how far each side has got, in lanes.
		const shared: Record<-1 | 1, { at: number; stars: Star[] }[]> = { [-1]: [], [1]: [] };
		const reach = { [-1]: 0, [1]: 0 };
		for (const unit of members) {
			if (unit.length === 1) {
				const star = unit[0];
				const slot = ([-1, 1] as const)
					.map((side) => {
						const free = shared[side].find((lane) =>
							lane.stars.every((other) => apart(star, other))
						);
						return { side, free, at: free?.at ?? reach[side] + 1 };
					})
					.sort((a, b) => a.at - b.at || weight[a.side] - weight[b.side] || a.side - b.side)[0];
				if (slot.free) slot.free.stars.push(star);
				else {
					shared[slot.side].push({ at: slot.at, stars: [star] });
					reach[slot.side] = slot.at;
				}
				weight[slot.side]++;
				star.lane = slot.side * (offset + slot.at);
				continue;
			}

			// A pack: its own lanes, one after another on the emptier side, with
			// some air around. Inside, a lane is reused as anywhere else.
			const side = ([-1, 1] as const)
				.slice()
				.sort((a, b) => reach[a] - reach[b] || weight[a] - weight[b] || a - b)[0];
			const rows: Star[][] = [];
			for (const star of unit) {
				const row = rows.find((lane) => lane.every((other) => apart(star, other)));
				if (row) row.push(star);
				else rows.push([star]);
			}
			const first = reach[side] + PACK_GAP + 1;
			rows.forEach((row, index) => {
				for (const star of row) star.lane = side * (offset + first + index);
			});
			reach[side] = first + rows.length - 1 + PACK_GAP;
			weight[side] += unit.length;
			packs.push({
				stars: unit,
				inner: side * (offset + first),
				outer: side * (offset + first + rows.length - 1),
				from: Math.min(...unit.map((star) => star.start)),
				until: Math.max(...unit.map((star) => star.until))
			});
		}
		offset += Math.max(reach[-1], reach[1]) + BAND_GAP;
	}

	return {
		stars: stars.sort((a, b) => a.start - b.start),
		from: stars.length > 0 ? Math.min(...stars.map((star) => star.start)) : to - 1,
		to,
		top: Math.min(0, ...stars.map((star) => star.lane)),
		bottom: Math.max(0, ...stars.map((star) => star.lane)),
		packs
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
