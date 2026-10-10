<script lang="ts" module>
	import { m } from '#lib/paraglide/messages.js';
	import type { ZoomLevel } from './zoom.js';

	export const zoomLabels: Record<ZoomLevel, () => string> = {
		year: m.timeline_zoom_year,
		small: m.timeline_zoom_small,
		medium: m.timeline_zoom_medium,
		large: m.timeline_zoom_large,
		xlarge: m.timeline_zoom_xlarge
	};

	/** One word for the trigger, where the full label doesn't fit. */
	const shortLabels: Record<ZoomLevel, () => string> = {
		year: m.timeline_zoom_short_year,
		small: m.timeline_zoom_short_small,
		medium: m.timeline_zoom_short_medium,
		large: m.timeline_zoom_short_large,
		xlarge: m.timeline_zoom_short_xlarge
	};
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import { stepZoom, zoomLevels } from './zoom.js';

	interface Props {
		zoom: ZoomLevel;
		onchange: (level: ZoomLevel) => void;
	}

	let { zoom, onchange }: Props = $props();

	const index = $derived(zoomLevels.indexOf(zoom));
	const REMOVE = 'M19 13H5v-2h14v2z';

	const levels: readonly MenuEntry[] = $derived(
		zoomLevels.map((level) => ({
			kind: 'radio' as const,
			label: zoomLabels[level](),
			checked: level === zoom,
			run: () => onchange(level)
		}))
	);
</script>

<!-- −, the level (a menu of all of them) and +: the keys + and − do the same. -->
<div class="zoom" role="group" aria-label={m.timeline_zoom()}>
	<button
		type="button"
		class="icon-btn sm"
		aria-label={m.timeline_zoom_out()}
		title={m.timeline_zoom_out_hint()}
		disabled={index === 0}
		onclick={() => onchange(stepZoom(zoom, -1))}
	>
		<Icon path={REMOVE} size={18} />
	</button>
	<PopupMenu
		label={m.timeline_zoom_current({ level: zoomLabels[zoom]() })}
		text={shortLabels[zoom]()}
		triggerClass="level"
		items={levels}
	/>
	<button
		type="button"
		class="icon-btn sm"
		aria-label={m.timeline_zoom_in()}
		title={m.timeline_zoom_in_hint()}
		disabled={index === zoomLevels.length - 1}
		onclick={() => onchange(stepZoom(zoom, 1))}
	>
		<Icon name="add" size={18} />
	</button>
</div>

<style>
	.zoom {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		height: var(--control-h-sm);
		padding: 0 2px;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-control);
	}

	.zoom :global(.icon-btn.sm) {
		width: calc(var(--control-h-sm) - 4px);
		height: calc(var(--control-h-sm) - 4px);
		border-radius: calc(var(--radius-control) - 2px);
	}

	/* The level reads as the control's value: plain text that opens a list. */
	.zoom :global(.level) {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 2px;
		min-width: 7.5em;
		height: calc(var(--control-h-sm) - 4px);
		padding: 0 var(--space-2);
		border: 0;
		border-radius: calc(var(--radius-control) - 2px);
		background: transparent;
		font-size: var(--font-size-sm);
		font-weight: 500;
		cursor: pointer;
	}

	.zoom :global(.level:hover),
	.zoom :global(.level[aria-expanded='true']) {
		background: var(--color-hover);
	}
</style>
