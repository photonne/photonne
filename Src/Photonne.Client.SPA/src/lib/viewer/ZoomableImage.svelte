<script lang="ts">
	import { clampPan, fitSize, IDENTITY, zoomAt, type ZoomState } from './zoom.js';

	interface Props {
		/** Shown at once (usually the Large thumbnail, already cached). */
		previewSrc: string;
		/** Swapped in when loaded: full resolution for zooming. */
		fullSrc: string;
		alt: string;
		/** Exposed so the viewer can route keys (+, -, 0) and know when to pan. */
		zoom?: ZoomState;
	}

	let { previewSrc, fullSrc, alt, zoom = $bindable(IDENTITY) }: Props = $props();

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

	function focusPoint(event: MouseEvent) {
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

	function onpointerdown(event: PointerEvent) {
		if (zoom.scale === 1 || event.button !== 0) return;
		stage!.setPointerCapture(event.pointerId);
		drag = { x: zoom.x, y: zoom.y, startX: event.clientX, startY: event.clientY };
	}

	function onpointermove(event: PointerEvent) {
		if (!drag) return;
		zoom = clampPan(
			{ ...zoom, x: drag.x + event.clientX - drag.startX, y: drag.y + event.clientY - drag.startY },
			fitted,
			stageSize
		);
	}

	function onpointerup() {
		drag = null;
	}
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="stage"
	class:zoomed={zoom.scale > 1}
	class:dragging={drag !== null}
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
		style:transform="translate({zoom.x}px, {zoom.y}px) scale({zoom.scale})"
	/>
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

	img {
		max-width: none;
		user-select: none;
		transition: transform var(--duration-fast) ease-out;
	}

	.dragging img {
		transition: none;
	}
</style>
