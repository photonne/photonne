import { describe, expect, it } from 'vitest';
import { blocksInRange, buildGridLayout, sectionAt, type GridSection } from './grid-model.js';

const options = {
	containerWidth: 900,
	targetRowHeight: 200,
	spacing: 4,
	headerHeight: 48,
	sectionGap: 24
};

const items = (prefix: string, n: number) =>
	Array.from({ length: n }, (_, i) => ({ id: `${prefix}${i}`, aspect: 1.5 }));

describe('buildGridLayout', () => {
	it('stacks header, rows and placeholders without gaps or overlaps', () => {
		const sections: GridSection<{ id: string; aspect: number }>[] = [
			{ key: '2026-09', count: 7, items: items('a', 7) },
			{ key: '2026-08', count: 40, items: null },
			{ key: '2026-07', count: 3, items: items('c', 3) }
		];

		const layout = buildGridLayout(sections, options);

		let expectedTop = 0;
		let previousKey = layout.blocks[0].key;
		layout.blocks.forEach((block, i) => {
			if (block.key !== previousKey) expectedTop += options.sectionGap;
			else if (block.kind === 'row' && layout.blocks[i - 1].kind === 'row')
				expectedTop += options.spacing;
			expect(block.top).toBeCloseTo(expectedTop, 6);
			expectedTop += block.height;
			previousKey = block.key;
		});
		expect(layout.totalHeight).toBeCloseTo(expectedTop, 6);
		expect(layout.blocks.filter((b) => b.kind === 'placeholder')).toHaveLength(1);
	});

	it('makes justified rows end flush with the container', () => {
		const layout = buildGridLayout([{ key: 'm', count: 12, items: items('a', 12) }], options);
		const rows = layout.blocks.filter((b) => b.kind === 'row');

		for (const row of rows.slice(0, -1)) {
			const last = row.cells.at(-1)!;
			expect(last.left + last.width).toBeCloseTo(options.containerWidth, 9);
		}
	});

	it('indexes loaded items for keyboard navigation', () => {
		const layout = buildGridLayout(
			[
				{ key: 'a', count: 2, items: items('a', 2) },
				{ key: 'b', count: 5, items: null }
			],
			options
		);

		expect(layout.rows.flat()).toEqual(['a0', 'a1']);
		expect(layout.blocks[layout.blockOfItem.get('a1')!].kind).toBe('row');
	});

	it('skips empty sections', () => {
		const layout = buildGridLayout([{ key: 'x', count: 0, items: null }], options);

		expect(layout.blocks).toEqual([]);
		expect(layout.totalHeight).toBe(0);
	});
});

describe('blocksInRange and sectionAt', () => {
	const layout = buildGridLayout(
		[
			{ key: 'a', count: 30, items: items('a', 30) },
			{ key: 'b', count: 30, items: null },
			{ key: 'c', count: 30, items: items('c', 30) }
		],
		options
	);

	it('returns exactly the blocks overlapping the window', () => {
		const from = 500;
		const to = 900;

		const inRange = blocksInRange(layout, from, to);
		const expected = layout.blocks.filter((b) => b.top + b.height >= from && b.top <= to);

		expect(inRange).toEqual(expected);
	});

	it('finds the section under an offset', () => {
		expect(sectionAt(layout, 0)).toBe('a');
		expect(sectionAt(layout, layout.sectionTops.get('b')! + 1)).toBe('b');
		expect(sectionAt(layout, layout.totalHeight)).toBe('c');
	});
});
