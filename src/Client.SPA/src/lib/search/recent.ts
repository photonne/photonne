/** A search the user ran, as the URL holds it and as its chip reads. */
export interface RecentSearch {
	/** searchParams(query).toString(): the search, exactly. */
	params: string;
	label: string;
}

export const MAX_RECENT = 8;

/** The list with `entry` first, without an earlier copy of it, capped. */
export function remember(list: readonly RecentSearch[], entry: RecentSearch): RecentSearch[] {
	if (!entry.params || !entry.label.trim()) return [...list];
	return [entry, ...list.filter((item) => item.params !== entry.params)].slice(0, MAX_RECENT);
}

/** A stored list, tolerating anything a previous version or a hand edit left there. */
export function parseRecent(raw: string | null): RecentSearch[] {
	if (!raw) return [];
	try {
		const value: unknown = JSON.parse(raw);
		if (!Array.isArray(value)) return [];
		return value
			.filter(
				(item): item is RecentSearch =>
					typeof item === 'object' &&
					item !== null &&
					typeof item.params === 'string' &&
					typeof item.label === 'string' &&
					item.params.length > 0
			)
			.slice(0, MAX_RECENT);
	} catch {
		return [];
	}
}
