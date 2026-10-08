<script lang="ts">
	import { untrack } from 'svelte';
	import { apiErrorCode, searchAssets, semanticSearchAssets } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { ListStore } from '#lib/timeline/list-store.svelte.js';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { toGridAsset } from '#lib/timeline/types.js';
	import { textSearchRequest, type SearchQuery } from './search-query.js';

	interface Props {
		/** One search for this component's whole life (the host keys it). */
		query: SearchQuery;
		/** Ask the semantic endpoint; false runs the text search on the same words. */
		semantic: boolean;
		/** The semantic search answered that it is off (no ML / no models). */
		onsemanticdown: () => void;
		/** Why the text search is showing although semantic was asked. */
		fallback?: boolean;
	}

	let { query, semantic, onsemanticdown, fallback = false }: Props = $props();

	const PAGE_SIZE = 100;
	/** The server's cap for the semantic search (it doesn't page). */
	const SEMANTIC_LIMIT = 200;

	let notice = $state<'capped' | 'too_short' | null>(null);

	// The search can't change under a mounted view (the host keys it).
	const search = untrack(() => ({ query, semantic }));

	// Text results page in by offset, newest first, grouped by month.
	const list = new PagedList(async (cursor) => {
		const offset = Number(cursor ?? 0);
		const { data } = await searchAssets({
			query: textSearchRequest(search.query, offset, PAGE_SIZE)
		});
		return data && { ...data, nextCursor: String(offset + data.items.length) };
	});

	// Semantic results come at once, ranked by likeness.
	const ranked = new ListStore({ grouping: 'none', reload: () => loadSemantic() });
	let rankedStatus = $state<'pending' | 'error' | 'ready'>('pending');

	async function loadSemantic() {
		rankedStatus = 'pending';
		const { data, error } = await semanticSearchAssets({
			query: { q: search.query.q, limit: SEMANTIC_LIMIT }
		});
		const code = apiErrorCode(error);
		if (code === 'semantic_search_unavailable') {
			onsemanticdown();
			return;
		}
		if (code === 'invalid_query') notice = 'too_short';
		else if (!data) {
			rankedStatus = 'error';
			return;
		}
		const items = data?.items ?? [];
		if (items.length >= SEMANTIC_LIMIT) notice = 'capped';
		ranked.items = items.map((item) => toGridAsset(item.asset));
		rankedStatus = 'ready';
	}

	if (search.semantic) loadSemantic();
	else list.start();
</script>

<CollectionView
	store={search.semantic ? ranked : list.store}
	title={m.search_results()}
	status={search.semantic ? rankedStatus : list.status}
	emptyText={notice === 'too_short' ? m.search_semantic_too_short() : m.search_no_results()}
	headers={!search.semantic}
	onnearend={() => !search.semantic && list.more()}
>
	{#snippet header()}
		{#if fallback}
			<p class="notice" role="status">{m.search_semantic_unavailable()}</p>
		{:else if notice === 'capped'}
			<p class="notice" role="status">{m.search_semantic_capped({ count: SEMANTIC_LIMIT })}</p>
		{/if}
	{/snippet}
</CollectionView>

<style>
	.notice {
		margin: 0 var(--space-4) var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-left: 3px solid var(--color-accent);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-size: var(--font-size-sm);
	}
</style>
