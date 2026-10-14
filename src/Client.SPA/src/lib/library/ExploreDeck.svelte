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
	 * the native app's viewer). A card to the side centres itself when
	 * clicked; the centred one opens.
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

	/** The page gutter (--page-gutter), where a deck that fits starts. */
	const GUTTER = 24;

	let list = $state<HTMLUListElement>();
	let centred = $state(0);
	/** How many cards there are, for the next button. */
	let count = $state(0);
	/** The deck fits its row: it sits at the left, like any other list. */
	let fits = $state(true);
	/** The cards have their first size: from here on they animate between sizes. */
	let ready = $state(false);

	function cards() {
		return list ? (Array.from(list.children) as HTMLElement[]) : [];
	}

	/** Scales and stacks every card by its distance to the centre. */
	function update() {
		if (!list) return;
		const items = cards();
		const bounds = list.getBoundingClientRect();
		// A deck that fits its row sits at the left, like any other list, with
		// its first card in front; one that overflows centres the front card.
		const first = items[0]?.getBoundingClientRect();
		const last = items.at(-1)?.getBoundingClientRect();
		fits = !first || !last || last.right - first.left <= bounds.width - 2 * GUTTER;
		const middle = fits && first ? first.left + first.width / 2 : bounds.left + bounds.width / 2;
		const distances = items.map((item) => {
			const box = item.getBoundingClientRect();
			return box.width > 0 ? Math.abs(box.left + box.width / 2 - middle) / box.width : 0;
		});
		deckScales(distances).forEach((card, i) => {
			items[i].style.setProperty('--scale', card.scale.toFixed(3));
			items[i].style.zIndex = String(card.depth);
			items[i].classList.toggle('centred', card.centred);
		});
		centred = Math.max(0, distances.indexOf(Math.min(...distances)));
		count = items.length;
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

	function centre(index: number, behavior: ScrollBehavior = 'smooth') {
		cards()[index]?.scrollIntoView({ behavior, inline: 'center', block: 'nearest' });
	}

	function step(direction: 1 | -1) {
		const index = Math.max(0, Math.min(cards().length - 1, centred + direction));
		centre(index);
		return index;
	}

	// A side card centres itself instead of opening: the deck is browsed by
	// bringing cards to the front, as the native strip is.
	function onclick(event: MouseEvent) {
		const card = (event.target as HTMLElement).closest('li');
		if (!card || !list) return;
		const index = cards().indexOf(card);
		if (index === -1 || index === centred) return;
		event.preventDefault();
		centre(index);
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
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
			disabled={centred === 0}
			onclick={() => step(-1)}
		>
			<Icon name="chevronLeft" size={20} />
		</button>
		<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
		<ul
			class="cards"
			class:fits
			class:ready
			role="list"
			aria-labelledby={id}
			bind:this={list}
			onscroll={update}
			{onclick}
			{onkeydown}
		>
			{@render children()}
		</ul>
		<button
			type="button"
			class="nav next"
			aria-label={m.explore_deck_next()}
			disabled={centred >= count - 1}
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

	.cards.fits::before,
	.cards.fits::after {
		width: var(--page-gutter);
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

	.cards > :global(li > *) {
		box-shadow: var(--shadow-raised);
	}

	/* Only the card in front shows its name; the others are its backdrop. */
	.cards > :global(li:not(.centred) .text) {
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
