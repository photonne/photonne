<script lang="ts">
	import { SvelteSet } from 'svelte/reactivity';
	import { createQuery } from '@tanstack/svelte-query';
	import { getMyFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import SearchField from '#lib/library/SearchField.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { unfoldLessPath, unfoldMorePath } from '#lib/search/icons.js';
	import FolderRows from '#lib/search/utilities/FolderRows.svelte';
	import {
		branchIds,
		countFolders,
		filterTree,
		totalAssets
	} from '#lib/search/utilities/folder-tree.js';
	import UtilityHeader from '#lib/search/utilities/UtilityHeader.svelte';

	const tree = createQuery(() => getMyFolderTreeOptions());
	const expanded = new SvelteSet<string>();
	let filter = $state('');

	// Already nested; a root can have a parent the user can't see.
	const roots = $derived(tree.data ?? []);
	const shown = $derived(filterTree(roots, filter));
</script>

<div class="page">
	<UtilityHeader
		title={m.utilities_locations()}
		lead={m.utilities_locations_lead()}
		count={roots.length
			? `${m.utilities_folder_count({ count: countFolders(roots) })} · ${m.utilities_photos({
					count: totalAssets(roots)
				})}`
			: null}
	>
		{#snippet actions()}
			<button
				type="button"
				class="btn"
				disabled={!roots.length || !!filter.trim()}
				onclick={() => {
					for (const id of branchIds(roots)) expanded.add(id);
				}}
			>
				<Icon path={unfoldMorePath} size={18} />
				{m.utilities_expand_all()}
			</button>
			<button
				type="button"
				class="btn"
				disabled={!!filter.trim() || expanded.size === 0}
				onclick={() => expanded.clear()}
			>
				<Icon path={unfoldLessPath} size={18} />
				{m.utilities_collapse_all()}
			</button>
		{/snippet}
		{#snippet tools()}
			{#if roots.length}
				<SearchField bind:value={filter} label={m.utilities_filter_folders()} width="320px" />
			{/if}
		{/snippet}
	</UtilityHeader>

	{#if tree.isPending}
		<Skeleton variant="rows" count={8} />
	{:else if tree.isError}
		<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
	{:else if roots.length === 0}
		<EmptyState icon="folder" title={m.utilities_locations_empty()} />
	{:else}
		<div class="tree">
			{#if shown.length === 0}
				<EmptyState compact icon="search" title={m.search_filter_nothing()} />
			{:else}
				<FolderRows folders={shown} {expanded} forceOpen={!!filter.trim()} />
			{/if}
		</div>
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.tree {
		padding: 0 var(--page-gutter);
	}
</style>
