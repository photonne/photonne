<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		/**
		 * `grid`: justified photo rows; `cards`: a grid of cards (albums,
		 * people, explore); `rows`: list or table rows; `text`: a few lines.
		 */
		variant?: 'grid' | 'cards' | 'tiles' | 'rows' | 'text';
		/** How many cards or rows. */
		count?: number;
		/** Round cards (people). */
		round?: boolean;
		/** No page gutter, for use inside a card or panel. */
		flush?: boolean;
	}

	let { variant = 'grid', count = 8, round = false, flush = false }: Props = $props();

	// Varied widths so the placeholder reads as photos, not as a table.
	const rowWidths = [
		[1.5, 0.75, 1, 1.33, 1],
		[1, 1.5, 0.75, 1.33],
		[0.75, 1, 1.78, 1, 1.33],
		[1.33, 1, 0.75, 1.5]
	];
</script>

<div class="skeleton {variant}" class:flush role="status" aria-label={m.loading()}>
	{#if variant === 'grid'}
		{#each rowWidths as row, r (r)}
			<div class="row">
				{#each row as grow, i (i)}<span class="block" style:flex-grow={grow}></span>{/each}
			</div>
		{/each}
	{:else if variant === 'cards'}
		{#each { length: count }, i (i)}
			<div class="card">
				<span class="block media" class:round></span>
				<span class="block line"></span>
				<span class="block line short"></span>
			</div>
		{/each}
	{:else if variant === 'tiles'}
		{#each { length: count }, i (i)}
			<div class="tile">
				<span class="block line short"></span>
				<span class="block value"></span>
			</div>
		{/each}
	{:else if variant === 'rows'}
		{#each { length: count }, i (i)}
			<div class="list-row">
				<span class="block dot"></span>
				<span class="block line"></span>
				<span class="block line short"></span>
			</div>
		{/each}
	{:else}
		{#each { length: Math.min(count, 4) }, i (i)}<span class="block line"></span>{/each}
	{/if}
</div>

<style>
	.skeleton {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-4) var(--page-gutter);
	}

	.flush {
		padding: 0;
	}

	.block {
		display: block;
		border-radius: var(--radius-sm);
		background: linear-gradient(
			90deg,
			var(--color-placeholder) 0%,
			var(--color-surface) 50%,
			var(--color-placeholder) 100%
		);
		background-size: 200% 100%;
		animation: shimmer 1.4s ease-in-out infinite;
	}

	.row {
		display: flex;
		gap: 4px;
		height: 180px;
	}

	.row .block {
		flex-basis: 0;
		border-radius: 2px;
	}

	.cards {
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: var(--space-4);
	}

	.tiles {
		grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
		gap: var(--space-4);
	}

	.tile {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
	}

	.value {
		width: 40%;
		height: 24px;
	}

	.card {
		display: grid;
		gap: var(--space-2);
	}

	.media {
		aspect-ratio: 1;
		border-radius: var(--radius-md);
	}

	.media.round {
		border-radius: 50%;
	}

	.line {
		height: 12px;
	}

	.short {
		width: 55%;
	}

	.list-row {
		display: grid;
		grid-template-columns: 36px 1fr 1fr;
		align-items: center;
		gap: var(--space-4);
		height: 52px;
		border-bottom: 1px solid var(--color-border);
	}

	.dot {
		width: 36px;
		height: 36px;
		border-radius: 50%;
	}

	@keyframes shimmer {
		from {
			background-position: 100% 0;
		}
		to {
			background-position: -100% 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.block {
			animation: none;
		}
	}
</style>
