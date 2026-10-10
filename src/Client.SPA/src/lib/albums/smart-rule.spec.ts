import { describe, expect, it } from 'vitest';
import {
	buildRule,
	emptyRule,
	isComplete,
	newCondition,
	orderedRange,
	parseRule,
	type Condition
} from './smart-rule.js';

const withoutIds = (conditions: Condition[]) =>
	conditions.map((condition) => {
		const rest: Partial<Condition> = { ...condition };
		delete rest.id;
		return rest;
	});

describe('parseRule', () => {
	it('reads a logical root over known leaves', () => {
		const model = parseRule({
			op: 'OR',
			conditions: [
				{ type: 'person', personIds: ['p1', 'p2'], match: 'all' },
				{ type: 'dateRange', from: '2024-05-01T00:00:00Z', to: null },
				{ type: 'mediaType', mediaType: 'Video' },
				{ type: 'scene', labels: ['beach'] }
			]
		});
		expect(model.op).toBe('OR');
		expect(model.preserved).toEqual([]);
		expect(withoutIds(model.conditions)).toEqual([
			{ type: 'person', personIds: ['p1', 'p2'], match: 'all' },
			{ type: 'dateRange', from: '2024-05-01', to: '' },
			{ type: 'mediaType', mediaType: 'Video' },
			// Labels match "all" on the server when the rule doesn't say.
			{ type: 'scene', labels: ['beach'], match: 'all' }
		]);
	});

	it('reads a bare leaf as a one-condition AND group', () => {
		const model = parseRule({ type: 'favorite', value: true });
		expect(model.op).toBe('AND');
		expect(withoutIds(model.conditions)).toEqual([{ type: 'favorite', value: true }]);
	});

	it('preserves what it cannot show', () => {
		const nested = { op: 'AND', conditions: [{ type: 'favorite', value: true }] };
		const not = { type: 'not', condition: { type: 'mediaType', mediaType: 'Video' } };
		const userTags = { type: 'tag', userTagIds: ['t1'] };
		const model = parseRule({ op: 'AND', conditions: [nested, not, userTags] });
		expect(model.conditions).toEqual([]);
		expect(model.preserved).toEqual([nested, not, userTags]);
	});

	it('starts empty without a rule', () => {
		expect(parseRule(null)).toEqual(emptyRule());
	});
});

describe('buildRule', () => {
	it('skips incomplete conditions and keeps preserved nodes', () => {
		const person = newCondition('person');
		const text = newCondition('text');
		if (text.type === 'text') text.query = '  boda ';
		const preserved = [{ type: 'not', condition: { type: 'favorite', value: true } }];
		expect(buildRule({ op: 'AND', conditions: [person, text], preserved })).toEqual({
			op: 'AND',
			conditions: [{ type: 'text', query: 'boda' }, ...preserved]
		});
	});

	it('returns null while nothing is complete', () => {
		expect(buildRule({ op: 'OR', conditions: [newCondition('folder')], preserved: [] })).toBeNull();
	});

	it('sends date bounds as UTC days', () => {
		const range = newCondition('dateRange');
		if (range.type === 'dateRange') {
			range.from = '2024-01-01';
			range.to = '2024-12-31';
		}
		expect(buildRule({ op: 'AND', conditions: [range], preserved: [] })).toEqual({
			op: 'AND',
			conditions: [{ type: 'dateRange', from: '2024-01-01T00:00:00Z', to: '2024-12-31T00:00:00Z' }]
		});
	});

	it('round-trips through parseRule', () => {
		const rule = {
			op: 'OR',
			conditions: [
				{ type: 'folder', folderIds: ['f1'], includeSubfolders: false },
				{ type: 'tag', tagType: 'Panorama' },
				{ type: 'object', labels: ['dog', 'cat'], match: 'any' },
				{ type: 'ocr', query: 'menu' }
			]
		};
		expect(buildRule(parseRule(rule))).toEqual(rule);
	});
});

describe('isComplete', () => {
	it('needs a value on conditions that pick things', () => {
		expect(isComplete(newCondition('person'))).toBe(false);
		expect(isComplete(newCondition('dateRange'))).toBe(false);
		expect(isComplete(newCondition('mediaType'))).toBe(true);
		expect(isComplete(newCondition('favorite'))).toBe(true);
	});
});

describe('orderedRange', () => {
	it('swaps reversed bounds', () => {
		expect(orderedRange('2024-05-01', '2024-01-01')).toEqual({
			from: '2024-01-01',
			to: '2024-05-01'
		});
		expect(orderedRange('', '2024-01-01')).toEqual({ from: '', to: '2024-01-01' });
	});
});
