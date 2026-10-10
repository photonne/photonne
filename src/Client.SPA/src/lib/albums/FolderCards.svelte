<script lang="ts">
	import { isAssetDrag } from '#lib/actions/drag-assets.js';
	import type { FolderResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { draggedAssetIds } from './dragged.js';
	import { icons } from './icons.js';

	interface Props {
		folders: readonly FolderResponse[];
		label: string;
		/** Photos dropped on a card move into that folder. */
		ondropassets?: (folder: FolderResponse, assetIds: string[]) => void;
	}

	let { folders, label, ondropassets }: Props = $props();

	let dropTarget = $state<string | null>(null);

	function cover(folder: FolderResponse) {
		const id = folder.previewAssetIds[0] ?? folder.firstAssetId;
		return id ? thumbnailUrl(id, 'Small') : null;
	}
</script>

<ul class="cards" aria-label={label}>
	{#each folders as folder (folder.id)}
		{@const src = cover(folder)}
		<li>
			<a
				href={appHref(`/folders/${folder.id}`)}
				class:drop={dropTarget === folder.id}
				title={ondropassets && folder.canWrite
					? m.drop_move_to_folder({ folder: folder.name })
					: undefined}
				ondragover={(event) => {
					if (!ondropassets || !folder.canWrite || !isAssetDrag(event)) return;
					event.preventDefault();
					dropTarget = folder.id;
				}}
				ondragleave={() => (dropTarget = null)}
				ondrop={(event) => {
					event.preventDefault();
					dropTarget = null;
					const ids = draggedAssetIds(event.dataTransfer);
					if (ids.length) ondropassets?.(folder, ids);
				}}
			>
				<span class="thumb" class:tile={!src}>
					{#if src}
						<img {src} alt="" loading="lazy" decoding="async" />
					{:else}
						<Icon
							path={folder.isShared ? icons.folderShared : undefined}
							name={folder.isShared ? undefined : 'folder'}
							size={28}
						/>
					{/if}
				</span>
				<span class="text">
					<span class="name">{folder.name}</span>
					<span class="count">{m.albums_items({ count: folder.assetCount })}</span>
				</span>
			</a>
		</li>
	{/each}
</ul>

<style>
	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		color: inherit;
		text-decoration: none;
	}

	a:hover {
		border-color: var(--color-border-strong);
		background: var(--color-hover);
	}

	a.drop {
		outline: 2px dashed var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 15%, transparent);
	}

	.thumb {
		flex: none;
		display: grid;
		place-items: center;
		width: 48px;
		height: 48px;
		overflow: hidden;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
		color: var(--color-text-muted);
	}

	.thumb.tile {
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.thumb img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.text {
		display: grid;
		min-width: 0;
	}

	.name {
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.count {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}
</style>
