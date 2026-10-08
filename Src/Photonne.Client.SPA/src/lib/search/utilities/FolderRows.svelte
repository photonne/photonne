<script lang="ts">
	import type { SvelteSet } from 'svelte/reactivity';
	import type { FolderResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { expandMorePath, folderOpenPath } from '../icons.js';
	import FolderRows from './FolderRows.svelte';
	import { ownAssets } from './folder-tree.js';

	interface Props {
		folders: readonly FolderResponse[];
		expanded: SvelteSet<string>;
		/** While filtering every branch shows open. */
		forceOpen?: boolean;
		depth?: number;
	}

	let { folders, expanded, forceOpen = false, depth = 0 }: Props = $props();
</script>

<ul class:root={depth === 0}>
	{#each folders as folder (folder.id)}
		{@const children = folder.subFolders ?? []}
		{@const open = children.length > 0 && (forceOpen || expanded.has(folder.id))}
		<li>
			<div class="row" style:padding-left="calc({depth} * 1.5rem + var(--space-2))">
				{#if children.length}
					<button
						type="button"
						class="toggle"
						class:open
						aria-expanded={open}
						aria-label={m.utilities_toggle_folder({ folder: folder.name })}
						disabled={forceOpen}
						onclick={() =>
							expanded.has(folder.id) ? expanded.delete(folder.id) : expanded.add(folder.id)}
					>
						<Icon path={expandMorePath} size={18} />
					</button>
				{:else}
					<span class="toggle" aria-hidden="true"></span>
				{/if}
				<span class="icon" aria-hidden="true">
					{#if open}<Icon path={folderOpenPath} size={20} />{:else}<Icon
							name="folder"
							size={20}
						/>{/if}
				</span>
				<span class="names">
					<a href={appHref(`/folders/${folder.id}`)}>{folder.name}</a>
					<span class="path">{folder.path}</span>
				</span>
				<span class="badges">
					{#if folder.isShared}<span class="badge">{m.utilities_badge_shared()}</span>{/if}
					{#if folder.externalLibraryId}<span class="badge">{m.utilities_badge_external()}</span
						>{/if}
					{#if folder.excludedFromDiscovery}<span class="badge">{m.utilities_badge_hidden()}</span
						>{/if}
				</span>
				<span class="counts">
					<span>{m.utilities_photos({ count: folder.assetCount })}</span>
					{#if children.length}
						<span class="muted">
							{m.utilities_subfolders({ count: children.length })} · {m.utilities_here({
								count: ownAssets(folder)
							})}
						</span>
					{/if}
				</span>
			</div>
			{#if open}
				<FolderRows folders={children} {expanded} {forceOpen} depth={depth + 1} />
			{/if}
		</li>
	{/each}
</ul>

<style>
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.root {
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		overflow: hidden;
	}

	.row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3) var(--space-2) var(--space-2);
		border-bottom: 1px solid var(--color-border);
	}

	.root > li:last-child > .row {
		border-bottom: 0;
	}

	.row:hover {
		background: var(--color-surface);
	}

	.toggle {
		display: grid;
		place-items: center;
		flex: none;
		width: 28px;
		height: 28px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	button.toggle :global(svg) {
		transform: rotate(-90deg);
		transition: transform var(--duration-fast);
	}

	button.toggle.open :global(svg) {
		transform: none;
	}

	button.toggle:hover:not(:disabled) {
		background: var(--color-surface-raised);
	}

	button.toggle:disabled {
		cursor: default;
	}

	.icon {
		display: grid;
		flex: none;
		color: #e0a526;
	}

	.names {
		display: grid;
		flex: 1;
		min-width: 0;
	}

	.names a {
		color: inherit;
		font-weight: 600;
		text-decoration: none;
	}

	.names a:hover {
		text-decoration: underline;
	}

	.path {
		overflow: hidden;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.badges {
		display: flex;
		gap: var(--space-1);
	}

	.badge {
		padding: 1px var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		white-space: nowrap;
	}

	.counts {
		display: grid;
		justify-items: end;
		flex: none;
		font-size: var(--font-size-sm);
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	.muted {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
