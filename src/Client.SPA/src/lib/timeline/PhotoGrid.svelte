<script lang="ts" generics="T extends GridAsset">
	import { tick, untrack } from 'svelte';
	import { startAssetDrag } from '#lib/actions/drag-assets.js';
	import Icon from '#lib/components/Icon.svelte';
	import { dayTitle } from '#lib/format.js';
	import { thumbnailSizeFor, thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import {
		anchorAfterReflow,
		blocksInRange,
		buildGridLayout,
		nearestSection,
		sectionAt,
		type GridBlock,
		type GridLayout,
		type GridOptions,
		type GridSection
	} from './grid-model.js';
	import { moveFocus, type Direction } from './keyboard-nav.js';
	import Scrubber from './Scrubber.svelte';
	import { spreadMarkers, yearMarkers } from './scrubber.js';
	import type { Selection } from './selection.svelte.js';
	import type { GridAsset } from './types.js';
	import { DEFAULT_ZOOM, groupingOf, rowHeightFor, WheelZoom, type ZoomLevel } from './zoom.js';

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
		/** Month headers; off for a single unnamed section (an album). */
		headers?: boolean;
		/** Scrolled near the end: load the next page, if the host pages. */
		onnearend?: () => void;
		/** Thumbnail size and grouping (zoom.ts); the medium month grid by default. */
		zoom?: ZoomLevel;
		/** Ctrl/Cmd + wheel over the grid asks for a step; without it the browser zooms. */
		onzoom?: (direction: 1 | -1) => void;
		/**
		 * Changes when the host regroups the same photos under other sections
		 * (months ↔ years), so the view anchors on a photo instead of a section.
		 */
		reflowKey?: string;
		/** Off for a sampled overview where cells only navigate (the year view). */
		selectable?: boolean;
		/** Muted text after a section's title (a year's photo count). */
		sectionSubtitle?: (key: string) => string;
		/** The scroll offset and how far it can go, e.g. to fold a header above it. */
		onscrolled?: (top: number, max: number) => void;
	}

	let {
		sections,
		selection,
		sectionTitle,
		itemLabel,
		onneedsection,
		onopen,
		label,
		headers = true,
		onnearend,
		zoom = DEFAULT_ZOOM,
		onzoom,
		reflowKey = '',
		selectable = true,
		sectionSubtitle,
		onscrolled
	}: Props = $props();

	const HEADER_HEIGHT = 52;
	const SUBHEADER_HEIGHT = 36;
	const SECTION_GAP = 16;
	const SPACING = 4;
	/** Above the first row and under the last one. */
	const PADDING = 8;
	/** At each side: the page gutter (--page-gutter), so rows line up with the title. */
	const GUTTER = 24;
	/** The scrubber's column, which also stands for the right gutter. */
	const SCRUBBER_WIDTH = 56;

	let scroller = $state<HTMLDivElement>();
	let scrollTop = $state(0);
	let viewportHeight = $state(0);

	$effect(() => {
		onscrolled?.(scrollTop, maxScroll);
	});
	let outerWidth = $state(0);
	let focusedId = $state<string | null>(null);
	/**
	 * The grid only gives up the scrubber's column while the scrubber is shown
	 * (long timelines), not for an album or a search that fits a few screens.
	 * Set from the layout (see the effect below): narrower rows only make the
	 * grid taller, so the choice never flips back and forth.
	 */
	// Not a $derived: the layout it feeds is what decides it, a cycle a
	// derived can't hold.
	// eslint-disable-next-line svelte/prefer-writable-derived
	let withScrubber = $state(false);

	const byDay = $derived(headers && groupingOf(zoom) === 'day');

	const options: GridOptions = $derived.by(() => {
		const containerWidth = Math.max(
			0,
			outerWidth - GUTTER - (withScrubber ? SCRUBBER_WIDTH : GUTTER)
		);
		return {
			containerWidth,
			targetRowHeight: rowHeightFor(zoom, containerWidth),
			spacing: SPACING,
			headerHeight: headers ? HEADER_HEIGHT : 0,
			subheaderHeight: SUBHEADER_HEIGHT,
			sectionGap: SECTION_GAP
		};
	});

	// Capture dates are UTC wall-clock instants (see format.ts), so the day is
	// the ISO date as written.
	const dayOf = (item: T) => item.capturedAt.slice(0, 10);

	const layout = $derived(buildGridLayout(sections, options, byDay ? dayOf : undefined));
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

	const scrubberShown = $derived(viewportHeight > 0 && maxScroll > viewportHeight);

	$effect(() => {
		withScrubber = scrubberShown;
	});

	// Load the months that come within a screen of the viewport.
	$effect(() => {
		for (const block of visible) {
			if (block.kind === 'placeholder') untrack(() => onneedsection(block.key));
		}
	});

	$effect(() => {
		if (onnearend && viewportHeight > 0 && scrollTop + viewportHeight * 2 >= layout.totalHeight) {
			untrack(() => onnearend());
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

	// A reflow (zoom, regrouping, resize) moves every row, so section offsets
	// mean nothing: the photo under the pointer (Ctrl + wheel) or at the top
	// keeps its place on screen instead.
	const shape = $derived(
		`${options.targetRowHeight}|${options.containerWidth}|${byDay}|${reflowKey}`
	);
	let previous: { layout: GridLayout<T>; shape: string } | null = null;
	let pointerAnchor: { screenY: number; x: number } | null = null;

	$effect(() => {
		const current = layout;
		const currentShape = shape;
		untrack(() => {
			const before = previous;
			previous = { layout: current, shape: currentShape };
			if (!scroller) return;
			if (before && before.shape !== currentShape) {
				reflow(before.layout, current);
				return;
			}
			if (!anchor || scrollTop <= 0) return;
			const top = current.sectionTops.get(anchor.key);
			if (top === undefined) return;
			const target = top + anchor.offset + PADDING;
			if (Math.abs(target - scroller.scrollTop) > 1) scroller.scrollTop = target;
		});
	});

	function reflow(before: GridLayout<T>, after: GridLayout<T>) {
		const pointer = pointerAnchor;
		pointerAnchor = null;
		// At the very top the view stays at the top, unless the pointer chose a photo.
		if (!pointer && scrollTop <= 0) return;
		// `scrollTop` (state) still holds the offset the old layout was seen at;
		// the element's own may already be clamped to the new, shorter canvas.
		const screenY = pointer?.screenY ?? 0;
		const y = scrollTop - PADDING + screenY;
		const target = anchorAfterReflow(before, after, y, pointer?.x);
		if (target === null) return;
		const top = Math.max(0, target + PADDING - screenY);
		scroller!.scrollTop = top;
		scrollTop = scroller!.scrollTop;
		rememberAnchor();
	}

	function onscroll() {
		scrollTop = scroller!.scrollTop;
		rememberAnchor();
	}

	// Ctrl/Cmd + wheel (and a trackpad pinch, which browsers report the same
	// way) zooms the grid. Registered by hand: it must be non-passive to keep
	// the browser from zooming the whole page.
	const wheelZoom = new WheelZoom();

	$effect(() => {
		const element = scroller;
		if (!element || !onzoom) return;
		const onwheel = (event: WheelEvent) => {
			if (!event.ctrlKey && !event.metaKey) return;
			event.preventDefault();
			const step = wheelZoom.push(event.deltaY, event.timeStamp);
			if (step === 0) return;
			const box = element.getBoundingClientRect();
			pointerAnchor = {
				screenY: event.clientY - box.top,
				x: event.clientX - box.left - GUTTER
			};
			onzoom?.(step);
		};
		element.addEventListener('wheel', onwheel, { passive: false });
		return () => element.removeEventListener('wheel', onwheel);
	});

	/**
	 * Scrolls to the section for `target` (`yyyy-MM`, or `yyyy` for a year),
	 * or the nearest older one; its photos load as it comes into view.
	 * Returns the section shown.
	 */
	export function scrollToSection(target: string) {
		const key = nearestSection([...layout.sectionTops.keys()], target);
		if (key === null || !scroller) return null;
		scroller.scrollTop = layout.sectionTops.get(key)! + PADDING;
		scrollTop = scroller.scrollTop;
		rememberAnchor();
		return key;
	}

	/** The section at the top of the view. */
	export function currentSection() {
		// A pixel of slack: the browser rounds scrollTop, so a section scrolled
		// to exactly (scrollToSection) can sit a fraction below the top.
		return sectionAt(layout, Math.max(0, scrollTop - PADDING + 1));
	}

	function seek(fraction: number) {
		scroller!.scrollTop = fraction * maxScroll;
	}

	// Without headers there are no dated sections to name on the scrubber.
	const currentLabel = $derived.by(() => {
		if (!headers) return '';
		const key = sectionAt(layout, scrollTop);
		return key ? sectionTitle(key) : '';
	});

	function labelAt(fraction: number) {
		if (!headers) return '';
		const key = sectionAt(layout, fraction * maxScroll);
		return key ? sectionTitle(key) : '';
	}

	// --- Selection -------------------------------------------------------

	let painting: { selected: boolean } | null = null;

	function onCellClick(event: MouseEvent, item: T) {
		focusedId = item.id;
		if (!selectable) onopen(item);
		else if (event.shiftKey) selection.selectRange(item.id, order);
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

	function toggleIds(ids: readonly string[]) {
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
			if (event.shiftKey && selectable) selection.selectRange(next, order);
			await focusItem(next);
			return;
		}

		if (!selectable) return;
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

	function blockKey(block: GridBlock<T>) {
		if (block.kind === 'row') return `r:${block.cells[0].item.id}`;
		if (block.kind === 'subheader') return `d:${block.group}`;
		return `${block.kind}:${block.key}`;
	}

	function cellTabIndex(id: string) {
		// Roving tabindex: one cell in the tab order, the arrows do the rest.
		if (focusedId !== null) return id === focusedId ? 0 : -1;
		return id === order[0] ? 0 : -1;
	}
</script>

{#snippet groupCheck(ids: readonly string[], title: string)}
	{@const state = selection.stateOf(ids)}
	<button
		type="button"
		class="section-check"
		class:on={state === 'all'}
		class:some={state === 'some'}
		aria-pressed={state === 'all' ? 'true' : state === 'some' ? 'mixed' : 'false'}
		aria-label={m.grid_select_section({ section: title })}
		onclick={() => toggleIds(ids)}
	>
		<Icon name="check" size={16} />
	</button>
{/snippet}

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
			style:margin-left="{GUTTER}px"
			{onkeydown}
		>
			{#each visible as block (blockKey(block))}
				{#if block.kind === 'header' && headers}
					{@const ids = itemsBySection.get(block.key) ?? []}
					{@const subtitle = sectionSubtitle?.(block.key)}
					<div class="header" style:top="{block.top + PADDING}px" style:height="{block.height}px">
						<h2>
							{sectionTitle(block.key)}
							{#if subtitle}<span class="subtitle">{subtitle}</span>{/if}
						</h2>
						{#if selectable && ids.length > 0}
							{@render groupCheck(ids, sectionTitle(block.key))}
						{/if}
					</div>
				{:else if block.kind === 'header'}
					<!-- headers off -->
				{:else if block.kind === 'subheader'}
					{@const ids = layout.groups.get(block.group) ?? []}
					<div
						class="header day"
						style:top="{block.top + PADDING}px"
						style:height="{block.height}px"
					>
						<h3>{dayTitle(block.group)}</h3>
						{#if selectable && ids.length > 0}
							{@render groupCheck(ids, dayTitle(block.group))}
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
							class:marked={item.isFavorite || item.isLivePhoto || item.isVideo}
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
							<span class="scrim" aria-hidden="true"></span>
							{#if selectable}
								<button
									type="button"
									class="check"
									tabindex="-1"
									aria-hidden="true"
									onpointerdown={(event) => onCheckPointerDown(event, item)}
								>
									<Icon name="check" size={16} />
								</button>
							{/if}
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

	{#if scrubberShown}
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

	.header h2,
	.header h3 {
		margin: 0;
		font-size: var(--font-size-md);
		font-weight: 600;
	}

	.header h3 {
		font-size: var(--font-size-sm);
		font-weight: 500;
		color: var(--color-text-muted);
	}

	.header h2::first-letter,
	.header h3::first-letter {
		text-transform: uppercase;
	}

	.subtitle {
		margin-left: var(--space-2);
		font-size: var(--font-size-sm);
		font-weight: 400;
		color: var(--color-text-muted);
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
		transition:
			background var(--duration-fast),
			border-radius var(--duration-fast);
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
			transform var(--duration-fast),
			border-radius var(--duration-fast);
	}

	img:global(.loaded) {
		opacity: 1;
	}

	/* Selected: the photo shrinks into a rounded inset on a tint of the accent. */
	.selected {
		background: var(--color-accent-soft);
	}

	.selected img {
		transform: scale(0.86);
		border-radius: var(--radius-md);
	}

	/* A shade along the top, so the check and the badges read on bright photos.
	   It shows on hover and while selecting; a cell with badges keeps a lighter one. */
	.scrim {
		position: absolute;
		inset: 0 0 auto;
		height: min(72px, 50%);
		background: linear-gradient(rgb(0 0 0 / 0.5), rgb(0 0 0 / 0.18) 55%, transparent);
		opacity: 0;
		pointer-events: none;
		transition: opacity var(--duration-fast);
	}

	.marked .scrim {
		opacity: 0.5;
	}

	.cell:hover .scrim,
	.selecting .scrim {
		opacity: 1;
	}

	.cell.selected .scrim {
		opacity: 0;
	}

	.check {
		position: absolute;
		top: 8px;
		left: 8px;
		display: grid;
		place-items: center;
		width: 24px;
		height: 24px;
		padding: 0;
		border: 2px solid #fff;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.18);
		/* A dark halo inside and out keeps the white ring on white skies. */
		box-shadow:
			0 0 0 1px rgb(0 0 0 / 0.25),
			inset 0 0 0 1px rgb(0 0 0 / 0.2),
			0 1px 4px rgb(0 0 0 / 0.4);
		color: transparent;
		cursor: pointer;
		opacity: 0;
		transition:
			opacity var(--duration-fast),
			background var(--duration-fast);
		touch-action: none;
	}

	.check:hover {
		background: rgb(255 255 255 / 0.3);
		color: #fff;
	}

	.cell:hover .check,
	.selecting .check,
	.selected .check {
		opacity: 1;
	}

	.selected .check {
		top: 4px;
		left: 4px;
		border-color: var(--color-bg);
		background: var(--color-accent);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
		color: var(--color-accent-text);
	}

	.badges {
		position: absolute;
		top: 8px;
		right: 8px;
		display: flex;
		gap: var(--space-1);
		color: #fff;
		filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.6));
		pointer-events: none;
	}

	.selected .badges {
		top: 6px;
		right: 6px;
	}
</style>
