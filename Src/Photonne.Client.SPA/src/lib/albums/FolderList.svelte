<script lang="ts">
	import type { FolderResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import type { ListView } from './album-list.js';
	import { cardClick } from './bulk.js';
	import { icons } from './icons.js';

	interface Props {
		folders: readonly FolderResponse[];
		label: string;
		view: ListView;
		selection: Selection;
		/** Where a folder is, for search results from any depth ("Camera / 2025"). */
		location?: (folder: FolderResponse) => string;
	}

	let { folders, label, view, selection, location }: Props = $props();

	const order = $derived(folders.map((folder) => folder.id));

	function cover(folder: FolderResponse) {
		const id = folder.previewAssetIds[0] ?? folder.firstAssetId;
		return id ? thumbnailUrl(id, view === 'grid' ? 'Medium' : 'Small') : null;
	}

	function select(folder: FolderResponse, mode: 'toggle' | 'range') {
		if (mode === 'range') selection.selectRange(folder.id, order);
		else selection.toggle(folder.id);
	}

	function onclick(event: MouseEvent, folder: FolderResponse) {
		const action = cardClick(event, selection.active);
		if (action === 'open') return;
		event.preventDefault();
		select(folder, action);
	}
</script>

<ul class={view} aria-label={label}>
	{#each folders as folder (folder.id)}
		{@const src = cover(folder)}
		{@const selected = selection.has(folder.id)}
		{@const where = location?.(folder)}
		<li class:selected class:selecting={selection.active}>
			<a
				href={appHref(`/folders/${folder.id}`)}
				aria-current={selected ? 'true' : undefined}
				onclick={(event) => onclick(event, folder)}
			>
				<span class="thumb">
					{#if src}
						<img {src} alt="" loading="lazy" decoding="async" />
					{:else}
						<Icon
							path={folder.isShared ? icons.folderShared : undefined}
							name={folder.isShared ? undefined : 'folder'}
							size={view === 'grid' ? 40 : 24}
						/>
					{/if}
				</span>
				<span class="text">
					<span class="name">{folder.name}</span>
					<span class="meta">
						{m.albums_items({ count: folder.assetCount })}
						{#if where}· {where}{/if}
					</span>
				</span>
				<span class="badges">
					{#if folder.externalLibraryId}
						<span class="badge" title={m.folders_library()}>
							<Icon path={icons.library} size={14} /><span class:visually-hidden={view === 'grid'}
								>{m.folders_library()}</span
							>
						</span>
					{:else if folder.isShared}
						<span class="badge" title={m.folders_shared_space_badge()}>
							<Icon path={icons.folderShared} size={14} /><span
								class:visually-hidden={view === 'grid'}>{m.folders_shared_space_badge()}</span
							>
						</span>
					{/if}
				</span>
			</a>
			<button
				type="button"
				class="check"
				aria-pressed={selected}
				aria-label={m.folders_select_named({ name: folder.name })}
				onclick={(event) => select(folder, event.shiftKey ? 'range' : 'toggle')}
			>
				<Icon name="check" size={16} />
			</button>
		</li>
	{/each}
</ul>

<style>
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	ul.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
		gap: var(--space-5, 20px) var(--space-4);
	}

	ul.list {
		display: grid;
		gap: 2px;
	}

	li {
		position: relative;
		min-width: 0;
	}

	a {
		display: grid;
		gap: 2px;
		color: inherit;
		text-decoration: none;
		border-radius: var(--radius-md);
	}

	.thumb {
		display: grid;
		place-items: center;
		overflow: hidden;
		background: var(--color-placeholder);
		color: var(--color-text-muted);
		transition: transform var(--duration-fast);
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

	.meta {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.badge {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px var(--space-2);
		border-radius: 999px;
		font-size: var(--font-size-xs);
		font-weight: 600;
	}

	.check {
		position: absolute;
		display: grid;
		place-items: center;
		width: 26px;
		height: 26px;
		padding: 0;
		border: 2px solid #fff;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.25);
		color: transparent;
		cursor: pointer;
		opacity: 0;
		transition: opacity var(--duration-fast);
	}

	li:hover .check,
	.check:focus-visible,
	.selecting .check,
	.selected .check {
		opacity: 1;
	}

	.selected .check {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	/* --- Grid: square covers ------------------------------------------ */

	.grid .thumb {
		aspect-ratio: 1;
		margin-bottom: var(--space-2);
		border-radius: var(--radius-md);
	}

	.grid a:hover img {
		transform: scale(1.03);
	}

	.grid img {
		transition: transform var(--duration-normal) ease;
	}

	.grid .badges {
		position: absolute;
		right: var(--space-2);
		top: var(--space-2);
	}

	.grid .badge {
		background: rgb(0 0 0 / 0.55);
		color: #fff;
	}

	.grid .check {
		top: var(--space-2);
		left: var(--space-2);
	}

	.grid .selected .thumb {
		transform: scale(0.92);
		outline: 3px solid var(--color-accent);
		outline-offset: 2px;
	}

	/* --- List: compact rows ------------------------------------------- */

	.list a {
		grid-template-columns: 40px minmax(0, 1fr) auto;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3) var(--space-2) calc(var(--space-3) + 34px);
	}

	.list a:hover,
	.list .selected a {
		background: var(--color-surface);
	}

	.list .selected a {
		box-shadow: inset 3px 0 0 var(--color-accent);
	}

	.list .thumb {
		width: 40px;
		height: 40px;
		border-radius: var(--radius-sm);
	}

	.list .badge {
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.list .check {
		top: 50%;
		left: var(--space-3);
		transform: translateY(-50%);
		border-color: var(--color-text-muted);
		background: transparent;
	}

	.list .selected .check {
		border-color: var(--color-accent);
		background: var(--color-accent);
	}

	@media (hover: none) {
		.check {
			opacity: 1;
		}
	}
</style>
