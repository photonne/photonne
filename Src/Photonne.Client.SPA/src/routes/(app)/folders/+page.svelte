<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { deleteFolder, updateFolder, type FolderResponse } from '#lib/api/index.js';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
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
	import { pathTo, sortTree } from '#lib/albums/folder-tree.js';
	import { icons } from '#lib/albums/icons.js';
	import ListViewToggle from '#lib/albums/ListViewToggle.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';

	const STORAGE_KEY = 'photonne.folders.list';

	const queryClient = useQueryClient();
	const treeQuery = createQuery(() => getFolderTreeOptions());
	const roots = $derived(sortTree(treeQuery.data ?? [], getLocale()));

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
	const shown = $derived(arrangeFolders(roots, options, getLocale()));
	const counts = $derived(folderScopeCounts(roots));
	const filtered = $derived(searching || options.scope !== 'all');

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
					class="bulk"
					disabled={!allowed.canMove || busy}
					title={allowed.canMove ? undefined : m.folders_bulk_cannot_move()}
					onclick={() => (moving = true)}
				>
					<Icon path={icons.moveToFolder} size={18} />{m.folders_bulk_move()}
				</button>
				<button
					type="button"
					class="bulk danger"
					disabled={!allowed.canDelete || busy}
					title={allowed.canDelete ? undefined : m.folders_bulk_cannot_delete()}
					onclick={() => (confirmingDelete = true)}
				>
					<Icon name="delete" size={18} />{m.folders_bulk_delete()}
				</button>
			{/snippet}
		</SelectionBar>
	</div>

	<h1>{m.nav_folders()}</h1>
	{#if treeQuery.isPending}
		<p class="note" role="status">{m.session_restoring()}</p>
	{:else if treeQuery.isError}
		<p class="note" role="alert">{m.error_loading()}</p>
	{:else if roots.length === 0}
		<div class="empty">
			<Icon name="folder" size={48} />
			<p class="title">{m.folders_none()}</p>
			<p>{m.folders_none_body()}</p>
		</div>
	{:else}
		<div class="filters" role="search" aria-label={m.folders_filters()}>
			<label class="search">
				<Icon name="search" size={18} />
				<span class="visually-hidden">{m.folders_search()}</span>
				<input type="search" placeholder={m.folders_search()} bind:value={options.query} />
			</label>

			<div class="segmented" role="radiogroup" aria-label={m.folders_scope()}>
				{#each scopes as scope (scope.key)}
					<label class:on={options.scope === scope.key}>
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
					class="icon"
					aria-label={options.descending ? m.folders_sort_descending() : m.folders_sort_ascending()}
					title={options.descending ? m.folders_sort_descending() : m.folders_sort_ascending()}
					onclick={() => (options.descending = !options.descending)}
				>
					<Icon path={options.descending ? icons.arrowDown : icons.arrowUp} size={18} />
				</button>
			</div>

			<ListViewToggle bind:view={options.view} />
		</div>

		{#if shown.length === 0}
			<p class="note">{filtered ? m.folders_no_match() : m.folders_none()}</p>
		{:else}
			{#if !searching}<p class="note">{m.folders_intro()}</p>{/if}
			<FolderList
				folders={shown}
				label={searching ? m.folders_results() : m.folders_roots()}
				view={options.view}
				{selection}
				{location}
			/>
		{/if}
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
		gap: var(--space-4);
		align-content: start;
		padding: var(--space-4);
	}

	.dock {
		position: sticky;
		top: 0;
		z-index: 3;
		height: 0;
		margin: calc(-1 * var(--space-4));
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.search {
		flex: 0 1 260px;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
	}

	.search input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	.segmented {
		display: inline-flex;
		padding: 2px;
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	.segmented label {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		height: 32px;
		padding: 0 var(--space-3);
		border-radius: calc(var(--radius-sm) - 2px);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.segmented label.on {
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		font-weight: 600;
	}

	.segmented label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
	}

	/* The radio covers its label: invisible, but it is what gets clicked. */
	.segmented input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		margin: 0;
		opacity: 0;
		cursor: pointer;
	}

	.count {
		color: var(--color-text-muted);
		font-weight: 400;
	}

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}

	select {
		height: 36px;
		padding: 0 var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
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

	.bulk {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-weight: 600;
		cursor: pointer;
	}

	.bulk:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.bulk.danger {
		border-color: var(--color-danger);
		color: var(--color-danger);
	}

	.bulk:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}

	.empty {
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-8) var(--space-4);
		color: var(--color-text-muted);
		text-align: center;
	}

	.empty p {
		margin: 0;
	}

	.empty .title {
		font-size: var(--font-size-lg);
		font-weight: 600;
		color: var(--color-text);
	}
</style>
