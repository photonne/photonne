<script lang="ts">
	import type { Snippet } from 'svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		id: string;
		title: string;
		/** "See all": the full grid of this row. */
		more?: { href: string; label?: string };
		/** The row's items, each an <li>. */
		children: Snippet;
	}

	let { id, title, more, children }: Props = $props();
</script>

<section aria-labelledby={id}>
	<div class="head">
		<h2 {id}>{title}</h2>
		{#if more}
			<a class="more" href={more.href}>{more.label ?? m.explore_see_all()}</a>
		{/if}
	</div>
	<ul class="row" role="list" aria-labelledby={id}>
		{@render children()}
	</ul>
</section>

<style>
	section {
		margin-top: var(--space-6);
	}

	.head {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		padding: 0 var(--space-4);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.more {
		margin-left: auto;
		color: var(--color-accent);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.more:hover {
		text-decoration: underline;
	}

	/* One scrolling row; focus moving along it scrolls it too. */
	.row {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: 150px;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-3) var(--space-4) var(--space-2);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-padding-inline: var(--space-4);
		scroll-snap-type: x proximity;
		list-style: none;
	}

	.row > :global(li) {
		scroll-snap-align: start;
	}
</style>
