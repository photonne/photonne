import { describe, expect, it } from 'vitest';
import type { FolderResponse } from '#lib/api/index.js';
import {
	arrangeFolders,
	defaultFolderListOptions,
	folderScope,
	folderScopeCounts,
	folderSelectionActions,
	moveDestinations,
	parseFolderListOptions,
	topmostFolders
} from './folder-list.js';

const folder = (
	overrides: Partial<FolderResponse> & Pick<FolderResponse, 'id' | 'name' | 'path'>
): FolderResponse => ({
	parentFolderId: null,
	createdAt: '2026-01-01T00:00:00Z',
	assetCount: 0,
	firstAssetId: null,
	previewAssetIds: [],
	isShared: false,
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

const year = folder({
	id: 'y',
	name: '2025',
	path: '/assets/users/ana/Cámara/2025',
	parentFolderId: 'cam',
	assetCount: 5
});
const camera = folder({
	id: 'cam',
	name: 'Cámara',
	path: '/assets/users/ana/Cámara',
	assetCount: 20,
	subFolders: [year]
});
const docs = folder({ id: 'docs', name: 'Documentos', path: '/assets/users/ana/Documentos' });
const family = folder({
	id: 'fam',
	name: 'Familia',
	path: '/assets/shared/Familia',
	isShared: true,
	assetCount: 7,
	canDelete: false
});
const nas = folder({
	id: 'nas',
	name: 'NAS',
	path: '/assets/shared/NAS',
	isShared: true,
	externalLibraryId: 'lib-1',
	assetCount: 100
});
const roots = [camera, docs, family, nas];

describe('folderScope', () => {
	it('puts external libraries first, even inside the shared space', () => {
		expect(roots.map(folderScope)).toEqual(['personal', 'personal', 'shared', 'external']);
		expect(folderScopeCounts(roots)).toEqual({ all: 4, personal: 2, shared: 1, external: 1 });
	});
});

describe('arrangeFolders', () => {
	const names = (list: FolderResponse[]) => list.map((f) => f.name);

	it('shows the top level by name, filtered by scope', () => {
		expect(names(arrangeFolders(roots, defaultFolderListOptions))).toEqual([
			'Cámara',
			'Documentos',
			'Familia',
			'NAS'
		]);
		expect(names(arrangeFolders(roots, { ...defaultFolderListOptions, scope: 'shared' }))).toEqual([
			'Familia'
		]);
	});

	it('searches every depth, ignoring accents', () => {
		const options = { ...defaultFolderListOptions, query: 'camara' };
		expect(names(arrangeFolders(roots, options))).toEqual(['Cámara']);
		expect(names(arrangeFolders(roots, { ...options, query: '2025' }))).toEqual(['2025']);
	});

	it('sorts by item count in either direction', () => {
		const options = { ...defaultFolderListOptions, sort: 'count' as const, descending: true };
		expect(names(arrangeFolders(roots, options))).toEqual([
			'NAS',
			'Cámara',
			'Familia',
			'Documentos'
		]);
	});
});

describe('parseFolderListOptions', () => {
	it('keeps sort, direction and view, never the scope', () => {
		expect(
			parseFolderListOptions('{"sort":"count","descending":true,"view":"list","scope":"shared"}')
		).toEqual({ ...defaultFolderListOptions, sort: 'count', descending: true, view: 'list' });
		expect(parseFolderListOptions('nope')).toEqual(defaultFolderListOptions);
	});
});

describe('folderSelectionActions', () => {
	it('offers an action only when every folder allows it', () => {
		expect(folderSelectionActions([camera, docs])).toEqual({ canMove: true, canDelete: true });
		expect(folderSelectionActions([camera, family])).toEqual({ canMove: true, canDelete: false });
		expect(folderSelectionActions([nas])).toEqual({ canMove: false, canDelete: false });
		expect(folderSelectionActions([])).toEqual({ canMove: false, canDelete: false });
	});
});

describe('topmostFolders', () => {
	it('drops folders inside another selected one', () => {
		expect(topmostFolders([year, camera, docs]).map((f) => f.id)).toEqual(['cam', 'docs']);
		// A sibling whose name starts the same isn't nested.
		const cameraOld = folder({
			id: 'old',
			name: 'Cámara vieja',
			path: '/assets/users/ana/Cámara vieja'
		});
		expect(topmostFolders([camera, cameraOld])).toHaveLength(2);
	});
});

describe('moveDestinations', () => {
	it('lists writable folders outside the moved subtrees and libraries', () => {
		const ids = moveDestinations(roots, [camera]).map((d) => d.folder.id);
		expect(ids).toEqual(['docs', 'fam']);
		expect(moveDestinations(roots, [docs]).map((d) => [d.folder.id, d.depth])).toEqual([
			['cam', 0],
			['y', 1],
			['fam', 0]
		]);
	});
});
