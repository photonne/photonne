/** The part of a folder the tree needs (FolderResponse satisfies it). */
export interface TreeFolder {
	id: string;
	name: string;
	parentFolderId: string | null;
	subFolders: TreeFolder[];
}

/** A copy of the tree with every level sorted by name, the way a file manager shows it. */
export function sortTree<T extends TreeFolder>(nodes: readonly T[], locale = 'es'): T[] {
	const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true });
	const sort = (level: readonly T[]): T[] =>
		[...level]
			.sort((a, b) => collator.compare(a.name, b.name))
			.map((node) => ({ ...node, subFolders: sort(node.subFolders as T[]) }));
	return sort(nodes);
}

/** The folder and its ancestors, root first; empty when it isn't in the tree. */
export function pathTo<T extends TreeFolder>(nodes: readonly T[], id: string): T[] {
	for (const node of nodes) {
		if (node.id === id) return [node];
		const below = pathTo(node.subFolders as T[], id);
		if (below.length) return [node, ...below];
	}
	return [];
}

export function findFolder<T extends TreeFolder>(nodes: readonly T[], id: string): T | null {
	return pathTo(nodes, id).at(-1) ?? null;
}

/** The folder's id and those of everything below it. */
export function subtreeIds(folder: TreeFolder): Set<string> {
	const ids = new Set<string>();
	const walk = (node: TreeFolder) => {
		ids.add(node.id);
		node.subFolders.forEach(walk);
	};
	walk(folder);
	return ids;
}

export interface TreeRow<T extends TreeFolder> {
	folder: T;
	depth: number;
	parentId: string | null;
	hasChildren: boolean;
	expanded: boolean;
}

/** The rows a tree shows: depth first, children only under expanded folders. */
export function visibleRows<T extends TreeFolder>(
	nodes: readonly T[],
	expanded: ReadonlySet<string>,
	exclude: ReadonlySet<string> = new Set()
): TreeRow<T>[] {
	const rows: TreeRow<T>[] = [];
	const walk = (level: readonly T[], depth: number, parentId: string | null) => {
		for (const folder of level) {
			if (exclude.has(folder.id)) continue;
			const children = (folder.subFolders as T[]).filter((child) => !exclude.has(child.id));
			const open = children.length > 0 && expanded.has(folder.id);
			rows.push({ folder, depth, parentId, hasChildren: children.length > 0, expanded: open });
			if (open) walk(children, depth + 1, folder.id);
		}
	};
	walk(nodes, 0, null);
	return rows;
}

export type TreeMove =
	{ kind: 'focus'; id: string } | { kind: 'expand'; id: string } | { kind: 'collapse'; id: string };

/**
 * What a key does in a tree (WAI-ARIA treeview pattern): Up/Down walk the
 * visible rows, Home/End jump, Right opens a folder or enters it, Left
 * closes it or goes up to its parent. Null for keys the tree ignores.
 */
export function treeKey<T extends TreeFolder>(
	rows: readonly TreeRow<T>[],
	currentId: string,
	key: string
): TreeMove | null {
	const index = rows.findIndex((row) => row.folder.id === currentId);
	if (index < 0) return rows.length ? { kind: 'focus', id: rows[0].folder.id } : null;
	const row = rows[index];
	const focus = (target: TreeRow<T> | undefined): TreeMove | null =>
		target ? { kind: 'focus', id: target.folder.id } : null;
	switch (key) {
		case 'ArrowDown':
			return focus(rows[index + 1]);
		case 'ArrowUp':
			return focus(rows[index - 1]);
		case 'Home':
			return focus(rows[0]);
		case 'End':
			return focus(rows.at(-1));
		case 'ArrowRight':
			if (!row.hasChildren) return null;
			return row.expanded ? focus(rows[index + 1]) : { kind: 'expand', id: row.folder.id };
		case 'ArrowLeft':
			if (row.expanded) return { kind: 'collapse', id: row.folder.id };
			return row.parentId ? { kind: 'focus', id: row.parentId } : null;
		default:
			return null;
	}
}

/**
 * Where a dragged folder may go: anywhere but itself, its own subtree and
 * the parent it already has.
 */
export function canDropFolder(tree: readonly TreeFolder[], draggedId: string, targetId: string) {
	const dragged = findFolder(tree, draggedId);
	if (!dragged) return false;
	return !subtreeIds(dragged).has(targetId) && dragged.parentFolderId !== targetId;
}

/** "2024: 5 · 2023: 7", the post-move summary of a move into year subfolders. */
export function yearSummary(breakdown: readonly { year: number; count: number }[]) {
	return breakdown.map((bucket) => `${bucket.year}: ${bucket.count}`).join(' · ');
}
