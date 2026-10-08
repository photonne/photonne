import type { TimelineResponse, UserDuplicateGroupResponse } from '#lib/api/index.js';

/**
 * Review of exact duplicates: in each group the user marks which copies go to
 * the trash. A group always keeps at least one copy, so marking can never
 * delete the photo itself.
 */

type Group = UserDuplicateGroupResponse;
type Marked = ReadonlySet<string>;

const byDate = (a: TimelineResponse, b: TimelineResponse) =>
	a.fileCreatedAt.localeCompare(b.fileCreatedAt) || a.id.localeCompare(b.id);

/** The copy taken first (ties: the first by id, so the choice is stable). */
export function oldest(assets: readonly TimelineResponse[]) {
	return [...assets].sort(byDate)[0];
}

/** The biggest file (ties: the oldest), usually the original. */
export function largest(assets: readonly TimelineResponse[]) {
	return [...assets].sort((a, b) => b.fileSize - a.fileSize || byDate(a, b))[0];
}

/** Keeps only `keeperId` in its group and marks the rest. */
export function keepOnly(marked: Marked, group: Group, keeperId: string): Set<string> {
	const next = new Set(marked);
	for (const asset of group.assets) {
		if (asset.id === keeperId) next.delete(asset.id);
		else next.add(asset.id);
	}
	return next;
}

/** In every group, keeps the copy `pick` chooses and marks the others. */
export function keepInAll(
	groups: readonly Group[],
	pick: (assets: readonly TimelineResponse[]) => TimelineResponse
): Set<string> {
	let marked = new Set<string>();
	for (const group of groups) {
		if (group.assets.length) marked = keepOnly(marked, group, pick(group.assets).id);
	}
	return marked;
}

/**
 * Marks or unmarks one copy. Marking the last copy left would empty the
 * group, so it is refused (the set comes back unchanged).
 */
export function toggleCopy(marked: Marked, group: Group, id: string): Set<string> {
	const next = new Set(marked);
	if (next.has(id)) {
		next.delete(id);
		return next;
	}
	const kept = group.assets.filter((asset) => !next.has(asset.id));
	if (kept.length <= 1) return next;
	next.add(id);
	return next;
}

/** Bytes the marked copies take. */
export function markedBytes(groups: readonly Group[], marked: Marked) {
	let total = 0;
	for (const group of groups)
		for (const asset of group.assets) if (marked.has(asset.id)) total += asset.fileSize;
	return total;
}

/** What could be freed keeping the biggest copy of each group. */
export function recoverableBytes(groups: readonly Group[]) {
	return groups.reduce(
		(sum, group) =>
			sum +
			group.assets.reduce((s, a) => s + a.fileSize, 0) -
			(group.assets.length ? largest(group.assets).fileSize : 0),
		0
	);
}

/** The groups without `ids`; a group left with one copy is no longer a duplicate. */
export function withoutAssets(groups: readonly Group[], ids: Iterable<string>): Group[] {
	const gone = new Set(ids);
	return groups
		.map((group) => {
			const assets = group.assets.filter((asset) => !gone.has(asset.id));
			return { ...group, assets, totalSize: assets.reduce((s, a) => s + a.fileSize, 0) };
		})
		.filter((group) => group.assets.length > 1);
}
