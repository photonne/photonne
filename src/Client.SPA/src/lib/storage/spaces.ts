/**
 * Where bytes live. Only the personal space counts towards a quota: shared
 * folders are the house's (usually filled by admins) and an external library
 * is another system's disk. Both can be hidden from the totals; a hidden space
 * stays in the bar, hatched, so it never looks as if it vanished.
 */
export type Space = 'personal' | 'shared' | 'library';

/** The spaces a viewer may hide. Personal always counts. */
export type HideableSpace = Exclude<Space, 'personal'>;

export const hideableSpaces: readonly HideableSpace[] = ['shared', 'library'];

export interface SpaceBytes {
	personal: number;
	shared: number;
	library: number;
}

/** The bytes that remain once the hidden spaces are taken out. */
export function visibleBytes(bytes: SpaceBytes, hidden: ReadonlySet<HideableSpace>): number {
	return (
		bytes.personal +
		(hidden.has('shared') ? 0 : bytes.shared) +
		(hidden.has('library') ? 0 : bytes.library)
	);
}

/** Unknown names are dropped: a stale preference should cost a toggle, not the page. */
export function parseHidden(raw: string | null): HideableSpace[] {
	if (!raw) return [];
	return raw
		.split(',')
		.map((name) => name.trim())
		.filter((name): name is HideableSpace => (hideableSpaces as string[]).includes(name));
}
