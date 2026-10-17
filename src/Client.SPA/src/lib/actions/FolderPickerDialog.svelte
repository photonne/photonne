<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { tick } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { apiErrorCode, createFolder, type FolderResponse } from '#lib/api/index.js';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { invalidateFolders } from '#lib/albums/cache.js';
	import { groupFolders, type FolderGroups } from '#lib/albums/folder-groups.js';
	import { pathTo, treeKey, visibleRows } from '#lib/albums/folder-tree.js';
	import { icons } from '#lib/albums/icons.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	interface Props {
		open: boolean;
		onclose: () => void;
		onpick: (folder: { id: string; name: string }, organizeByCaptureYear: boolean) => void;
	}

	let { open, onclose, onpick }: Props = $props();

	/** A heading over one group's folders: it folds, but isn't a destination. */
	interface GroupNode {
		id: string;
		name: string;
		parentFolderId: null;
		subFolders: FolderResponse[];
		group: true;
	}
	type TreeNode = FolderResponse | GroupNode;
	const isGroup = (node: TreeNode): node is GroupNode => 'group' in node;

	const queryClient = useQueryClient();
	const tree = createQuery(() => ({ ...getFolderTreeOptions(), enabled: open }));

	/** The destination; a folder just created here may not be in the tree yet. */
	let chosen = $state<{ id: string; name: string; external: boolean } | null>(null);
	let byYear = $state(false);
	let newName = $state('');
	let creating = $state(false);
	let createError = $state<string | null>(null);
	let focusId = $state<string | null>(null);
	let list = $state<HTMLUListElement>();

	const GROUPS = [
		{ key: 'personal', label: m.folders_group_personal },
		{ key: 'shared', label: m.folders_group_shared },
		{ key: 'external', label: m.folders_group_external }
	] as const;
	// Only the groups open: a long tree starts folded to its top level.
	const expanded = new SvelteSet<string>();

	// Each opening starts afresh: no folder, option or half-typed name left from the last move.
	$effect(() => {
		if (!open) return;
		chosen = null;
		byYear = false;
		newName = '';
		createError = null;
		focusId = null;
		expanded.clear();
		for (const { key } of GROUPS) expanded.add(`group:${key}`);
	});

	/** Keeps the folders one can move into, and the ancestors that lead to them. */
	function writableOnly(nodes: readonly FolderResponse[]): FolderResponse[] {
		return nodes.flatMap((folder) => {
			const subFolders = writableOnly(folder.subFolders ?? []);
			return folder.canWrite || subFolders.length ? [{ ...folder, subFolders }] : [];
		});
	}

	const groups = $derived.by<FolderGroups>(() => {
		const all = groupFolders(tree.data ?? [], session.user?.username ?? '', getLocale());
		return {
			personal: writableOnly(all.personal),
			shared: writableOnly(all.shared),
			external: writableOnly(all.external)
		};
	});
	const nodes = $derived<TreeNode[]>(
		GROUPS.filter(({ key }) => groups[key].length).map(({ key, label }) => ({
			id: `group:${key}`,
			name: label(),
			parentFolderId: null,
			subFolders: groups[key],
			group: true
		}))
	);
	const rows = $derived(visibleRows(nodes, expanded));
	const tabStop = $derived(
		rows.find((r) => r.folder.id === focusId)?.folder.id ??
			rows.find((r) => r.folder.id === chosen?.id)?.folder.id ??
			rows[0]?.folder.id
	);

	function toggle(id: string) {
		if (expanded.has(id)) expanded.delete(id);
		else expanded.add(id);
	}

	function choose(node: TreeNode) {
		if (isGroup(node) || !node.canWrite) {
			toggle(node.id);
			return;
		}
		chosen = { id: node.id, name: node.name, external: !!node.externalLibraryId };
		createError = null;
	}

	async function focusRow(id: string) {
		focusId = id;
		await tick();
		list?.querySelector<HTMLElement>(`[data-folder="${id}"]`)?.focus();
	}

	function onkeydown(event: KeyboardEvent, node: TreeNode) {
		if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			choose(node);
			return;
		}
		const move = treeKey(rows, node.id, event.key);
		if (!move) return;
		event.preventDefault();
		if (move.kind === 'expand') expanded.add(move.id);
		else if (move.kind === 'collapse') expanded.delete(move.id);
		else focusRow(move.id);
	}

	function iconFor(folder: FolderResponse) {
		if (folder.externalLibraryId) return icons.library;
		if (folder.isShared || folder.sharedWithCount > 0) return icons.folderShared;
		return undefined;
	}

	// Libraries mirror a disk the server only reads: no new folders inside.
	const canCreateHere = $derived(!chosen?.external);
	const canCreate = $derived(
		!!newName.trim() && !/[\\/]/.test(newName) && !creating && canCreateHere
	);

	/** Creates the folder inside the chosen one (or at the top of the user's) and picks it. */
	async function create(event: SubmitEvent) {
		event.preventDefault();
		if (!canCreate) return;
		const name = newName.trim();
		const parent = chosen;
		creating = true;
		createError = null;
		const { data, error } = await createFolder({
			body: { name, parentFolderId: parent?.id ?? null }
		});
		creating = false;
		if (!data) {
			createError =
				apiErrorCode(error) === 'folder_already_exists'
					? m.folders_error_exists()
					: m.action_failed();
			return;
		}
		invalidateFolders(queryClient, parent?.id);
		// Its branch opens, so the new folder shows selected once the tree reloads.
		if (parent) for (const ancestor of pathTo(nodes, parent.id)) expanded.add(ancestor.id);
		else expanded.add('group:personal');
		chosen = { id: data.id, name: data.name, external: false };
		newName = '';
	}
</script>

<Dialog {open} title={m.folder_picker_title()} {onclose} width="520px">
	{#if tree.isPending}
		<Skeleton variant="rows" count={6} flush />
	{:else if tree.isError}
		<p class="status" role="alert">
			{m.error_loading()}
			<button type="button" class="btn sm" onclick={() => tree.refetch()}
				>{m.session_retry()}</button
			>
		</p>
	{:else if rows.length === 0}
		<p class="status">{m.folder_picker_empty()}</p>
	{:else}
		<ul class="tree" role="tree" aria-label={m.folder_picker_title()} bind:this={list}>
			{#each rows as row (row.folder.id)}
				{@const node = row.folder}
				{#if isGroup(node)}
					<li
						role="treeitem"
						class="group"
						data-folder={node.id}
						aria-level={row.depth + 1}
						aria-expanded={row.expanded}
						aria-selected="false"
						tabindex={node.id === tabStop ? 0 : -1}
						style:--depth={row.depth}
						onclick={() => toggle(node.id)}
						onkeydown={(event) => onkeydown(event, node)}
						onfocus={() => (focusId = node.id)}
					>
						<span class="twisty" class:open={row.expanded} role="presentation">
							<Icon path={icons.expandMore} size={18} />
						</span>
						<span class="name">{node.name}</span>
					</li>
				{:else}
					<li
						role="treeitem"
						data-folder={node.id}
						aria-level={row.depth + 1}
						aria-expanded={row.hasChildren ? row.expanded : undefined}
						aria-selected={node.id === chosen?.id}
						aria-disabled={!node.canWrite || undefined}
						tabindex={node.id === tabStop ? 0 : -1}
						class:current={node.id === chosen?.id}
						class:locked={!node.canWrite}
						style:--depth={row.depth}
						onclick={() => choose(node)}
						onkeydown={(event) => onkeydown(event, node)}
						onfocus={() => (focusId = node.id)}
					>
						{#if row.hasChildren}
							<!-- Mouse shortcut for the arrows; keyboard users have Left/Right. -->
							<span
								class="twisty"
								class:open={row.expanded}
								role="presentation"
								onclick={(event) => {
									event.stopPropagation();
									toggle(node.id);
								}}
							>
								<Icon path={icons.expandMore} size={18} />
							</span>
						{:else}
							<span class="twisty"></span>
						{/if}
						<Icon path={iconFor(node)} name={iconFor(node) ? undefined : 'folder'} size={18} />
						<span class="name">{node.name}</span>
					</li>
				{/if}
			{/each}
		</ul>
	{/if}

	{#if !tree.isPending && !tree.isError}
		<form class="new" onsubmit={create}>
			<span class="where">
				{chosen ? m.folder_picker_new_in({ folder: chosen.name }) : m.folder_picker_new_in_root()}
			</span>
			<div class="row">
				<input
					aria-label={m.folder_picker_new_name()}
					placeholder={m.folder_picker_new_name()}
					maxlength="255"
					disabled={!canCreateHere}
					bind:value={newName}
				/>
				<button type="submit" class="btn" disabled={!canCreate}>
					<Icon path={icons.newFolder} size={18} />
					{m.folders_create()}
				</button>
			</div>
			{#if !canCreateHere}
				<p class="hint">{m.folder_picker_new_external()}</p>
			{:else if /[\\/]/.test(newName)}
				<p class="error" role="alert">{m.folders_error_slash()}</p>
			{:else if createError}
				<p class="error" role="alert">{createError}</p>
			{/if}
		</form>
	{/if}

	<label class="by-year">
		<input type="checkbox" bind:checked={byYear} />
		{m.folder_picker_by_year()}
	</label>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
		<button
			type="button"
			class="primary"
			disabled={!chosen || creating}
			onclick={() => chosen && onpick({ id: chosen.id, name: chosen.name }, byYear)}
		>
			{m.folder_picker_move()}
		</button>
	{/snippet}
</Dialog>

<style>
	.tree {
		margin: 0 0 var(--space-3);
		padding: 0;
		list-style: none;
		max-height: 360px;
		overflow-y: auto;
		display: grid;
		gap: 1px;
	}

	[role='treeitem'] {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		height: 34px;
		padding: 0 var(--space-2) 0 calc(var(--space-1) + var(--depth) * 18px);
		border-radius: var(--radius-sm);
		cursor: pointer;
		user-select: none;
	}

	[role='treeitem']:hover {
		background: var(--color-hover);
	}

	[role='treeitem']:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.group {
		margin-top: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		font-weight: 600;
		letter-spacing: 0.04em;
		text-transform: uppercase;
	}

	.group:first-child {
		margin-top: 0;
	}

	.current,
	.current:hover {
		background: color-mix(in srgb, var(--color-accent) 18%, transparent);
		font-weight: 600;
	}

	.current :global(svg) {
		color: var(--color-accent);
	}

	/* Only on the way to folders one can move into. */
	.locked {
		color: var(--color-text-muted);
	}

	.twisty {
		flex: none;
		display: grid;
		place-items: center;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		color: var(--color-text-muted);
		transform: rotate(-90deg);
		transition: transform var(--duration-fast);
	}

	.twisty.open {
		transform: none;
	}

	.twisty:hover {
		background: var(--color-hover);
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.new {
		display: grid;
		gap: var(--space-1);
		margin: 0 0 var(--space-3);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.where {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.row {
		display: flex;
		gap: var(--space-2);
	}

	.row input {
		flex: 1;
		min-width: 0;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.row .btn {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.hint,
	.error {
		margin: 0;
		font-size: var(--font-size-xs);
	}

	.hint {
		color: var(--color-text-muted);
	}

	.error {
		color: var(--color-danger);
	}

	.by-year {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.status {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
		color: var(--color-text-muted);
	}
</style>
