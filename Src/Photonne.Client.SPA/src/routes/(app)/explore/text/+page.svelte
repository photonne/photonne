<script lang="ts">
	import { page } from '$app/state';
	import SearchPhotos from '#lib/library/SearchPhotos.svelte';
	import TextSearchForm from '#lib/library/TextSearchForm.svelte';
	import TextSearchStart from '#lib/library/TextSearchStart.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const query = $derived(page.url.searchParams.get('q')?.trim() ?? '');
</script>

{#if query}
	{#key query}
		<SearchPhotos
			title={m.explore_text_title({ query })}
			filter={{ textQuery: query }}
			emptyText={m.explore_text_no_photos()}
			crumb={{ href: appHref('/explore'), label: m.nav_explore() }}
		>
			<div class="form"><TextSearchForm value={query} /></div>
		</SearchPhotos>
	{/key}
{:else}
	<TextSearchStart />
{/if}

<style>
	.form {
		padding: var(--space-2) var(--space-4) 0;
	}
</style>
