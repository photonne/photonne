<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import type { ScrubberMarker } from './scrubber.js';

	interface Props {
		markers: readonly ScrubberMarker[];
		/** Current scroll position, 0 (newest) to 1 (oldest). */
		fraction: number;
		currentLabel: string;
		labelAt: (fraction: number) => string;
		onseek: (fraction: number) => void;
	}

	let { markers, fraction, currentLabel, labelAt, onseek }: Props = $props();

	let rail = $state<HTMLDivElement>();
	let railHeight = $state(0);
	let hover = $state<number | null>(null);
	let dragging = $state(false);

	function fractionAt(event: PointerEvent) {
		const box = rail!.getBoundingClientRect();
		return Math.min(1, Math.max(0, (event.clientY - box.top) / box.height));
	}

	function onpointerdown(event: PointerEvent) {
		if (event.button !== 0) return;
		event.preventDefault();
		rail!.setPointerCapture(event.pointerId);
		dragging = true;
		onseek(fractionAt(event));
	}

	function onpointermove(event: PointerEvent) {
		hover = fractionAt(event);
		if (dragging) onseek(hover);
	}

	function onpointerup(event: PointerEvent) {
		dragging = false;
		rail!.releasePointerCapture(event.pointerId);
	}

	function onkeydown(event: KeyboardEvent) {
		const steps: Record<string, number> = {
			ArrowUp: -0.02,
			ArrowDown: 0.02,
			PageUp: -0.1,
			PageDown: 0.1
		};
		if (event.key in steps) onseek(Math.min(1, Math.max(0, fraction + steps[event.key])));
		else if (event.key === 'Home') onseek(0);
		else if (event.key === 'End') onseek(1);
		else return;
		event.preventDefault();
	}

	const bubble = $derived(
		dragging
			? { at: fraction, text: currentLabel }
			: hover !== null
				? { at: hover, text: labelAt(hover) }
				: null
	);
</script>

<div
	class="rail"
	bind:this={rail}
	bind:clientHeight={railHeight}
	role="slider"
	tabindex="0"
	aria-label={m.grid_scrubber()}
	aria-orientation="vertical"
	aria-valuemin={0}
	aria-valuemax={100}
	aria-valuenow={Math.round(fraction * 100)}
	aria-valuetext={currentLabel}
	{onpointerdown}
	{onpointermove}
	{onpointerup}
	onpointerleave={() => (hover = null)}
	{onkeydown}
>
	{#each markers as marker (marker.label)}
		<span class="year" style:top="{marker.fraction * railHeight}px">{marker.label}</span>
	{/each}

	<span class="thumb" style:top="{fraction * railHeight}px"></span>

	{#if bubble && bubble.text}
		<span class="bubble" style:top="{bubble.at * railHeight}px">{bubble.text}</span>
	{/if}
</div>

<style>
	.rail {
		position: absolute;
		top: 16px;
		bottom: 16px;
		right: 0;
		width: 56px;
		cursor: ns-resize;
		touch-action: none;
		user-select: none;
	}

	.rail:focus-visible {
		outline-offset: -2px;
	}

	.year {
		position: absolute;
		right: 12px;
		transform: translateY(-50%);
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		pointer-events: none;
	}

	.thumb {
		position: absolute;
		right: 4px;
		width: 4px;
		height: 32px;
		transform: translateY(-50%);
		border-radius: 2px;
		background: var(--color-accent);
		pointer-events: none;
	}

	.bubble {
		position: absolute;
		right: 60px;
		transform: translateY(-50%);
		padding: var(--space-1) var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		font-size: var(--font-size-sm);
		white-space: nowrap;
		pointer-events: none;
	}

	.bubble::first-letter {
		text-transform: uppercase;
	}
</style>
