<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import type { AlbumResponse } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import AlbumCard from './AlbumCard.svelte';
	import type { ListView } from './album-list.js';
	import { toggleAlbumPin, toggleFolderPin } from './cache.js';
	import FolderCard from './FolderCard.svelte';
	import type { PinnedEntry } from './pinned.js';

	interface Props {
		entries: readonly PinnedEntry[];
		view: ListView;
		/** The albums page keeps its pinned albums selectable, as in its own list. */
		albumSelection?: {
			selection: Selection;
			select: (album: AlbumResponse, mode: 'toggle' | 'range') => void;
		};
	}

	let { entries, view, albumSelection }: Props = $props();

	const queryClient = useQueryClient();
</script>

<!-- Albums and folders together, as in the sidebar and the native app's Collections. -->
<section aria-labelledby="pinned-title">
	<h2 id="pinned-title">{m.nav_section_pinned()}</h2>
	<ul class={view}>
		{#each entries as entry (entry.kind + entry.item.id)}
			{#if entry.kind === 'album'}
				{@const album = entry.item}
				<AlbumCard
					{album}
					{view}
					selected={albumSelection?.selection.has(album.id)}
					selecting={albumSelection?.selection.active}
					onselect={albumSelection && ((mode) => albumSelection.select(album, mode))}
					ontogglepin={(a) => toggleAlbumPin(queryClient, a)}
				/>
			{:else}
				<FolderCard
					folder={entry.item}
					{view}
					ontogglepin={(folder) => toggleFolderPin(queryClient, folder)}
				/>
			{/if}
		{/each}
	</ul>
</section>

<style>
	h2 {
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-lg);
		font-weight: 600;
		line-height: 1.3;
	}

	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	ul.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: var(--space-6) var(--space-4);
	}

	ul.list {
		display: grid;
		gap: 2px;
	}
</style>
