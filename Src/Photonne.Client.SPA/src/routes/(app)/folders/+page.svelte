<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { deleteFolder, updateFolder, type FolderResponse } from '#lib/api/index.js';
	import {
		getAllAlbumsOptions,
		getAllFoldersOptions,
		getFolderTreeOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { runBulk } from '#lib/albums/bulk.js';
	import { invalidateFolders } from '#lib/albums/cache.js';
	import {
		arrangeFolders,
		folderScopeCounts,
		folderSelectionActions,
		parseFolderListOptions,
		topmostFolders,
		type FolderScope,
		type FolderSort
	} from '#lib/albums/folder-list.js';
	import FolderList from '#lib/albums/FolderList.svelte';
	import FolderMoveDialog from '#lib/albums/FolderMoveDialog.svelte';
	import { browsableRoots, groupFolders } from '#lib/albums/folder-groups.js';
	import { pathTo } from '#lib/albums/folder-tree.js';
	import { icons } from '#lib/albums/icons.js';
	import ListViewToggle from '#lib/albums/ListViewToggle.svelte';
	import { mergePinned } from '#lib/albums/pinned.js';
	import PinnedSection from '#lib/albums/PinnedSection.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { session } from '#lib/auth/session.svelte.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';

	const STORAGE_KEY = 'photonne.folders.list';

	const queryClient = useQueryClient();
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const username = $derived(session.user?.username ?? '');
	const roots = $derived(browsableRoots(groupFolders(treeQuery.data ?? [], username, getLocale())));
	// What the sidebar pins, from any depth, with the pinned albums.
	const albums = createQuery(() => getAllAlbumsOptions());
	const allFolders = createQuery(() => getAllFoldersOptions());

	let options = $state(parseFolderListOptions(readSaved()));

	function readSaved() {
		try {
			return localStorage.getItem(STORAGE_KEY);
		} catch {
			return null;
		}
	}

	$effect(() => {
		const { sort, descending, view } = options;
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify({ sort, descending, view }));
		} catch {
			// Storage may be unavailable (private mode); the defaults are fine.
		}
	});

	const searching = $derived(options.query.trim() !== '');
	const shown = $derived(arrangeFolders(roots, options, getLocale(), username));
	const counts = $derived(folderScopeCounts(roots, username));
	const filtered = $derived(searching || options.scope !== 'all');
	// Hidden while filtering, like on the albums page.
	const pinned = $derived(filtered ? [] : mergePinned(albums.data ?? [], allFolders.data ?? []));

	/** "Camera / 2025" for a search hit below the top level. */
	function location(folder: FolderResponse) {
		if (!searching) return '';
		return pathTo(roots, folder.id)
			.slice(0, -1)
			.map((ancestor) => ancestor.name)
			.join(' / ');
	}

	const scopes: { key: FolderScope; label: () => string }[] = [
		{ key: 'all', label: m.folders_scope_all },
		{ key: 'personal', label: m.folders_scope_personal },
		{ key: 'shared', label: m.folders_scope_shared },
		{ key: 'external', label: m.folders_scope_external }
	];
	const sorts: { key: FolderSort; label: () => string }[] = [
		{ key: 'name', label: m.folders_sort_name },
		{ key: 'count', label: m.folders_sort_count }
	];

	// --- Selection and bulk actions ---------------------------------------

	const selection = new Selection();
	const selected = $derived(shown.filter((folder) => selection.has(folder.id)));
	const targets = $derived(topmostFolders(selected));
	const allowed = $derived(folderSelectionActions(selected));
	let confirmingDelete = $state(false);
	let moving = $state(false);
	let busy = $state(false);

	// What a filter hides leaves the selection: actions apply to what is seen.
	$effect(() => {
		const visible = shown.map((folder) => folder.id);
		const hidden = [...selection.ids].filter((id) => !visible.includes(id));
		if (hidden.length) selection.set(hidden, false);
	});

	function onkeydown(event: KeyboardEvent) {
		if (confirmingDelete || moving || event.defaultPrevented) return;
		const target = event.target as HTMLElement;
		if (target.closest('input, textarea, select, dialog, [role="dialog"]')) return;
		if (event.key === 'Escape' && selection.active) {
			selection.clear();
		} else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
			selection.set(
				shown.map((folder) => folder.id),
				true
			);
		} else if (event.key === 'Delete' && selection.active && allowed.canDelete) {
			confirmingDelete = true;
		} else return;
		event.preventDefault();
	}

	async function run(
		action: (folder: FolderResponse) => Promise<boolean>,
		done: (count: number) => string
	) {
		const batch = targets;
		const byId = new Map(batch.map((folder) => [folder.id, folder]));
		busy = true;
		const outcome = await runBulk(
			batch.map((folder) => folder.id),
			(id) => action(byId.get(id)!)
		);
		busy = false;
		// Nested folders went with their parent; only failures stay selected.
		selection.clear();
		selection.set(outcome.failed, true);
		invalidateFolders(queryClient);
		if (outcome.failed.length)
			toasts.error(m.folders_bulk_partial({ done: outcome.succeeded.length, total: batch.length }));
		else toasts.show(done(outcome.succeeded.length));
	}

	function clearFilters() {
		options.query = '';
		options.scope = 'all';
	}

	async function removeSelected() {
		await run(
			async (folder) => !(await deleteFolder({ path: { folderId: folder.id } })).error,
			(count) => m.folders_bulk_deleted({ count })
		);
		confirmingDelete = false;
	}

	async function moveSelected(parent: { id: string; name: string } | null) {
		await run(
			async (folder) =>
				!(
					await updateFolder({
						path: { folderId: folder.id },
						body: { name: folder.name, parentFolderId: parent?.id ?? null }
					})
				).error,
			(count) => m.folders_bulk_moved({ count, parent: parent?.name ?? m.folders_location_root() })
		);
		moving = false;
	}
</script>

<svelte:head>
	<title>{m.nav_folders()} · {m.app_name()}</title>
</svelte:head>

<svelte:window {onkeydown} />

<div class="page">
	<div class="dock">
		<SelectionBar {selection}>
			{#snippet actions()}
				<button
					type="button"
					class="btn"
					disabled={!allowed.canMove || busy}
					title={allowed.canMove ? undefined : m.folders_bulk_cannot_move()}
					onclick={() => (moving = true)}
				>
					<Icon path={icons.moveToFolder} size={18} />{m.folders_bulk_move()}
				</button>
				<button
					type="button"
					class="btn danger"
					disabled={!allowed.canDelete || busy}
					title={allowed.canDelete ? undefined : m.folders_bulk_cannot_delete()}
					onclick={() => (confirmingDelete = true)}
				>
					<Icon name="delete" size={18} />{m.folders_bulk_delete()}
				</button>
			{/snippet}
		</SelectionBar>
	</div>

	{#snippet toolbar()}
		<div class="filters" role="group" aria-label={m.folders_filters()}>
			<div class="segmented" role="radiogroup" aria-label={m.folders_scope()}>
				{#each scopes as scope (scope.key)}
					<label>
						<input type="radio" name="scope" value={scope.key} bind:group={options.scope} />
						{scope.label()} <span class="count">{counts[scope.key]}</span>
					</label>
				{/each}
			</div>

			<div class="sort">
				<label>
					<span class="visually-hidden">{m.folders_sort()}</span>
					<select bind:value={options.sort}>
						{#each sorts as sort (sort.key)}<option value={sort.key}>{sort.label()}</option>{/each}
					</select>
				</label>
				<button
					type="button"
					class="icon-btn"
					aria-label={options.descending ? m.folders_sort_descending() : m.folders_sort_ascending()}
					title={options.descending ? m.folders_sort_descending() : m.folders_sort_ascending()}
					onclick={() => (options.descending = !options.descending)}
				>
					<Icon path={options.descending ? icons.arrowDown : icons.arrowUp} size={18} />
				</button>
			</div>

			<span class="view"><ListViewToggle bind:view={options.view} /></span>
		</div>
	{/snippet}

	<!-- The filters wait for the folders: counts of 0 while loading read as "none". -->
	<PageHeader
		title={m.nav_folders()}
		subtitle={roots.length && !searching ? m.folders_intro() : null}
		toolbar={roots.length ? toolbar : undefined}
	>
		{#snippet actions()}
			{#if roots.length}
				<div class="search" role="search" aria-label={m.folders_filters()}>
					<Icon name="search" size={18} />
					<input
						type="search"
						aria-label={m.folders_search()}
						placeholder={m.folders_search()}
						bind:value={options.query}
					/>
				</div>
			{/if}
		{/snippet}
	</PageHeader>

	{#if treeQuery.isPending}
		<Skeleton variant="cards" count={10} />
	{:else if treeQuery.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if roots.length === 0}
		<EmptyState icon="folder" title={m.folders_none()} hint={m.folders_none_hint()}>
			{#snippet action()}
				<a class="btn primary" href={appHref('/upload')}>
					<Icon name="upload" size={18} />{m.folders_upload()}
				</a>
			{/snippet}
		</EmptyState>
	{:else if shown.length === 0}
		<EmptyState compact icon="search" title={filtered ? m.folders_no_match() : m.folders_none()}>
			{#snippet action()}
				{#if filtered}
					<button type="button" class="btn" onclick={clearFilters}
						>{m.albums_clear_filters()}</button
					>
				{/if}
			{/snippet}
		</EmptyState>
	{:else}
		<div class="content">
			{#if pinned.length}
				<PinnedSection entries={pinned} view={options.view} />
				<h2>{m.folders_roots()}</h2>
			{/if}
			<FolderList
				folders={shown}
				label={searching ? m.folders_results() : m.folders_roots()}
				view={options.view}
				{selection}
				{location}
			/>
		</div>
	{/if}
</div>

<ConfirmDialog
	open={confirmingDelete}
	title={m.folders_bulk_delete_title({ count: targets.length })}
	confirmLabel={m.folders_bulk_delete()}
	danger
	{busy}
	onconfirm={removeSelected}
	onclose={() => (confirmingDelete = false)}
>
	<p>{m.folders_bulk_delete_message({ count: targets.length })}</p>
	{#if targets.length < selected.length}<p>{m.folders_bulk_nested()}</p>{/if}
</ConfirmDialog>

{#if moving}
	<FolderMoveDialog
		folders={targets}
		tree={roots}
		{busy}
		onclose={() => (moving = false)}
		onmove={moveSelected}
	/>
{/if}

<style>
	.page {
		display: grid;
		align-content: start;
		padding-bottom: var(--space-8);
	}

	.dock {
		position: sticky;
		top: 0;
		z-index: 3;
		height: 0;
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
		width: 100%;
	}

	.search {
		position: relative;
		display: flex;
		align-items: center;
		color: var(--color-text-muted);
	}

	.search :global(svg) {
		position: absolute;
		left: var(--space-3);
		pointer-events: none;
	}

	.search input {
		width: 240px;
		padding-left: calc(var(--space-3) + 26px);
	}

	.count {
		color: var(--color-text-muted);
		font-weight: 400;
		font-variant-numeric: tabular-nums;
	}

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}

	.view {
		margin-left: auto;
	}

	.content {
		padding: var(--space-2) var(--page-gutter) 0;
	}

	h2 {
		margin: var(--space-6) 0 var(--space-3);
		font-size: var(--font-size-lg);
		font-weight: 600;
		line-height: 1.3;
	}

	.status {
		margin: 0;
		padding: var(--space-6) var(--page-gutter);
		color: var(--color-text-muted);
	}
</style>
