import type { OrganizeSuggestionResponse } from '#lib/api/index.js';
import { monthTitle } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import { labelText } from '../text.js';

const MONTH = /^\d{4}-\d{2}$/;

/** The server titles a month batch "2024-07" and a scene by its model label. */
export function suggestionTitle(suggestion: OrganizeSuggestionResponse) {
	if (suggestion.kind === 'month' && MONTH.test(suggestion.title))
		return monthTitle(suggestion.title);
	if (suggestion.kind === 'scene') return labelText(suggestion.title);
	return suggestion.title;
}

/** "julio de 2024 – agosto de 2024", from the batch's `yyyy-MM` bounds. */
export function suggestionRange(suggestion: OrganizeSuggestionResponse) {
	const from = suggestion.from && MONTH.test(suggestion.from) ? monthTitle(suggestion.from) : null;
	const to = suggestion.to && MONTH.test(suggestion.to) ? monthTitle(suggestion.to) : null;
	if (!from || !to) return from ?? to;
	return from === to ? from : m.organize_range({ from, to });
}
