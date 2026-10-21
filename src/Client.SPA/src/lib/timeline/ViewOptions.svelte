<script lang="ts">
	import ChromeMenu from '#lib/components/ui/ChromeMenu.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import {
		gridLayout,
		type GridLayoutChoice,
		type GridRatio
	} from './grid-layout-preference.svelte.js';
	import SizeControl from './SizeControl.svelte';

	/**
	 * The view options of a photo grid, in a menu of the floating chrome: the
	 * layout and, for a grid, its cells' ratio (shared by every grid, as in
	 * Settings › Appearance), and the size of this grid's thumbnails.
	 */
	let { size, onsize }: { size: number; onsize: (size: number) => void } = $props();

	// Material "tune" (Apache 2.0): two sliders.
	const TUNE =
		'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z';
	const MASONRY = 'M3 3h8v11H3zm10 0h8v7h-8zM3 16h8v5H3zm10-4h8v9h-8z';
	const GRID = 'M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z';

	const layouts: { value: GridLayoutChoice; label: () => string; icon: string }[] = [
		{ value: 'masonry', label: m.settings_grid_layout_masonry, icon: MASONRY },
		{ value: 'rows', label: m.settings_grid_layout_rows, icon: GRID }
	];
	const ratios: { value: GridRatio; label: () => string; hint: () => string }[] = [
		{
			value: 'square',
			label: m.settings_grid_ratio_square,
			hint: m.settings_grid_ratio_square_hint
		},
		{
			value: 'original',
			label: m.settings_grid_ratio_original,
			hint: m.settings_grid_ratio_original_hint
		}
	];
</script>

<ChromeMenu label={m.view_options()} iconPath={TUNE}>
	<div class="group" role="radiogroup" aria-label={m.view_layout()}>
		<span class="caption">{m.view_layout()}</span>
		<div class="segmented">
			{#each layouts as layout (layout.value)}
				<label>
					<input
						type="radio"
						name="grid-layout"
						value={layout.value}
						checked={gridLayout.value === layout.value}
						onchange={() => gridLayout.set(layout.value)}
					/>
					<svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"
						><path d={layout.icon} fill="currentColor" /></svg
					>
					{layout.label()}
				</label>
			{/each}
		</div>
	</div>
	{#if gridLayout.value === 'rows'}
		<div class="group" role="radiogroup" aria-label={m.settings_grid_ratio()}>
			<span class="caption">{m.settings_grid_ratio()}</span>
			<div class="segmented">
				{#each ratios as ratio (ratio.value)}
					<label title={ratio.hint()}>
						<input
							type="radio"
							name="grid-ratio"
							value={ratio.value}
							checked={gridLayout.ratio === ratio.value}
							onchange={() => gridLayout.setRatio(ratio.value)}
						/>
						{ratio.label()}
					</label>
				{/each}
			</div>
		</div>
	{/if}
	<div class="group">
		<span class="caption">{m.view_size()}</span>
		<SizeControl value={size} onchange={onsize} marks />
	</div>
</ChromeMenu>

<style>
	.group {
		display: grid;
		gap: var(--space-2);
	}

	.caption {
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
</style>
