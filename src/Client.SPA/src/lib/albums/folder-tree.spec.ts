import { describe, expect, it } from 'vitest';
import {
	canDropFolder,
	findFolder,
	pathTo,
	sortTree,
	subtreeIds,
	treeKey,
	visibleRows,
	yearSummary,
	type TreeFolder
} from './folder-tree.js';

const node = (
	id: string,
	children: TreeFolder[] = [],
	parent: string | null = null
): TreeFolder => ({
	id,
	name: id,
	parentFolderId: parent,
	subFolders: children.map((child) => ({ ...child, parentFolderId: id }))
});

// Fotos ─┬─ 2024 ─── Verano
//        └─ 2023
// Docs
const tree = [node('Fotos', [node('2024', [node('Verano')]), node('2023')]), node('Docs')];

describe('sortTree', () => {
	it('sorts every level by name, numbers naturally', () => {
		const sorted = sortTree([node('b', [node('10'), node('9')]), node('A')]);
		expect(sorted.map((n) => n.name)).toEqual(['A', 'b']);
		expect(sorted[1].subFolders.map((n) => n.name)).toEqual(['9', '10']);
	});
});

describe('pathTo / findFolder / subtreeIds', () => {
	it('finds a folder with its ancestors', () => {
		expect(pathTo(tree, 'Verano').map((n) => n.id)).toEqual(['Fotos', '2024', 'Verano']);
		expect(pathTo(tree, 'nope')).toEqual([]);
		expect(findFolder(tree, '2023')?.id).toBe('2023');
	});

	it('collects a subtree', () => {
		expect([...subtreeIds(tree[0])]).toEqual(['Fotos', '2024', 'Verano', '2023']);
	});
});

describe('visibleRows', () => {
	it('shows children of expanded folders only', () => {
		const rows = visibleRows(tree, new Set(['Fotos']));
		expect(rows.map((r) => [r.folder.id, r.depth, r.expanded])).toEqual([
			['Fotos', 0, true],
			['2024', 1, false],
			['2023', 1, false],
			['Docs', 0, false]
		]);
		expect(rows[1].hasChildren).toBe(true);
		expect(rows[1].parentId).toBe('Fotos');
	});

	it('leaves out excluded folders and their subtree', () => {
		const rows = visibleRows(tree, new Set(['Fotos', '2024']), new Set(['2024']));
		expect(rows.map((r) => r.folder.id)).toEqual(['Fotos', '2023', 'Docs']);
	});
});

describe('treeKey', () => {
	const rows = visibleRows(tree, new Set(['Fotos']));

	it('walks rows up and down, and jumps to the ends', () => {
		expect(treeKey(rows, 'Fotos', 'ArrowDown')).toEqual({ kind: 'focus', id: '2024' });
		expect(treeKey(rows, '2024', 'ArrowUp')).toEqual({ kind: 'focus', id: 'Fotos' });
		expect(treeKey(rows, 'Docs', 'ArrowDown')).toBeNull();
		expect(treeKey(rows, '2023', 'Home')).toEqual({ kind: 'focus', id: 'Fotos' });
		expect(treeKey(rows, 'Fotos', 'End')).toEqual({ kind: 'focus', id: 'Docs' });
	});

	it('opens and enters with Right, closes and climbs with Left', () => {
		expect(treeKey(rows, '2024', 'ArrowRight')).toEqual({ kind: 'expand', id: '2024' });
		expect(treeKey(rows, 'Fotos', 'ArrowRight')).toEqual({ kind: 'focus', id: '2024' });
		expect(treeKey(rows, '2023', 'ArrowRight')).toBeNull();
		expect(treeKey(rows, 'Fotos', 'ArrowLeft')).toEqual({ kind: 'collapse', id: 'Fotos' });
		expect(treeKey(rows, '2023', 'ArrowLeft')).toEqual({ kind: 'focus', id: 'Fotos' });
		expect(treeKey(rows, 'Docs', 'ArrowLeft')).toBeNull();
	});

	it('ignores other keys', () => {
		expect(treeKey(rows, 'Fotos', 'a')).toBeNull();
	});
});

describe('canDropFolder', () => {
	it('refuses itself, its subtree and its current parent', () => {
		expect(canDropFolder(tree, '2024', 'Docs')).toBe(true);
		expect(canDropFolder(tree, 'Fotos', 'Verano')).toBe(false);
		expect(canDropFolder(tree, '2024', '2024')).toBe(false);
		expect(canDropFolder(tree, '2024', 'Fotos')).toBe(false);
	});
});

describe('yearSummary', () => {
	it('lists the years a move spread the photos into', () => {
		expect(
			yearSummary([
				{ year: 2024, count: 5 },
				{ year: 2023, count: 7 }
			])
		).toBe('2024: 5 · 2023: 7');
	});
});
