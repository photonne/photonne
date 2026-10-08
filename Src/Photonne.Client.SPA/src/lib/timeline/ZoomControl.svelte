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
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { stepZoom, zoomLevels } from './zoom.js';

	interface Props {
		zoom: ZoomLevel;
		onchange: (level: ZoomLevel) => void;
	}

	let { zoom, onchange }: Props = $props();

	const index = $derived(zoomLevels.indexOf(zoom));
	const REMOVE = 'M19 13H5v-2h14v2z';
</script>

<div class="zoom" role="group" aria-label={m.timeline_zoom()}>
	<button
		type="button"
		class="icon"
		aria-label={m.timeline_zoom_out()}
		title={m.timeline_zoom_out_hint()}
		disabled={index === 0}
		onclick={() => onchange(stepZoom(zoom, -1))}
	>
		<Icon path={REMOVE} size={18} />
	</button>
	<input
		type="range"
		min="0"
		max={zoomLevels.length - 1}
		step="1"
		value={index}
		aria-label={m.timeline_zoom()}
		aria-valuetext={zoomLabels[zoom]()}
		title={zoomLabels[zoom]()}
		oninput={(event) => onchange(zoomLevels[Number(event.currentTarget.value)])}
	/>
	<button
		type="button"
		class="icon"
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
		gap: var(--space-1);
	}

	input {
		width: 96px;
		accent-color: var(--color-accent);
		cursor: pointer;
	}

	.icon {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.icon:disabled {
		opacity: 0.4;
		cursor: default;
	}
</style>
