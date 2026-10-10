import { describe, expect, it } from 'vitest';
import { groupState, keptIds, reviewGroups, toggleGroup, toggleId, yearSummary } from './review.js';

describe('move review', () => {
	const groups = reviewGroups([
		{ year: 2023, assetIds: ['a', 'b'] },
		{ year: 2025, assetIds: ['c'] },
		{ year: 2024, assetIds: [] }
	]);

	it('orders years newest first and drops empty ones', () => {
		expect(groups.map((group) => group.label)).toEqual(['2025', '2023']);
	});

	it('keeps what was not taken out', () => {
		expect(keptIds(groups, new Set(['b']))).toEqual(['c', 'a']);
	});

	it('toggles a whole year', () => {
		const year = groups[1];
		expect(groupState(year, new Set())).toBe('all');
		expect(groupState(year, new Set(['a']))).toBe('some');

		const out = toggleGroup(year, new Set(['c']));
		expect([...out].sort()).toEqual(['a', 'b', 'c']);
		expect(groupState(year, out)).toBe('none');

		// From "some", ticking brings the whole year back.
		const back = toggleGroup(year, new Set(['a', 'c']));
		expect([...back]).toEqual(['c']);
	});

	it('takes one photo out and puts it back', () => {
		const out = toggleId(new Set(), 'a');
		expect([...out]).toEqual(['a']);
		expect([...toggleId(out, 'a')]).toEqual([]);
	});

	it('summarises the years of a move', () => {
		expect(
			yearSummary([
				{ year: 2024, count: 7 },
				{ year: 2023, count: 5 }
			])
		).toBe('2023: 5 · 2024: 7');
	});
});
