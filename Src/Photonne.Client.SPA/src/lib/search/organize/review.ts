import type { YearCount, YearGroup } from '#lib/api/index.js';

/** A labelled block of ids in the review before a move (a capture year). */
export interface ReviewGroup {
	label: string;
	ids: readonly string[];
}

/** Server year groups as review groups, newest year first. */
export function reviewGroups(groups: readonly YearGroup[]): ReviewGroup[] {
	return [...groups]
		.sort((a, b) => b.year - a.year)
		.filter((group) => group.assetIds.length > 0)
		.map((group) => ({ label: String(group.year), ids: group.assetIds }));
}

/** What still moves after the review took some photos out, in order. */
export function keptIds(groups: readonly ReviewGroup[], excluded: ReadonlySet<string>) {
	return groups.flatMap((group) => group.ids.filter((id) => !excluded.has(id)));
}

/** How much of a group still moves: for the group's tri-state checkbox. */
export function groupState(group: ReviewGroup, excluded: ReadonlySet<string>) {
	const out = group.ids.filter((id) => excluded.has(id)).length;
	return out === 0 ? 'all' : out === group.ids.length ? 'none' : 'some';
}

/** Ticking a group puts all of it back; unticking takes all of it out. */
export function toggleGroup(group: ReviewGroup, excluded: ReadonlySet<string>) {
	const next = new Set(excluded);
	const putBack = groupState(group, excluded) !== 'all';
	for (const id of group.ids) {
		if (putBack) next.delete(id);
		else next.add(id);
	}
	return next;
}

/** "2023: 5 · 2024: 7", the years a move by capture year filed into. */
export function yearSummary(breakdown: readonly YearCount[]) {
	return [...breakdown]
		.sort((a, b) => a.year - b.year)
		.map((year) => `${year.year}: ${year.count}`)
		.join(' · ');
}

/** Takes one photo out of the move, or puts it back. */
export function toggleId(excluded: ReadonlySet<string>, id: string) {
	const next = new Set(excluded);
	if (next.has(id)) next.delete(id);
	else next.add(id);
	return next;
}
