<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import { compact, count, shortMonth } from './format.js';
	import { niceMax, type GrowthMonth } from './stats.js';

	/**
	 * Stacked monthly bars (photos under videos). The chart is one focus stop:
	 * arrow keys, Home and End move between months and the readout under it
	 * says the numbers, as hovering does with the mouse.
	 */
	let { months, label }: { months: GrowthMonth[]; label: string } = $props();

	// Drawn at the size it is shown (no scaling), so the axis text keeps the
	// token's size at any width.
	let width = $state(720);
	const WIDTH = $derived(Math.max(280, Math.round(width)));
	const HEIGHT = 220;
	const PAD = { top: 8, right: 8, bottom: 24, left: 44 };
	const plotW = $derived(WIDTH - PAD.left - PAD.right);
	const plotH = HEIGHT - PAD.top - PAD.bottom;

	let active = $state<number | null>(null);

	const max = $derived(niceMax(Math.max(0, ...months.map((p) => p.photos + p.videos))));
	const ticks = $derived([0, 0.25, 0.5, 0.75, 1].map((f) => f * max));
	const slot = $derived(months.length ? plotW / months.length : plotW);
	const barW = $derived(Math.max(2, slot * 0.68));
	const y = (value: number) => PAD.top + plotH - (value / max) * plotH;
	// One label every few months so they never collide (about 64 px each).
	const labelEvery = $derived(
		Math.max(1, Math.ceil(months.length / Math.max(2, Math.floor(plotW / 64))))
	);
	const total = $derived(months.reduce((sum, p) => sum + p.photos + p.videos, 0));
	const shown = $derived(active !== null ? months[active] : null);

	function onkeydown(event: KeyboardEvent) {
		if (!months.length) return;
		const last = months.length - 1;
		const current = active ?? last;
		const next =
			event.key === 'ArrowLeft'
				? Math.max(0, current - 1)
				: event.key === 'ArrowRight'
					? Math.min(last, current + 1)
					: event.key === 'Home'
						? 0
						: event.key === 'End'
							? last
							: null;
		if (next === null) return;
		event.preventDefault();
		active = next;
	}
</script>

<div class="chart">
	<!-- A slider over the months: the readout is its value. -->
	<div
		class="plot"
		bind:clientWidth={width}
		role="slider"
		tabindex="0"
		aria-label={m.admin_dash_growth_aria({ label, total: count(total) })}
		aria-valuemin={0}
		aria-valuemax={Math.max(0, months.length - 1)}
		aria-valuenow={active ?? months.length - 1}
		aria-valuetext={shown
			? `${shortMonth(shown.key)}: ${m.admin_dash_growth_readout({ photos: count(shown.photos), videos: count(shown.videos) })}`
			: undefined}
		{onkeydown}
		onfocus={() => (active ??= months.length - 1)}
		onblur={() => (active = null)}
		onpointerleave={() => (active = null)}
	>
		<svg viewBox="0 0 {WIDTH} {HEIGHT}" width={WIDTH} height={HEIGHT} aria-hidden="true">
			{#each ticks as tick (tick)}
				<line class="grid" x1={PAD.left} x2={WIDTH - PAD.right} y1={y(tick)} y2={y(tick)} />
				<text class="axis" x={PAD.left - 6} y={y(tick)} text-anchor="end" dominant-baseline="middle"
					>{compact(tick)}</text
				>
			{/each}
			{#each months as month, i (month.key)}
				{@const x = PAD.left + i * slot + (slot - barW) / 2}
				<g class="bar" class:dim={active !== null && active !== i}>
					<rect
						class="photos"
						{x}
						y={y(month.photos)}
						width={barW}
						height={plotH + PAD.top - y(month.photos)}
					/>
					<rect
						class="videos"
						{x}
						y={y(month.photos + month.videos)}
						width={barW}
						height={y(month.photos) - y(month.photos + month.videos)}
					/>
					<!-- A full-height hit area so short bars are easy to point at. -->
					<rect
						class="hit"
						role="presentation"
						x={PAD.left + i * slot}
						y={PAD.top}
						width={slot}
						height={plotH}
						onpointerenter={() => (active = i)}
					/>
				</g>
				{#if i % labelEvery === (months.length - 1) % labelEvery}
					<text class="axis" x={x + barW / 2} y={HEIGHT - 6} text-anchor="middle"
						>{shortMonth(month.key)}</text
					>
				{/if}
			{/each}
		</svg>
	</div>
	<div class="foot">
		<span class="legend"><i class="photos"></i>{m.admin_dash_photos()}</span>
		<span class="legend"><i class="videos"></i>{m.admin_dash_videos()}</span>
		<span class="readout">
			{#if shown}
				<strong>{shortMonth(shown.key)}</strong>:
				{m.admin_dash_growth_readout({ photos: count(shown.photos), videos: count(shown.videos) })}
			{:else}
				{m.admin_dash_growth_hint()}
			{/if}
		</span>
	</div>
</div>

<style>
	.chart {
		display: grid;
		gap: var(--space-2);
	}

	.plot {
		border-radius: var(--radius-sm);
	}

	svg {
		display: block;
		max-width: 100%;
		overflow: visible;
		border-radius: var(--radius-sm);
	}

	.grid {
		stroke: var(--color-border);
		stroke-width: 1;
	}

	.axis {
		fill: var(--color-text-muted);
		font-size: var(--font-size-2xs);
		font-variant-numeric: tabular-nums;
	}

	.photos {
		fill: var(--admin-chart-photos);
		background: var(--admin-chart-photos);
	}

	.videos {
		fill: var(--admin-chart-videos);
		background: var(--admin-chart-videos);
	}

	.hit {
		fill: transparent;
	}

	.bar rect:not(.hit) {
		transition: opacity var(--duration-fast);
	}

	.bar.dim rect:not(.hit) {
		opacity: 0.35;
	}

	.foot {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-4);
		font-size: var(--font-size-sm);
	}

	.legend {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		color: var(--color-text-muted);
	}

	.legend i {
		width: 10px;
		height: 10px;
		border-radius: 2px;
	}

	.readout {
		margin-left: auto;
		color: var(--color-text-muted);
	}
</style>
