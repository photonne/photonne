import { describe, expect, it } from 'vitest';
import {
	anchorAfterReflow,
	blocksInRange,
	buildGridLayout,
	nearestSection,
	sectionAt,
	sectionSpan,
	subgroups,
	type GridSection
} from './grid-model.js';

const options = {
	containerWidth: 900,
	targetRowHeight: 200,
	spacing: 4,
	headerHeight: 48,
	sectionGap: 24
};

const item = (prefix: string, i: number, aspect = 1.5) => ({ id: `${prefix}${i}`, aspect });
const items = (prefix: string, n: number) => Array.from({ length: n }, (_, i) => item(prefix, i));

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

describe('masonry', () => {
	const masonry = { ...options, layout: 'masonry' as const, headerHeight: 0 };

	it('keeps every photo its shape in equal columns, each on the shortest one', () => {
		const layout = buildGridLayout(
			[
				{
					key: 'a',
					count: 4,
					items: [item('a', 0, 2), item('a', 1, 1), item('a', 2, 0.5), item('a', 3, 1)]
				}
			],
			{ ...masonry, containerWidth: 1000, targetRowHeight: 200, spacing: 0 }
		);
		// 1000 / (200 · 1.5) ≈ 3.3 → 3 columns; 4 photos: the fourth goes under the first.
		const W = 1000 / 3;
		const cells = layout.blocks.flatMap((b) => (b.kind === 'row' ? b.cells : []));
		expect(cells.map((c) => c.width)).toEqual([W, W, W, W]);
		expect(cells.map((c) => c.height)).toEqual([W / 2, W, W * 2, W]);
		expect(cells.map((c) => c.left)).toEqual([0, W, 2 * W, 0]);
		// The fourth photo opens the second block, which starts where it does.
		expect(layout.blocks.filter((b) => b.kind === 'row')[1].top).toBe(W / 2);
	});

	it('drops the next photo into the shortest column, under the others', () => {
		const layout = buildGridLayout(
			[
				{
					key: 'a',
					count: 3,
					items: [item('a', 0, 1), item('a', 1, 0.5), item('a', 2, 1)]
				}
			],
			{ ...masonry, containerWidth: 300, targetRowHeight: 100, spacing: 10 }
		);
		// 2 columns of 145px; the third photo goes under the first (the shorter column).
		const [first, second] = layout.blocks.filter((b) => b.kind === 'row');
		expect(first.kind === 'row' && first.cells.map((c) => c.left)).toEqual([0, 155]);
		expect(second.kind === 'row' && second.cells[0].left).toBe(0);
		expect(second.top).toBe(145 + 10);
		// The second block starts inside the first (the tall column), so the
		// section is as tall as its tallest column.
		expect(second.top).toBeLessThan(first.top + first.height);
		expect(layout.totalHeight).toBe(300);
		// The running bottom never drops: the first block already reaches 290.
		expect(layout.reach).toEqual([0, 290, 300]);
	});

	it('lays nothing out before the container has a width', () => {
		const layout = buildGridLayout([{ key: 'a', count: 30, items: items('a', 30) }], {
			...masonry,
			containerWidth: 0
		});
		expect(layout.blocks.filter((b) => b.kind === 'row')).toEqual([]);
	});

	it('finds the blocks under a window when they overlap', () => {
		const layout = buildGridLayout(
			[
				{
					key: 'a',
					count: 40,
					items: items('a', 40).map((it, i) => ({ ...it, aspect: i % 3 ? 1.5 : 0.4 }))
				}
			],
			{ ...masonry, containerWidth: 1000, targetRowHeight: 200, spacing: 4 }
		);
		for (const [from, to] of [
			[0, 100],
			[500, 900],
			[1200, 1300]
		]) {
			expect(blocksInRange(layout, from, to)).toEqual(
				layout.blocks.filter((b) => b.top + b.height >= from && b.top <= to)
			);
		}
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

describe('day subgroups', () => {
	const dated = (day: string, n: number) =>
		Array.from({ length: n }, (_, i) => ({ id: `${day}-${i}`, aspect: 1.5, day }));

	it('splits a loaded month into consecutive day runs under subheaders', () => {
		const monthItems = [...dated('2026-09-28', 3), ...dated('2026-09-20', 8)];
		const layout = buildGridLayout(
			[
				{ key: '2026-09', count: 11, items: monthItems },
				{ key: '2026-08', count: 9, items: null }
			],
			{ ...options, subheaderHeight: 30 },
			(item) => item.day
		);

		const kinds = layout.blocks.map((b) => b.kind);
		expect(kinds[0]).toBe('header');
		expect(kinds[1]).toBe('subheader');
		expect(kinds.filter((k) => k === 'subheader')).toHaveLength(2);
		expect(kinds.at(-1)).toBe('placeholder');
		expect(layout.groups.get('2026-09-20')).toHaveLength(8);
		// A day never shares a row with another.
		for (const row of layout.rows) expect(new Set(row.map((id) => id.slice(0, 10))).size).toBe(1);
		// Blocks still never overlap.
		for (let i = 1; i < layout.blocks.length; i++) {
			const before = layout.blocks[i - 1];
			expect(layout.blocks[i].top).toBeGreaterThanOrEqual(before.top + before.height - 1e-6);
		}
	});

	it('runs keep the input order', () => {
		expect(subgroups(['a1', 'a2', 'b1', 'a3'], (x) => x[0])).toEqual([
			{ key: 'a', items: ['a1', 'a2'] },
			{ key: 'b', items: ['b1'] },
			{ key: 'a', items: ['a3'] }
		]);
	});
});

describe('nearestSection', () => {
	const months = ['2026-09', '2026-06', '2024-07', '2023-03'];

	it('finds the month, else the closest older one, else the oldest', () => {
		expect(nearestSection(months, '2026-06')).toBe('2026-06');
		expect(nearestSection(months, '2026-07')).toBe('2026-06');
		expect(nearestSection(months, '2025-01')).toBe('2024-07');
		expect(nearestSection(months, '2020-01')).toBe('2023-03');
		expect(nearestSection([], '2020-01')).toBeNull();
	});

	it('maps years onto months and months onto years', () => {
		expect(nearestSection(months, '2024')).toBe('2024-07');
		expect(nearestSection(['2026', '2024', '2023'], '2024-07')).toBe('2024');
	});
});

describe('anchorAfterReflow', () => {
	const sections = [
		{ key: 'a', count: 30, items: items('a', 30) },
		{ key: 'b', count: 30, items: items('b', 30) }
	];
	const small = buildGridLayout(sections, { ...options, targetRowHeight: 100 });
	const large = buildGridLayout(sections, { ...options, targetRowHeight: 300 });

	it('keeps the same photo at the same height of its row', () => {
		const block = small.blocks[small.blockOfItem.get('b7')!];
		const cell = block.kind === 'row' ? block.cells.find((c) => c.item.id === 'b7')! : null;
		const y = block.top + block.height / 4;

		const target = anchorAfterReflow(small, large, y, cell!.left + 1)!;

		const moved = large.blocks[large.blockOfItem.get('b7')!];
		expect(target).toBeCloseTo(moved.top + moved.height / 4, 6);
	});

	it('takes the first photo of the next row when on a header', () => {
		const y = small.sectionTops.get('b')! + 1;

		const target = anchorAfterReflow(small, large, y);

		expect(target).toBeCloseTo(large.blocks[large.blockOfItem.get('b0')!].top, 6);
	});

	it('falls back to sections when the photos are different', () => {
		const years = buildGridLayout(
			[
				{ key: '2026', count: 3, items: items('y', 3) },
				{ key: '2024', count: 3, items: items('z', 3) }
			],
			options
		);
		const months = buildGridLayout(
			[
				{ key: '2026-09', count: 30, items: null },
				{ key: '2024-07', count: 30, items: null }
			],
			options
		);

		const target = anchorAfterReflow(years, months, years.sectionTops.get('2024')! + 5);

		expect(target).toBe(months.sectionTops.get('2024-07'));
	});

	it('keeps the fraction of a section that is still a placeholder', () => {
		const before = buildGridLayout([{ key: 'p', count: 60, items: null }], options);
		const after = buildGridLayout([{ key: 'p', count: 60, items: null }], {
			...options,
			targetRowHeight: 400
		});
		const span = sectionSpan(before, 'p')!;
		const y = span.top + (span.bottom - span.top) / 2;

		const target = anchorAfterReflow(before, after, y)!;

		const to = sectionSpan(after, 'p')!;
		expect(target).toBeCloseTo(to.top + (to.bottom - to.top) / 2, 6);
	});
});
