import { describe, expect, it } from 'vitest';
import { indexById, withChangedItems, withoutItems } from './collection-ops.js';

const months: [string, { id: string; fav: boolean }[]][] = [
	[
		'2026-09',
		[
			{ id: 'a', fav: false },
			{ id: 'b', fav: false }
		]
	],
	['2026-08', [{ id: 'c', fav: false }]]
];

describe('collection ops', () => {
	it('indexes loaded items only', () => {
		const index = indexById([
			{ key: 'x', count: 1, items: [{ id: 'a', aspect: 1 }] },
			{ key: 'y', count: 4, items: null }
		]);
		expect([...index.keys()]).toEqual(['a']);
	});

	it('removes items and reports per month', () => {
		expect(withoutItems(months, ['b', 'c'])).toEqual([
			{ key: '2026-09', items: [{ id: 'a', fav: false }], removed: 1 },
			{ key: '2026-08', items: [], removed: 1 }
		]);
	});

	it('changes only the months that hold the items', () => {
		const changed = withChangedItems(months, ['c'], (item) => ({ ...item, fav: true }));
		expect(changed).toEqual([{ key: '2026-08', items: [{ id: 'c', fav: true }] }]);
	});
});
