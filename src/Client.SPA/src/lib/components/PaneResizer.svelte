<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import { clampWidth, saveWidth } from './pane-width.js';

	interface Props {
		/** The pane's width in px, bound by the host. */
		width: number;
		min: number;
		max: number;
		/** Restored on double click. */
		initial: number;
		/** localStorage key that remembers the width. */
		storageKey: string;
		/** Id of the pane it resizes (aria-controls). */
		controls: string;
	}

	let { width = $bindable(), min, max, initial, storageKey, controls }: Props = $props();

	let dragging = $state(false);
	let startX = 0;
	let startWidth = 0;

	function set(value: number) {
		width = clampWidth(value, min, max);
		saveWidth(storageKey, width);
	}

	function onpointerdown(event: PointerEvent) {
		if (event.button !== 0) return;
		event.preventDefault();
		(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
		dragging = true;
		startX = event.clientX;
		startWidth = width;
	}

	function onpointermove(event: PointerEvent) {
		if (dragging) set(startWidth + event.clientX - startX);
	}

	function onpointerup() {
		dragging = false;
	}

	// The WAI-ARIA window splitter pattern: arrows move it, Home/End go to the ends.
	function onkeydown(event: KeyboardEvent) {
		const step = event.shiftKey ? 64 : 16;
		const next =
			event.key === 'ArrowLeft'
				? width - step
				: event.key === 'ArrowRight'
					? width + step
					: event.key === 'Home'
						? min
						: event.key === 'End'
							? max
							: null;
		if (next === null) return;
		event.preventDefault();
		set(next);
	}
</script>

<!-- A focusable separator is the ARIA window-splitter pattern: interactive,
     though the linter only knows the static kind. -->
<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<div
	class="resizer"
	class:dragging
	role="separator"
	aria-orientation="vertical"
	aria-label={m.pane_resize()}
	aria-controls={controls}
	aria-valuenow={width}
	aria-valuemin={min}
	aria-valuemax={max}
	tabindex="0"
	title={m.pane_resize_hint()}
	{onpointerdown}
	{onpointermove}
	{onpointerup}
	onpointercancel={onpointerup}
	ondblclick={() => set(initial)}
	{onkeydown}
></div>

<style>
	.resizer {
		position: relative;
		width: 9px;
		margin: 0 -4px;
		z-index: 1;
		cursor: col-resize;
		touch-action: none;
	}

	/* The visible line: the pane's border, highlighted while in use. */
	.resizer::after {
		content: '';
		position: absolute;
		inset: 0 4px;
		background: var(--color-border);
		transition: background var(--duration-fast);
	}

	.resizer:hover::after,
	.resizer:focus-visible::after,
	.dragging::after {
		inset: 0 3px;
		background: var(--color-accent);
	}

	.resizer:focus-visible {
		outline: none;
	}
</style>
