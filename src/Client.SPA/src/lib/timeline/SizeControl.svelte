<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { MAX_SIZE, MIN_SIZE, stepSize } from './zoom.js';

	interface Props {
		/** 0–100 (zoom.ts). */
		value: number;
		onchange: (size: number) => void;
		/** What is being sized: the photos' thumbnails by default. */
		label?: string;
		smallerLabel?: string;
		largerLabel?: string;
	}

	let {
		value,
		onchange,
		label = m.timeline_zoom(),
		smallerLabel = m.timeline_zoom_out_hint(),
		largerLabel = m.timeline_zoom_in_hint()
	}: Props = $props();

	const REMOVE = 'M19 13H5v-2h14v2z';
	let announcement = $state('');

	function step(direction: 1 | -1) {
		const next = stepSize(value, direction);
		if (next === value) return;
		onchange(next);
		announcement = m.size_changed({ percent: next });
	}
</script>

<!--
	−, a slider and +. On a phone the slider goes away: the buttons are
	enough there and the header has no room for it.
-->
<div class="size" role="group" aria-label={label}>
	<button
		type="button"
		class="icon-btn sm"
		aria-label={smallerLabel}
		title={smallerLabel}
		disabled={value <= MIN_SIZE}
		onclick={() => step(-1)}
	>
		<Icon path={REMOVE} size={18} />
	</button>
	<input
		type="range"
		min={MIN_SIZE}
		max={MAX_SIZE}
		step="1"
		{value}
		aria-label={label}
		aria-valuetext={m.size_value({ percent: value })}
		oninput={(event) => onchange(Number(event.currentTarget.value))}
	/>
	<button
		type="button"
		class="icon-btn sm"
		aria-label={largerLabel}
		title={largerLabel}
		disabled={value >= MAX_SIZE}
		onclick={() => step(1)}
	>
		<Icon name="add" size={18} />
	</button>
	<p class="visually-hidden" role="status">{announcement}</p>
</div>

<style>
	.size {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		height: var(--control-h-sm);
		padding: 0 2px;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-control);
	}

	.size :global(.icon-btn.sm) {
		width: calc(var(--control-h-sm) - 4px);
		height: calc(var(--control-h-sm) - 4px);
		border-radius: calc(var(--radius-control) - 2px);
	}

	input {
		width: 120px;
		margin: 0 var(--space-1);
		accent-color: var(--color-accent);
		border-radius: var(--radius-control);
		cursor: pointer;
	}

	@media (max-width: 720px) {
		input {
			display: none;
		}
	}
</style>
