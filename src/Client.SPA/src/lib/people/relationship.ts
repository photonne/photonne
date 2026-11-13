import { m } from '#lib/paraglide/messages.js';

/** What a person is to the user; the codes the server accepts. */
export const RELATIONSHIP_CATEGORIES = [
	'family',
	'partner',
	'children',
	'friends',
	'work',
	'pet',
	'other'
] as const;

export type RelationshipCategory = (typeof RELATIONSHIP_CATEGORIES)[number];

/** The stored code as a known category; one a newer server added reads as "other". */
export function relationshipCategory(
	value: string | null | undefined
): RelationshipCategory | null {
	if (!value) return null;
	return (RELATIONSHIP_CATEGORIES as readonly string[]).includes(value)
		? (value as RelationshipCategory)
		: 'other';
}

const names: Record<RelationshipCategory, () => string> = {
	family: m.people_relationship_family,
	partner: m.people_relationship_partner,
	children: m.people_relationship_children,
	friends: m.people_relationship_friends,
	work: m.people_relationship_work,
	pet: m.people_relationship_pet,
	other: m.people_relationship_other
};

export function categoryName(category: RelationshipCategory | null): string {
	return category ? names[category]() : m.people_relationship_none();
}

// One message per category, "|"-separated, so each language brings its own.
const suggestions: Partial<Record<RelationshipCategory, () => string>> = {
	family: m.people_relationship_suggest_family,
	partner: m.people_relationship_suggest_partner,
	children: m.people_relationship_suggest_children,
	friends: m.people_relationship_suggest_friends,
	work: m.people_relationship_suggest_work,
	pet: m.people_relationship_suggest_pet
};

/** Usual labels within a category ("Madre", "Padre"…), offered while typing. */
export function labelSuggestions(category: RelationshipCategory | null): string[] {
	const list = category ? suggestions[category]?.() : undefined;
	return list ? list.split('|') : [];
}

/** "Familia · Madre", "Amistades", or null without a relationship. */
export function relationshipText(person: {
	relationshipCategory?: string | null;
	relationshipLabel?: string | null;
}): string | null {
	const category = relationshipCategory(person.relationshipCategory);
	if (!category) return null;
	const label = person.relationshipLabel?.trim();
	return label ? `${categoryName(category)} · ${label}` : categoryName(category);
}
