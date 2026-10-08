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
	| { kind: 'row'; key: string; top: number; height: number; cells: GridCell<T>[] }
	| { kind: 'placeholder'; key: string; top: number; height: number };

export interface GridOptions extends LayoutOptions {
	headerHeight: number;
	/** Space after a section's last row, before the next header. */
	sectionGap: number;
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
}

export function buildGridLayout<T extends GridItem>(
	sections: readonly GridSection<T>[],
	options: GridOptions
): GridLayout<T> {
	const blocks: GridBlock<T>[] = [];
	const sectionTops = new Map<string, number>();
	const rows: string[][] = [];
	const blockOfItem = new Map<string, number>();
	let top = 0;

	for (const section of sections) {
		if (section.count === 0 && (section.items?.length ?? 0) === 0) continue;

		sectionTops.set(section.key, top);
		blocks.push({ kind: 'header', key: section.key, top, height: options.headerHeight });
		top += options.headerHeight;

		if (section.items === null) {
			const height = estimateHeight(section.count, options);
			blocks.push({ kind: 'placeholder', key: section.key, top, height });
			top += height;
		} else {
			const items = section.items;
			const layoutRows = computeRows(
				items.map((item) => clampAspect(item.aspect)),
				options
			);
			layoutRows.forEach((row, rowIndex) => {
				if (rowIndex > 0) top += options.spacing;
				const justified =
					rowIndex < layoutRows.length - 1 || row.height !== options.targetRowHeight;
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
				blocks.push({ kind: 'row', key: section.key, top, height: row.height, cells });
				top += row.height;
			});
		}
		top += options.sectionGap;
	}

	return {
		blocks,
		totalHeight: Math.max(0, top - (blocks.length ? options.sectionGap : 0)),
		sectionTops,
		rows,
		blockOfItem
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
