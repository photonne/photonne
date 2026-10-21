<script lang="ts">
	import type { Snippet } from 'svelte';
	import {
		centerOn,
		clampPan,
		fitSize,
		IDENTITY,
		visibleRect,
		zoomAt,
		type ZoomState
	} from './zoom.js';
	import ZoomNavigator from './ZoomNavigator.svelte';

	interface Props {
		/** Shown at once (usually the Large thumbnail, already cached). */
		previewSrc: string;
		/** Swapped in when loaded: full resolution for zooming. */
		fullSrc: string;
		alt: string;
		/** Exposed so the viewer can route keys (+, -, 0) and know when to pan. */
		zoom?: ZoomState;
		/**
		 * Drawn over the image (face boxes…) in the image's own box, so
		 * percentages of it stay on the same pixels through zoom and pan.
		 * Gets the zoom scale, to keep strokes the same width on screen.
		 */
		overlay?: Snippet<[number]>;
		/** A horizontal swipe at the fitted size: 1 for the next photo, -1 for the previous. */
		onswipe?: (direction: 1 | -1) => void;
		/** A swipe down at the fitted size. */
		ondismiss?: () => void;
	}

	let {
		previewSrc,
		fullSrc,
		alt,
		zoom = $bindable(IDENTITY),
		overlay,
		onswipe,
		ondismiss
	}: Props = $props();

	let stageWidth = $state(0);
	let stageHeight = $state(0);
	let natural = $state({ width: 0, height: 0 });
	let fullLoaded = $state(false);
	let stage = $state<HTMLDivElement>();

	const stageSize = $derived({ width: stageWidth, height: stageHeight });
	// The preview is a thumbnail of a (usually) larger original: let it fill the
	// stage so the swap to the original doesn't make the photo jump in size.
	const fitted = $derived(fitSize(natural, stageSize, !fullLoaded));

	// A new asset: back to the fitted view, and preload the original.
	$effect(() => {
		const src = fullSrc;
		zoom = IDENTITY;
		fullLoaded = false;
		const image = new Image();
		image.onload = () => {
			if (src === fullSrc) fullLoaded = true;
		};
		image.src = src;
		return () => (image.onload = null);
	});

	function onload(event: Event) {
		const image = event.currentTarget as HTMLImageElement;
		natural = { width: image.naturalWidth, height: image.naturalHeight };
	}

	function focusPoint(event: { clientX: number; clientY: number }) {
		const box = stage!.getBoundingClientRect();
		return {
			x: event.clientX - box.left - box.width / 2,
			y: event.clientY - box.top - box.height / 2
		};
	}

	export function zoomBy(factor: number) {
		zoom = zoomAt(zoom, zoom.scale * factor, { x: 0, y: 0 }, fitted, stageSize);
	}

	export function reset() {
		zoom = IDENTITY;
	}

	function ondblclick(event: MouseEvent) {
		zoom = zoom.scale > 1 ? IDENTITY : zoomAt(zoom, 2.5, focusPoint(event), fitted, stageSize);
	}

	function onwheel(event: WheelEvent) {
		if (!event.ctrlKey && zoom.scale === 1) return;
		event.preventDefault();
		if (event.ctrlKey) {
			zoom = zoomAt(
				zoom,
				zoom.scale * Math.exp(-event.deltaY * 0.01),
				focusPoint(event),
				fitted,
				stageSize
			);
		} else {
			zoom = clampPan(
				{ ...zoom, x: zoom.x - event.deltaX, y: zoom.y - event.deltaY },
				fitted,
				stageSize
			);
		}
	}

	let drag = $state<{ x: number; y: number; startX: number; startY: number } | null>(null);

	// Touch gestures: two fingers pinch to zoom; one finger at the fitted size
	// swipes sideways to the neighbours or down to close, the photo following
	// the finger until it lifts.
	const SWIPE_DISTANCE = 60;
	const DISMISS_DISTANCE = 100;
	// Bookkeeping for the handlers, never drawn: no need for it to be reactive.
	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	const pointers = new Map<number, { x: number; y: number }>();
	let pinch = $state<{ distance: number } | null>(null);
	let swipe = $state<{ startX: number; startY: number; dx: number; dy: number } | null>(null);
	/** How far the photo follows a swipe: sideways, or down only. */
	const swipeShift = $derived.by(() => {
		if (!swipe) return { x: 0, y: 0 };
		return Math.abs(swipe.dx) >= swipe.dy ? { x: swipe.dx, y: 0 } : { x: 0, y: swipe.dy };
	});

	function spread() {
		const [a, b] = [...pointers.values()];
		return {
			distance: Math.hypot(a.x - b.x, a.y - b.y),
			center: { clientX: (a.x + b.x) / 2, clientY: (a.y + b.y) / 2 }
		};
	}

	function onpointerdown(event: PointerEvent) {
		const mouse = event.pointerType === 'mouse';
		// A mouse only drags the zoomed photo; at the fitted size its clicks are
		// left alone (a face box under it still gets them).
		if (mouse && (event.button !== 0 || zoom.scale === 1)) return;
		pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
		// Touch and pen are captured implicitly by the element they land on and
		// their events bubble here; capturing them to the stage would retarget
		// the click of a tap away from a face box.
		if (mouse) stage!.setPointerCapture(event.pointerId);
		if (pointers.size === 2) {
			// A second finger: whatever the first was doing becomes a pinch.
			drag = null;
			swipe = null;
			pinch = { distance: spread().distance };
			return;
		}
		if (pointers.size > 2) return;
		if (zoom.scale > 1) {
			drag = { x: zoom.x, y: zoom.y, startX: event.clientX, startY: event.clientY };
		} else if (event.pointerType !== 'mouse' && (onswipe || ondismiss)) {
			swipe = { startX: event.clientX, startY: event.clientY, dx: 0, dy: 0 };
		}
	}

	function onpointermove(event: PointerEvent) {
		if (!pointers.has(event.pointerId)) return;
		pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
		if (pinch && pointers.size === 2) {
			const { distance, center } = spread();
			zoom = zoomAt(
				zoom,
				(zoom.scale * distance) / pinch.distance,
				focusPoint(center),
				fitted,
				stageSize
			);
			pinch = { distance };
		} else if (drag) {
			zoom = clampPan(
				{
					...zoom,
					x: drag.x + event.clientX - drag.startX,
					y: drag.y + event.clientY - drag.startY
				},
				fitted,
				stageSize
			);
		} else if (swipe) {
			swipe = {
				...swipe,
				dx: event.clientX - swipe.startX,
				dy: Math.max(0, event.clientY - swipe.startY)
			};
		}
	}

	function onpointerup(event: PointerEvent) {
		pointers.delete(event.pointerId);
		if (pinch) {
			if (pointers.size < 2) pinch = null;
			// The finger left on the glass carries on panning from here.
			const [rest] = [...pointers.values()];
			drag =
				rest && zoom.scale > 1 ? { x: zoom.x, y: zoom.y, startX: rest.x, startY: rest.y } : null;
			return;
		}
		drag = null;
		const done = swipe;
		swipe = null;
		if (!done || event.type === 'pointercancel') return;
		const { dx, dy } = done;
		if (Math.abs(dx) >= SWIPE_DISTANCE && Math.abs(dx) > dy) onswipe?.(dx < 0 ? 1 : -1);
		else if (dy >= DISMISS_DISTANCE && dy > Math.abs(dx)) ondismiss?.();
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="stage"
	class:zoomed={zoom.scale > 1}
	class:dragging={drag !== null || swipe !== null || pinch !== null}
	bind:this={stage}
	bind:clientWidth={stageWidth}
	bind:clientHeight={stageHeight}
	{ondblclick}
	{onwheel}
	{onpointerdown}
	{onpointermove}
	{onpointerup}
	onpointercancel={onpointerup}
>
	<img
		src={fullLoaded ? fullSrc : previewSrc}
		{alt}
		draggable="false"
		{onload}
		style:width="{fitted.width}px"
		style:height="{fitted.height}px"
		style:transform="translate({zoom.x + swipeShift.x}px, {zoom.y + swipeShift.y}px) scale({zoom.scale})"
	/>
	{#if overlay && natural.width > 0}
		<div
			class="overlay"
			style:width="{fitted.width}px"
			style:height="{fitted.height}px"
			style:transform="translate({zoom.x + swipeShift.x}px, {zoom.y + swipeShift.y}px) scale({zoom.scale})"
		>
			{@render overlay(zoom.scale)}
		</div>
	{/if}
	{#if zoom.scale > 1 && natural.width > 0}
		<ZoomNavigator
			src={previewSrc}
			aspect={natural.width / natural.height}
			frame={visibleRect(zoom, fitted, stageSize)}
			onpan={(point) => (zoom = centerOn(zoom, point, fitted, stageSize))}
		/>
	{/if}
</div>

<style>
	.stage {
		position: relative;
		width: 100%;
		height: 100%;
		display: grid;
		place-items: center;
		overflow: hidden;
		touch-action: none;
	}

	.stage.zoomed {
		cursor: grab;
	}

	.stage.dragging {
		cursor: grabbing;
	}

	/* The image and its overlay share one cell, both centred. */
	img,
	.overlay {
		grid-area: 1 / 1;
	}

	img {
		max-width: none;
		user-select: none;
		transition: transform var(--duration-fast) ease-out;
	}

	.overlay {
		position: relative;
		pointer-events: none;
		transition: transform var(--duration-fast) ease-out;
	}

	.dragging img,
	.dragging .overlay {
		transition: none;
	}
</style>
