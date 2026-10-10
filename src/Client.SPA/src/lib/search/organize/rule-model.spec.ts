import { describe, expect, it } from 'vitest';
import { buildRule, conditionNode, newCondition, type Condition } from './rule-model.js';

const make = (type: Condition['type'], change: Partial<Condition> = {}) => ({
	...newCondition(type, type),
	...change
});

describe('organize rule', () => {
	it('leaves incomplete conditions out', () => {
		expect(conditionNode(make('person'))).toBeNull();
		expect(conditionNode(make('dateRange'))).toBeNull();
		expect(conditionNode(make('text', { text: '  ' }))).toBeNull();
		expect(buildRule('AND', [make('object'), make('scene')])).toBeNull();
	});

	it('writes each condition as the server reads it', () => {
		expect(conditionNode(make('dateRange', { from: '2024-01-01' }))).toEqual({
			type: 'dateRange',
			from: '2024-01-01T00:00:00Z',
			to: null
		});
		expect(conditionNode(make('folder', { ids: ['f1'], includeSubfolders: false }))).toEqual({
			type: 'folder',
			folderIds: ['f1'],
			includeSubfolders: false
		});
		expect(conditionNode(make('person', { ids: ['p1', 'p2'], match: 'all' }))).toEqual({
			type: 'person',
			personIds: ['p1', 'p2'],
			match: 'all'
		});
		expect(conditionNode(make('scene', { labels: ['beach'] }))).toEqual({
			type: 'scene',
			labels: ['beach'],
			match: 'any'
		});
		expect(conditionNode(make('mediaType'))).toEqual({ type: 'mediaType', mediaType: 'Image' });
		expect(conditionNode(make('favorite', { value: false }))).toEqual({
			type: 'favorite',
			value: false
		});
		expect(conditionNode(make('tag'))).toEqual({ type: 'tag', tagType: 'LivePhoto' });
		expect(conditionNode(make('ocr', { text: ' factura ' }))).toEqual({
			type: 'ocr',
			query: 'factura'
		});
	});

	it('negates a condition', () => {
		expect(conditionNode(make('tag', { text: 'Screenshot', negate: true }))).toEqual({
			type: 'not',
			condition: { type: 'tag', tagType: 'Screenshot' }
		});
	});

	it('groups several conditions and keeps a single one bare', () => {
		const favorite = make('favorite');
		const video = make('mediaType', { text: 'Video' });

		expect(buildRule('OR', [favorite])).toEqual({ type: 'favorite', value: true });
		expect(buildRule('OR', [favorite, make('person'), video])).toEqual({
			op: 'OR',
			conditions: [
				{ type: 'favorite', value: true },
				{ type: 'mediaType', mediaType: 'Video' }
			]
		});
	});
});
