import { describe, expect, it } from 'vitest';
import { Selection } from './selection.svelte.js';

const order = ['a', 'b', 'c', 'd', 'e'];

describe('Selection', () => {
	it('toggles and sets the anchor', () => {
		const selection = new Selection();

		selection.toggle('b');
		selection.toggle('d');
		selection.toggle('b');

		expect([...selection.ids]).toEqual(['d']);
		expect(selection.anchor).toBe('b');
	});

	it('extends from a fixed anchor in either direction', () => {
		const selection = new Selection();
		selection.toggle('c');

		selection.selectRange('e', order);
		expect([...selection.ids].sort()).toEqual(['c', 'd', 'e']);

		selection.selectRange('a', order);
		expect([...selection.ids].sort()).toEqual(['a', 'b', 'c', 'd', 'e']);
		expect(selection.anchor).toBe('c');
	});

	it('degrades a range without anchor to a toggle', () => {
		const selection = new Selection();

		selection.selectRange('d', order);

		expect([...selection.ids]).toEqual(['d']);
		expect(selection.anchor).toBe('d');
	});

	it('reports the tri-state of a group', () => {
		const selection = new Selection();
		selection.set(['a', 'b'], true);

		expect(selection.stateOf(['a', 'b'])).toBe('all');
		expect(selection.stateOf(['a', 'c'])).toBe('some');
		expect(selection.stateOf(['c'])).toBe('none');
		expect(selection.stateOf([])).toBe('none');
	});

	it('clears ids and anchor', () => {
		const selection = new Selection();
		selection.toggle('a');

		selection.clear();

		expect(selection.active).toBe(false);
		expect(selection.anchor).toBeNull();
	});
});
