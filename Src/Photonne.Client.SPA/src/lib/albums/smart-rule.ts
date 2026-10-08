import type { SmartRuleNode } from '#lib/api/index.js';

/*
 * The smart-album rule editor's model. A rule is a tree of SmartRuleNode
 * (docs/smart-albums/rule-schema.md): logical nodes (op AND/OR) over
 * condition leaves. The editor shows one root group of typed conditions; any
 * node it can't show (a nested group, "not", user tags…) is kept as is and
 * sent back on save, so editing here never drops what another client set up.
 */

export type Match = 'any' | 'all';

export type Condition =
	| { id: number; type: 'dateRange'; from: string; to: string }
	| { id: number; type: 'folder'; folderIds: string[]; includeSubfolders: boolean }
	| { id: number; type: 'person'; personIds: string[]; match: Match }
	| { id: number; type: 'object' | 'scene'; labels: string[]; match: Match }
	| { id: number; type: 'mediaType'; mediaType: 'Image' | 'Video' }
	| { id: number; type: 'favorite'; value: boolean }
	| { id: number; type: 'tag'; tagType: StructuralTag }
	| { id: number; type: 'text' | 'ocr'; query: string };

export type ConditionType = Condition['type'];

/** Conditions in the order the "Add condition" menu offers them. */
export const conditionTypes: readonly ConditionType[] = [
	'person',
	'dateRange',
	'folder',
	'mediaType',
	'favorite',
	'tag',
	'object',
	'scene',
	'text',
	'ocr'
];

/** Tags the server detects on its own (AssetTagType), minus the hidden Live Photo clip. */
export const structuralTags = [
	'LivePhoto',
	'Burst',
	'Panorama',
	'Screenshot',
	'HDR',
	'Portrait'
] as const;
export type StructuralTag = (typeof structuralTags)[number];

export interface RuleModel {
	/** The root group: every condition (AND) or any of them (OR). */
	op: 'AND' | 'OR';
	conditions: Condition[];
	/** Nodes this editor can't show, re-sent untouched. */
	preserved: SmartRuleNode[];
}

let nextId = 1;

export function newCondition(type: ConditionType): Condition {
	const id = nextId++;
	switch (type) {
		case 'dateRange':
			return { id, type, from: '', to: '' };
		case 'folder':
			return { id, type, folderIds: [], includeSubfolders: true };
		case 'person':
			return { id, type, personIds: [], match: 'any' };
		case 'object':
		case 'scene':
			return { id, type, labels: [], match: 'any' };
		case 'mediaType':
			return { id, type, mediaType: 'Image' };
		case 'favorite':
			return { id, type, value: true };
		case 'tag':
			return { id, type, tagType: 'LivePhoto' };
		case 'text':
		case 'ocr':
			return { id, type, query: '' };
	}
}

export function emptyRule(): RuleModel {
	return { op: 'AND', conditions: [], preserved: [] };
}

const isLogical = (node: SmartRuleNode) => !!node.op;
const matchOf = (node: SmartRuleNode): Match =>
	node.match?.toLowerCase() === 'all' ? 'all' : 'any';
/** The date part of the server's DateTime ("2024-05-01T00:00:00Z" → "2024-05-01"). */
const dateOf = (value: string | null | undefined) => (value ? value.slice(0, 10) : '');

/** A condition leaf the editor understands, or null to preserve the node. */
export function toCondition(node: SmartRuleNode): Condition | null {
	if (isLogical(node)) return null;
	const id = nextId++;
	switch (node.type?.toLowerCase()) {
		case 'daterange':
			return node.from || node.to
				? { id, type: 'dateRange', from: dateOf(node.from), to: dateOf(node.to) }
				: null;
		case 'folder':
			return {
				id,
				type: 'folder',
				folderIds: node.folderIds ?? [],
				includeSubfolders: node.includeSubfolders ?? true
			};
		case 'person':
			return { id, type: 'person', personIds: node.personIds ?? [], match: matchOf(node) };
		case 'object':
		case 'scene': {
			const type = node.type.toLowerCase() as 'object' | 'scene';
			// Labels default to "all" on the server when no match is given.
			const match: Match = node.match ? matchOf(node) : 'all';
			return { id, type, labels: node.labels ?? [], match };
		}
		case 'mediatype': {
			const mediaType = node.mediaType?.toLowerCase();
			if (mediaType === 'image') return { id, type: 'mediaType', mediaType: 'Image' };
			if (mediaType === 'video') return { id, type: 'mediaType', mediaType: 'Video' };
			return null;
		}
		case 'favorite':
			return { id, type: 'favorite', value: node.value ?? true };
		case 'tag': {
			const tag = structuralTags.find((t) => t.toLowerCase() === node.tagType?.toLowerCase());
			return tag && !node.userTagIds?.length ? { id, type: 'tag', tagType: tag } : null;
		}
		case 'text':
		case 'ocr':
			return node.query
				? { id, type: node.type.toLowerCase() as 'text' | 'ocr', query: node.query }
				: null;
		default:
			return null;
	}
}

/** The inverse of buildRule: a logical root over leaves, or a bare leaf. */
export function parseRule(rule: SmartRuleNode | null | undefined): RuleModel {
	if (!rule) return emptyRule();
	const root = isLogical(rule) ? rule : null;
	const children = root ? (root.conditions ?? []) : [rule];
	const model: RuleModel = {
		op: root?.op?.toUpperCase() === 'OR' ? 'OR' : 'AND',
		conditions: [],
		preserved: []
	};
	for (const node of children) {
		const condition = toCondition(node);
		if (condition) model.conditions.push(condition);
		else model.preserved.push(node);
	}
	return model;
}

/** True when the condition has what it needs to be sent (a half-filled row is skipped). */
export function isComplete(condition: Condition) {
	switch (condition.type) {
		case 'dateRange':
			return !!(condition.from || condition.to);
		case 'folder':
			return condition.folderIds.length > 0;
		case 'person':
			return condition.personIds.length > 0;
		case 'object':
		case 'scene':
			return condition.labels.length > 0;
		case 'text':
		case 'ocr':
			return condition.query.trim().length > 0;
		default:
			return true;
	}
}

export function toNode(condition: Condition): SmartRuleNode {
	switch (condition.type) {
		case 'dateRange':
			return {
				type: 'dateRange',
				from: condition.from ? `${condition.from}T00:00:00Z` : null,
				to: condition.to ? `${condition.to}T00:00:00Z` : null
			};
		case 'folder':
			return {
				type: 'folder',
				folderIds: condition.folderIds,
				includeSubfolders: condition.includeSubfolders
			};
		case 'person':
			return { type: 'person', personIds: condition.personIds, match: condition.match };
		case 'object':
		case 'scene':
			return { type: condition.type, labels: condition.labels, match: condition.match };
		case 'mediaType':
			return { type: 'mediaType', mediaType: condition.mediaType };
		case 'favorite':
			return { type: 'favorite', value: condition.value };
		case 'tag':
			return { type: 'tag', tagType: condition.tagType };
		case 'text':
		case 'ocr':
			return { type: condition.type, query: condition.query.trim() };
	}
}

/**
 * The rule to send, or null when nothing is complete yet. Always a logical
 * root, so the AND/OR choice survives even with a single condition.
 */
export function buildRule(model: RuleModel): SmartRuleNode | null {
	const nodes = [...model.conditions.filter(isComplete).map(toNode), ...model.preserved];
	if (nodes.length === 0) return null;
	return { op: model.op, conditions: nodes };
}

/** Changes a date range so "to" never ends before "from". */
export function orderedRange(from: string, to: string) {
	return from && to && to < from ? { from: to, to: from } : { from, to };
}
