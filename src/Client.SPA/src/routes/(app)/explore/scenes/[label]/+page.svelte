<script lang="ts">
	import { page } from '$app/state';
	import { displayLabel } from '#lib/library/labels.js';
	import SearchPhotos from '#lib/library/SearchPhotos.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const label = $derived(page.params.label ?? '');
</script>

{#key label}
	<SearchPhotos
		title={displayLabel(label, getLocale())}
		filter={{ sceneLabel: [label] }}
		emptyText={m.explore_label_no_photos()}
		crumb={{ href: appHref('/explore/scenes'), label: m.explore_scenes() }}
	/>
{/key}
