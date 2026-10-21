<script lang="ts" module>
	import { m } from '#lib/paraglide/messages.js';
	import { sizeLevel } from './zoom.js';

	const LEVEL_NAMES = [
		m.size_level_0,
		m.size_level_1,
		m.size_level_2,
		m.size_level_3,
		m.size_level_4
	];
	const LEVEL_MARKS = ['XS', 'S', 'M', 'L', 'XL'];

	/** "Mediano" for a size of 50: the name of its level. */
	export function sizeName(size: number) {
		return LEVEL_NAMES[sizeLevel(size)]();
	}
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { MAX_SIZE, MIN_SIZE, SIZE_LEVELS, SIZE_STEP, stepSize } from './zoom.js';

	interface Props {
		/** 0–100, on one of the five levels (zoom.ts). */
		value: number;
		onchange: (size: number) => void;
		/** What is being sized: the photos' thumbnails by default. */
		label?: string;
		smallerLabel?: string;
		largerLabel?: string;
		/** The levels' marks (XS … XL) under the stops, as in a menu. */
		marks?: boolean;
	}

	let {
		value,
		onchange,
		label = m.timeline_zoom(),
		smallerLabel = m.timeline_zoom_out_hint(),
		largerLabel = m.timeline_zoom_in_hint(),
		marks = false
	}: Props = $props();

	const REMOVE = 'M19 13H5v-2h14v2z';
	let announcement = $state('');

	function set(next: number) {
		if (next === value) return;
		onchange(next);
		announcement = m.size_changed({ level: sizeName(next) });
	}
</script>

<!--
	−, a slider that stops on the five levels, and +. On a phone the slider
	goes away outside a menu: the buttons are enough there.
-->
<div class="size" class:marks role="group" aria-label={label}>
	<div class="row">
		<button
			type="button"
			class="icon-btn sm"
			aria-label={smallerLabel}
			title={smallerLabel}
			disabled={value <= MIN_SIZE}
			onclick={() => set(stepSize(value, -1))}
		>
			<Icon path={REMOVE} size={18} />
		</button>
		<div class="track" style:--fill="{(value / MAX_SIZE) * 100}%">
			<span class="stops" aria-hidden="true">
				{#each SIZE_LEVELS as level (level)}
					<span class="stop" class:passed={level <= value}></span>
				{/each}
			</span>
			<input
				type="range"
				min={MIN_SIZE}
				max={MAX_SIZE}
				step={SIZE_STEP}
				{value}
				aria-label={label}
				aria-valuetext={m.size_value({ level: sizeName(value) })}
				oninput={(event) => set(Number(event.currentTarget.value))}
			/>
		</div>
		<button
			type="button"
			class="icon-btn sm"
			aria-label={largerLabel}
			title={largerLabel}
			disabled={value >= MAX_SIZE}
			onclick={() => set(stepSize(value, 1))}
		>
			<Icon name="add" size={18} />
		</button>
	</div>
	{#if marks}
		<div class="names" aria-hidden="true">
			{#each LEVEL_MARKS as mark, index (mark)}
				<span class:current={index * SIZE_STEP === value}>{mark}</span>
			{/each}
		</div>
	{/if}
	<p class="visually-hidden" role="status">{announcement}</p>
</div>

<style>
	.size {
		display: inline-grid;
		gap: 2px;
	}

	.row {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		height: var(--control-h-sm);
		padding: 0 2px;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-control);
	}

	/* In a menu: no frame, the full width. */
	.marks {
		display: grid;
	}

	.marks .row {
		border: 0;
		padding: 0;
	}

	.size :global(.icon-btn.sm) {
		width: calc(var(--control-h-sm) - 4px);
		height: calc(var(--control-h-sm) - 4px);
		border-radius: calc(var(--radius-control) - 2px);
	}

	/* A rail with a dot on each level, filled up to the size, under a
	   native range input that keeps the keys, the pointer and the a11y. */
	.track {
		position: relative;
		display: grid;
		align-items: center;
		width: 120px;
		height: 24px;
		margin: 0 var(--space-1);
	}

	.marks .track {
		flex: 1;
		width: auto;
	}

	.stops {
		position: absolute;
		inset: 0 9px;
		display: flex;
		align-items: center;
		justify-content: space-between;
		pointer-events: none;
	}

	.stops::before,
	.stops::after {
		content: '';
		position: absolute;
		left: 0;
		height: 4px;
		border-radius: 4px;
	}

	.stops::before {
		right: 0;
		background: color-mix(in srgb, var(--color-text) 15%, transparent);
	}

	.stops::after {
		width: var(--fill);
		background: var(--color-primary-fill);
	}

	.stop {
		position: relative;
		z-index: 1;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--color-surface-raised);
		box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--color-text) 25%, transparent);
	}

	.stop.passed {
		background: var(--color-primary-fill);
		box-shadow: none;
	}

	input {
		position: relative;
		z-index: 2;
		width: 100%;
		height: 24px;
		margin: 0;
		background: transparent;
		cursor: pointer;
		appearance: none;
	}

	input::-webkit-slider-runnable-track {
		height: 24px;
		background: transparent;
	}

	input::-moz-range-track {
		height: 24px;
		background: transparent;
	}

	input::-webkit-slider-thumb {
		width: 18px;
		height: 18px;
		margin-top: 3px;
		border: 3px solid var(--color-surface-raised);
		border-radius: 50%;
		background: var(--color-primary-fill);
		box-shadow: 0 1px 4px rgb(0 0 0 / 0.3);
		appearance: none;
	}

	input::-moz-range-thumb {
		width: 12px;
		height: 12px;
		border: 3px solid var(--color-surface-raised);
		border-radius: 50%;
		background: var(--color-primary-fill);
		box-shadow: 0 1px 4px rgb(0 0 0 / 0.3);
	}

	input:focus-visible {
		outline: none;
	}

	input:focus-visible::-webkit-slider-thumb {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	.names {
		display: flex;
		justify-content: space-between;
		padding: 0 calc(var(--control-h-sm) + var(--space-1) + 5px);
		font-size: var(--font-size-2xs);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	.names span {
		width: 2ch;
		text-align: center;
	}

	.names .current {
		color: var(--color-text);
	}

	@media (max-width: 720px) {
		.size:not(.marks) .track {
			display: none;
		}
	}
</style>
