<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailSizeFor, thumbnailUrl } from '#lib/media.js';

	interface Props {
		href: string;
		title: string;
		/** Second line: the server's subtitle or a count. */
		subtitle?: string | null;
		coverAssetId: string | null;
		/** `large`: the landscape cards of Recuerdos; `small`: a theme's period in Explorar. */
		size?: 'large' | 'small';
	}

	let { href, title, subtitle, coverAssetId, size = 'large' }: Props = $props();

	const cover = $derived(
		coverAssetId
			? thumbnailUrl(coverAssetId, thumbnailSizeFor(size === 'large' ? 480 : 220), null)
			: null
	);
</script>

<a class="card {size}" {href}>
	{#if cover}
		<img src={cover} alt="" loading="lazy" decoding="async" />
	{:else}
		<span class="mark" aria-hidden="true"
			><Icon name="history" size={size === 'large' ? 36 : 28} /></span
		>
	{/if}
	<span class="text">
		<span class="title">{title}</span>
		{#if subtitle}<span class="subtitle">{subtitle}</span>{/if}
	</span>
</a>

<style>
	.card {
		position: relative;
		display: block;
		overflow: hidden;
		/* The native MemoryCardShape: one corner rounder than the rest, the
		   mark of a memory in both apps. */
		border-radius: var(--radius-xl) var(--radius-md) var(--radius-md) var(--radius-md);
		background: var(--color-placeholder);
		transition:
			translate var(--duration-emphasis) var(--ease-spring),
			box-shadow var(--duration-normal);
		color: var(--color-on-media);
		text-decoration: none;
		isolation: isolate;
	}

	.large {
		aspect-ratio: 1 / 0.62;
	}

	.small {
		aspect-ratio: 1;
	}

	img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform 600ms ease;
	}

	.card:hover img,
	.card:focus-visible img {
		transform: scale(1.04);
	}

	.card:hover {
		translate: 0 -3px;
		box-shadow: var(--shadow-bar);
	}

	/* Without a cover: a tinted card with a mark, dark text. */
	.card:not(:has(img)) {
		background: var(--color-brand-tile);
		color: var(--color-text);
	}

	.card:not(:has(img))::after {
		display: none;
	}

	.card:not(:has(img)) .title {
		text-shadow: none;
	}

	.mark {
		position: absolute;
		inset: 0 0 var(--space-8);
		display: grid;
		place-items: center;
		color: var(--color-accent);
	}

	/* Legible white text on any photo. */
	.card::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to top, var(--color-scrim), transparent 60%);
	}

	.text {
		position: absolute;
		inset: auto 0 0;
		z-index: 1;
		display: grid;
		padding: var(--space-3) var(--space-4);
	}

	.small .text {
		padding: var(--space-2) var(--space-3);
	}

	.title {
		font-weight: 700;
		line-height: 1.2;
		letter-spacing: -0.01em;
		text-shadow: 0 1px 2px var(--color-scrim-soft);
	}

	.large .title {
		font-size: var(--font-size-lg);
	}

	.small .title {
		font-size: var(--font-size-sm);
	}

	.subtitle {
		font-size: var(--font-size-sm);
		opacity: 0.9;
	}

	.card:focus-visible {
		outline-offset: 3px;
	}

	@media (prefers-reduced-motion: reduce) {
		img {
			transition: none;
		}
	}
</style>
