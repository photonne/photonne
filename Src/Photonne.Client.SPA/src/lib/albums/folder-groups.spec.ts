import { describe, expect, it } from 'vitest';
import type { FolderResponse } from '#lib/api/index.js';
import { browsableRoots, groupFolders } from './folder-groups.js';

const folder = (
	id: string,
	path: string,
	overrides: Partial<FolderResponse> = {}
): FolderResponse => ({
	id,
	name: path.split('/').at(-1)!,
	path,
	parentFolderId: null,
	createdAt: '2026-01-01T00:00:00Z',
	assetCount: 0,
	firstAssetId: null,
	previewAssetIds: [],
	isShared: path.startsWith('/assets/shared'),
	isOwner: true,
	canWrite: true,
	canDelete: true,
	sharedWithCount: 0,
	externalLibraryId: null,
	excludedFromDiscovery: false,
	isPinned: false,
	pinnedAt: null,
	subFolders: [],
	...overrides
});

const child = (parent: FolderResponse, node: FolderResponse) => {
	parent.subFolders.push({ ...node, parentFolderId: parent.id });
	return parent;
};

// The tree on disk, as the server sends it: /assets at the top, plus a
// _trash and a folder created at the top of the home, both without a parent.
function diskTree() {
	const assets = folder('assets', '/assets');
	const users = folder('users', '/assets/users', { parentFolderId: 'assets' });
	const home = folder('home', '/assets/users/ana', { parentFolderId: 'users' });
	child(
		home,
		child(folder('cam', '/assets/users/ana/Cámara'), folder('y', '/assets/users/ana/Cámara/2025'))
	);
	child(home, folder('arch', '/assets/users/ana/_archive'));
	const bob = folder('bob', '/assets/users/bob', { parentFolderId: 'users' });
	child(bob, folder('trip', '/assets/users/bob/Viaje'));
	child(users, home);
	child(users, bob);
	const shared = folder('shared', '/assets/shared', { parentFolderId: 'assets' });
	child(shared, folder('fam', '/assets/shared/Familia'));
	child(shared, folder('strash', '/assets/shared/_trash'));
	const nas = folder('nas', '/assets/shared/NAS', { externalLibraryId: 'lib' });
	child(nas, folder('raw', '/assets/shared/NAS/_trash', { externalLibraryId: 'lib' }));
	child(shared, nas);
	child(assets, users);
	child(assets, shared);
	return [
		assets,
		folder('trash', '/assets/users/ana/_trash'),
		folder('docs', '/assets/users/ana/Documentos')
	];
}

const ids = (list: FolderResponse[]) => list.map((f) => f.id);

describe('groupFolders', () => {
	const groups = groupFolders(diskTree(), 'ana');

	it('drops containers and system folders, whatever their parent', () => {
		expect(ids(groups.personal)).toEqual(['cam', 'docs']);
		expect(ids(groups.shared)).toEqual(['fam', 'trip']);
		expect(ids(groups.external)).toEqual(['nas']);
		expect(ids(browsableRoots(groups))).toEqual(['cam', 'docs', 'fam', 'trip', 'nas']);
	});

	it('keeps subtrees, and system names inside a library', () => {
		expect(ids(groups.personal[0].subFolders)).toEqual(['y']);
		expect(groups.personal[0].subFolders[0].parentFolderId).toBe('cam');
		expect(ids(groups.external[0].subFolders)).toEqual(['raw']);
	});

	it('gives personal roots no parent, the API name for the top of the home', () => {
		expect(groups.personal.map((f) => f.parentFolderId)).toEqual([null, null]);
		expect(groups.shared[0].parentFolderId).toBe('shared');
	});
});
