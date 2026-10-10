<script lang="ts">
	import type { Snippet } from 'svelte';
	import SectionTitle from './SectionTitle.svelte';

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
	<div class="head"><SectionTitle {id} {title} {more} /></div>
	<ul class="row" role="list" aria-labelledby={id}>
		{@render children()}
	</ul>
</section>

<style>
	section {
		margin-top: var(--space-4);
	}

	.head {
		padding: 0 var(--page-gutter);
	}

	/* One scrolling row; focus moving along it scrolls it too. */
	.row {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: 150px;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-2) var(--page-gutter);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-padding-inline: var(--page-gutter);
		scroll-snap-type: x proximity;
		list-style: none;
	}

	.row > :global(li) {
		scroll-snap-align: start;
	}
</style>
