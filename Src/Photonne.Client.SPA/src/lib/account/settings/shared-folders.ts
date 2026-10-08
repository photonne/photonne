import type { FolderResponse } from '#lib/api/index.js';

/** Where the server keeps shared folders (FoldersEndpoint.GetSharedRootPath). */
const SHARED_ROOT = '/assets/shared';

export interface SharedFolderRow {
	id: string;
	name: string;
	path: string;
	/** Nesting under the shared root, 0 for a top-level shared folder. */
	depth: number;
	assetCount: number;
	excluded: boolean;
}

function isShared(path: string) {
	const normalized = path.replace(/\\/g, '/').replace(/\/+$/, '');
	return normalized.toLowerCase().startsWith(`${SHARED_ROOT}/`);
}

/**
 * The folders the user can hide from their own timeline, memories, people
 * and search: those in the shared space (the only ones the server accepts).
 * The list may come flat or as a tree; either way it is flattened and sorted
 * by path so children follow their parent.
 */
export function sharedFolderRows(folders: readonly FolderResponse[]): SharedFolderRow[] {
	const seen = new Map<string, FolderResponse>();
	const walk = (list: readonly FolderResponse[]) => {
		for (const folder of list) {
			if (!seen.has(folder.id)) seen.set(folder.id, folder);
			if (folder.subFolders?.length) walk(folder.subFolders);
		}
	};
	walk(folders);

	return [...seen.values()]
		.filter((folder) => isShared(folder.path))
		.map((folder) => {
			const relative = folder.path.replace(/\\/g, '/').slice(SHARED_ROOT.length + 1);
			return {
				id: folder.id,
				name: folder.name,
				path: folder.path,
				depth: relative.split('/').filter(Boolean).length - 1,
				assetCount: folder.assetCount,
				excluded: folder.excludedFromDiscovery
			};
		})
		.sort((a, b) => a.path.localeCompare(b.path));
}
