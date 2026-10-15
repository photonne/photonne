<script lang="ts">
	import { onDestroy, untrack } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import type { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import FolderPickerDialog from '#lib/actions/FolderPickerDialog.svelte';
	import {
		apiErrorCode,
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
	import FolderFormDialog from './FolderFormDialog.svelte';
	import FolderList from './FolderList.svelte';
	import { moveAssets, onAssetsMoved } from './folder-moves.js';
	import { browsableRoots, groupFolders } from './folder-groups.js';
	import { pathTo, sortTree } from './folder-tree.js';
	import { folderView } from './folder-view.svelte.js';
	import { icons } from './icons.js';
	import ListViewToggle from './ListViewToggle.svelte';
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import ShareDialog from './ShareDialog.svelte';

	let { folderId }: { folderId: string } = $props();

	const queryClient = useQueryClient();
	const folderQuery = createQuery(() => getFolderByIdOptions({ path: { folderId } }));
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const folder = $derived(folderQuery.data);
	const tree = $derived(
		browsableRoots(groupFolders(treeQuery.data ?? [], session.user?.username ?? '', getLocale()))
	);
	const ancestors = $derived(pathTo(tree, folderId).slice(0, -1));
	const subfolders = $derived(sortTree(folder?.subFolders ?? [], getLocale()));
	const subfoldersId = $props.id();

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
			// A photo lives in one folder: moved anywhere else, it left this one.
			if (event.to === folderId || !event.exact) list.store.reload();
			else list.store.remove(event.assetIds);
			invalidateFolders(queryClient, folderId);
		})
	);

	let editing = $state(false);
	let creatingSub = $state(false);
	let sharing = $state(false);
	let confirmingDelete = $state(false);
	/** The selection waiting for "remove from folder" to be confirmed. */
	let removing = $state<Selection | null>(null);
	/** The photos the folder picker is open for. */
	let moving = $state<string[] | null>(null);
	let busy = $state(false);

	const canWrite = $derived(folder?.canWrite ?? false);
	const isLibrary = $derived(!!folder?.externalLibraryId);

	// A folder with subfolders and photos shows them as two sections, each
	// of which folds away (folderView remembers) so the other has the page.
	// With only one of them there is nothing to fold: what it has fills the page.
	const hasPhotos = $derived(list.status !== 'ready' || list.store.items.length > 0);
	const twoSections = $derived(subfolders.length > 0 && hasPhotos);
	const photosShown = $derived(!twoSections || folderView.sections.photos);
	const subfoldersShown = $derived(folderView.sections.subfolders);

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

	// The server only unlinks the photos (the files stay where they are) and
	// has no way to link them back, so there's no Undo: it asks first.
	async function removeFromFolder() {
		const assetIds = [...(removing?.ids ?? [])];
		if (!assetIds.length || busy) return;
		busy = true;
		const { error } = await removeFolderAssets({ body: { folderId, assetIds } });
		busy = false;
		removing = null;
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
	<!-- Gone is final; anything else (offline, a server error) may pass, so it gets a retry. -->
	{@const missing = apiErrorCode(folderQuery.error) === 'folder_not_found'}
	<div class="missing">
		<p role="alert">{missing ? m.folders_not_found() : m.folders_load_failed()}</p>
		<div class="missing-actions">
			{#if !missing}
				<button type="button" class="btn primary" onclick={() => folderQuery.refetch()}>
					{m.folders_retry()}
				</button>
			{/if}
			<a class="btn" href={appHref('/folders')}>{m.folders_back()}</a>
		</div>
	</div>
{:else}
	<CollectionView
		store={list.store}
		title={folder?.name ?? ''}
		status={folderQuery.isPending ? 'pending' : list.status}
		emptyText={subfolders.length ? m.folders_empty_with_subfolders() : m.folders_empty()}
		viewerActions={['trash', 'album']}
		collapsed={!photosShown}
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
				<div class="about" class:fill={!photosShown}>
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
						<section class="section" class:fill={!photosShown}>
							<div class="section-head">
								<h2>
									<button
										type="button"
										aria-expanded={subfoldersShown}
										aria-controls={subfoldersId}
										onclick={() => folderView.toggle('subfolders')}
									>
										<span class="chevron" class:open={subfoldersShown} aria-hidden="true">
											<Icon name="chevronRight" size={18} />
										</span>
										{m.folders_subfolders()}
										<span class="count">{subfolders.length}</span>
									</button>
								</h2>
								<!-- The same tiles or rows as the folders index. -->
								{#if subfoldersShown}<ListViewToggle bind:view={folderView.value} />{/if}
							</div>
							<div id={subfoldersId} class="section-body" hidden={!subfoldersShown}>
								<FolderList
									folders={subfolders}
									label={m.folders_subfolders()}
									view={folderView.value}
									ondropassets={(target, assetIds) =>
										moveAssets(queryClient, {
											assetIds,
											from: { id: folder.id, name: folder.name },
											to: target
										})}
								/>
							</div>
						</section>
						{#if twoSections}
							<div class="section-head">
								<h2>
									<button
										type="button"
										aria-expanded={photosShown}
										onclick={() => folderView.toggle('photos')}
									>
										<span class="chevron" class:open={photosShown} aria-hidden="true">
											<Icon name="chevronRight" size={18} />
										</span>
										{m.folders_photos()}
										{#if list.status === 'ready'}
											<span class="count">{list.store.items.length}</span>
										{/if}
									</button>
								</h2>
							</div>
						{/if}
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
					onclick={() => (removing = selection)}
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

<ConfirmDialog
	danger
	open={removing !== null}
	title={m.folders_remove_title()}
	message={m.folders_remove_message({ name: folder?.name ?? '', count: removing?.size ?? 0 })}
	confirmLabel={m.folders_remove_confirm()}
	{busy}
	onconfirm={removeFromFolder}
	onclose={() => (removing = null)}
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
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-4) var(--space-2);
	}

	/* The photos folded away: the subfolders get the page's height and
	   scroll on their own. */
	.about.fill {
		flex: 1;
		min-height: 0;
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

	.section {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		min-height: 0;
	}

	.section.fill {
		flex: 1;
	}

	.section-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.section-head h2 {
		flex: 1;
		min-width: 0;
		margin: 0;
		font-size: var(--font-size-md);
		font-weight: 600;
		line-height: 1.3;
	}

	/* The heading folds its section: the chevron turns, the name stays. */
	.section-head button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		max-width: 100%;
		margin-left: calc(-1 * var(--space-1));
		padding: 2px var(--space-2) 2px var(--space-1);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: inherit;
		font: inherit;
		font-weight: 600;
		text-align: left;
		cursor: pointer;
	}

	.section-head button:hover {
		background: var(--color-hover);
	}

	.chevron {
		display: grid;
		transition: transform var(--duration-fast);
	}

	.chevron.open {
		transform: rotate(90deg);
	}

	.count {
		margin-left: var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 500;
		color: var(--color-text-muted);
	}

	/* With the photos under it the list keeps to a band (at least a row of
	   tiles) and scrolls inside; a hovered tile lifts a little, so the band
	   leaves it room at the top. */
	.section-body {
		min-height: 0;
		max-height: max(30vh, 320px);
		overflow-y: auto;
		padding: var(--space-1) 2px 2px;
	}

	.section-body[hidden] {
		display: none;
	}

	.fill .section-body {
		flex: 1;
		max-height: none;
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

	.missing-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}
</style>
