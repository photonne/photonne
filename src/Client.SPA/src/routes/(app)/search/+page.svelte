<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import SearchDiscover from '#lib/search/SearchDiscover.svelte';
	import SearchForm from '#lib/search/SearchForm.svelte';
	import SearchResults from '#lib/search/SearchResults.svelte';
	import {
		hasCriteria,
		parseSearch,
		searchKey,
		searchParams,
		type SearchQuery
	} from '#lib/search/search-query.js';

	// The URL is the search: typing and filters navigate, so Back steps
	// through earlier searches and a link reproduces one.
	const query = $derived(parseSearch(new URLSearchParams(page.url.search)));
	const key = $derived(searchKey(query));

	// Once the semantic search says it's off, the words run through the text
	// search instead (and say so) for the rest of the visit.
	let semanticDown = $state(false);
	const semantic = $derived(query.semantic && !semanticDown);

	function search(next: SearchQuery) {
		const params = searchParams(next).toString();
		if (params === key) return;
		// The form keeps focus (a filter menu stays open while ticking people).
		goto(`${appHref('/search')}${params ? `?${params}` : ''}`, { reset: false });
	}
</script>

<svelte:head>
	<title>{query.q ? `${query.q} · ` : ''}{m.nav_search()} · {m.app_name()}</title>
</svelte:head>

<div class="search">
	<!-- One h1: "Buscar" until there is a search, then the results' own
	     ("Resultados", with their count). The form stays mounted between both,
	     so typing and an open filter menu survive the first search. -->
	{#if !hasCriteria(query)}
		<PageHeader title={m.nav_search()} />
	{/if}
	<div class="bar" class:solo={hasCriteria(query)}>
		<SearchForm {query} onsearch={search} autofocus={!hasCriteria(query)} />
	</div>

	<div class="body">
		{#if hasCriteria(query)}
			{#key `${key}|${semantic}`}
				<SearchResults
					{query}
					{semantic}
					fallback={query.semantic && !semantic}
					onsemanticdown={() => (semanticDown = true)}
				/>
			{/key}
		{:else}
			<SearchDiscover />
		{/if}
	</div>
</div>

<style>
	.search {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.bar {
		padding: 0 var(--page-gutter) var(--space-3);
		border-bottom: 1px solid var(--color-border);
	}

	.bar.solo {
		padding-top: var(--space-4);
	}

	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}
</style>
