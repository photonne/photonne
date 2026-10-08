import type { FolderResponse } from '#lib/api/index.js';
import { fold, type ListView } from './album-list.js';
import { subtreeIds } from './folder-tree.js';

/** Which folders: everything, the user's own, the shared space, or external libraries. */
export type FolderScope = 'all' | 'personal' | 'shared' | 'external';
export type FolderSort = 'name' | 'count';

export interface FolderListOptions {
	scope: FolderScope;
	sort: FolderSort;
	descending: boolean;
	view: ListView;
	/** Matched against names at any depth, accent- and case-insensitive. */
	query: string;
}

export const defaultFolderListOptions: FolderListOptions = {
	scope: 'all',
	sort: 'name',
	descending: false,
	view: 'grid',
	query: ''
};

/**
 * The bucket a folder belongs to. An external library may point inside the
 * shared space or a home, so the library comes first (as in the native
 * app's FolderPartition): a folder never lands in two buckets.
 */
export function folderScope(folder: FolderResponse): Exclude<FolderScope, 'all'> {
	if (folder.externalLibraryId) return 'external';
	return folder.isShared ? 'shared' : 'personal';
}

/** Every folder of the tree, depth first. */
export function flattenTree(nodes: readonly FolderResponse[]): FolderResponse[] {
	return nodes.flatMap((node) => [node, ...flattenTree(node.subFolders ?? [])]);
}

/**
 * The folders the index shows: the top level while browsing, any depth
 * while searching (a folder should be found wherever it is nested), in the
 * chosen scope and order.
 */
export function arrangeFolders(
	roots: readonly FolderResponse[],
	options: FolderListOptions,
	locale = 'es'
): FolderResponse[] {
	const needle = fold(options.query.trim());
	const source = needle ? flattenTree(roots) : roots;
	const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true });
	const byName = (a: FolderResponse, b: FolderResponse) => collator.compare(a.name, b.name);
	const compare =
		options.sort === 'count'
			? (a: FolderResponse, b: FolderResponse) => a.assetCount - b.assetCount || byName(a, b)
			: byName;
	return source
		.filter(
			(folder) =>
				(options.scope === 'all' || folderScope(folder) === options.scope) &&
				(!needle || fold(folder.name).includes(needle))
		)
		.sort((a, b) => (options.descending ? compare(b, a) : compare(a, b)));
}

/** How many top-level folders each scope holds, for the scope tabs. */
export function folderScopeCounts(roots: readonly FolderResponse[]) {
	const counts = { all: roots.length, personal: 0, shared: 0, external: 0 };
	for (const folder of roots) counts[folderScope(folder)]++;
	return counts;
}

/**
 * Saved sort, direction and view. The scope isn't kept: it hides folders,
 * and a filter restored weeks later reads as missing data (native app).
 */
export function parseFolderListOptions(raw: string | null): FolderListOptions {
	try {
		const saved = JSON.parse(raw ?? '{}') as Partial<FolderListOptions>;
		return {
			...defaultFolderListOptions,
			sort: saved.sort === 'count' ? 'count' : 'name',
			descending: saved.descending === true,
			view: saved.view === 'list' ? 'list' : 'grid'
		};
	} catch {
		return { ...defaultFolderListOptions };
	}
}

/**
 * What a selection of folders allows: an action only if it works for every
 * one. External libraries are read-only mirrors of the host, whatever the
 * flags say (the server reports an admin as owner of any shared path).
 */
export function folderSelectionActions(selected: readonly FolderResponse[]) {
	const some = selected.length > 0;
	return {
		canMove: some && selected.every((f) => f.canWrite && !f.externalLibraryId),
		canDelete: some && selected.every((f) => f.canDelete && !f.externalLibraryId)
	};
}

const normalized = (path: string) => path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();

/**
 * Drops folders nested in another selected one: deleting or moving the
 * parent already takes its subtree, and acting on the child afterwards
 * would fail (it's gone) or pull it out of its parent unasked.
 */
export function topmostFolders<T extends Pick<FolderResponse, 'path'>>(
	selected: readonly T[]
): T[] {
	const prefixes = selected.map((folder) => `${normalized(folder.path)}/`);
	return selected.filter((folder) => {
		const path = normalized(folder.path);
		return !prefixes.some((prefix) => path.startsWith(prefix));
	});
}

/**
 * Where the selected folders can be moved: writable folders outside every
 * selected subtree and outside external libraries, depth first with their
 * depth for indentation.
 */
export function moveDestinations(
	tree: readonly FolderResponse[],
	selected: readonly FolderResponse[]
): { folder: FolderResponse; depth: number }[] {
	const all = flattenTree(tree);
	const excluded = new Set<string>();
	for (const folder of selected) {
		const node = all.find((candidate) => candidate.id === folder.id) ?? folder;
		for (const id of subtreeIds(node)) excluded.add(id);
	}
	const out: { folder: FolderResponse; depth: number }[] = [];
	const walk = (nodes: readonly FolderResponse[], depth: number) => {
		for (const node of nodes) {
			if (excluded.has(node.id)) continue;
			if (node.canWrite && !node.externalLibraryId) out.push({ folder: node, depth });
			walk(node.subFolders ?? [], depth + 1);
		}
	};
	walk(tree, 0);
	return out;
}
