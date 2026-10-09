<script lang="ts">
	import { page } from '$app/state';
	import SearchPhotos from '#lib/library/SearchPhotos.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const tag = $derived(page.params.tag ?? '');
</script>

<!-- The API has no exact tag filter: the text search matches tags (and also
     names, paths, captions and recognised text that contain the word). -->
{#key tag}
	<SearchPhotos
		title={`#${tag}`}
		filter={{ q: tag }}
		emptyText={m.explore_label_no_photos()}
		crumb={{ href: appHref('/explore'), label: m.nav_explore() }}
	>
		<p class="note">{m.explore_tag_note()}</p>
	</SearchPhotos>
{/key}

<style>
	.note {
		margin: var(--space-1) 0 0;
		max-width: 80ch;
		padding: 0 var(--page-gutter) var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
