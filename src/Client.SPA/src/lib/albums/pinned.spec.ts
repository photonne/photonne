import { describe, expect, it } from 'vitest';
import type { AlbumResponse, FolderResponse } from '#lib/api/index.js';
import { mergePinned } from './pinned.js';

const album = (overrides: Partial<AlbumResponse>): AlbumResponse => ({
	id: 'a',
	name: 'A',
	description: null,
	createdAt: '2026-01-01T00:00:00Z',
	updatedAt: '2026-01-01T00:00:00Z',
	assetCount: 0,
	coverThumbnailUrl: null,
	previewThumbnailUrls: [],
	isOwner: true,
	isShared: false,
	sharedWithCount: 0,
	canRead: true,
	canWrite: true,
	canDelete: true,
	canManagePermissions: true,
	hasActiveShareLink: false,
	isPinned: false,
	pinnedAt: null,
	kind: 'Manual',
	...overrides
});

const folder = (overrides: Partial<FolderResponse>): FolderResponse => ({
	id: 'f',
	name: 'F',
	path: '/assets/users/ana/F',
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

describe('mergePinned', () => {
	it('mixes pinned albums, smart ones included, and folders, the last pinned first', () => {
		const entries = mergePinned(
			[
				album({ id: 'viaje', isPinned: true, pinnedAt: '2026-09-01T00:00:00Z' }),
				album({ id: 'perros', kind: 'Smart', isPinned: true, pinnedAt: '2026-10-01T00:00:00Z' }),
				album({ id: 'suelto' })
			],
			[
				folder({ id: 'camara', isPinned: true, pinnedAt: '2026-09-15T00:00:00Z' }),
				folder({ id: 'docs' })
			]
		);
		expect(entries.map((entry) => `${entry.kind}:${entry.item.id}`)).toEqual([
			'album:perros',
			'folder:camara',
			'album:viaje'
		]);
	});
});
