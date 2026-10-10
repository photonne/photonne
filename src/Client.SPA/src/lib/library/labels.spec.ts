import { describe, expect, it } from 'vitest';
import { displayLabel, isLabelSort, sortLabels } from './labels.js';

const label = (name: string, count: number) => ({
	label: name,
	assetCount: count,
	coverAssetId: null
});

describe('sortLabels', () => {
	const labels = [label('photo 10', 5), label('Árbol', 5), label('photo 2', 9), label('beach', 1)];

	it('puts the most photographed first, ties by name', () => {
		expect(sortLabels(labels, 'count', 'es').map((l) => l.label)).toEqual([
			'photo 2',
			'Árbol',
			'photo 10',
			'beach'
		]);
	});

	it('sorts names naturally, ignoring case and accents', () => {
		expect(sortLabels(labels, 'name', 'es').map((l) => l.label)).toEqual([
			'Árbol',
			'beach',
			'photo 2',
			'photo 10'
		]);
	});

	it('leaves the input alone', () => {
		sortLabels(labels, 'name', 'es');
		expect(labels[0].label).toBe('photo 10');
	});
});

describe('labels', () => {
	it('capitalises a detector label for a title', () => {
		expect(displayLabel('dog', 'es')).toBe('Dog');
		expect(displayLabel('', 'es')).toBe('');
	});

	it('recognises a stored sort', () => {
		expect(isLabelSort('name')).toBe(true);
		expect(isLabelSort('size')).toBe(false);
		expect(isLabelSort(null)).toBe(false);
	});
});
