/** A label of Explorar (scene, object) with its photo count and cover. */
export interface Label {
	label: string;
	assetCount: number;
	coverAssetId: string | null;
}

export type LabelSort = 'count' | 'name';

/**
 * By number of photos (what shows up most, first) or by name in natural
 * order ("photo 2" before "photo 10"), ignoring case and accents.
 */
export function sortLabels<T extends Label>(labels: readonly T[], sort: LabelSort, locale: string) {
	const collator = new Intl.Collator(locale, { numeric: true, sensitivity: 'base' });
	return [...labels].sort((a, b) =>
		sort === 'count'
			? b.assetCount - a.assetCount || collator.compare(a.label, b.label)
			: collator.compare(a.label, b.label)
	);
}

/** Detector labels come lower-case ("dog", "beach"); a title starts upper-case. */
export function displayLabel(label: string, locale: string) {
	return label.charAt(0).toLocaleUpperCase(locale) + label.slice(1);
}

export function isLabelSort(value: string | null): value is LabelSort {
	return value === 'count' || value === 'name';
}
