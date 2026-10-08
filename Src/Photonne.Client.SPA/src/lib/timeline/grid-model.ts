import {
	clampAspect,
	computeRows,
	estimateHeight,
	type LayoutOptions
} from './justified-layout.js';

export interface GridItem {
	id: string;
	/** Display width / height; null when unknown (laid out as FALLBACK_ASPECT). */
	aspect: number | null | undefined;
}

/** A dated group of the grid (a month in the timeline). */
export interface GridSection<T extends GridItem> {
	key: string;
	count: number;
	/** Null until loaded: the section is then a placeholder of estimated height. */
	items: readonly T[] | null;
}

export interface GridCell<T> {
	item: T;
	left: number;
	width: number;
}

export type GridBlock<T> =
	| { kind: 'header'; key: string; top: number; height: number }
	/** A day inside a month (day grouping); `group` is its key. */
	| { kind: 'subheader'; key: string; group: string; top: number; height: number }
	| { kind: 'row'; key: string; top: number; height: number; cells: GridCell<T>[] }
	| { kind: 'placeholder'; key: string; top: number; height: number };

export interface GridOptions extends LayoutOptions {
	headerHeight: number;
	/** Space after a section's last row, before the next header. */
	sectionGap: number;
	/** Height of a day header, with `subgroupOf`. */
	subheaderHeight?: number;
}

export interface GridLayout<T> {
	blocks: GridBlock<T>[];
	totalHeight: number;
	/** Where each section's header starts. */
	sectionTops: Map<string, number>;
	/** Loaded rows as ids, in visual order: what keyboard navigation walks. */
	rows: string[][];
	/** Index into `blocks` of the row holding each loaded item. */
	blockOfItem: Map<string, number>;
	/** Item ids of each subgroup (day), for its select-all check. */
	groups: Map<string, string[]>;
}

/**
 * Lays sections out top to bottom. With `subgroupOf` (a day key per item),
 * a loaded section is split into runs of consecutive items with the same
 * key, each under its own subheader and justified on its own; a placeholder
 * stays one block, since its days aren't known until it loads.
 */
export function buildGridLayout<T extends GridItem>(
	sections: readonly GridSection<T>[],
	options: GridOptions,
	subgroupOf?: (item: T) => string
): GridLayout<T> {
	const blocks: GridBlock<T>[] = [];
	const sectionTops = new Map<string, number>();
	const rows: string[][] = [];
	const blockOfItem = new Map<string, number>();
	const groups = new Map<string, string[]>();
	let top = 0;

	const layRows = (key: string, items: readonly T[]) => {
		const layoutRows = computeRows(
			items.map((item) => clampAspect(item.aspect)),
			options
		);
		layoutRows.forEach((row, rowIndex) => {
			if (rowIndex > 0) top += options.spacing;
			const justified = rowIndex < layoutRows.length - 1 || row.height !== options.targetRowHeight;
			const cells: GridCell<T>[] = [];
			let left = 0;
			for (let i = 0; i < row.count; i++) {
				const item = items[row.start + i];
				const isLast = i === row.count - 1;
				// The last cell of a justified row absorbs rounding, so rows end
				// flush with the container instead of a sub-pixel short.
				const width =
					isLast && justified
						? options.containerWidth - left
						: clampAspect(item.aspect) * row.height;
				cells.push({ item, left, width });
				left += width + options.spacing;
			}
			for (const cell of cells) blockOfItem.set(cell.item.id, blocks.length);
			rows.push(cells.map((cell) => cell.item.id));
			blocks.push({ kind: 'row', key, top, height: row.height, cells });
			top += row.height;
		});
	};

	for (const section of sections) {
		if (section.count === 0 && (section.items?.length ?? 0) === 0) continue;

		sectionTops.set(section.key, top);
		blocks.push({ kind: 'header', key: section.key, top, height: options.headerHeight });
		top += options.headerHeight;

		if (section.items === null) {
			const height = estimateHeight(section.count, options);
			blocks.push({ kind: 'placeholder', key: section.key, top, height });
			top += height;
		} else if (subgroupOf) {
			subgroups(section.items, subgroupOf).forEach((group, index) => {
				if (index > 0) top += options.spacing * 2;
				const height = options.subheaderHeight ?? options.headerHeight;
				blocks.push({ kind: 'subheader', key: section.key, group: group.key, top, height });
				groups.set(
					group.key,
					group.items.map((item) => item.id)
				);
				top += height;
				layRows(section.key, group.items);
			});
		} else {
			layRows(section.key, section.items);
		}
		top += options.sectionGap;
	}

	return {
		blocks,
		totalHeight: Math.max(0, top - (blocks.length ? options.sectionGap : 0)),
		sectionTops,
		rows,
		blockOfItem,
		groups
	};
}

/** Blocks overlapping [from, to], in order (binary search on the sorted tops). */
export function blocksInRange<T>(layout: GridLayout<T>, from: number, to: number): GridBlock<T>[] {
	const { blocks } = layout;
	let lo = 0;
	let hi = blocks.length;
	while (lo < hi) {
		const mid = (lo + hi) >> 1;
		if (blocks[mid].top + blocks[mid].height < from) lo = mid + 1;
		else hi = mid;
	}
	const result: GridBlock<T>[] = [];
	for (let i = lo; i < blocks.length && blocks[i].top <= to; i++) result.push(blocks[i]);
	return result;
}

/** The section shown at vertical offset `y` (the last one starting at or above it). */
export function sectionAt<T>(layout: GridLayout<T>, y: number): string | null {
	let found: string | null = null;
	for (const [key, top] of layout.sectionTops) {
		if (top > y) break;
		found = key;
	}
	return found;
}

/** Runs of consecutive items sharing a key (items come in display order). */
export function subgroups<T>(items: readonly T[], keyOf: (item: T) => string) {
	const runs: { key: string; items: T[] }[] = [];
	for (const item of items) {
		const key = keyOf(item);
		const last = runs.at(-1);
		if (last?.key === key) last.items.push(item);
		else runs.push({ key, items: [item] });
	}
	return runs;
}

/** Where a section starts and ends (the next section's top, or the end). */
export function sectionSpan<T>(layout: GridLayout<T>, key: string) {
	const top = layout.sectionTops.get(key);
	if (top === undefined) return null;
	let bottom = layout.totalHeight;
	let found = false;
	for (const [other, otherTop] of layout.sectionTops) {
		if (found) {
			bottom = otherTop;
			break;
		}
		found = other === key;
	}
	return { top, bottom };
}

/**
 * The section to show for `target` (a `yyyy-MM` or `yyyy` key) among `keys`,
 * newest first: the target itself, else the closest older one, else the
 * oldest. Keys of different lengths compare on their common prefix, so a
 * year finds its newest month and a month finds its year.
 */
export function nearestSection(keys: readonly string[], target: string): string | null {
	for (const key of keys) {
		const length = Math.min(key.length, target.length);
		if (key.slice(0, length) <= target.slice(0, length)) return key;
	}
	return keys.at(-1) ?? null;
}

/**
 * After a reflow (zoom, regrouping, resize), the new vertical offset of what
 * was at `y` in the old layout: the same photo at the same relative height
 * in its row when it's still laid out, else the same fraction of the same
 * section, else the start of the nearest one. `x` picks the photo within the
 * row (under the pointer); without it, the row's first photo. Null when
 * nothing matches.
 */
export function anchorAfterReflow<T extends GridItem>(
	before: GridLayout<T>,
	after: GridLayout<T>,
	y: number,
	x?: number
): number | null {
	const row = rowNear(before, y);
	if (row) {
		const cell =
			(x === undefined ? null : row.cells.find((c) => x >= c.left && x < c.left + c.width)) ??
			row.cells[0];
		const index = after.blockOfItem.get(cell.item.id);
		if (index !== undefined) {
			const target = after.blocks[index];
			const within = Math.min(1, Math.max(0, (y - row.top) / row.height));
			return target.top + within * target.height;
		}
	}

	const key = sectionAt(before, y);
	if (key === null) return null;
	const from = sectionSpan(before, key)!;
	const match = nearestSection([...after.sectionTops.keys()], key);
	const to = match === null ? null : sectionSpan(after, match);
	if (!to) return null;
	// A section that only maps by prefix (a year onto its newest month) is
	// entered at its start: a fraction of a year says nothing about a month.
	if (match !== key) return to.top;
	const fraction = from.bottom > from.top ? (y - from.top) / (from.bottom - from.top) : 0;
	return to.top + Math.min(1, Math.max(0, fraction)) * (to.bottom - to.top);
}

/** The loaded row under `y`, or the first one below it in the same section. */
function rowNear<T>(layout: GridLayout<T>, y: number) {
	// A header is at most a few hundred pixels above its first row.
	const blocks = blocksInRange(layout, y, y + 2000);
	const first = blocks[0];
	for (const block of blocks) {
		if (block.key !== first.key || block.kind === 'placeholder') return null;
		if (block.kind === 'row') return block;
	}
	return null;
}
