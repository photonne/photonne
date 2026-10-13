<script lang="ts">
	import { formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { hiddenSpaces } from './hidden-spaces.svelte.js';
	import type { HideableSpace, Space, SpaceBytes } from './spaces.js';

	interface Props {
		bytes: SpaceBytes;
		/** Accessible name of the bar. */
		label: string;
		/** Share of the track the filled part takes (0..1), to compare rows. */
		scale?: number;
		height?: number;
		/** The legend doubles as the show/hide toggles. */
		legend?: boolean;
	}

	let { bytes, label, scale = 1, height = 12, legend = true }: Props = $props();

	const names: Record<Space, () => string> = {
		personal: m.storage_space_personal,
		shared: m.storage_space_shared,
		library: m.storage_space_library
	};
	const order: Space[] = ['personal', 'shared', 'library'];
	const total = $derived(bytes.personal + bytes.shared + bytes.library);
	const segments = $derived(order.filter((space) => bytes[space] > 0));
	const isHidden = (space: Space) => space !== 'personal' && hiddenSpaces.has(space);
	const description = $derived(
		segments
			.map(
				(space) =>
					`${names[space]()} ${formatBytes(bytes[space])}${isHidden(space) ? ` (${m.storage_space_hidden()})` : ''}`
			)
			.join(', ')
	);
</script>

<div class="space-bar">
	<div class="track" style:height="{height}px" role="img" aria-label="{label}: {description}">
		<div class="fill" style:width="{Math.max(0, Math.min(1, scale)) * 100}%">
			{#each segments as space (space)}
				<span class="seg {space}" class:hidden={isHidden(space)} style:flex-grow={bytes[space]}
				></span>
			{/each}
		</div>
	</div>
	{#if legend && total > 0}
		<div class="legend">
			{#each segments as space (space)}
				{#if space === 'personal'}
					<span class="item"
						><i class="swatch {space}"></i>{names[space]()}
						<span class="num muted">{formatBytes(bytes[space])}</span></span
					>
				{:else}
					{@const hideable = space as HideableSpace}
					<button
						type="button"
						class="item toggle"
						class:off={isHidden(space)}
						aria-pressed={!isHidden(space)}
						title={isHidden(space) ? m.storage_space_show() : m.storage_space_hide()}
						onclick={() => hiddenSpaces.toggle(hideable)}
					>
						<i class="swatch {space}" class:hidden={isHidden(space)}></i>{names[space]()}
						<span class="num muted">{formatBytes(bytes[space])}</span>
					</button>
				{/if}
			{/each}
		</div>
	{/if}
</div>

<style>
	.space-bar {
		display: grid;
		gap: var(--space-1);
		min-width: 0;
	}

	.track {
		overflow: hidden;
		border-radius: 999px;
		background: var(--color-placeholder);
	}

	.fill {
		display: flex;
		gap: 2px;
		height: 100%;
		transition: width var(--duration-normal);
	}

	.seg {
		flex-basis: 0;
		min-width: 2px;
	}

	.personal {
		background: var(--color-space-personal);
	}

	.shared {
		background: var(--color-space-shared);
	}

	.library {
		background: var(--color-space-library);
	}

	/* Hidden: still there, but hatched and greyed so it reads as "not counted". */
	.hidden {
		background: repeating-linear-gradient(
			135deg,
			var(--color-space-hidden) 0 2px,
			transparent 2px 5px
		);
		opacity: 0.7;
	}

	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-4);
		font-size: var(--font-size-sm);
	}

	.item {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.toggle {
		margin: calc(-1 * var(--space-1));
		padding: var(--space-1);
		border: 0;
		border-radius: var(--radius-sm);
		background: none;
		color: inherit;
		font: inherit;
		cursor: pointer;
	}

	.toggle:hover {
		background: var(--color-hover);
	}

	.toggle:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
	}

	.toggle.off {
		color: var(--color-text-muted);
		text-decoration: line-through;
	}

	.swatch {
		flex: none;
		width: 10px;
		height: 10px;
		border-radius: 2px;
	}

	.swatch.hidden {
		outline: 1px solid var(--color-space-hidden);
		outline-offset: -1px;
	}

	.muted {
		color: var(--color-text-muted);
	}

	.num {
		font-variant-numeric: tabular-nums;
	}

	@media (prefers-reduced-motion: reduce) {
		.fill {
			transition: none;
		}
	}
</style>
