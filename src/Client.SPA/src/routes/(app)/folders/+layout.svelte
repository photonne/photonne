<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { tick } from 'svelte';
	import { MediaQuery } from 'svelte/reactivity';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { invalidateFolders } from '#lib/albums/cache.js';
	import FolderFormDialog from '#lib/albums/FolderFormDialog.svelte';
	import { moveAssets, moveFolder } from '#lib/albums/folder-moves.js';
	import { browsableRoots, groupFolders } from '#lib/albums/folder-groups.js';
	import { findFolder } from '#lib/albums/folder-tree.js';
	import FolderTree from '#lib/albums/FolderTree.svelte';
	import { icons } from '#lib/albums/icons.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import PaneResizer from '#lib/components/PaneResizer.svelte';
	import { savedWidth } from '#lib/components/pane-width.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { chrome } from '#lib/navigation/chrome.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const queryClient = useQueryClient();
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const groups = $derived(
		groupFolders(treeQuery.data ?? [], session.user?.username ?? '', getLocale())
	);
	const tree = $derived(browsableRoots(groups));
	const currentId = $derived(page.params.folderId ?? null);

	let creating = $state(false);

	// The tree pane is as wide as the user leaves it: deep trees need room.
	const TREE = { initial: 280, min: 200, max: 640, key: 'photonne.folders.treeWidth' };
	let treeWidth = $state(savedWidth(TREE.key, TREE.initial, TREE.min, TREE.max));

	// On a phone the tree and the folder can't share the width: the tree becomes
	// a drawer behind a button, and opening a folder puts it away.
	const narrow = new MediaQuery('max-width: 720px');
	let treeOpen = $state(false);
	$effect(() => {
		void currentId;
		treeOpen = false;
	});

	async function openTree() {
		treeOpen = true;
		await tick();
		// The tree's own tab stop (the open folder), else the heading's link.
		const tree = document.getElementById('folders-tree');
		(tree?.querySelector<HTMLElement>('[tabindex="0"]') ?? tree?.querySelector('a'))?.focus();
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && treeOpen && narrow.current) {
			event.preventDefault();
			treeOpen = false;
		}
	}
</script>

<svelte:window {onkeydown} />

<div
	class="folders"
	class:narrow={narrow.current}
	class:bleed={chrome.bleed}
	class:immersive={chrome.immersive}
	style:--tree-width="{treeWidth}px"
>
	{#if narrow.current && treeOpen}
		<button
			type="button"
			class="scrim"
			tabindex="-1"
			aria-label={m.folders_tree_close()}
			onclick={() => (treeOpen = false)}
		></button>
	{/if}
	<aside id="folders-tree" class="pane" class:open={treeOpen} aria-labelledby="folders-tree-title">
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
			{#if narrow.current}
				<button
					type="button"
					class="icon-btn"
					aria-label={m.folders_tree_close()}
					title={m.folders_tree_close()}
					onclick={() => (treeOpen = false)}
				>
					<Icon name="close" size={20} />
				</button>
			{/if}
		</div>
		{#if treeQuery.isPending}
			<div class="loading"><Skeleton variant="text" count={4} /></div>
		{:else if treeQuery.isError}
			<p class="note" role="alert">{m.error_loading()}</p>
		{:else}
			<FolderTree
				{groups}
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

	{#if narrow.current}
		<div class="tree-bar">
			<button
				type="button"
				class="btn sm"
				aria-expanded={treeOpen}
				aria-controls="folders-tree"
				onclick={openTree}
			>
				<Icon name="folder" size={18} />
				{m.folders_tree()}
			</button>
		</div>
	{:else}
		<PaneResizer
			bind:width={treeWidth}
			min={TREE.min}
			max={TREE.max}
			initial={TREE.initial}
			storageKey={TREE.key}
			controls="folders-tree"
			quiet
		/>
	{/if}

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

	/* Beside the page, the tree is a card of its own under the chrome: a
	   folder's photos pass beneath the chrome, the tree never does, and the
	   chrome's capsules float over one clear band across both. */
	.folders:not(.narrow) .pane {
		margin: var(--bleed-top, 0px) 0 var(--float-margin);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-xl);
		background: var(--color-surface-raised);
	}

	.pane-head {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		padding-left: var(--space-1);
	}

	.pane-head h2 {
		margin-right: auto;
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

	/* Phone: one column, the tree slides over it as a drawer. */
	.folders.narrow {
		position: relative;
		grid-template-columns: minmax(0, 1fr);
		grid-template-rows: auto minmax(0, 1fr);
	}

	.narrow .pane {
		position: absolute;
		inset: 0 auto 0 0;
		z-index: 20;
		width: min(320px, 85%);
		background: var(--color-bg);
		box-shadow: var(--shadow-raised);
	}

	.narrow .pane:not(.open) {
		display: none;
	}

	.scrim {
		position: absolute;
		inset: 0;
		z-index: 19;
		padding: 0;
		border: 0;
		background: var(--color-scrim-soft);
	}

	.tree-bar {
		padding: var(--space-2) var(--page-gutter) 0;
	}

	.tree-bar .btn {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
	}

	/* A phone with a folder open: the tree's button floats under the chrome
	   (and goes with it while scrolling down), the photos pass beneath. */
	.narrow.bleed {
		grid-template-rows: minmax(0, 1fr);
	}

	.narrow.bleed .tree-bar {
		position: absolute;
		top: var(--bleed-top);
		left: 0;
		z-index: 5;
		transition:
			translate var(--duration-emphasis) var(--ease-standard),
			opacity var(--duration-emphasis) var(--ease-standard);
	}

	.narrow.bleed.immersive .tree-bar {
		translate: 0 calc(-1 * var(--bleed-top) - 100%);
		opacity: 0;
		pointer-events: none;
	}

	.narrow.bleed .tree-bar .btn {
		box-shadow: var(--shadow-bar);
	}

	.narrow.bleed .main {
		--bleed-top: calc(var(--chrome-top) + var(--control-h-sm) + var(--space-2));
	}
</style>
