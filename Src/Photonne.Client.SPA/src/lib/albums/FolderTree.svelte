<script lang="ts">
	import { tick } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { isAssetDrag } from '#lib/actions/drag-assets.js';
	import type { FolderResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { draggedAssetIds, FOLDER_MIME, isFolderDrag } from './dragged.js';
	import { canDropFolder, pathTo, treeKey, visibleRows } from './folder-tree.js';
	import { icons } from './icons.js';

	interface Props {
		tree: readonly FolderResponse[];
		/** The open folder: selected, and its ancestors expanded. */
		currentId: string | null;
		label: string;
		onopen: (folder: FolderResponse) => void;
		ondropassets: (folder: FolderResponse, assetIds: string[]) => void;
		ondropfolder: (folder: FolderResponse, parent: FolderResponse) => void;
	}

	let { tree, currentId, label, onopen, ondropassets, ondropfolder }: Props = $props();

	const expanded = new SvelteSet<string>();
	let focusId = $state<string | null>(null);
	let dropTarget = $state<string | null>(null);
	let draggedFolder = $state<FolderResponse | null>(null);
	let list = $state<HTMLUListElement>();
	let hoverTimer: ReturnType<typeof setTimeout> | undefined;

	// Opening a folder (from a card, a link…) reveals it in the tree.
	$effect(() => {
		if (!currentId) return;
		for (const ancestor of pathTo(tree, currentId).slice(0, -1)) expanded.add(ancestor.id);
		focusId = currentId;
	});

	const rows = $derived(visibleRows(tree, expanded));
	// One row is in the tab order: the focused one, else the open one, else the first.
	const tabStop = $derived(
		rows.find((r) => r.folder.id === focusId)?.folder.id ??
			rows.find((r) => r.folder.id === currentId)?.folder.id ??
			rows[0]?.folder.id
	);

	async function focusRow(id: string) {
		focusId = id;
		await tick();
		list?.querySelector<HTMLElement>(`[data-folder="${id}"]`)?.focus();
	}

	function toggle(id: string) {
		if (expanded.has(id)) expanded.delete(id);
		else expanded.add(id);
	}

	function onkeydown(event: KeyboardEvent, folder: FolderResponse) {
		if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			onopen(folder);
			return;
		}
		if (event.key === '*') {
			// Expand all siblings (treeview convention).
			const row = rows.find((r) => r.folder.id === folder.id);
			for (const sibling of rows.filter((r) => r.parentId === row?.parentId && r.hasChildren))
				expanded.add(sibling.folder.id);
			return;
		}
		const move = treeKey(rows, folder.id, event.key);
		if (!move) return;
		event.preventDefault();
		if (move.kind === 'expand') expanded.add(move.id);
		else if (move.kind === 'collapse') expanded.delete(move.id);
		else focusRow(move.id);
	}

	// --- Drag and drop: photos onto a folder, a folder onto another -------

	function accepts(event: DragEvent, folder: FolderResponse) {
		if (!folder.canWrite) return false;
		if (isAssetDrag(event)) return true;
		return (
			isFolderDrag(event) && !!draggedFolder && canDropFolder(tree, draggedFolder.id, folder.id)
		);
	}

	function ondragover(event: DragEvent, folder: FolderResponse) {
		if (!accepts(event, folder)) return;
		event.preventDefault();
		if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
		if (dropTarget !== folder.id) {
			dropTarget = folder.id;
			// Hovering a closed folder for a moment opens it, to reach deeper ones.
			clearTimeout(hoverTimer);
			hoverTimer = setTimeout(() => {
				if (dropTarget === folder.id) expanded.add(folder.id);
			}, 700);
		}
	}

	function ondragleave(event: DragEvent, folder: FolderResponse) {
		const to = event.relatedTarget as Node | null;
		if (to && (event.currentTarget as HTMLElement).contains(to)) return;
		if (dropTarget === folder.id) dropTarget = null;
	}

	function ondrop(event: DragEvent, folder: FolderResponse) {
		event.preventDefault();
		dropTarget = null;
		clearTimeout(hoverTimer);
		if (isFolderDrag(event)) {
			if (draggedFolder && canDropFolder(tree, draggedFolder.id, folder.id))
				ondropfolder(draggedFolder, folder);
			draggedFolder = null;
			return;
		}
		const ids = draggedAssetIds(event.dataTransfer);
		if (ids.length) ondropassets(folder, ids);
	}

	function ondragstart(event: DragEvent, folder: FolderResponse) {
		if (!event.dataTransfer) return;
		draggedFolder = folder;
		event.dataTransfer.setData(FOLDER_MIME, folder.id);
		event.dataTransfer.effectAllowed = 'move';
	}

	function iconFor(folder: FolderResponse) {
		if (folder.externalLibraryId) return icons.library;
		if (folder.isShared || folder.sharedWithCount > 0) return icons.folderShared;
		return undefined;
	}
</script>

{#if rows.length === 0}
	<p class="empty">{m.folders_none()}</p>
{:else}
	<ul class="tree" role="tree" aria-label={label} bind:this={list}>
		{#each rows as row (row.folder.id)}
			{@const folder = row.folder}
			<li
				role="treeitem"
				data-folder={folder.id}
				aria-level={row.depth + 1}
				aria-expanded={row.hasChildren ? row.expanded : undefined}
				aria-selected={folder.id === currentId}
				aria-label={m.folders_tree_item({ name: folder.name, count: folder.assetCount })}
				tabindex={folder.id === tabStop ? 0 : -1}
				class:current={folder.id === currentId}
				class:drop={dropTarget === folder.id}
				style:--depth={row.depth}
				draggable={folder.canWrite && !folder.externalLibraryId}
				onclick={() => onopen(folder)}
				onkeydown={(event) => onkeydown(event, folder)}
				onfocus={() => (focusId = folder.id)}
				ondragstart={(event) => ondragstart(event, folder)}
				ondragend={() => (draggedFolder = null)}
				ondragover={(event) => ondragover(event, folder)}
				ondragleave={(event) => ondragleave(event, folder)}
				ondrop={(event) => ondrop(event, folder)}
			>
				{#if row.hasChildren}
					<!-- Mouse shortcut for the arrows; keyboard users have Left/Right. -->
					<span
						class="twisty"
						class:open={row.expanded}
						role="presentation"
						onclick={(event) => {
							event.stopPropagation();
							toggle(folder.id);
						}}
					>
						<Icon path={icons.expandMore} size={18} />
					</span>
				{:else}
					<span class="twisty"></span>
				{/if}
				<Icon path={iconFor(folder)} name={iconFor(folder) ? undefined : 'folder'} size={18} />
				<span class="name">{folder.name}</span>
				{#if folder.isPinned}<span class="pinned"><Icon name="pin" size={12} /></span>{/if}
				<span class="count" aria-hidden="true">{folder.assetCount}</span>
			</li>
		{/each}
	</ul>
{/if}

<style>
	.tree {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		gap: 1px;
	}

	[role='treeitem'] {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		height: 32px;
		padding: 0 var(--space-2) 0 calc(var(--space-1) + var(--depth) * 16px);
		border-radius: var(--radius-sm);
		cursor: pointer;
		user-select: none;
	}

	[role='treeitem']:hover {
		background: var(--color-surface);
	}

	[role='treeitem']:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.current {
		background: color-mix(in srgb, var(--color-accent) 14%, transparent);
		color: var(--color-accent);
		font-weight: 600;
	}

	.current:hover {
		background: color-mix(in srgb, var(--color-accent) 20%, transparent);
	}

	.drop {
		outline: 2px dashed var(--color-accent);
		outline-offset: -2px;
		background: color-mix(in srgb, var(--color-accent) 15%, transparent);
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
		background: var(--color-border);
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.pinned {
		color: var(--color-text-muted);
	}

	.count {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		font-weight: 400;
		font-variant-numeric: tabular-nums;
	}

	.empty {
		margin: 0;
		padding: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
