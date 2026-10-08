<script lang="ts">
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
		border-radius: var(--radius-md);
		background: var(--color-placeholder);
		color: #fff;
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

	/* Legible white text on any photo. */
	.card::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to top, rgb(0 0 0 / 0.65), rgb(0 0 0 / 0) 60%);
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
		font-weight: 600;
		line-height: 1.25;
		text-shadow: 0 1px 2px rgb(0 0 0 / 0.4);
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
