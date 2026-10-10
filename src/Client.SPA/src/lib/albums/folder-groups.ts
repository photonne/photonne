import type { FolderResponse } from '#lib/api/index.js';
import { sortTree } from './folder-tree.js';

const USERS = '/assets/users/';
const SHARED = '/assets/shared/';
const SYSTEM = new Set(['_trash', '_archive']);

/**
 * The folders as the user thinks of them, in the buckets the folder pages
 * filter and group by (as in the native app's FolderPartition):
 *
 * - `personal`: the folders directly in the user's home.
 * - `shared`: the folders directly in the shared space, and those of other
 *   users' homes shared with the user.
 * - `external`: one root per external library.
 *
 * `GET /api/folders/tree` is the tree on disk: `/assets`, `/assets/users`,
 * the home itself and the `_trash`/`_archive` system folders are containers
 * no one files into, so they never show. The hierarchy is rebuilt from the
 * paths, not from `parentFolderId`: a folder created at the top of a home
 * has no parent on the server, and would otherwise land beside `/assets`.
 *
 * A personal root's parent is `null`: that is how the API names the top of
 * the user's folders when creating or moving one. Every level comes sorted
 * by name.
 */
export interface FolderGroups {
	personal: FolderResponse[];
	shared: FolderResponse[];
	external: FolderResponse[];
}

const normalized = (path: string) => path.replace(/\\/g, '/').replace(/\/+$/, '');
const parentPath = (path: string) => path.slice(0, path.lastIndexOf('/'));

/** What follows `prefix` when it is a single path segment, else null. */
function childOf(path: string, prefix: string) {
	if (!path.toLowerCase().startsWith(prefix.toLowerCase())) return null;
	const rest = path.slice(prefix.length);
	return rest && !rest.includes('/') ? rest : null;
}

function flatten(nodes: readonly FolderResponse[], out = new Map<string, FolderResponse>()) {
	for (const node of nodes) {
		if (!out.has(node.id)) out.set(node.id, node);
		flatten(node.subFolders ?? [], out);
	}
	return out;
}

export function groupFolders(
	tree: readonly FolderResponse[],
	username: string,
	locale = 'es'
): FolderGroups {
	const all = [...flatten(tree).values()];
	const byPath = new Map(all.map((folder) => [normalized(folder.path).toLowerCase(), folder]));
	const children = new Map<string, FolderResponse[]>();
	for (const folder of all) {
		const parent = byPath.get(parentPath(normalized(folder.path)).toLowerCase());
		if (!parent) continue;
		const list = children.get(parent.id) ?? [];
		list.push(folder);
		children.set(parent.id, list);
	}

	// Inside a library everything is the host's own folders, whatever its names.
	const build = (folder: FolderResponse, parentFolderId: string | null): FolderResponse => ({
		...folder,
		parentFolderId,
		subFolders: (children.get(folder.id) ?? [])
			.filter((child) => folder.externalLibraryId || !SYSTEM.has(child.name.toLowerCase()))
			.map((child) => build(child, folder.id))
	});
	const pathParentId = (folder: FolderResponse) =>
		byPath.get(parentPath(normalized(folder.path)).toLowerCase())?.id ?? folder.parentFolderId;

	const home = `${USERS}${username}/`;
	const groups: FolderGroups = { personal: [], shared: [], external: [] };
	for (const folder of all) {
		const path = normalized(folder.path);
		if (folder.externalLibraryId) {
			const parent = byPath.get(parentPath(path).toLowerCase());
			if (parent?.externalLibraryId !== folder.externalLibraryId)
				groups.external.push(build(folder, pathParentId(folder)));
			continue;
		}
		const own = username ? childOf(path, home) : null;
		if (own) {
			if (!SYSTEM.has(own.toLowerCase())) groups.personal.push(build(folder, null));
			continue;
		}
		const inSharedSpace = childOf(path, SHARED) !== null;
		const inOthersHome = childOf(parentPath(path), USERS) !== null;
		if ((inSharedSpace || inOthersHome) && !SYSTEM.has(folder.name.toLowerCase()))
			groups.shared.push(build(folder, pathParentId(folder)));
	}
	return {
		personal: sortTree(groups.personal, locale),
		shared: sortTree(groups.shared, locale),
		external: sortTree(groups.external, locale)
	};
}

/** The three groups as one list of top-level folders, personal first. */
export function browsableRoots(groups: FolderGroups): FolderResponse[] {
	return [...groups.personal, ...groups.shared, ...groups.external];
}
