<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import SectionTitle from './SectionTitle.svelte';

	/**
	 * A row of Explorar: cards the size of those in Recuerdos, scrolled
	 * sideways without a scrollbar, by dragging them with the mouse (as the
	 * native app's strips), with the arrows at the row's ends, or by the
	 * finger, trackpad and Shift + wheel the browser already gives.
	 */
	interface Props {
		id: string;
		title: string;
		/** "See all": the full grid of this row. */
		more?: { href: string; label?: string };
		/** The row's items, each an <li>. */
		children: Snippet;
	}

	let { id, title, more, children }: Props = $props();

	/** A drag shorter than this (in px) is still a click on a card. */
	const CLICK_SLOP = 6;

	let row = $state<HTMLUListElement>();
	let atStart = $state(true);
	let atEnd = $state(true);

	function measure() {
		if (!row) return;
		atStart = row.scrollLeft <= 1;
		atEnd = row.scrollLeft + row.clientWidth >= row.scrollWidth - 1;
	}

	$effect(() => {
		if (!row) return;
		measure();
		const sizes = new ResizeObserver(measure);
		sizes.observe(row);
		// The cards come with their data: the ends move when they do.
		const items = new MutationObserver(measure);
		items.observe(row, { childList: true });
		return () => {
			sizes.disconnect();
			items.disconnect();
		};
	});

	function page(direction: 1 | -1) {
		row?.scrollBy({ left: direction * row.clientWidth * 0.8, behavior: 'smooth' });
	}

	// Dragging with a mouse: the row follows the pointer (tracked on the
	// window, so leaving the row doesn't drop it), and a real drag swallows
	// the click that would open the card under it on release. No pointer
	// capture: with it, a plain click would no longer reach the card.
	let drag = $state<{ x: number; left: number; moved: boolean } | null>(null);
	let dragged = false;

	function onpointerdown(event: PointerEvent) {
		if (event.pointerType !== 'mouse' || event.button !== 0 || !row) return;
		drag = { x: event.clientX, left: row.scrollLeft, moved: false };
		window.addEventListener('pointermove', onpointermove);
		window.addEventListener('pointerup', onpointerup);
		window.addEventListener('pointercancel', onpointerup);
	}

	function onpointermove(event: PointerEvent) {
		if (!drag || !row) return;
		const dx = event.clientX - drag.x;
		if (!drag.moved && Math.abs(dx) < CLICK_SLOP) return;
		drag.moved = true;
		row.scrollLeft = drag.left - dx;
	}

	function onpointerup() {
		window.removeEventListener('pointermove', onpointermove);
		window.removeEventListener('pointerup', onpointerup);
		window.removeEventListener('pointercancel', onpointerup);
		if (!drag) return;
		if (drag.moved) {
			dragged = true;
			setTimeout(() => (dragged = false), 0);
			// Snap to a card once the hand lets go.
			row?.scrollBy({ left: 0, behavior: 'smooth' });
		}
		drag = null;
	}

	function onclick(event: MouseEvent) {
		if (!dragged) return;
		event.preventDefault();
		event.stopPropagation();
	}
</script>

<section aria-labelledby={id}>
	<div class="head"><SectionTitle {id} {title} {more} /></div>
	<div class="strip">
		<button
			type="button"
			class="arrow prev"
			aria-label={m.explore_row_previous()}
			hidden={atStart}
			onclick={() => page(-1)}
		>
			<Icon name="chevronLeft" size={20} />
		</button>
		<ul
			class="row"
			class:dragging={drag?.moved}
			role="list"
			aria-labelledby={id}
			bind:this={row}
			onscroll={measure}
			{onpointerdown}
			onclickcapture={onclick}
		>
			{@render children()}
		</ul>
		<button
			type="button"
			class="arrow next"
			aria-label={m.explore_row_next()}
			hidden={atEnd}
			onclick={() => page(1)}
		>
			<Icon name="chevronRight" size={20} />
		</button>
	</div>
</section>

<style>
	section {
		margin-top: var(--space-4);
	}

	.head {
		padding: 0 var(--page-gutter);
	}

	.strip {
		position: relative;
	}

	/* One scrolling row, no scrollbar; focus moving along it scrolls it too. */
	.row {
		--card: 280px;
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: var(--card);
		gap: var(--space-4);
		margin: 0;
		padding: var(--space-2) var(--page-gutter);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-padding-inline: var(--page-gutter);
		scroll-snap-type: x proximity;
		scrollbar-width: none;
		list-style: none;
		cursor: grab;
	}

	.row::-webkit-scrollbar {
		display: none;
	}

	.row.dragging {
		scroll-snap-type: none;
		cursor: grabbing;
		user-select: none;
	}

	/* While dragging, the cards don't react to the pointer passing over them. */
	.row.dragging > :global(li) {
		pointer-events: none;
	}

	.row > :global(li) {
		scroll-snap-align: start;
	}

	.row :global(img) {
		-webkit-user-drag: none;
	}

	.arrow {
		position: absolute;
		top: 50%;
		z-index: 1;
		display: grid;
		place-items: center;
		width: var(--control-h);
		height: var(--control-h);
		padding: 0;
		border: 1px solid var(--color-border);
		border-radius: 50%;
		background: var(--color-surface-raised);
		color: var(--color-text);
		box-shadow: var(--shadow-raised);
		transform: translateY(-50%);
		cursor: pointer;
	}

	.arrow:hover {
		background: var(--color-surface);
	}

	/* The display above would beat the attribute. */
	.arrow[hidden] {
		display: none;
	}

	.prev {
		left: var(--space-2);
	}

	.next {
		right: var(--space-2);
	}

	@media (max-width: 720px) {
		.row {
			--card: 220px;
		}
	}

	/* A finger scrolls the row itself: no arrows on touch screens. */
	@media (pointer: coarse) {
		.arrow {
			display: none;
		}

		.row {
			cursor: auto;
		}
	}
</style>
