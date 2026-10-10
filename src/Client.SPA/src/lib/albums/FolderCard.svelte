<script lang="ts">
	import type { FolderResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { ListView } from './album-list.js';
	import { cardClick } from './bulk.js';
	import { icons } from './icons.js';

	interface Props {
		folder: FolderResponse;
		view?: ListView;
		selected?: boolean;
		/** Some folder is selected: a plain click selects instead of opening. */
		selecting?: boolean;
		/** Makes the card selectable (Ctrl/Shift click, the check on hover). */
		onselect?: (mode: 'toggle' | 'range') => void;
		/** Where the folder is, for search results from any depth ("Camera / 2025"). */
		where?: string;
		/** Shows the pin button, as on album cards. */
		ontogglepin?: (folder: FolderResponse) => void;
	}

	let {
		folder,
		view = 'grid',
		selected = false,
		selecting = false,
		onselect,
		where,
		ontogglepin
	}: Props = $props();

	const src = $derived.by(() => {
		const id = folder.previewAssetIds[0] ?? folder.firstAssetId;
		return id ? thumbnailUrl(id, view === 'grid' ? 'Medium' : 'Small') : null;
	});

	function onclick(event: MouseEvent) {
		if (!onselect) return;
		const action = cardClick(event, selecting);
		if (action === 'open') return;
		event.preventDefault();
		onselect(action);
	}
</script>

<li class="card {view}" class:selected class:selecting class:pinnable={ontogglepin}>
	<a href={appHref(`/folders/${folder.id}`)} aria-current={selected ? 'true' : undefined} {onclick}>
		<span class="thumb" class:tile={!src}>
			{#if src}
				<img {src} alt="" loading="lazy" decoding="async" />
			{:else}
				<Icon
					path={folder.isShared ? icons.folderShared : undefined}
					name={folder.isShared ? undefined : 'folder'}
					size={view === 'grid' ? 44 : 22}
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
					<Icon path={icons.folderShared} size={14} /><span class:visually-hidden={view === 'grid'}
						>{m.folders_shared_space_badge()}</span
					>
				</span>
			{/if}
		</span>
	</a>
	{#if onselect}
		<button
			type="button"
			class="check"
			aria-pressed={selected}
			aria-label={m.folders_select_named({ name: folder.name })}
			onclick={(event) => onselect(event.shiftKey ? 'range' : 'toggle')}
		>
			<Icon name="check" size={16} />
		</button>
	{/if}
	{#if ontogglepin}
		<button
			type="button"
			class="pin"
			class:on={folder.isPinned}
			aria-pressed={folder.isPinned}
			aria-label={folder.isPinned
				? m.albums_unpin_named({ name: folder.name })
				: m.albums_pin_named({ name: folder.name })}
			title={folder.isPinned ? m.albums_unpin() : m.albums_pin()}
			onclick={() => ontogglepin(folder)}
		>
			<Icon name="pin" size={16} />
		</button>
	{/if}
</li>

<style>
	.card {
		position: relative;
		min-width: 0;
	}

	a {
		display: grid;
		gap: 2px;
		color: inherit;
		text-decoration: none;
		border-radius: var(--radius-lg);
	}

	a:focus-visible {
		outline: none;
	}

	.thumb {
		display: grid;
		place-items: center;
		overflow: hidden;
		background: var(--color-placeholder);
		color: var(--color-text-muted);
		transition:
			transform var(--duration-normal) ease,
			box-shadow var(--duration-normal) ease;
	}

	/* No photo to show: the folder's mark on a tinted tile. */
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
		gap: 2px;
		min-width: 0;
	}

	.name {
		font-size: var(--font-size-md);
		font-weight: 600;
		line-height: 1.3;
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
		gap: var(--space-1);
		min-height: 24px;
		padding: 0 var(--space-2);
		border-radius: 999px;
		font-size: var(--font-size-xs);
		font-weight: 600;
		white-space: nowrap;
	}

	.check,
	.pin {
		position: absolute;
		display: grid;
		place-items: center;
		padding: 0;
		border-radius: 50%;
		cursor: pointer;
		opacity: 0;
		transition:
			opacity var(--duration-fast),
			transform var(--duration-normal) ease;
	}

	.check {
		width: 26px;
		height: 26px;
		border: 2px solid #fff;
		background: rgb(0 0 0 / 0.25);
		color: transparent;
	}

	.pin {
		top: var(--space-2);
		right: var(--space-2);
		width: var(--control-h-sm);
		height: var(--control-h-sm);
		border: 0;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
	}

	.pin.on {
		opacity: 1;
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.card:hover .check,
	.card:hover .pin,
	.check:focus-visible,
	.pin:focus-visible,
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
		border-radius: var(--radius-lg);
	}

	.grid a:hover .thumb {
		transform: translateY(-3px);
		box-shadow: var(--shadow-raised);
	}

	.grid:has(a:hover) .check,
	.grid:has(a:hover) .pin,
	.grid:has(a:hover) .badges {
		transform: translateY(-3px);
	}

	.grid a:focus-visible .thumb {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	.grid .text {
		padding: 0 2px;
	}

	.grid .badges {
		position: absolute;
		top: var(--space-2);
		right: var(--space-2);
		transition: transform var(--duration-normal) ease;
	}

	/* The pin takes the corner; the badge sits beside it. */
	.grid.pinnable .badges {
		right: calc(var(--space-2) + var(--control-h-sm) + var(--space-1));
	}

	.grid .badge {
		width: 28px;
		min-height: 28px;
		justify-content: center;
		padding: 0;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
		backdrop-filter: blur(6px);
	}

	.grid .check {
		top: var(--space-2);
		left: var(--space-2);
	}

	.grid.selected .thumb,
	.grid.selected a:hover .thumb {
		transform: scale(0.92);
		box-shadow: none;
		outline: 3px solid var(--color-accent);
		outline-offset: 2px;
	}

	/* --- List: compact rows ------------------------------------------- */

	.list a {
		grid-template-columns: 40px minmax(0, 1fr) auto;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3) var(--space-2) calc(var(--space-3) + 34px);
		border-radius: var(--radius-md);
	}

	.list.pinnable a {
		padding-right: calc(var(--space-2) + var(--control-h-sm) + var(--space-2));
	}

	.list a:hover,
	.list.selected a {
		background: var(--color-hover);
	}

	.list a:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.list.selected a {
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
		border-color: var(--color-border-strong);
		background: transparent;
	}

	.list.selected .check {
		border-color: var(--color-accent);
		background: var(--color-accent);
	}

	.list .pin {
		top: 50%;
		transform: translateY(-50%);
		background: transparent;
		color: var(--color-text-muted);
	}

	.list .pin:hover {
		background: var(--color-hover);
	}

	.list .pin.on {
		background: transparent;
		color: var(--color-accent);
	}

	@media (hover: none) {
		.check,
		.pin {
			opacity: 1;
		}
	}
</style>
