<script lang="ts">
	import type { AlbumResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { isSmart, type ListView } from './album-list.js';
	import { cardClick } from './bulk.js';
	import { icons } from './icons.js';

	interface Props {
		album: AlbumResponse;
		ontogglepin: (album: AlbumResponse) => void;
		view?: ListView;
		selected?: boolean;
		/** Some album is selected: a plain click selects instead of opening. */
		selecting?: boolean;
		/** Makes the card selectable (Ctrl/Shift click, the check on hover). */
		onselect?: (mode: 'toggle' | 'range') => void;
	}

	let {
		album,
		ontogglepin,
		view = 'grid',
		selected = false,
		selecting = false,
		onselect
	}: Props = $props();

	const smart = $derived(isSmart(album));
	const sharedLabel = $derived(
		!album.isOwner
			? m.albums_shared_with_you()
			: album.sharedWithCount > 0
				? m.albums_shared_with({ count: album.sharedWithCount })
				: null
	);
	const updated = $derived(
		new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium', timeZone: 'UTC' }).format(
			Date.parse(album.updatedAt)
		)
	);

	function onclick(event: MouseEvent) {
		if (!onselect) return;
		const action = cardClick(event, selecting);
		if (action === 'open') return;
		event.preventDefault();
		onselect(action);
	}
</script>

<li class="card {view}" class:selected class:selecting>
	<a
		href={appHref(`/albums/${album.id}`)}
		class="link"
		aria-current={selected ? 'true' : undefined}
		{onclick}
	>
		<div class="cover" class:empty={!album.coverThumbnailUrl}>
			{#if album.coverThumbnailUrl}
				<img src={album.coverThumbnailUrl} alt="" loading="lazy" decoding="async" />
			{:else}
				<Icon
					path={smart ? icons.smart : undefined}
					name={smart ? undefined : 'album'}
					size={view === 'list' ? 24 : 40}
				/>
			{/if}
			{#if view === 'grid'}
				<span class="pills">
					{#if smart}
						<span class="pill" title={m.albums_smart_hint()}>
							<Icon path={icons.smart} size={14} />{m.albums_smart()}
						</span>
					{/if}
					{#if sharedLabel}
						<span class="pill" title={sharedLabel}>
							<Icon name="people" size={14} /><span class="ellipsis">{sharedLabel}</span>
						</span>
					{/if}
					{#if album.hasActiveShareLink}
						<span class="pill" title={m.albums_link_active()}>
							<Icon name="link" size={14} /><span class="visually-hidden"
								>{m.albums_link_active()}</span
							>
						</span>
					{/if}
				</span>
			{/if}
		</div>
		<span class="text">
			<span class="name">{album.name}</span>
			<span class="meta">
				{m.albums_items({ count: album.assetCount })}
				{#if sharedLabel && view === 'list'}· {sharedLabel}{/if}
				{#if view === 'list' && smart}· {m.albums_smart()}{/if}
			</span>
		</span>
		{#if view === 'list'}
			<span class="updated" title={m.albums_sort_updated()}>{updated}</span>
		{/if}
	</a>
	{#if onselect}
		<button
			type="button"
			class="check"
			aria-pressed={selected}
			aria-label={m.albums_select_named({ name: album.name })}
			onclick={(event) => onselect(event.shiftKey ? 'range' : 'toggle')}
		>
			<Icon name="check" size={16} />
		</button>
	{/if}
	<button
		type="button"
		class="pin"
		class:on={album.isPinned}
		aria-pressed={album.isPinned}
		aria-label={album.isPinned
			? m.albums_unpin_named({ name: album.name })
			: m.albums_pin_named({ name: album.name })}
		title={album.isPinned ? m.albums_unpin() : m.albums_pin()}
		onclick={() => ontogglepin(album)}
	>
		<Icon name="pin" size={16} />
	</button>
</li>

<style>
	.card {
		position: relative;
		min-width: 0;
	}

	.link {
		display: grid;
		gap: 2px;
		color: inherit;
		text-decoration: none;
		border-radius: var(--radius-lg);
	}

	.link:focus-visible {
		outline: none;
	}

	.link:focus-visible .cover {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	.cover {
		position: relative;
		display: grid;
		place-items: center;
		aspect-ratio: 1;
		margin-bottom: var(--space-2);
		overflow: hidden;
		border-radius: var(--radius-lg);
		background: var(--color-placeholder);
		color: var(--color-text-muted);
		transition:
			transform var(--duration-normal) ease,
			box-shadow var(--duration-normal) ease;
	}

	/* No photo yet: the album's own mark on a tinted tile. */
	.cover.empty {
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.cover img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.grid .link:hover .cover {
		transform: translateY(-3px);
		box-shadow: var(--shadow-raised);
	}

	.text {
		display: grid;
		gap: 2px;
		min-width: 0;
		padding: 0 2px;
	}

	.name {
		font-size: var(--font-size-md);
		font-weight: 600;
		line-height: 1.3;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.meta,
	.updated {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	/* Pills on the photo: dark glass, legible on any cover. */
	.pills {
		position: absolute;
		left: var(--space-2);
		right: var(--space-2);
		bottom: var(--space-2);
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
	}

	.pill {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 24px;
		padding: 0 var(--space-2);
		border-radius: 999px;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
		font-size: var(--font-size-xs);
		font-weight: 600;
		backdrop-filter: blur(6px);
		max-width: 100%;
	}

	.ellipsis {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.pin,
	.check {
		position: absolute;
		top: var(--space-2);
		display: grid;
		place-items: center;
		width: var(--control-h-sm);
		height: var(--control-h-sm);
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
		cursor: pointer;
		opacity: 0;
		transition:
			opacity var(--duration-fast),
			transform var(--duration-normal) ease;
	}

	.pin {
		right: var(--space-2);
	}

	.check {
		left: var(--space-2);
		width: 26px;
		height: 26px;
		border: 2px solid #fff;
		background: rgb(0 0 0 / 0.25);
		color: transparent;
	}

	.pin.on {
		opacity: 1;
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	/* The overlay buttons ride along with the lifted cover. */
	.grid:has(.link:hover) .pin,
	.grid:has(.link:hover) .check {
		transform: translateY(-3px);
	}

	.card:hover .pin,
	.card:hover .check,
	.pin:focus-visible,
	.check:focus-visible,
	.selecting .check {
		opacity: 1;
	}

	.selected .check {
		opacity: 1;
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.grid.selected .cover,
	.grid.selected .link:hover .cover {
		transform: scale(0.92);
		box-shadow: none;
		outline: 3px solid var(--color-accent);
		outline-offset: 2px;
	}

	/* --- List rows ---------------------------------------------------- */

	.list .link {
		grid-template-columns: 48px minmax(0, 1fr) auto;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) calc(var(--space-2) + 72px) var(--space-2) calc(var(--space-2) + 36px);
		border-radius: var(--radius-md);
	}

	.list .link:hover,
	.list.selected .link {
		background: var(--color-hover);
	}

	.list .link:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.list .link:focus-visible .cover {
		outline: none;
	}

	.list.selected .link {
		box-shadow: inset 3px 0 0 var(--color-accent);
	}

	.list .cover {
		width: 48px;
		margin: 0;
		border-radius: var(--radius-sm);
	}

	.list .text {
		padding: 0;
	}

	.list .check {
		top: 50%;
		left: var(--space-2);
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
		.pin,
		.check {
			opacity: 1;
		}
	}
</style>
