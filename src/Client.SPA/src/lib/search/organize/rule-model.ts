import type { SmartRuleNode } from '#lib/api/index.js';

/**
 * The flat rule the organize editor builds: one group (all / any of the
 * conditions) of typed conditions, each of which can be negated. It becomes
 * the server's SmartRuleNode tree (docs/smart-albums/rule-schema.md).
 */

export const conditionTypes = [
	'dateRange',
	'folder',
	'person',
	'mediaType',
	'favorite',
	'tag',
	'object',
	'scene',
	'text',
	'ocr'
] as const;

export type ConditionType = (typeof conditionTypes)[number];

/** Structural tags the server detects (AssetTagType). */
export const structuralTags = ['LivePhoto', 'Burst', 'Panorama', 'Screenshot', 'HDR', 'Portrait'];

export interface Condition {
	/** Stable id for the editor's list. */
	key: string;
	type: ConditionType;
	/** "Is not": the condition wrapped in a `not`. */
	negate: boolean;
	/** dateRange, as calendar days. */
	from: string | null;
	to: string | null;
	/** folder or person ids. */
	ids: string[];
	includeSubfolders: boolean;
	/** object / scene labels. */
	labels: string[];
	/** person / object / scene: any of the values, or all of them together. */
	match: 'any' | 'all';
	/** favorite. */
	value: boolean;
	/** mediaType ('Image' / 'Video'), tag (a structural tag), text and ocr. */
	text: string;
}

export function newCondition(type: ConditionType, key: string): Condition {
	return {
		key,
		type,
		negate: false,
		from: null,
		to: null,
		ids: [],
		includeSubfolders: true,
		labels: [],
		match: 'any',
		value: true,
		text: type === 'mediaType' ? 'Image' : type === 'tag' ? structuralTags[0] : ''
	};
}

const day = (value: string) => `${value}T00:00:00Z`;

/** The condition as a rule node, or null while it is incomplete. */
export function conditionNode(condition: Condition): SmartRuleNode | null {
	const node = leaf(condition);
	if (!node) return null;
	return condition.negate ? { type: 'not', condition: node } : node;
}

function leaf(c: Condition): SmartRuleNode | null {
	const text = c.text.trim();
	switch (c.type) {
		case 'dateRange':
			return c.from || c.to
				? { type: 'dateRange', from: c.from ? day(c.from) : null, to: c.to ? day(c.to) : null }
				: null;
		case 'folder':
			return c.ids.length
				? { type: 'folder', folderIds: c.ids, includeSubfolders: c.includeSubfolders }
				: null;
		case 'person':
			return c.ids.length ? { type: 'person', personIds: c.ids, match: c.match } : null;
		case 'object':
		case 'scene':
			return c.labels.length ? { type: c.type, labels: c.labels, match: c.match } : null;
		case 'mediaType':
			return text ? { type: 'mediaType', mediaType: text } : null;
		case 'favorite':
			return { type: 'favorite', value: c.value };
		case 'tag':
			return text ? { type: 'tag', tagType: text } : null;
		case 'text':
		case 'ocr':
			return text ? { type: c.type, query: text } : null;
	}
}

/**
 * The whole rule: a single condition on its own, several under AND / OR,
 * or null when nothing is complete yet.
 */
export function buildRule(op: 'AND' | 'OR', conditions: readonly Condition[]) {
	const nodes = conditions.map(conditionNode).filter((node): node is SmartRuleNode => !!node);
	if (nodes.length === 0) return null;
	if (nodes.length === 1) return nodes[0];
	return { op, conditions: nodes } satisfies SmartRuleNode;
}
