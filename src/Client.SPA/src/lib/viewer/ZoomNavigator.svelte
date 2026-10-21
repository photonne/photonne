<script lang="ts">
	import type { ImageRect } from './zoom.js';

	interface Props {
		/** The photo, small: a thumbnail is enough. */
		src: string;
		/** Width / height of the photo. */
		aspect: number;
		/** The part of the photo on screen. */
		frame: ImageRect;
		/** Centre the view on this point of the photo (fractions of it). */
		onpan: (point: { x: number; y: number }) => void;
	}

	let { src, aspect, frame, onpan }: Props = $props();

	let map = $state<HTMLDivElement>();
	let dragging = $state(false);

	function panTo(event: PointerEvent) {
		const box = map!.getBoundingClientRect();
		onpan({
			x: Math.min(1, Math.max(0, (event.clientX - box.left) / box.width)),
			y: Math.min(1, Math.max(0, (event.clientY - box.top) / box.height))
		});
	}

	// Its own pointer: the photo under it neither drags nor swipes.
	function onpointerdown(event: PointerEvent) {
		if (event.pointerType === 'mouse' && event.button !== 0) return;
		event.stopPropagation();
		map!.setPointerCapture(event.pointerId);
		dragging = true;
		panTo(event);
	}

	function onpointermove(event: PointerEvent) {
		if (!dragging) return;
		event.stopPropagation();
		panTo(event);
	}

	function onpointerup(event: PointerEvent) {
		event.stopPropagation();
		dragging = false;
	}
</script>

<!--
	The whole photo and, framed on it, the part that the zoom shows: a click
	or a drag on it moves the view there. A help for the pointer; the keys
	and the gestures pan the photo itself.
-->
<div class="navigator" aria-hidden="true">
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		bind:this={map}
		class="map"
		class:dragging
		style:--aspect={aspect}
		{onpointerdown}
		{onpointermove}
		{onpointerup}
		onpointercancel={onpointerup}
		ondblclick={(event) => event.stopPropagation()}
	>
		<img {src} alt="" draggable="false" />
		<span
			class="frame"
			style:left="{frame.x * 100}%"
			style:top="{frame.y * 100}%"
			style:width="{frame.width * 100}%"
			style:height="{frame.height * 100}%"
		></span>
	</div>
</div>

<style>
	/* A small glass card at the photo's lower left, clear of the actions. */
	.navigator {
		position: absolute;
		left: var(--float-margin);
		bottom: calc(env(safe-area-inset-bottom, 0px) + var(--space-6));
		z-index: 2;
		padding: 4px;
		border: 1px solid rgb(255 255 255 / 0.1);
		border-radius: var(--radius-md);
		background: rgb(36 36 40 / 0.72);
		-webkit-backdrop-filter: blur(20px);
		backdrop-filter: blur(20px);
		box-shadow: var(--shadow-nav);
		animation: navigator-in var(--duration-normal) var(--ease-standard);
	}

	/* At most 168×120 (112×84 on a phone), in the photo's shape. */
	.map {
		--box-w: 168px;
		--box-h: 120px;
		position: relative;
		width: min(var(--box-w), calc(var(--box-h) * var(--aspect)));
		aspect-ratio: var(--aspect);
		overflow: hidden;
		border-radius: calc(var(--radius-md) - 4px);
		cursor: pointer;
		touch-action: none;
	}

	img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
		user-select: none;
	}

	/* What is on screen, bright; the rest of the photo, dimmed around it. */
	.frame {
		position: absolute;
		border: 2px solid #fff;
		border-radius: 3px;
		box-shadow: 0 0 0 999px rgb(0 0 0 / 0.5);
		pointer-events: none;
		transition:
			left var(--duration-fast) ease-out,
			top var(--duration-fast) ease-out,
			width var(--duration-fast) ease-out,
			height var(--duration-fast) ease-out;
	}

	.dragging .frame {
		transition: none;
	}

	/* A phone: smaller, and above the actions, which take the bottom's width. */
	@media (max-width: 700px) {
		.navigator {
			bottom: calc(env(safe-area-inset-bottom, 0px) + var(--space-6) + 68px);
		}

		.map {
			--box-w: 112px;
			--box-h: 84px;
		}
	}

	@keyframes navigator-in {
		from {
			opacity: 0;
			transform: translateY(6px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.navigator {
			animation: none;
		}

		.frame {
			transition: none;
		}
	}
</style>
