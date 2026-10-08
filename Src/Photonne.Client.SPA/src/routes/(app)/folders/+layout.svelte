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
</script>

<div class="folders">
	<aside class="pane" aria-labelledby="folders-tree-title">
		<div class="pane-head">
			<h2 id="folders-tree-title"><a href={appHref('/folders')}>{m.nav_folders()}</a></h2>
			<button
				type="button"
				class="icon"
				aria-label={m.folders_new()}
				title={m.folders_new()}
				onclick={() => (creating = true)}
			>
				<Icon path={icons.newFolder} size={20} />
			</button>
		</div>
		{#if treeQuery.isPending}
			<p class="note" role="status">{m.session_restoring()}</p>
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
		grid-template-columns: 264px minmax(0, 1fr);
		height: 100%;
	}

	.pane {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		min-height: 0;
		padding: var(--space-3) var(--space-2) var(--space-3) var(--space-3);
		border-right: 1px solid var(--color-border);
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
	}

	h2 a {
		color: inherit;
		text-decoration: none;
	}

	h2 a:hover {
		text-decoration: underline;
	}

	.icon {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover {
		background: var(--color-surface);
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

	@media (max-width: 1100px) {
		.folders {
			grid-template-columns: 216px minmax(0, 1fr);
		}
	}
</style>
