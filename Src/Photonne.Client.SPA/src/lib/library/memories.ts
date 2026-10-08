import type { MemoryResponse } from '#lib/api/index.js';

/**
 * How the memories feed is laid out, as in the native app: Recuerdos keeps the
 * journeys through time (on this day, this month, a person through the
 * years); the themes (trips, beach days, favorites of a year…) live in
 * Explorar; pairs of people only on a person's page (the feed leaves them out).
 */
export type MemoryArea = 'memories' | 'explore' | 'people';

/** The three blocks of the Recuerdos page, in display order. */
export type MemoryBlock = 'today' | 'month' | 'years';

export function memoryArea(kind: string): MemoryArea {
	switch (kind.toLowerCase()) {
		case 'onthisday':
		case 'thismonth':
		case 'personthroughyears':
			return 'memories';
		case 'peopletogether':
			return 'people';
		default:
			return 'explore';
	}
}

export function memoryBlock(kind: string): MemoryBlock | null {
	switch (kind.toLowerCase()) {
		case 'onthisday':
			return 'today';
		case 'thismonth':
			return 'month';
		case 'personthroughyears':
			return 'years';
		default:
			return null;
	}
}

const BLOCKS: MemoryBlock[] = ['today', 'month', 'years'];

/**
 * The Recuerdos blocks that have something. Today and this month go newest
 * year first ("1 year ago" before "5 years ago"); people keep the server's
 * order, which ranks the most present first.
 */
export function memoryBlocks(memories: readonly MemoryResponse[]) {
	return BLOCKS.map((block) => {
		const inBlock = memories.filter((memory) => memoryBlock(memory.kind) === block);
		return { block, memories: block === 'years' ? inBlock : newestFirst(inBlock) };
	}).filter((group) => group.memories.length > 0);
}

/** One row of Explorar: a theme ("Días de playa") and its periods. */
export interface ThemeRow {
	key: string;
	title: string;
	memories: MemoryResponse[];
}

// Coarse sections that fix the order of the theme rows; within a section the
// rows go alphabetically, so a row stays where it was yesterday instead of
// moving with the nightly score.
const SECTION_ORDER: Record<string, number> = {
	trip: 0,
	favoritesofyear: 1,
	curatedscene: 2,
	petsandfood: 2
};

/**
 * Folds the Explorar memories into one row per theme. The server ranks by
 * score, which scatters one theme across the feed; grouped, "Días de playa"
 * reads as one row of years, newest first.
 */
export function themeRows(memories: readonly MemoryResponse[]): ThemeRow[] {
	const rows = new Map<string, ThemeRow & { order: number }>();
	for (const memory of memories) {
		if (memoryArea(memory.kind) !== 'explore') continue;
		const key = memory.themeKey || memory.kind;
		let row = rows.get(key);
		if (!row) {
			row = {
				key,
				title: memory.groupTitle || memory.title,
				memories: [],
				order: SECTION_ORDER[memory.kind.toLowerCase()] ?? 3
			};
			rows.set(key, row);
		}
		row.memories.push(memory);
	}
	return [...rows.values()]
		.sort((a, b) => a.order - b.order || a.title.localeCompare(b.title))
		.map(({ key, title, memories }) => ({ key, title, memories: newestFirst(memories) }));
}

function newestFirst(memories: MemoryResponse[]) {
	return [...memories].sort((a, b) => b.windowEnd.localeCompare(a.windowEnd));
}
