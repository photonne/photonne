<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { deckScales } from './explore-deck.js';
	import SectionTitle from './SectionTitle.svelte';

	/**
	 * A row of Explorar as a deck of cards: the cards sit on each other,
	 * the one nearest the centre is shown whole and bigger, and the deck
	 * scrolls sideways one card at a time (like the thumbnail strip under
	 * the native app's viewer). Any card opens when clicked. A row short
	 * enough to fit flat is shown flat, like any other list.
	 */
	interface Props {
		id: string;
		title: string;
		/** "See all": the full grid of this row. */
		more?: { href: string; label?: string };
		/** The deck's cards, each an <li> holding one link. */
		children: Snippet;
	}

	let { id, title, more, children }: Props = $props();

	/** The page gutter (--page-gutter) and the gap between flat cards. */
	const GUTTER = 24;
	const GAP = 12;

	let list = $state<HTMLUListElement>();
	let centred = $state(0);
	/** How many cards there are, for the next button. */
	let count = $state(0);
	/** The cards fit flat in the row: no deck, no overlap, every name shown. */
	let flat = $state(true);
	/** The cards have their first size: from here on they animate between sizes. */
	let ready = $state(false);

	function cards() {
		return list ? (Array.from(list.children) as HTMLElement[]) : [];
	}

	/** Scales and stacks every card by its distance to the centre. */
	function update() {
		if (!list) return;
		const items = cards();
		count = items.length;
		const width = list.clientWidth;
		const card = parseFloat(getComputedStyle(list).getPropertyValue('--card')) || 0;
		flat = items.length * card + (items.length - 1) * GAP <= width - 2 * GUTTER;
		if (flat) {
			for (const item of items) {
				item.style.removeProperty('--scale');
				item.style.zIndex = '';
				item.classList.remove('centred');
			}
			centred = 0;
			ready = true;
			return;
		}
		const bounds = list.getBoundingClientRect();
		const middle = bounds.left + bounds.width / 2;
		const distances = items.map((item) => {
			const box = item.getBoundingClientRect();
			return box.width > 0 ? Math.abs(box.left + box.width / 2 - middle) / box.width : 0;
		});
		deckScales(distances).forEach((scaled, i) => {
			items[i].style.setProperty('--scale', scaled.scale.toFixed(3));
			items[i].style.zIndex = String(scaled.depth);
			items[i].classList.toggle('centred', scaled.centred);
		});
		centred = Math.max(0, distances.indexOf(Math.min(...distances)));
		// Not from the stylesheet's guess on the first paint.
		ready = true;
	}

	$effect(() => {
		if (!list) return;
		update();
		const observer = new ResizeObserver(update);
		observer.observe(list);
		// Cards render after their data arrives: rescale when they come and go.
		const children = new MutationObserver(update);
		children.observe(list, { childList: true });
		return () => {
			observer.disconnect();
			children.disconnect();
		};
	});

	function centre(index: number) {
		cards()[index]?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
	}

	function step(direction: 1 | -1) {
		const index = Math.max(0, Math.min(count - 1, centred + direction));
		centre(index);
		return index;
	}

	function onkeydown(event: KeyboardEvent) {
		if (flat || (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')) return;
		event.preventDefault();
		const index = step(event.key === 'ArrowRight' ? 1 : -1);
		// Focus follows the centred card, so Enter opens what's in front.
		cards()[index]?.querySelector<HTMLElement>('a, button')?.focus({ preventScroll: true });
	}
</script>

<section aria-labelledby={id}>
	<div class="head"><SectionTitle {id} {title} {more} /></div>
	<div class="deck">
		<button
			type="button"
			class="nav prev"
			aria-label={m.explore_deck_previous()}
			disabled={flat || centred === 0}
			onclick={() => step(-1)}
		>
			<Icon name="chevronLeft" size={20} />
		</button>
		<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
		<ul
			class="cards"
			class:flat
			class:ready
			role="list"
			aria-labelledby={id}
			bind:this={list}
			onscroll={update}
			{onkeydown}
		>
			{@render children()}
		</ul>
		<button
			type="button"
			class="nav next"
			aria-label={m.explore_deck_next()}
			disabled={flat || centred >= count - 1}
			onclick={() => step(1)}
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

	.deck {
		position: relative;
	}

	/*
	 * The cards overlap by --overlap of their width; the spacers at both ends
	 * let the first and last card reach the centre. Snapping keeps one card
	 * in front after every scroll.
	 */
	.cards {
		--card: 150px;
		--overlap: 0.4;
		display: flex;
		align-items: center;
		margin: 0;
		padding: var(--space-3) 0 var(--space-2);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		list-style: none;
	}

	.cards::-webkit-scrollbar {
		display: none;
	}

	.cards::before,
	.cards::after {
		content: '';
		flex: none;
		width: calc(50% - var(--card) / 2);
	}

	/* Flat: a plain row from the gutter, cards side by side at full size. */
	.cards.flat {
		gap: 12px;
		scroll-snap-type: none;
	}

	.cards.flat::before,
	.cards.flat::after {
		width: calc(var(--page-gutter) - 12px);
	}

	.cards > :global(li) {
		position: relative;
		flex: none;
		width: var(--card);
		margin-left: calc(var(--card) * -1 * var(--overlap));
		scroll-snap-align: center;
		transform: scale(var(--scale, 0.78));
	}

	.cards.ready > :global(li) {
		transition: transform var(--duration-normal) ease;
	}

	/* Before the first measure the deck starts on its first card. */
	.cards > :global(li:first-child) {
		--scale: 1;
		margin-left: 0;
	}

	.cards.flat > :global(li) {
		--scale: 1;
		margin-left: 0;
	}

	.cards > :global(li > *) {
		box-shadow: var(--shadow-raised);
	}

	/* Only the card in front shows its name; the others are its backdrop. */
	.cards:not(.flat) > :global(li:not(.centred) .text) {
		opacity: 0;
		transition: opacity var(--duration-normal) ease;
	}

	.cards > :global(li.centred .text) {
		transition: opacity var(--duration-normal) ease;
	}

	.nav {
		position: absolute;
		top: 50%;
		z-index: 1000;
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

	.nav:disabled {
		opacity: 0;
		pointer-events: none;
	}

	.prev {
		left: var(--space-2);
	}

	.next {
		right: var(--space-2);
	}

	@media (max-width: 720px) {
		.cards {
			--card: 118px;
		}

		.nav {
			display: none;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.cards.ready > :global(li),
		.cards > :global(li .text) {
			transition: none;
		}
	}
</style>
