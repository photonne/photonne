import { appHref } from '#lib/navigation/href.js';
import { emptySearch, searchParams, type SearchQuery } from './search-query.js';

/** A text search (no AI) with just these filters, as a link to the search page. */
export function searchHref(query: Partial<SearchQuery>) {
	return `${appHref('/search')}?${searchParams({ ...emptySearch, semantic: false, ...query })}`;
}
