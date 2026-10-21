<script lang="ts" module>
	/** A scroll position that survives a relayout: a section and an offset into it. */
	export interface GridPosition {
		key: string;
		offset: number;
	}
</script>

<script lang="ts" generics="T extends GridAsset">
	import { tick, untrack, type Snippet } from 'svelte';
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
		type GridLayoutMode,
		type GridOptions,
		type GridSection
	} from './grid-model.js';
	import { moveFocus, type Direction } from './keyboard-nav.js';
	import Scrubber from './Scrubber.svelte';
	import { rowYearMarkers, spreadMarkers, yearMarkers } from './scrubber.js';
	import type { Selection } from './selection.svelte.js';
	import type { GridAsset } from './types.js';
	import {
		DEFAULT_SIZE,
		rowHeightFor,
		WheelZoom,
		yearRowHeightFor,
		type Grouping
	} from './zoom.js';

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
		/** Thumbnail size, 0–100 (zoom.ts); the medium grid by default. */
		size?: number;
		/**
		 * `day` splits each section into days; `year` sizes rows for the
		 * sampled year view. With headers off it makes no difference.
		 */
		grouping?: Grouping;
		/** Justified rows (the timeline) or a masonry of columns (grid-model.ts). */
		layout?: GridLayoutMode;
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
		/**
		 * The page's header, above the photos and scrolling with them, so the
		 * grid takes the whole height once it goes up.
		 */
		lead?: Snippet;
		/**
		 * Loads placeholder sections, so a Shift range or Select all covers the
		 * photos of months not loaded yet. Without it (a paged list, which has
		 * no placeholders for its unloaded pages) they cover the loaded ones.
		 */
		onloadsections?: (keys: string[]) => Promise<void>;
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
		size = DEFAULT_SIZE,
		grouping = 'month',
		layout: layoutMode = 'rows',
		onzoom,
		reflowKey = '',
		selectable = true,
		sectionSubtitle,
		onscrolled,
		lead,
		onloadsections
	}: Props = $props();

	const HEADER_HEIGHT = 52;
	/** A chevron up (Material keyboard_arrow_up). */
	const UP_PATH = 'M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z';
	const SUBHEADER_HEIGHT = 36;
	const SECTION_GAP = 16;
	/** Between photos: a hairline, as in the native grid, so it reads as one mosaic. */
	const SPACING = 2;
	/** Above the first row and under the last one. */
	const PADDING = 8;
	/** At each side: the page gutter (--page-gutter), so rows line up with the title. */
	const GUTTER = 24;
	/** The scrubber's column, which also stands for the right gutter. */
	const SCRUBBER_WIDTH = 56;

	let scroller = $state<HTMLDivElement>();
	let scrollTop = $state(0);
	let viewportHeight = $state(0);
	// Inside the scroll, around the photos: the room kept for the floating
	// chrome above (--bleed-top) and below, and the page's header (lead).
	let roomHeight = $state(0);
	let leadHeight = $state(0);
	let endHeight = $state(0);
	/**
	 * From scrollTop to a y on the layout at the top of the view, under the
	 * chrome. The room above shifts both the photos and the view's top
	 * edge, so only the header and the padding count.
	 */
	const offset = $derived(PADDING + leadHeight);
	/** The view's height between the chrome above and below. */
	const clearHeight = $derived(Math.max(0, viewportHeight - roomHeight - endHeight));

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
	let withScrubber = $state(false);

	const byDay = $derived(headers && grouping === 'day');

	const options: GridOptions = $derived.by(() => {
		const containerWidth = Math.max(
			0,
			outerWidth - GUTTER - (withScrubber ? SCRUBBER_WIDTH : GUTTER)
		);
		return {
			containerWidth,
			layout: layoutMode,
			targetRowHeight:
				grouping === 'year'
					? yearRowHeightFor(size, containerWidth)
					: rowHeightFor(size, containerWidth),
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
		blocksInRange(
			layout,
			scrollTop - offset - viewportHeight,
			scrollTop - offset + viewportHeight * 2
		)
	);
	const order = $derived(layout.rows.flat());
	const itemsBySection = $derived(
		new Map(sections.map((s) => [s.key, s.items?.map((item) => item.id) ?? []]))
	);
	const maxScroll = $derived(
		Math.max(
			0,
			roomHeight + leadHeight + layout.totalHeight + PADDING * 2 + endHeight - viewportHeight
		)
	);
	// Years on the rail: where the months start, or, without month sections
	// (an album in one section), where the rows' own dates change year.
	const yearOf = (item: T) => item.capturedAt.slice(0, 4);
	const markers = $derived(
		spreadMarkers(
			headers
				? yearMarkers(layout.sectionTops, layout.totalHeight)
				: rowYearMarkers(
						layout.blocks.flatMap((block) =>
							block.kind === 'row' ? [{ top: block.top, year: yearOf(block.cells[0].item) }] : []
						),
						layout.totalHeight
					),
			Math.max(1, viewportHeight - 32),
			22
		)
	);
	/** Headerless, but its rows run by date: the rail can still name a month. */
	const dated = $derived(!headers && markers.length > 0);

	const scrubberShown = $derived(viewportHeight > 0 && maxScroll > viewportHeight);

	$effect(() => {
		// A masonry gets *shorter* when it narrows (same columns, each
		// thinner), so giving the column could take the scrubber away again,
		// and so on: once given, the column stays for the grid's life.
		if (scrubberShown || layoutMode === 'rows') withScrubber = scrubberShown;
	});

	// Load the months that come within a screen of the viewport.
	$effect(() => {
		for (const block of visible) {
			if (block.kind === 'placeholder') untrack(() => onneedsection(block.key));
		}
	});

	$effect(() => {
		if (
			onnearend &&
			viewportHeight > 0 &&
			scrollTop - offset + viewportHeight * 2 >= layout.totalHeight
		) {
			untrack(() => onnearend());
		}
	});

	// Scroll anchoring. When a month above (or under) the viewport swaps its
	// estimated height for the real one, or the width changes, the content
	// the user is looking at stays put instead of jumping.
	let anchor: { key: string; offset: number } | null = null;

	function rememberAnchor() {
		const y = scrollTop - offset;
		const key = sectionAt(layout, y);
		anchor = key === null ? null : { key, offset: y - layout.sectionTops.get(key)! };
	}

	// A reflow (zoom, regrouping, resize) moves every row, so section offsets
	// mean nothing: the photo under the pointer (Ctrl + wheel) or at the top
	// keeps its place on screen instead.
	const shape = $derived(
		`${options.layout}|${options.targetRowHeight}|${options.containerWidth}|${byDay}|${reflowKey}`
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
			const target = top + anchor.offset + offset;
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
		const y = scrollTop - offset + screenY;
		const target = anchorAfterReflow(before, after, y, pointer?.x);
		if (target === null) return;
		const top = Math.max(0, target + offset - screenY);
		scroller!.scrollTop = top;
		scrollTop = scroller!.scrollTop;
		rememberAnchor();
	}

	// While the view moves, the month floats in a pill at the top and, far
	// down, a pill leads back to the top; both go a moment after it stops.
	const SETTLE_MS = 1500;
	let scrolling = $state(false);
	let settle: ReturnType<typeof setTimeout> | undefined;

	function onscroll() {
		scrollTop = scroller!.scrollTop;
		rememberAnchor();
		scrolling = true;
		clearTimeout(settle);
		settle = setTimeout(() => (scrolling = false), SETTLE_MS);
	}

	$effect(() => () => clearTimeout(settle));

	function backToTop() {
		const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		scroller?.scrollTo({ top: 0, behavior: smooth ? 'smooth' : 'auto' });
	}

	// Ctrl/Cmd + wheel (and a trackpad pinch, which browsers report the same
	// way) zooms the grid. Registered by hand: it must be non-passive to keep
	// the browser from zooming the whole page.
	// Finer than the default: each step is a small one (WHEEL_STEP).
	const wheelZoom = new WheelZoom(40, 80);

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
		// Rounded up: section tops can be fractional (masonry) and the browser
		// truncates scrollTop, which would leave the view a hair inside the
		// section above, anchored to it as that one loads.
		scroller.scrollTop = Math.ceil(layout.sectionTops.get(key)! + offset);
		scrollTop = scroller.scrollTop;
		rememberAnchor();
		return key;
	}

	/** Where the view is: the section at the top and how far into it. */
	export function position(): GridPosition | null {
		const y = scrollTop - offset;
		const key = sectionAt(layout, y);
		return key === null ? null : { key, offset: y - layout.sectionTops.get(key)! };
	}

	// A position to go back to (Back to the page), applied once the grid has
	// its size and that section is laid out.
	let pendingPosition: GridPosition | null = null;

	export function restorePosition(target: GridPosition) {
		pendingPosition = target;
		applyPosition();
	}

	function applyPosition() {
		const target = pendingPosition;
		if (!target || !scroller || options.containerWidth <= 0 || viewportHeight <= 0) return;
		pendingPosition = null;
		const top = layout.sectionTops.get(target.key);
		if (top === undefined) {
			scrollToSection(target.key);
			return;
		}
		scroller.scrollTop = top + target.offset + offset;
		scrollTop = scroller.scrollTop;
		rememberAnchor();
	}

	$effect(() => {
		void layout;
		void viewportHeight;
		untrack(applyPosition);
	});

	/** The section at the top of the view. */
	export function currentSection() {
		// A pixel of slack: the browser rounds scrollTop, so a section scrolled
		// to exactly (scrollToSection) can sit a fraction below the top.
		return sectionAt(layout, Math.max(0, scrollTop - offset + 1));
	}

	function seek(fraction: number) {
		scroller!.scrollTop = fraction * maxScroll;
	}

	// The month at `y`: the section's, or, without dated sections, the one
	// of the row there when the rows run by date. Otherwise nothing to name.
	function monthLabelAt(y: number) {
		if (headers) {
			const key = sectionAt(layout, y);
			return key ? sectionTitle(key) : '';
		}
		if (!dated) return '';
		const row = blocksInRange(layout, y, y).find((block) => block.kind === 'row');
		return row ? sectionTitle(row.cells[0].item.capturedAt.slice(0, 7)) : '';
	}

	const currentLabel = $derived(monthLabelAt(Math.max(0, scrollTop - offset)));

	function labelAt(fraction: number) {
		return monthLabelAt(Math.max(0, fraction * maxScroll - offset));
	}

	// --- Selection -------------------------------------------------------

	let painting: { selected: boolean } | null = null;

	// A long press on a touch screen toggles the cell, the way a phone's
	// gallery starts selecting; the click that follows it is swallowed.
	const LONG_PRESS_MS = 450;
	let press: { id: string; x: number; y: number; timer: number } | null = null;
	let pressed: string | null = null;

	function onCellPointerDown(event: PointerEvent, item: T) {
		cancelPress();
		pressed = null;
		if (!selectable || event.pointerType !== 'touch') return;
		const timer = window.setTimeout(() => {
			press = null;
			pressed = item.id;
			focusedId = item.id;
			selection.toggle(item.id);
			navigator.vibrate?.(10);
		}, LONG_PRESS_MS);
		press = { id: item.id, x: event.clientX, y: event.clientY, timer };
	}

	function onCellPointerMove(event: PointerEvent) {
		// A finger that moves is scrolling, not pressing.
		if (press && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 10) cancelPress();
	}

	function cancelPress() {
		if (press) window.clearTimeout(press.timer);
		press = null;
	}

	function onCellClick(event: MouseEvent, item: T) {
		if (pressed === item.id) {
			pressed = null;
			return;
		}
		focusedId = item.id;
		if (!selectable) onopen(item);
		else if (event.shiftKey) selectRangeTo(item.id);
		else if (event.ctrlKey || event.metaKey || selection.active) selection.toggle(item.id);
		else onopen(item);
	}

	function onCheckPointerDown(event: PointerEvent, item: T) {
		if (event.button !== 0) return;
		event.preventDefault();
		event.stopPropagation();
		focusedId = item.id;
		if (event.shiftKey) {
			selectRangeTo(item.id);
			return;
		}
		// Drag painting: the first box decides whether the stroke selects or
		// clears, and every cell the pointer crosses gets the same state.
		painting = { selected: !selection.has(item.id) };
		selection.toggle(item.id);
		window.addEventListener('pointerup', () => (painting = null), { once: true });
	}

	/** The index of the section holding `id`, or -1 if it isn't loaded. */
	function sectionIndexOf(id: string) {
		return sections.findIndex((s) => s.items?.some((item) => item.id === id));
	}

	/** Loads the placeholder sections among these, then lets the layout catch up. */
	async function loadSections(candidates: readonly GridSection<T>[]) {
		const keys = candidates.filter((s) => s.items === null).map((s) => s.key);
		if (keys.length === 0 || !onloadsections) return;
		await onloadsections(keys);
		await tick();
	}

	/** Shift: anchor → `target`, with the months between them loaded first. */
	async function selectRangeTo(target: string) {
		const anchor = selection.anchor;
		if (anchor !== null) {
			const [a, b] = [sectionIndexOf(anchor), sectionIndexOf(target)];
			if (a >= 0 && b >= 0) await loadSections(sections.slice(Math.min(a, b), Math.max(a, b) + 1));
		}
		selection.selectRange(target, order);
	}

	async function selectAll() {
		await loadSections(sections);
		selection.set(order, true);
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
		if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
			event.preventDefault();
			await selectAll();
		} else if (event.key === ' ' && focusedId) {
			event.preventDefault();
			if (event.shiftKey) await selectRangeTo(focusedId);
			else selection.toggle(focusedId);
		}
	}

	// Escape clears the selection wherever the focus is (the selection bar, the
	// page), unless a dialog, a menu or the viewer is open: Escape is theirs.
	function onWindowKeydown(event: KeyboardEvent) {
		if (event.key !== 'Escape' || !selectable || !selection.active || event.defaultPrevented)
			return;
		// Something open over the grid takes the Escape: a dialog, a menu, a
		// popover menu. A closed one (the chrome's menus) stays in the page,
		// and the toaster is a manual popover that is always open.
		const open =
			'dialog[open], [role="dialog"]:not([popover]), [popover="auto"]:popover-open, [role="menu"]';
		if (document.querySelector(open)) return;
		const target = event.target as HTMLElement | null;
		if (target?.closest('input, textarea, select')) return;
		event.preventDefault();
		selection.clear();
	}

	/** Scrolls an item into view if needed, then moves DOM focus to it. */
	export async function focusItem(id: string) {
		focusedId = id;
		const blockIndex = layout.blockOfItem.get(id);
		if (blockIndex === undefined || !scroller) return;
		const block = layout.blocks[blockIndex];
		const top = block.top + offset;
		const bottom = top + block.height;
		if (top < scroller.scrollTop + HEADER_HEIGHT) scroller.scrollTop = top - HEADER_HEIGHT;
		else if (bottom > scroller.scrollTop + clearHeight)
			scroller.scrollTop = bottom - clearHeight + PADDING;
		scrollTop = scroller.scrollTop;
		await tick();
		scroller.querySelector<HTMLElement>(`[data-id="${id}"]`)?.focus({ preventScroll: true });
	}

	// A browser drops the focus of an element that moves in the DOM, and the
	// keyed blocks below move when the grid regroups (a year's sample and the
	// same photo in its month share a key). If the focused photo lost the
	// focus that way, and not to a click elsewhere or to another element,
	// give it back.
	let lastPointer = { at: Number.NEGATIVE_INFINITY, id: null as string | null };

	function onpointerdown(event: PointerEvent) {
		const target = event.target as Element | null;
		lastPointer = {
			at: event.timeStamp,
			id: target?.closest?.<HTMLElement>('[data-id]')?.dataset.id ?? null
		};
	}

	function onfocusout(event: FocusEvent) {
		const cell = event.target as HTMLElement;
		const id = cell.dataset?.id;
		if (event.relatedTarget || !id || id !== focusedId) return;
		if (event.timeStamp - lastPointer.at < 500 && lastPointer.id !== id) return;
		requestAnimationFrame(() => {
			if (focusedId !== id || !document.hasFocus()) return;
			if (document.activeElement && document.activeElement !== document.body) return;
			scroller?.querySelector<HTMLElement>(`[data-id="${CSS.escape(id)}"]`)?.focus({
				preventScroll: true
			});
		});
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

<svelte:window onkeydown={onWindowKeydown} onpointerdowncapture={onpointerdown} />

<!-- The pills centre on the photos: the gutter on the left, the scrubber's
     column (or a gutter) on the right. -->
<div
	class="frame"
	bind:clientWidth={outerWidth}
	style:--pill-shift="{(GUTTER - (scrubberShown ? SCRUBBER_WIDTH : GUTTER)) / 2}px"
>
	<div
		class="scroller"
		bind:this={scroller}
		bind:clientHeight={viewportHeight}
		{onscroll}
		{onfocusout}
		role="region"
		aria-label={label}
	>
		<div class="room" aria-hidden="true" bind:clientHeight={roomHeight}></div>
		{#if lead}
			<div class="lead" bind:clientHeight={leadHeight}>{@render lead()}</div>
		{/if}
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
					{#each block.cells as cell (cell.item.id)}
						{@const item = cell.item}
						{@const height = cell.height ?? block.height}
						<!-- Sized by the row height, or by the column width in a masonry:
						     a portrait column cell can be twice as tall as it is wide. -->
						{@const size = thumbnailSizeFor(cell.height === undefined ? height : cell.width)}
						{@const selected = selection.has(item.id)}
						<div
							class="cell"
							class:selected
							class:selecting={selection.active}
							class:marked={item.isFavorite || item.isLivePhoto || item.isVideo}
							style:top="{block.top + (cell.top ?? 0) + PADDING}px"
							style:left="{cell.left}px"
							style:width="{cell.width}px"
							style:height="{height}px"
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
								ondragstart={(event) => {
									cancelPress();
									startAssetDrag(event, selected ? [...selection.ids] : [item.id]);
								}}
								onclick={(event) => onCellClick(event, item)}
								onpointerdown={(event) => onCellPointerDown(event, item)}
								onpointermove={onCellPointerMove}
								onpointerup={cancelPress}
								onpointercancel={cancelPress}
								oncontextmenu={(event) => {
									// The long press selected: no context menu over it.
									if (pressed === item.id || press?.id === item.id) event.preventDefault();
								}}
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
		<div class="end" aria-hidden="true" bind:clientHeight={endHeight}></div>
	</div>

	<!-- Once the photos reach the top: over the page's header it would say
	     the month of photos still below. -->
	{#if scrolling && scrollTop > 0 && scrollTop >= leadHeight && currentLabel}
		<span class="date-pill glass" aria-hidden="true">{currentLabel}</span>
	{/if}

	<!-- Not while selecting: the actions float at the bottom then. -->
	{#if scrolling && scrollTop > viewportHeight * 2 && !selection.active}
		<button type="button" class="top-pill glass" onclick={backToTop}>
			<Icon path={UP_PATH} size={18} />{m.grid_back_to_top()}
		</button>
	{/if}

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

	/* The floating chrome's room, inside the scroll so the photos pass
	   beneath it (a full-window page sets --bleed-top and --bleed-bottom). */
	.room {
		height: var(--bleed-top, 0px);
	}

	/* After the last row: the chrome below, or the phone's tabs when the
	   page's own room has gone to the photos (--grid-end-room). */
	.end {
		height: calc(var(--bleed-bottom, 0px) + var(--grid-end-room, 0px));
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
		background: var(--color-primary-fill);
		border-color: var(--color-primary-fill);
		color: var(--color-on-primary-fill);
	}

	.section-check.some {
		border-color: var(--color-accent);
	}

	.placeholder {
		position: absolute;
		left: 0;
		right: 0;
		border-radius: var(--radius-xs);
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
		-webkit-touch-callout: none;
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
			transform var(--duration-emphasis) var(--ease-spring),
			border-radius var(--duration-emphasis);
	}

	img:global(.loaded) {
		opacity: 1;
	}

	/* Selected: the photo shrinks into a rounded inset on a tint of the gold. */
	.selected {
		background: color-mix(in srgb, var(--color-brand) 18%, var(--color-bg));
	}

	.selected img {
		transform: scale(0.88);
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

	/* Without hover (a touch screen) the check is always there to tap. */
	@media (hover: none) {
		.check {
			opacity: 1;
		}
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
		background: var(--color-primary-fill);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
		color: var(--color-on-primary-fill);
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

	/* The floating pills, centred on the photos (the native FloatingDatePill
	   and ScrollToTopPill). */
	.date-pill,
	.top-pill {
		position: absolute;
		left: calc(50% + var(--pill-shift, 0px));
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 6px;
		padding: var(--space-2) var(--space-4);
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-pill);
		font-size: var(--font-size-sm);
		font-weight: 650;
		white-space: nowrap;
		translate: -50% 0;
		animation: pill-in var(--duration-emphasis) var(--ease-spring);
	}

	/* A block, so its first letter can be raised. */
	.date-pill {
		top: calc(var(--bleed-top, 0px) + var(--space-2));
		display: block;
		pointer-events: none;
	}

	.date-pill::first-letter {
		text-transform: uppercase;
	}

	.top-pill {
		bottom: calc(var(--bleed-bottom, 0px) + var(--grid-end-room, 0px) + var(--space-4));
		cursor: pointer;
	}

	.top-pill:hover {
		box-shadow:
			var(--shadow-pill),
			inset 0 0 0 100px var(--color-hover);
	}

	@keyframes pill-in {
		from {
			opacity: 0;
			scale: 0.8;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.date-pill,
		.top-pill {
			animation: none;
		}
	}
</style>
