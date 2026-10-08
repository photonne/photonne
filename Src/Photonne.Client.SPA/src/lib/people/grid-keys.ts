import { moveFocus, type Direction } from '#lib/timeline/keyboard-nav.js';

const KEYS: Record<string, Direction> = {
	ArrowLeft: 'left',
	ArrowRight: 'right',
	ArrowUp: 'up',
	ArrowDown: 'down'
};

/** Splits a flat order into rows of `columns` (the last one may be shorter). */
export function chunkRows<T>(items: readonly T[], columns: number): T[][] {
	const size = Math.max(1, Math.floor(columns));
	const rows: T[][] = [];
	for (let i = 0; i < items.length; i += size) rows.push(items.slice(i, i + size));
	return rows;
}

/** Columns of a wrapped grid: how many cells share the first cell's row. */
export function columnsOf(tops: readonly number[]) {
	if (tops.length === 0) return 1;
	let count = 0;
	while (count < tops.length && Math.abs(tops[count] - tops[0]) < 2) count++;
	return count;
}

/** Index the key leads to (Home/End, or an arrow over a `columns`-wide grid); null if none. */
export function targetIndex(key: string, index: number, total: number, columns: number) {
	if (total === 0) return null;
	if (key === 'Home') return 0;
	if (key === 'End') return total - 1;
	const direction = KEYS[key];
	if (!direction) return null;
	const ids = Array.from({ length: total }, (_, i) => String(i));
	const next = moveFocus(chunkRows(ids, columns), String(index), direction);
	return next === null ? null : Number(next);
}

/**
 * Arrow keys, Home and End move focus between the `[data-cell]` elements of
 * a card grid (people, faces), the way the photo grid moves between photos.
 * Use as `{@attach gridKeys}` on the grid's container.
 */
export function gridKeys(node: HTMLElement) {
	function onkeydown(event: KeyboardEvent) {
		if (event.altKey || event.ctrlKey || event.metaKey || event.defaultPrevented) return;
		const cells = [...node.querySelectorAll<HTMLElement>('[data-cell]')];
		const current = (event.target as HTMLElement).closest<HTMLElement>('[data-cell]');
		const index = current ? cells.indexOf(current) : -1;
		if (index < 0) return;
		const columns = columnsOf(cells.map((cell) => cell.getBoundingClientRect().top));
		const next = targetIndex(event.key, index, cells.length, columns);
		if (next === null || next === index) return;
		event.preventDefault();
		cells[next].focus();
		cells[next].scrollIntoView({ block: 'nearest' });
	}
	node.addEventListener('keydown', onkeydown);
	return () => node.removeEventListener('keydown', onkeydown);
}
