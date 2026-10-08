<script lang="ts" generics="T extends GridAsset">
	import { tick, untrack } from 'svelte';
	import { startAssetDrag } from '#lib/actions/drag-assets.js';
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailSizeFor, thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import {
		blocksInRange,
		buildGridLayout,
		sectionAt,
		type GridOptions,
		type GridSection
	} from './grid-model.js';
	import { moveFocus, type Direction } from './keyboard-nav.js';
	import Scrubber from './Scrubber.svelte';
	import { spreadMarkers, yearMarkers } from './scrubber.js';
	import type { Selection } from './selection.svelte.js';
	import type { GridAsset } from './types.js';

	interface Props {
		sections: readonly GridSection<T>[];
		selection: Selection;
		/** Human title of a section (a month). */
		sectionTitle: (key: string) => string;
		/** Accessible name of a cell. */
		itemLabel: (item: T) => string;
		/** A placeholder section came near the viewport: load its items. */
		onneedsection: (key: string) => void;
		onopen: (item: T) => void;
		label: string;
	}

	let { sections, selection, sectionTitle, itemLabel, onneedsection, onopen, label }: Props =
		$props();

	const HEADER_HEIGHT = 52;
	const SECTION_GAP = 16;
	const SPACING = 4;
	const PADDING = 16;
	const SCRUBBER_WIDTH = 56;

	let scroller = $state<HTMLDivElement>();
	let scrollTop = $state(0);
	let viewportHeight = $state(0);
	let outerWidth = $state(0);
	let focusedId = $state<string | null>(null);

	const options: GridOptions = $derived.by(() => {
		const containerWidth = Math.max(0, outerWidth - PADDING * 2 - SCRUBBER_WIDTH);
		return {
			containerWidth,
			targetRowHeight: containerWidth < 600 ? 120 : containerWidth < 1100 ? 170 : 210,
			spacing: SPACING,
			headerHeight: HEADER_HEIGHT,
			sectionGap: SECTION_GAP
		};
	});

	const layout = $derived(buildGridLayout(sections, options));
	const visible = $derived(
		blocksInRange(layout, scrollTop - viewportHeight, scrollTop + viewportHeight * 2)
	);
	const order = $derived(layout.rows.flat());
	const itemsBySection = $derived(
		new Map(sections.map((s) => [s.key, s.items?.map((item) => item.id) ?? []]))
	);
	const maxScroll = $derived(Math.max(0, layout.totalHeight + PADDING * 2 - viewportHeight));
	const markers = $derived(
		spreadMarkers(
			yearMarkers(layout.sectionTops, layout.totalHeight),
			Math.max(1, viewportHeight - 32),
			22
		)
	);

	// Load the months that come within a screen of the viewport.
	$effect(() => {
		for (const block of visible) {
			if (block.kind === 'placeholder') untrack(() => onneedsection(block.key));
		}
	});

	// Scroll anchoring. When a month above (or under) the viewport swaps its
	// estimated height for the real one, or the width changes, the content
	// the user is looking at stays put instead of jumping.
	let anchor: { key: string; offset: number } | null = null;

	function rememberAnchor() {
		const y = scrollTop - PADDING;
		const key = sectionAt(layout, y);
		anchor = key === null ? null : { key, offset: y - layout.sectionTops.get(key)! };
	}

	$effect(() => {
		const tops = layout.sectionTops;
		untrack(() => {
			if (!scroller || !anchor || scrollTop <= 0) return;
			const top = tops.get(anchor.key);
			if (top === undefined) return;
			const target = top + anchor.offset + PADDING;
			if (Math.abs(target - scroller.scrollTop) > 1) scroller.scrollTop = target;
		});
	});

	function onscroll() {
		scrollTop = scroller!.scrollTop;
		rememberAnchor();
	}

	function seek(fraction: number) {
		scroller!.scrollTop = fraction * maxScroll;
	}

	const currentLabel = $derived.by(() => {
		const key = sectionAt(layout, scrollTop);
		return key ? sectionTitle(key) : '';
	});

	function labelAt(fraction: number) {
		const key = sectionAt(layout, fraction * maxScroll);
		return key ? sectionTitle(key) : '';
	}

	// --- Selection -------------------------------------------------------

	let painting: { selected: boolean } | null = null;

	function onCellClick(event: MouseEvent, item: T) {
		focusedId = item.id;
		if (event.shiftKey) selection.selectRange(item.id, order);
		else if (event.ctrlKey || event.metaKey || selection.active) selection.toggle(item.id);
		else onopen(item);
	}

	function onCheckPointerDown(event: PointerEvent, item: T) {
		if (event.button !== 0) return;
		event.preventDefault();
		event.stopPropagation();
		focusedId = item.id;
		if (event.shiftKey) {
			selection.selectRange(item.id, order);
			return;
		}
		// Drag painting: the first box decides whether the stroke selects or
		// clears, and every cell the pointer crosses gets the same state.
		painting = { selected: !selection.has(item.id) };
		selection.toggle(item.id);
		window.addEventListener('pointerup', () => (painting = null), { once: true });
	}

	function onCellPointerEnter(item: T) {
		if (painting) selection.set([item.id], painting.selected);
	}

	function toggleSection(key: string) {
		const ids = itemsBySection.get(key) ?? [];
		selection.set(ids, selection.stateOf(ids) !== 'all');
	}

	// --- Keyboard --------------------------------------------------------

	const keys: Record<string, Direction> = {
		ArrowLeft: 'left',
		ArrowRight: 'right',
		ArrowUp: 'up',
		ArrowDown: 'down'
	};

	async function onkeydown(event: KeyboardEvent) {
		const direction = keys[event.key];
		if (direction) {
			event.preventDefault();
			const next = moveFocus(layout.rows, focusedId, direction);
			if (next === null) return;
			if (event.shiftKey) selection.selectRange(next, order);
			await focusItem(next);
			return;
		}

		if (event.key === 'Escape' && selection.active) {
			event.preventDefault();
			selection.clear();
		} else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
			event.preventDefault();
			selection.set(order, true);
		} else if (event.key === ' ' && focusedId) {
			event.preventDefault();
			if (event.shiftKey) selection.selectRange(focusedId, order);
			else selection.toggle(focusedId);
		}
	}

	/** Scrolls an item into view if needed, then moves DOM focus to it. */
	export async function focusItem(id: string) {
		focusedId = id;
		const blockIndex = layout.blockOfItem.get(id);
		if (blockIndex === undefined || !scroller) return;
		const block = layout.blocks[blockIndex];
		const top = block.top + PADDING;
		const bottom = top + block.height;
		if (top < scroller.scrollTop + HEADER_HEIGHT) scroller.scrollTop = top - HEADER_HEIGHT;
		else if (bottom > scroller.scrollTop + viewportHeight)
			scroller.scrollTop = bottom - viewportHeight + PADDING;
		scrollTop = scroller.scrollTop;
		await tick();
		scroller.querySelector<HTMLElement>(`[data-id="${id}"]`)?.focus({ preventScroll: true });
	}

	function cellTabIndex(id: string) {
		// Roving tabindex: one cell in the tab order, the arrows do the rest.
		if (focusedId !== null) return id === focusedId ? 0 : -1;
		return id === order[0] ? 0 : -1;
	}
</script>

<div class="frame" bind:clientWidth={outerWidth}>
	<div
		class="scroller"
		bind:this={scroller}
		bind:clientHeight={viewportHeight}
		{onscroll}
		role="region"
		aria-label={label}
	>
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div
			class="canvas"
			style:height="{layout.totalHeight + PADDING * 2}px"
			style:width="{options.containerWidth}px"
			{onkeydown}
		>
			{#each visible as block (block.kind === 'row' ? `r:${block.cells[0].item.id}` : `${block.kind}:${block.key}`)}
				{#if block.kind === 'header'}
					{@const state = selection.stateOf(itemsBySection.get(block.key) ?? [])}
					<div class="header" style:top="{block.top + PADDING}px" style:height="{block.height}px">
						<h2>{sectionTitle(block.key)}</h2>
						{#if (itemsBySection.get(block.key)?.length ?? 0) > 0}
							<button
								type="button"
								class="section-check"
								class:on={state === 'all'}
								class:some={state === 'some'}
								aria-pressed={state === 'all' ? 'true' : state === 'some' ? 'mixed' : 'false'}
								aria-label={m.grid_select_section({ section: sectionTitle(block.key) })}
								onclick={() => toggleSection(block.key)}
							>
								<Icon name="check" size={16} />
							</button>
						{/if}
					</div>
				{:else if block.kind === 'placeholder'}
					<div
						class="placeholder"
						style:top="{block.top + PADDING}px"
						style:height="{block.height}px"
					></div>
				{:else}
					{@const size = thumbnailSizeFor(block.height)}
					{#each block.cells as cell (cell.item.id)}
						{@const item = cell.item}
						{@const selected = selection.has(item.id)}
						<div
							class="cell"
							class:selected
							class:selecting={selection.active}
							style:top="{block.top + PADDING}px"
							style:left="{cell.left}px"
							style:width="{cell.width}px"
							style:height="{block.height}px"
							style:--placeholder={item.dominantColor ?? 'var(--color-placeholder)'}
							onpointerenter={() => onCellPointerEnter(item)}
						>
							<button
								type="button"
								class="open"
								data-id={item.id}
								tabindex={cellTabIndex(item.id)}
								aria-label={itemLabel(item)}
								aria-pressed={selection.active ? selected : undefined}
								draggable="true"
								ondragstart={(event) =>
									startAssetDrag(event, selected ? [...selection.ids] : [item.id])}
								onclick={(event) => onCellClick(event, item)}
								onfocus={() => (focusedId = item.id)}
							>
								<img
									src={thumbnailUrl(item.id, size, item.thumbnailVersion)}
									alt=""
									loading="lazy"
									decoding="async"
									draggable="false"
									onload={(event) => event.currentTarget.classList.add('loaded')}
								/>
							</button>
							<button
								type="button"
								class="check"
								tabindex="-1"
								aria-hidden="true"
								onpointerdown={(event) => onCheckPointerDown(event, item)}
							>
								<Icon name="check" size={16} />
							</button>
							<span class="badges" aria-hidden="true">
								{#if item.isFavorite}<Icon name="favorite" size={16} />{/if}
								{#if item.isLivePhoto}<Icon name="livePhoto" size={16} />{/if}
								{#if item.isVideo}<Icon name="play" size={18} />{/if}
							</span>
						</div>
					{/each}
				{/if}
			{/each}
		</div>
	</div>

	{#if maxScroll > viewportHeight}
		<Scrubber
			{markers}
			fraction={maxScroll > 0 ? scrollTop / maxScroll : 0}
			{currentLabel}
			{labelAt}
			onseek={seek}
		/>
	{/if}
</div>

<style>
	.frame {
		position: relative;
		height: 100%;
	}

	.scroller {
		height: 100%;
		overflow-y: auto;
		overflow-x: hidden;
		/* Anchoring is done by hand (see the effect above); the browser's own
		   would fight it when estimated months turn into real ones. */
		overflow-anchor: none;
		scrollbar-width: none;
	}

	.scroller::-webkit-scrollbar {
		display: none;
	}

	.canvas {
		position: relative;
		margin-left: 16px;
		outline: none;
	}

	.header {
		position: absolute;
		left: 0;
		right: 0;
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.header h2 {
		margin: 0;
		font-size: var(--font-size-md);
		font-weight: 600;
	}

	.header h2::first-letter {
		text-transform: uppercase;
	}

	.section-check {
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		padding: 0;
		border: 2px solid var(--color-text-muted);
		border-radius: 50%;
		background: transparent;
		color: transparent;
		cursor: pointer;
		opacity: 0;
		transition: opacity var(--duration-fast);
	}

	.header:hover .section-check,
	.section-check:focus-visible,
	.section-check.on,
	.section-check.some {
		opacity: 1;
	}

	.section-check.on {
		background: var(--color-accent);
		border-color: var(--color-accent);
		color: var(--color-accent-text);
	}

	.section-check.some {
		border-color: var(--color-accent);
	}

	.placeholder {
		position: absolute;
		left: 0;
		right: 0;
		border-radius: var(--radius-sm);
		background: repeating-linear-gradient(
			to bottom,
			var(--color-placeholder) 0 160px,
			transparent 160px 164px
		);
		opacity: 0.6;
	}

	.cell {
		position: absolute;
		overflow: hidden;
		background: var(--placeholder);
	}

	.open {
		display: block;
		width: 100%;
		height: 100%;
		padding: 0;
		border: 0;
		background: none;
		cursor: pointer;
	}

	.open:focus-visible {
		outline: 3px solid var(--color-focus);
		outline-offset: -3px;
	}

	img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
		opacity: 0;
		transition:
			opacity var(--duration-normal),
			transform var(--duration-fast);
	}

	img:global(.loaded) {
		opacity: 1;
	}

	.selected img {
		transform: scale(0.88);
		border-radius: var(--radius-sm);
	}

	.selected {
		background: color-mix(in srgb, var(--color-accent) 18%, var(--color-bg));
	}

	.check {
		position: absolute;
		top: 6px;
		left: 6px;
		display: grid;
		place-items: center;
		width: 24px;
		height: 24px;
		padding: 0;
		border: 2px solid white;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.25);
		color: transparent;
		cursor: pointer;
		opacity: 0;
		transition: opacity var(--duration-fast);
		touch-action: none;
	}

	.cell:hover .check,
	.selecting .check,
	.selected .check {
		opacity: 1;
	}

	.selected .check {
		background: var(--color-accent);
		border-color: var(--color-accent);
		color: var(--color-accent-text);
	}

	.badges {
		position: absolute;
		top: 6px;
		right: 6px;
		display: flex;
		gap: 4px;
		color: white;
		filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.6));
		pointer-events: none;
	}
</style>
