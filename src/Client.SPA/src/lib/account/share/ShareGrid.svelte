<script lang="ts">
	import { tick } from 'svelte';
	import type { SharedAssetDto } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { longDate } from '#lib/format.js';
	import { thumbnailSizeFor } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { moveFocus, type Direction } from '#lib/timeline/keyboard-nav.js';
	import { clampAspect, computeRows } from '#lib/timeline/justified-layout.js';
	import { aspectOf, isVideo, shareMediaUrl } from './share-api.js';

	interface Props {
		token: string;
		password: string | null;
		assets: readonly SharedAssetDto[];
		label: string;
		onopen: (asset: SharedAssetDto) => void;
	}

	let { token, password, assets, label, onopen }: Props = $props();

	const SPACING = 4;

	let width = $state(0);
	let focusedId = $state<string | null>(null);
	let grid = $state<HTMLDivElement>();

	// Same justified rows as the library grid. A shared album is a few hundred
	// photos at most, so it is laid out in full; images load lazily.
	const rows = $derived.by(() => {
		const targetRowHeight = width < 600 ? 140 : width < 1100 ? 190 : 230;
		const layout = computeRows(
			assets.map((asset) => clampAspect(aspectOf(asset))),
			{ containerWidth: width, targetRowHeight, spacing: SPACING }
		);
		return layout.map((row, r) => {
			const items = assets.slice(row.start, row.start + row.count);
			const justified = r < layout.length - 1 || row.height !== targetRowHeight;
			return {
				height: row.height,
				justified,
				cells: items.map((asset) => ({ asset, width: clampAspect(aspectOf(asset)) * row.height }))
			};
		});
	});

	const idRows = $derived(rows.map((row) => row.cells.map((cell) => cell.asset.id)));

	const keys: Record<string, Direction> = {
		ArrowLeft: 'left',
		ArrowRight: 'right',
		ArrowUp: 'up',
		ArrowDown: 'down'
	};

	async function onkeydown(event: KeyboardEvent) {
		const direction = keys[event.key];
		if (!direction) return;
		event.preventDefault();
		const target = moveFocus(idRows, focusedId, direction);
		if (target) await focusItem(target);
	}

	/** Moves keyboard focus to a cell (e.g. back from the viewer). */
	export async function focusItem(id: string) {
		focusedId = id;
		await tick();
		grid?.querySelector<HTMLElement>(`[data-id="${CSS.escape(id)}"]`)?.focus();
	}

	function tabIndexOf(id: string) {
		return (focusedId ?? assets[0]?.id) === id ? 0 : -1;
	}

	function itemLabel(asset: SharedAssetDto) {
		const date = longDate(asset.fileCreatedAt);
		return isVideo(asset) ? m.grid_item_video({ date }) : m.grid_item_photo({ date });
	}
</script>

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<div
	class="grid"
	role="list"
	aria-label={label}
	bind:clientWidth={width}
	bind:this={grid}
	{onkeydown}
>
	{#each rows as row, r (r)}
		<div class="row" style:height="{row.height}px" class:ragged={!row.justified}>
			{#each row.cells as cell (cell.asset.id)}
				{@const size = thumbnailSizeFor(row.height)}
				<div
					role="listitem"
					class="cell"
					style:width={row.justified ? undefined : `${cell.width}px`}
					style:flex-grow={row.justified ? cell.width : 0}
				>
					<button
						type="button"
						data-id={cell.asset.id}
						tabindex={tabIndexOf(cell.asset.id)}
						aria-label={itemLabel(cell.asset)}
						onfocus={() => (focusedId = cell.asset.id)}
						onclick={() => onopen(cell.asset)}
					>
						<img
							src={shareMediaUrl(token, cell.asset.id, { thumbnail: size }, password)}
							alt=""
							loading="lazy"
							decoding="async"
							draggable="false"
							onload={(event) => event.currentTarget.classList.add('loaded')}
						/>
						{#if isVideo(cell.asset)}
							<span class="badge" aria-hidden="true"><Icon name="play" size={18} /></span>
						{/if}
					</button>
				</div>
			{/each}
		</div>
	{/each}
</div>

<style>
	.grid {
		display: grid;
		gap: 4px;
	}

	.row {
		display: flex;
		gap: 4px;
	}

	.cell {
		position: relative;
		flex-basis: 0;
		min-width: 0;
		overflow: hidden;
		background: var(--color-placeholder);
	}

	.ragged .cell {
		flex: none;
	}

	button {
		display: block;
		width: 100%;
		height: 100%;
		padding: 0;
		border: 0;
		background: none;
		cursor: pointer;
	}

	button:focus-visible {
		outline: 3px solid var(--color-focus);
		outline-offset: -3px;
	}

	img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
		opacity: 0;
		transition:
			opacity var(--duration-normal),
			transform var(--duration-normal);
	}

	img:global(.loaded) {
		opacity: 1;
	}

	button:hover img {
		transform: scale(1.03);
	}

	.badge {
		position: absolute;
		top: 6px;
		right: 6px;
		color: white;
		filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.6));
	}
</style>
