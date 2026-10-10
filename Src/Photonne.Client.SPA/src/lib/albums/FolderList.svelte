<script lang="ts">
	import type { FolderResponse } from '#lib/api/index.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import type { ListView } from './album-list.js';
	import FolderCard from './FolderCard.svelte';

	interface Props {
		folders: readonly FolderResponse[];
		label: string;
		view: ListView;
		selection: Selection;
		/** Where a folder is, for search results from any depth ("Camera / 2025"). */
		location?: (folder: FolderResponse) => string;
	}

	let { folders, label, view, selection, location }: Props = $props();

	const order = $derived(folders.map((folder) => folder.id));

	function select(folder: FolderResponse, mode: 'toggle' | 'range') {
		if (mode === 'range') selection.selectRange(folder.id, order);
		else selection.toggle(folder.id);
	}
</script>

<ul class={view} aria-label={label}>
	{#each folders as folder (folder.id)}
		<FolderCard
			{folder}
			{view}
			selected={selection.has(folder.id)}
			selecting={selection.active}
			onselect={(mode) => select(folder, mode)}
			where={location?.(folder)}
		/>
	{/each}
</ul>

<style>
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
