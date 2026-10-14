/**
 * How a deck of cards (ExploreDeck) draws each card from its distance to the
 * centre, in card widths: the nearest one is full size and on top, the rest
 * shrink and go under it the further out they are.
 */

/** Scale of the card in front and of one a long way out. */
const FRONT_SCALE = 1;
const BACK_SCALE = 0.78;
/** Beyond this many card widths every card is as small as it gets. */
const FAR = 1.6;

export interface DeckCard {
	scale: number;
	/** Stacking order: higher is nearer the front. */
	depth: number;
	/** The one card in front, which shows its name and opens on click. */
	centred: boolean;
}

export function deckScales(distances: readonly number[]): DeckCard[] {
	if (distances.length === 0) return [];
	const nearest = Math.min(...distances);
	let centredIndex = distances.indexOf(nearest);
	return distances.map((distance, index) => {
		const t = Math.min(distance, FAR) / FAR;
		const centred = index === centredIndex;
		// Two cards at the same distance: only the first is in front.
		if (centred) centredIndex = -1;
		return {
			scale: FRONT_SCALE - (FRONT_SCALE - BACK_SCALE) * t,
			depth: 100 - Math.round(Math.min(distance, 9) * 10),
			centred
		};
	});
}
