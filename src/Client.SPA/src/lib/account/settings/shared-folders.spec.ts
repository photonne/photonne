import { describe, expect, it } from 'vitest';
import type { FolderResponse } from '#lib/api/index.js';
import { sharedFolderRows } from './shared-folders.js';

function folder(id: string, path: string, extra: Partial<FolderResponse> = {}): FolderResponse {
	return {
		id,
		path,
		name: path.split('/').at(-1)!,
		parentFolderId: null,
		createdAt: '2026-01-01T00:00:00Z',
		assetCount: 3,
		firstAssetId: null,
		previewAssetIds: [],
		isShared: true,
		isOwner: false,
		canWrite: false,
		canDelete: false,
		sharedWithCount: 0,
		externalLibraryId: null,
		excludedFromDiscovery: false,
		isPinned: false,
		pinnedAt: null,
		subFolders: [],
		...extra
	};
}

describe('sharedFolderRows', () => {
	it('keeps only shared-space folders, sorted with their depth', () => {
		const rows = sharedFolderRows([
			folder('p', '/assets/users/ana/Camera'),
			folder('b', '/assets/shared/Familia/2024', { excludedFromDiscovery: true }),
			folder('a', '/assets/shared/Familia'),
			folder('x', '/assets/sharedish/Other')
		]);
		expect(rows.map((r) => [r.id, r.depth, r.excluded])).toEqual([
			['a', 0, false],
			['b', 1, true]
		]);
	});

	it('walks a tree and does not repeat folders', () => {
		const child = folder('c', '/assets/shared/Viajes/Roma');
		const rows = sharedFolderRows([
			folder('v', '/assets/shared/Viajes', { subFolders: [child] }),
			child
		]);
		expect(rows.map((r) => r.id)).toEqual(['v', 'c']);
	});
});
