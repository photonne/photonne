<script lang="ts">
	import { thumbnailSizeFor, thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import Icon from '#lib/components/Icon.svelte';
	import { icons } from './icons.js';
	import { displayLabel } from './labels.js';

	interface Props {
		href: string;
		label: string;
		count: number;
		coverAssetId: string | null;
		/** The landscape card of a row of Explorar (Recuerdos' shape); square in a grid. */
		wide?: boolean;
	}

	let { href, label, count, coverAssetId, wide = false }: Props = $props();

	const cover = $derived(
		coverAssetId ? thumbnailUrl(coverAssetId, thumbnailSizeFor(wide ? 480 : 180), null) : null
	);
</script>

<a class="tile" class:wide {href}>
	{#if cover}
		<img src={cover} alt="" loading="lazy" decoding="async" />
	{:else}
		<span class="mark" aria-hidden="true"><Icon path={icons.label} size={28} /></span>
	{/if}
	<span class="text">
		<span class="label">{displayLabel(label, getLocale())}</span>
		<span class="count">{m.memories_photo_count({ count })}</span>
	</span>
</a>

<style>
	.tile {
		position: relative;
		display: block;
		aspect-ratio: 1;
		overflow: hidden;
		border-radius: var(--radius-md);
		background: var(--color-placeholder);
		color: var(--color-on-media);
		text-decoration: none;
		isolation: isolate;
	}

	.tile.wide {
		aspect-ratio: 1 / 0.62;
	}

	.tile.wide .text {
		padding: var(--space-3) var(--space-4);
	}

	.tile.wide .label {
		font-size: var(--font-size-lg);
	}

	.tile.wide .count {
		font-size: var(--font-size-sm);
	}

	img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform 600ms ease;
	}

	.tile:hover img,
	.tile:focus-visible img {
		transform: scale(1.05);
	}

	.tile::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to top, var(--color-scrim), transparent 55%);
	}

	/* Without a cover: a tinted tile with the label's mark, dark text. */
	.tile:not(:has(img)) {
		background: var(--color-brand-tile);
		color: var(--color-text);
	}

	.mark {
		position: absolute;
		inset: 0 0 var(--space-8);
		display: grid;
		place-items: center;
		color: var(--color-accent);
	}

	.tile:not(:has(img))::after {
		display: none;
	}

	.text {
		position: absolute;
		inset: auto 0 0;
		z-index: 1;
		display: grid;
		padding: var(--space-2) var(--space-3);
	}

	.label {
		overflow: hidden;
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.count {
		font-size: var(--font-size-xs);
		opacity: 0.85;
	}

	.tile:focus-visible {
		outline-offset: 3px;
	}

	@media (prefers-reduced-motion: reduce) {
		img {
			transition: none;
		}
	}
</style>
