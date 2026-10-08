import { describe, expect, it } from 'vitest';
import {
	branchIds,
	countFolders,
	filterTree,
	ownAssets,
	totalAssets,
	type TreeFolder
} from './folder-tree.js';

const folder = (id: string, path: string, assetCount: number, subFolders: TreeFolder[] = []) => ({
	id,
	name: path.split('/').at(-1)!,
	path,
	assetCount,
	subFolders
});

// Counts include subfolders, as the server sends them.
const tree = [
	folder('ana', '/ana', 57, [
		folder('viajes', '/ana/Viajes', 15, [folder('roma', '/ana/Viajes/Roma', 5)]),
		folder('cámara', '/ana/Cámara', 40)
	]),
	folder('shared', '/shared/Familia', 7)
];

describe('folder tree', () => {
	it('counts folders and photos', () => {
		expect(countFolders(tree)).toBe(5);
		expect(ownAssets(tree[0])).toBe(2);
		expect(ownAssets(tree[0].subFolders[0])).toBe(10);
		expect(totalAssets(tree)).toBe(64);
	});

	it('lists the folders that can expand', () => {
		expect(branchIds(tree)).toEqual(['ana', 'viajes']);
	});

	it('filters keeping the way to each match', () => {
		const found = filterTree(tree, 'roma');
		expect(found.map((f) => f.id)).toEqual(['ana']);
		expect(found[0].subFolders.map((f) => f.id)).toEqual(['viajes']);
		expect(found[0].subFolders[0].subFolders.map((f) => f.id)).toEqual(['roma']);

		// Accents don't matter, and a matching folder keeps its subtree.
		expect(filterTree(tree, 'camara')[0].subFolders.map((f) => f.id)).toEqual(['cámara']);
		expect(filterTree(tree, 'viajes')[0].subFolders[0].subFolders).toHaveLength(1);
		expect(filterTree(tree, ' ')).toHaveLength(2);
	});
});
