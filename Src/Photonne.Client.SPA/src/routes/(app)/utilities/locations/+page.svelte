<script lang="ts">
	import '#lib/search/ui.css';
	import { SvelteSet } from 'svelte/reactivity';
	import { createQuery } from '@tanstack/svelte-query';
	import { getMyFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
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
	<UtilityHeader title={m.utilities_locations()} lead={m.utilities_locations_lead()}>
		{#snippet toolbar()}
			<button
				type="button"
				class="ui-button"
				disabled={!!filter.trim()}
				onclick={() => {
					for (const id of branchIds(roots)) expanded.add(id);
				}}
			>
				<Icon path={unfoldMorePath} size={18} />
				{m.utilities_expand_all()}
			</button>
			<button
				type="button"
				class="ui-button"
				disabled={!!filter.trim() || expanded.size === 0}
				onclick={() => expanded.clear()}
			>
				<Icon path={unfoldLessPath} size={18} />
				{m.utilities_collapse_all()}
			</button>
		{/snippet}
	</UtilityHeader>

	{#if tree.isPending}
		<p class="ui-status" role="status">{m.utilities_loading()}</p>
	{:else if tree.isError}
		<p class="ui-status" role="alert">{m.error_loading()}</p>
	{:else if roots.length === 0}
		<p class="ui-status">{m.utilities_locations_empty()}</p>
	{:else}
		<div class="bar">
			<input
				type="search"
				class="ui-field"
				aria-label={m.utilities_filter_folders()}
				placeholder={m.utilities_filter_folders()}
				bind:value={filter}
			/>
			<span class="totals">
				{m.utilities_folder_count({ count: countFolders(roots) })} · {m.utilities_photos({
					count: totalAssets(roots)
				})}
			</span>
		</div>
		<div class="tree">
			{#if shown.length === 0}
				<p class="ui-status">{m.search_filter_nothing()}</p>
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

	.bar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
		padding: 0 var(--space-4) var(--space-3);
	}

	.bar input {
		flex: 0 1 360px;
	}

	.totals {
		margin-left: auto;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.tree {
		padding: 0 var(--space-4);
	}
</style>
