<script lang="ts">
	import type { AlbumResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { isSmart } from './album-list.js';
	import { icons } from './icons.js';

	interface Props {
		album: AlbumResponse;
		ontogglepin: (album: AlbumResponse) => void;
	}

	let { album, ontogglepin }: Props = $props();

	const smart = $derived(isSmart(album));
	const sharedLabel = $derived(
		!album.isOwner
			? m.albums_shared_with_you()
			: album.sharedWithCount > 0
				? m.albums_shared_with({ count: album.sharedWithCount })
				: null
	);
</script>

<li class="card">
	<a href={appHref(`/albums/${album.id}`)} class="link">
		<div class="cover">
			{#if album.coverThumbnailUrl}
				<img src={album.coverThumbnailUrl} alt="" loading="lazy" decoding="async" />
			{:else}
				<Icon path={smart ? icons.smart : undefined} name={smart ? undefined : 'album'} size={40} />
			{/if}
		</div>
		<span class="name">{album.name}</span>
		<span class="meta">
			{m.albums_items({ count: album.assetCount })}
			{#if sharedLabel}· {sharedLabel}{/if}
		</span>
		<span class="badges">
			{#if smart}
				<span class="badge" title={m.albums_smart_hint()}>
					<Icon path={icons.smart} size={14} />{m.albums_smart()}
				</span>
			{/if}
			{#if album.hasActiveShareLink}
				<span class="badge" title={m.albums_link_active()}>
					<Icon name="link" size={14} /><span class="visually-hidden">{m.albums_link_active()}</span
					>
				</span>
			{/if}
		</span>
	</a>
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
		<Icon name="pin" size={18} />
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
		border-radius: var(--radius-md);
	}

	.cover {
		display: grid;
		place-items: center;
		aspect-ratio: 1;
		margin-bottom: var(--space-2);
		overflow: hidden;
		border-radius: var(--radius-md);
		background: var(--color-placeholder);
		color: var(--color-text-muted);
	}

	.cover img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--duration-normal) ease;
	}

	.link:hover img {
		transform: scale(1.03);
	}

	.name {
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.meta {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.badges {
		position: absolute;
		left: var(--space-2);
		top: var(--space-2);
		display: flex;
		gap: var(--space-1);
	}

	.badge {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px var(--space-2);
		border-radius: 999px;
		background: rgb(0 0 0 / 0.55);
		color: #fff;
		font-size: var(--font-size-xs);
		font-weight: 600;
	}

	.pin {
		position: absolute;
		right: var(--space-2);
		top: var(--space-2);
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.55);
		color: #fff;
		cursor: pointer;
		opacity: 0;
		transition: opacity var(--duration-fast);
	}

	.pin.on {
		opacity: 1;
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.card:hover .pin,
	.pin:focus-visible {
		opacity: 1;
	}

	@media (hover: none) {
		.pin {
			opacity: 1;
		}
	}
</style>
