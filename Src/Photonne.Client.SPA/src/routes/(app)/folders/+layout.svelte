<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { invalidateFolders } from '#lib/albums/cache.js';
	import FolderFormDialog from '#lib/albums/FolderFormDialog.svelte';
	import { moveAssets, moveFolder } from '#lib/albums/folder-moves.js';
	import { findFolder, sortTree } from '#lib/albums/folder-tree.js';
	import FolderTree from '#lib/albums/FolderTree.svelte';
	import { icons } from '#lib/albums/icons.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import PaneResizer from '#lib/components/PaneResizer.svelte';
	import { savedWidth } from '#lib/components/pane-width.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const queryClient = useQueryClient();
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const tree = $derived(sortTree(treeQuery.data ?? [], getLocale()));
	const currentId = $derived(page.params.folderId ?? null);

	let creating = $state(false);

	// The tree pane is as wide as the user leaves it: deep trees need room.
	const TREE = { initial: 280, min: 200, max: 640, key: 'photonne.folders.treeWidth' };
	let treeWidth = $state(savedWidth(TREE.key, TREE.initial, TREE.min, TREE.max));
</script>

<div class="folders" style:--tree-width="{treeWidth}px">
	<aside id="folders-tree" class="pane" aria-labelledby="folders-tree-title">
		<div class="pane-head">
			<h2 id="folders-tree-title"><a href={appHref('/folders')}>{m.nav_folders()}</a></h2>
			<button
				type="button"
				class="icon-btn"
				aria-label={m.folders_new()}
				title={m.folders_new()}
				onclick={() => (creating = true)}
			>
				<Icon path={icons.newFolder} size={20} />
			</button>
		</div>
		{#if treeQuery.isPending}
			<div class="loading"><Skeleton variant="text" count={4} /></div>
		{:else if treeQuery.isError}
			<p class="note" role="alert">{m.error_loading()}</p>
		{:else}
			<FolderTree
				{tree}
				{currentId}
				label={m.folders_tree()}
				onopen={(folder) => goto(appHref(`/folders/${folder.id}`))}
				ondropassets={(folder, assetIds) => {
					const from = currentId ? findFolder(tree, currentId) : null;
					moveAssets(queryClient, { assetIds, from, to: folder });
				}}
				ondropfolder={(folder, parent) => moveFolder(queryClient, folder, parent)}
			/>
		{/if}
		<p class="tip">{m.folders_drag_tip()}</p>
	</aside>

	<PaneResizer
		bind:width={treeWidth}
		min={TREE.min}
		max={TREE.max}
		initial={TREE.initial}
		storageKey={TREE.key}
		controls="folders-tree"
	/>

	<div class="main">
		{@render children()}
	</div>
</div>

{#if creating}
	<FolderFormDialog
		parentId={currentId}
		{tree}
		isAdmin={session.isAdmin}
		onclose={() => (creating = false)}
		onsaved={(folder) => {
			creating = false;
			invalidateFolders(queryClient, currentId ?? undefined);
			toasts.show(m.folders_created({ name: folder.name }));
			goto(appHref(`/folders/${folder.id}`));
		}}
	/>
{/if}

<style>
	.folders {
		display: grid;
		grid-template-columns: var(--tree-width) auto minmax(0, 1fr);
		height: 100%;
	}

	.pane {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		min-height: 0;
		padding: var(--space-3) var(--space-2) var(--space-3) var(--space-3);
		overflow-y: auto;
	}

	.pane-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-left: var(--space-1);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-md);
		font-weight: 600;
	}

	h2 a {
		color: inherit;
		text-decoration: none;
	}

	h2 a:hover {
		text-decoration: underline;
	}

	/* The pane is narrow: the skeleton keeps the tree's own inset. */
	.loading {
		--page-gutter: var(--space-2);
	}

	.note,
	.tip {
		margin: 0;
		padding: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.tip {
		margin-top: auto;
		font-size: var(--font-size-xs);
	}

	.main {
		min-width: 0;
		height: 100%;
		overflow: auto;
	}
</style>
