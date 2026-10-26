import { longDate } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import type { SearchQuery } from './search-query.js';
import { labelText, lastSegment } from './text.js';

/**
 * How a recent search reads on its chip: the words, then what narrowed it,
 * the same wording as the search's own filter chips. People without a known
 * name count as "persona".
 */
export function searchLabel(
	query: SearchQuery,
	personName: (id: string) => string | null | undefined
) {
	const parts: string[] = [];
	if (query.q) parts.push(`«${query.q}»`);
	for (const id of query.people) parts.push(personName(id) || m.search_recent_person());
	for (const label of [...query.objects, ...query.scenes]) parts.push(labelText(label));
	if (query.tag) parts.push(`#${query.tag}`);
	if (query.camera) parts.push([query.make, query.camera].filter(Boolean).join(' '));
	if (query.place) parts.push(query.placeName || m.search_chip_place_unknown());
	if (query.folder) parts.push(m.search_chip_folder({ folder: lastSegment(query.folder) }));
	if (query.ocr) parts.push(m.search_chip_ocr({ text: query.ocr }));
	if (query.type)
		parts.push(query.type === 'video' ? m.search_chip_type_video() : m.search_chip_type_image());
	if (query.from && query.to)
		parts.push(m.search_chip_between({ from: longDate(query.from), to: longDate(query.to) }));
	else if (query.from) parts.push(m.search_chip_from({ date: longDate(query.from) }));
	else if (query.to) parts.push(m.search_chip_to({ date: longDate(query.to) }));
	return parts.join(' · ');
}
