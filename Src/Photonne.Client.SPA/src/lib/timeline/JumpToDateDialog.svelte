<script lang="ts">
	import { untrack } from 'svelte';
	import Dialog from '#lib/components/Dialog.svelte';
	import { monthTitle } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { monthsOf, yearsWithPhotos, type MonthCount } from './jump.js';

	interface Props {
		/** The timeline's months with their counts. */
		months: readonly MonthCount[];
		/** The section on screen (`yyyy-MM` or `yyyy`): its year opens preselected. */
		current: string | null;
		onjump: (monthKey: string) => void;
		onclose: () => void;
	}

	let { months, current, onjump, onclose }: Props = $props();

	const years = $derived(yearsWithPhotos(months));
	// Mounted each time it opens, so the year starts at what is on screen.
	let year = $state(
		untrack(() => {
			const shown = current ? Number(current.slice(0, 4)) : NaN;
			return years.includes(shown) ? shown : (years[0] ?? 0);
		})
	);
	const grid = $derived(monthsOf(months, year));

	const monthName = new Intl.DateTimeFormat(getLocale(), { month: 'long', timeZone: 'UTC' });
	const count = new Intl.NumberFormat(getLocale());
</script>

<Dialog open title={m.timeline_jump_title()} {onclose} width="480px">
	<div class="jump">
		<label class="year">
			<span>{m.timeline_jump_year()}</span>
			<select bind:value={year}>
				{#each years as option (option)}<option value={option}>{option}</option>{/each}
			</select>
		</label>

		<ul class="months" aria-label={m.timeline_jump_months({ year })}>
			{#each grid as month (month.key)}
				<li>
					<button
						type="button"
						disabled={month.count === 0}
						aria-label={month.count
							? m.timeline_jump_month({ month: monthTitle(month.key), count: month.count })
							: m.timeline_jump_month_empty({ month: monthTitle(month.key) })}
						onclick={() => onjump(month.key)}
					>
						<span class="name">
							{monthName.format(Date.UTC(year, month.month - 1, 1))}
						</span>
						<span class="count">{month.count ? count.format(month.count) : '—'}</span>
					</button>
				</li>
			{/each}
		</ul>
		<p class="hint">{m.timeline_jump_hint()}</p>
	</div>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
	{/snippet}
</Dialog>

<style>
	.jump {
		display: grid;
		gap: var(--space-4);
	}

	.year {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		font-weight: 600;
		font-size: var(--font-size-sm);
	}

	select {
		height: 36px;
		padding: 0 var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		font-weight: 400;
	}

	.months {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.months button {
		display: grid;
		justify-items: center;
		gap: 2px;
		width: 100%;
		padding: var(--space-2) var(--space-1);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.months button:hover:not(:disabled) {
		background: var(--color-surface);
		border-color: var(--color-accent);
	}

	.months button:disabled {
		opacity: 0.45;
		cursor: default;
	}

	.name {
		font-weight: 600;
	}

	.name::first-letter {
		text-transform: uppercase;
	}

	.count {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		font-variant-numeric: tabular-nums;
	}

	.hint {
		margin: 0;
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	@media (max-width: 480px) {
		.months {
			grid-template-columns: repeat(3, minmax(0, 1fr));
		}
	}
</style>
