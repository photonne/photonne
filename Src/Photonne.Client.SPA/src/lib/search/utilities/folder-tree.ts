import { matches } from '../text.js';

/** The part of a folder the locations tree reads. */
export interface TreeFolder {
	id: string;
	name: string;
	path: string;
	/** Photos in the folder and everything under it (the server adds them up). */
	assetCount: number;
	subFolders: TreeFolder[];
}

/** Folders in the tree, at every depth. */
export function countFolders(nodes: readonly TreeFolder[]): number {
	return nodes.reduce((sum, node) => sum + 1 + countFolders(node.subFolders), 0);
}

/** Photos directly in a folder, not in its subfolders. */
export function ownAssets(node: TreeFolder): number {
	return node.assetCount - node.subFolders.reduce((sum, child) => sum + child.assetCount, 0);
}

export function totalAssets(nodes: readonly TreeFolder[]) {
	return nodes.reduce((sum, node) => sum + node.assetCount, 0);
}

/** Ids of every folder with subfolders: "expand all". */
export function branchIds(nodes: readonly TreeFolder[]): string[] {
	return nodes.flatMap((node) =>
		node.subFolders.length ? [node.id, ...branchIds(node.subFolders)] : []
	);
}

/**
 * The folders whose name or path matches, with their ancestors so they stay
 * in place; a match keeps its whole subtree.
 */
export function filterTree<T extends TreeFolder>(nodes: readonly T[], text: string): T[] {
	if (!text.trim()) return [...nodes];
	return nodes.flatMap((node) => {
		if (matches(node.name, text) || matches(node.path, text)) return [node];
		const children = filterTree(node.subFolders as T[], text);
		return children.length ? [{ ...node, subFolders: children }] : [];
	});
}
