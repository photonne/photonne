<script lang="ts">
	import { onDestroy, untrack } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import type { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import FolderPickerDialog from '#lib/actions/FolderPickerDialog.svelte';
	import {
		deleteFolder,
		getFolderAssets,
		removeFolderAssets,
		setFolderDiscoveryVisibility
	} from '#lib/api/index.js';
	import {
		getFolderByIdOptions,
		getFolderTreeOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import { invalidateFolders, toggleFolderPin } from './cache.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import FolderCards from './FolderCards.svelte';
	import FolderFormDialog from './FolderFormDialog.svelte';
	import { moveAssets, onAssetsMoved } from './folder-moves.js';
	import { pathTo, sortTree } from './folder-tree.js';
	import { icons } from './icons.js';
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import ShareDialog from './ShareDialog.svelte';

	let { folderId }: { folderId: string } = $props();

	const queryClient = useQueryClient();
	const folderQuery = createQuery(() => getFolderByIdOptions({ path: { folderId } }));
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const folder = $derived(folderQuery.data);
	const tree = $derived(sortTree(treeQuery.data ?? [], getLocale()));
	const ancestors = $derived(pathTo(tree, folderId).slice(0, -1));
	const subfolders = $derived(sortTree(folder?.subFolders ?? [], getLocale()));

	// The folder endpoint isn't sorted by capture date; the grid groups by month.
	const list = new PagedList(async () => {
		const { data } = await getFolderAssets({ path: { folderId: untrack(() => folderId) } });
		if (!data) return undefined;
		const items = [...data].sort(
			(a, b) => Date.parse(b.fileCreatedAt) - Date.parse(a.fileCreatedAt)
		);
		return { items, hasMore: false };
	});
	list.start();

	onDestroy(
		onAssetsMoved((event) => {
			if (event.from === folderId) list.store.remove(event.assetIds);
			else if (event.to === folderId) list.store.reload();
			invalidateFolders(queryClient, folderId);
		})
	);

	let editing = $state(false);
	let creatingSub = $state(false);
	let sharing = $state(false);
	let confirmingDelete = $state(false);
	/** The photos the folder picker is open for. */
	let moving = $state<string[] | null>(null);
	let busy = $state(false);

	const canWrite = $derived(folder?.canWrite ?? false);
	const isLibrary = $derived(!!folder?.externalLibraryId);

	async function toggleDiscovery() {
		if (!folder) return;
		const included = folder.excludedFromDiscovery;
		const { error } = await setFolderDiscoveryVisibility({
			path: { folderId },
			body: { included }
		});
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		invalidateFolders(queryClient, folderId);
		toasts.show(
			included
				? m.folders_discovery_shown({ name: folder.name })
				: m.folders_discovery_hidden({ name: folder.name })
		);
	}

	const menu = $derived.by(() => {
		if (!folder) return [];
		const items: MenuEntry[] = [];
		if (canWrite && !isLibrary)
			items.push({ label: m.folders_edit(), icon: 'edit', run: () => (editing = true) });
		if (folder.isOwner)
			items.push({
				label: m.albums_share_people(),
				icon: { path: icons.personAdd },
				run: () => (sharing = true)
			});
		if (folder.isShared)
			items.push({
				label: folder.excludedFromDiscovery
					? m.folders_discovery_show()
					: m.folders_discovery_hide(),
				icon: { path: folder.excludedFromDiscovery ? icons.visibility : icons.visibilityOff },
				run: toggleDiscovery
			});
		if (folder.canDelete)
			items.push({
				label: m.folders_delete(),
				icon: 'delete',
				danger: true,
				run: () => (confirmingDelete = true)
			});
		return items;
	});

	async function removeFromFolder(selection: Selection) {
		const assetIds = [...selection.ids];
		if (!assetIds.length || busy) return;
		busy = true;
		const { error } = await removeFolderAssets({ body: { folderId, assetIds } });
		busy = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		list.store.remove(assetIds);
		invalidateFolders(queryClient, folderId);
		toasts.show(m.folders_removed({ count: assetIds.length }));
	}

	async function remove() {
		if (!folder) return;
		busy = true;
		const { error } = await deleteFolder({ path: { folderId } });
		busy = false;
		confirmingDelete = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		invalidateFolders(queryClient);
		toasts.show(m.folders_deleted({ name: folder.name }));
		const parent = folder.parentFolderId;
		await goto(appHref(parent ? `/folders/${parent}` : '/folders'), { replace: true });
	}

	function moveSelection(target: { id: string; name: string }, byYear: boolean) {
		const assetIds = moving;
		moving = null;
		if (!folder || !assetIds?.length) return;
		moveAssets(queryClient, {
			assetIds,
			from: { id: folder.id, name: folder.name },
			to: target,
			byYear
		});
	}
</script>

<svelte:head>
	<title>{folder?.name ?? m.nav_folders()} · {m.app_name()}</title>
</svelte:head>

{#if folderQuery.isError}
	<div class="missing">
		<p role="alert">{m.folders_not_found()}</p>
		<a class="btn" href={appHref('/folders')}>{m.folders_back()}</a>
	</div>
{:else}
	<CollectionView
		store={list.store}
		title={folder?.name ?? ''}
		status={folderQuery.isPending ? 'pending' : list.status}
		emptyText={subfolders.length ? m.folders_empty_with_subfolders() : m.folders_empty()}
		viewerActions={['trash', 'album']}
	>
		{#snippet toolbar()}
			{#if folder}
				{#if canWrite && !isLibrary}
					<button
						type="button"
						class="btn sm tool"
						title={m.folders_new_sub()}
						onclick={() => (creatingSub = true)}
					>
						<Icon path={icons.newFolder} size={18} /><span class="label">{m.folders_new_sub()}</span
						>
					</button>
				{/if}
				{#if folder.isOwner}
					<button
						type="button"
						class="btn sm tool"
						title={m.albums_share()}
						onclick={() => (sharing = true)}
					>
						<Icon name="share" size={18} /><span class="label">{m.albums_share()}</span>
					</button>
				{/if}
				<button
					type="button"
					class="icon-btn"
					aria-pressed={folder.isPinned}
					aria-label={folder.isPinned ? m.albums_unpin() : m.albums_pin()}
					title={folder.isPinned ? m.albums_unpin() : m.albums_pin()}
					onclick={() => toggleFolderPin(queryClient, folder)}
				>
					<Icon name="pin" size={18} />
				</button>
				{#if menu.length}<PopupMenu label={m.albums_more()} items={menu} />{/if}
			{/if}
		{/snippet}

		{#snippet header()}
			{#if folder}
				<div class="about">
					{#if ancestors.length}
						<nav aria-label={m.folders_breadcrumb()}>
							<ol class="crumbs">
								<li><a href={appHref('/folders')}>{m.nav_folders()}</a></li>
								{#each ancestors as ancestor (ancestor.id)}
									<li><a href={appHref(`/folders/${ancestor.id}`)}>{ancestor.name}</a></li>
								{/each}
							</ol>
						</nav>
					{/if}
					<p class="meta">
						<span>{m.albums_items({ count: folder.assetCount })}</span>
						{#if isLibrary}
							<span class="pill"><Icon path={icons.library} size={14} />{m.folders_library()}</span>
						{/if}
						{#if folder.isShared}
							<span class="pill"
								><Icon path={icons.folderShared} size={14} />{m.folders_shared_space_badge()}</span
							>
						{:else if folder.sharedWithCount > 0}
							<span class="pill"
								><Icon name="people" size={14} />{m.albums_shared_with({
									count: folder.sharedWithCount
								})}</span
							>
						{/if}
						{#if folder.excludedFromDiscovery}
							<span class="pill" title={m.folders_discovery_hidden_hint()}>
								<Icon path={icons.visibilityOff} size={14} />{m.folders_discovery_hidden_badge()}
							</span>
						{/if}
					</p>
					{#if subfolders.length}
						<div class="subfolders">
							<FolderCards
								folders={subfolders}
								label={m.folders_subfolders()}
								ondropassets={(target, assetIds) =>
									moveAssets(queryClient, {
										assetIds,
										from: { id: folder.id, name: folder.name },
										to: target
									})}
							/>
						</div>
					{/if}
				</div>
			{/if}
		{/snippet}

		{#snippet selectionActions(selection: Selection, batch: BatchActions)}
			{#if canWrite && !isLibrary}
				<button
					type="button"
					class="icon-btn"
					title={m.folders_move()}
					aria-label={m.folders_move()}
					onclick={() => (moving = [...selection.ids])}
				>
					<Icon path={icons.moveToFolder} />
				</button>
				<button
					type="button"
					class="icon-btn"
					title={m.folders_remove()}
					aria-label={m.folders_remove()}
					disabled={busy}
					onclick={() => removeFromFolder(selection)}
				>
					<Icon path={icons.remove} />
				</button>
			{/if}
			<BatchActionBar
				actions={batch}
				{selection}
				available={['favorite', 'album', 'share', 'download', 'trash']}
			/>
		{/snippet}
	</CollectionView>
{/if}

<FolderPickerDialog open={moving !== null} onclose={() => (moving = null)} onpick={moveSelection} />

{#if (editing || creatingSub) && folder}
	<FolderFormDialog
		folder={editing ? folder : undefined}
		parentId={folderId}
		{tree}
		isAdmin={session.isAdmin}
		onclose={() => {
			editing = false;
			creatingSub = false;
		}}
		onsaved={(saved) => {
			const created = creatingSub;
			editing = false;
			creatingSub = false;
			invalidateFolders(queryClient, folderId);
			toasts.show(created ? m.folders_created({ name: saved.name }) : m.folders_saved());
			if (created) goto(appHref(`/folders/${saved.id}`));
		}}
	/>
{/if}

{#if sharing && folder}
	<ShareDialog
		kind="folder"
		targetId={folderId}
		name={folder.name}
		canManagePeople
		canLink={false}
		onclose={() => (sharing = false)}
		onchanged={() => invalidateFolders(queryClient, folderId)}
	/>
{/if}

<ConfirmDialog
	danger
	open={confirmingDelete}
	title={m.folders_delete_title()}
	message={m.folders_delete_message({ name: folder?.name ?? '', count: folder?.assetCount ?? 0 })}
	confirmLabel={m.folders_delete()}
	{busy}
	onconfirm={remove}
	onclose={() => (confirmingDelete = false)}
/>

<style>
	/* Narrow windows keep the icon; the text stays as the accessible name. */
	@media (max-width: 1180px) {
		.tool .label {
			position: absolute;
			width: 1px;
			height: 1px;
			overflow: hidden;
			clip: rect(0 0 0 0);
			white-space: nowrap;
		}

		/* Icon only, it looks like its icon-only neighbours. */
		.tool:has(.label) {
			width: var(--control-h);
			min-height: var(--control-h);
			padding: 0;
			border-color: transparent;
			border-radius: 50%;
			background: transparent;
		}

		.tool:has(.label):hover {
			background: var(--color-hover);
		}
	}

	.about {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-4) var(--space-2);
	}

	.crumbs {
		display: flex;
		flex-wrap: wrap;
		margin: 0;
		padding: 0;
		list-style: none;
		font-size: var(--font-size-sm);
	}

	.crumbs li + li::before {
		content: '›';
		padding: 0 var(--space-2);
		color: var(--color-text-muted);
	}

	.crumbs a {
		color: var(--color-text-muted);
		text-decoration: none;
	}

	.crumbs a:hover {
		color: var(--color-text);
		text-decoration: underline;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.pill {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 24px;
		padding: 0 var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		color: var(--color-text);
		font-size: var(--font-size-xs);
		font-weight: 600;
	}

	.subfolders {
		max-height: 30vh;
		overflow-y: auto;
		padding: 2px;
	}

	.missing {
		padding: var(--space-6) var(--page-gutter);
		display: grid;
		gap: var(--space-3);
		justify-items: start;
	}

	.missing p {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
