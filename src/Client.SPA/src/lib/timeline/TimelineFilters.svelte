<script lang="ts">
	import { untrack } from 'svelte';
	import ChromeMenu from '#lib/components/ui/ChromeMenu.svelte';
	import { longDate } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { groupingLabels } from './GroupingControl.svelte';
	import { dateRange, type TimelineOrder } from './timeline-filter.js';
	import type { TimelineView } from './timeline-view.svelte.js';
	import { groupings, type Grouping } from './zoom.js';

	interface Props {
		view: TimelineView;
		/** The days the library spans, `yyyy-MM-dd`, to bound the pickers. */
		first: string | null;
		last: string | null;
		/** Opens "go to a date" (the menu closes first). */
		onjump: () => void;
		/** Something to say to screen readers after a change. */
		onannounce: (text: string) => void;
	}

	let { view, first, last, onjump, onannounce }: Props = $props();

	// Material "filter_alt" (Apache 2.0): a funnel.
	const FUNNEL =
		'M4.25 5.61C6.27 8.2 10 13 10 13v6c0 .55.45 1 1 1h2c.55 0 1-.45 1-1v-6s3.72-4.8 5.74-7.39A.998.998 0 0 0 18.95 4H5.04c-.83 0-1.3.95-.79 1.61z';
	const EVENT =
		'M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z';

	type Mode = 'all' | 'day' | 'range';
	let menu = $state<ChromeMenu>();
	// The pickers keep what was typed until it makes a valid choice; they
	// start from the range in force when the menu is made.
	const initial = untrack(() => view.range);
	let mode = $state<Mode>(initial ? (initial.from === initial.to ? 'day' : 'range') : 'all');
	let from = $state(initial?.from ?? '');
	let to = $state(initial?.to ?? '');

	function apply() {
		if (mode === 'all') view.setRange(null);
		else {
			const range = mode === 'day' ? dateRange(from, from) : dateRange(from, to);
			if (!range) return;
			view.setRange(range);
		}
		const range = view.range;
		onannounce(
			!range
				? m.filters_dates_all()
				: range.from === range.to
					? m.filters_active_day({ date: longDate(range.from) })
					: m.filters_active_range({ from: longDate(range.from), to: longDate(range.to) })
		);
	}

	function setMode(next: Mode) {
		mode = next;
		if (next === 'all') apply();
		else if (from && (next === 'day' || to)) apply();
	}

	function setOrder(order: TimelineOrder) {
		view.setOrder(order);
		onannounce(order === 'oldest' ? m.filters_order_oldest() : m.filters_order_newest());
	}

	function setGrouping(grouping: Grouping) {
		view.setGrouping(grouping);
		onannounce(m.timeline_group_changed({ grouping: groupingLabels[grouping]() }));
	}

	function clear() {
		mode = 'all';
		from = '';
		to = '';
		view.setRange(null);
		view.setOrder('newest');
		onannounce(m.filters_clear());
	}

	const modes: { value: Mode; label: () => string }[] = [
		{ value: 'all', label: m.filters_dates_all },
		{ value: 'day', label: m.filters_dates_day },
		{ value: 'range', label: m.filters_dates_range }
	];
	const orders: { value: TimelineOrder; label: () => string }[] = [
		{ value: 'newest', label: m.filters_order_newest },
		{ value: 'oldest', label: m.filters_order_oldest }
	];
</script>

<ChromeMenu bind:this={menu} label={m.filters()} iconPath={FUNNEL} active={view.filtered}>
	<button
		type="button"
		class="jump"
		aria-keyshortcuts="G"
		onclick={() => {
			menu?.close();
			onjump();
		}}
	>
		<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"
			><path d={EVENT} fill="currentColor" /></svg
		>
		{m.filters_jump()}
		<kbd>G</kbd>
	</button>

	<fieldset>
		<legend>{m.filters_dates()}</legend>
		<div class="segmented">
			{#each modes as option (option.value)}
				<label>
					<input
						type="radio"
						name="filter-dates"
						checked={mode === option.value}
						onchange={() => setMode(option.value)}
					/>
					{option.label()}
				</label>
			{/each}
		</div>
		{#if mode !== 'all'}
			<div class="days">
				<label class="field">
					{mode === 'day' ? m.filters_day() : m.filters_from()}
					<input type="date" min={first} max={last} bind:value={from} onchange={apply} />
				</label>
				{#if mode === 'range'}
					<label class="field">
						{m.filters_to()}
						<input type="date" min={first} max={last} bind:value={to} onchange={apply} />
					</label>
				{/if}
			</div>
		{/if}
	</fieldset>

	<fieldset>
		<legend>{m.filters_group()}</legend>
		<div class="segmented">
			{#each groupings as option (option)}
				<label>
					<input
						type="radio"
						name="filter-grouping"
						checked={view.grouping === option}
						onchange={() => setGrouping(option)}
					/>
					{groupingLabels[option]()}
				</label>
			{/each}
		</div>
	</fieldset>

	<fieldset>
		<legend>{m.filters_order()}</legend>
		<div class="segmented">
			{#each orders as option (option.value)}
				<label>
					<input
						type="radio"
						name="filter-order"
						checked={view.order === option.value}
						onchange={() => setOrder(option.value)}
					/>
					{option.label()}
				</label>
			{/each}
		</div>
	</fieldset>

	{#if view.filtered}
		<button type="button" class="btn ghost sm clear" onclick={clear}>{m.filters_clear()}</button>
	{/if}
</ChromeMenu>

<style>
	.jump {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 40px;
		padding: 0 var(--space-3);
		border: 0;
		border-radius: var(--radius-pill);
		background: var(--color-active-pill);
		color: inherit;
		font: inherit;
		font-weight: 500;
		cursor: pointer;
	}

	.jump:hover {
		background: var(--color-hover);
	}

	kbd {
		margin-left: auto;
		padding: 0 var(--space-1);
		border: 1px solid color-mix(in srgb, currentColor 25%, transparent);
		border-radius: var(--radius-xs);
		font: inherit;
		font-size: var(--font-size-2xs);
		line-height: 18px;
		color: var(--color-text-muted);
	}

	fieldset {
		display: grid;
		gap: var(--space-2);
		min-width: 0;
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend {
		margin-bottom: var(--space-2);
		padding: 0;
		font-size: var(--font-size-2xs);
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--color-text-muted);
	}

	.segmented {
		display: flex;
	}

	.segmented > label {
		flex: 1;
		justify-content: center;
	}

	.days {
		display: flex;
		gap: var(--space-2);
	}

	.field {
		flex: 1;
		display: grid;
		gap: var(--space-1);
		min-width: 0;
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	.field input {
		width: 100%;
	}

	.clear {
		justify-self: start;
	}
</style>
