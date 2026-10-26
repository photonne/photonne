<script lang="ts">
	import { page } from '$app/state';
	import SearchPhotos from '#lib/library/SearchPhotos.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const tag = $derived(page.params.tag ?? '');
</script>

<!-- The photos with exactly this tag (a user tag or a detected one). -->
{#key tag}
	<SearchPhotos
		title={`#${tag}`}
		filter={{ tag }}
		emptyText={m.explore_label_no_photos()}
		emptyHint={m.explore_label_no_photos_hint()}
		crumb={{ href: appHref('/explore'), label: m.nav_explore() }}
	/>
{/key}
