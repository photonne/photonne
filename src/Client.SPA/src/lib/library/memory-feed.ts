import { getMemoryFeedOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';

/**
 * The whole feed in one request, shared by Recuerdos and Explorar (same query
 * key, one fetch): rows are grouped client-side, and a truncated feed would
 * cut a theme's years in the middle (see MemoryFeedEndpoint.MaxLimit).
 */
export const memoryFeedOptions = () => getMemoryFeedOptions({ query: { limit: 500 } });
