<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import type { MonthCount } from './jump.js';
	import JumpToDateDialog from './JumpToDateDialog.svelte';
	import type { TimelineView } from './timeline-view.svelte.js';
	import ZoomControl, { zoomLabels } from './ZoomControl.svelte';

	interface Props {
		view: TimelineView;
		months: readonly MonthCount[];
		/** The section on screen, for the dialog's starting year. */
		current: () => string | null;
		onjump: (monthKey: string) => void;
		/** Something covers the timeline (the viewer): its shortcuts rest. */
		paused?: boolean;
	}

	let { view, months, current, onjump, paused = false }: Props = $props();

	let jumping = $state<{ current: string | null } | null>(null);
	let announcement = $state('');

	function zoomTo(direction: 1 | -1) {
		const before = view.zoom;
		view.step(direction);
		if (view.zoom !== before) announce();
	}

	function announce() {
		announcement = m.timeline_zoom_changed({ level: zoomLabels[view.zoom]() });
	}

	// + / - zoom and G opens "go to a date", as long as nothing else has the
	// keyboard: a text field, a dialog, or a modifier shortcut (Ctrl + + is
	// the browser's own zoom).
	function onkeydown(event: KeyboardEvent) {
		if (paused || jumping || event.defaultPrevented) return;
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		const target = event.target as HTMLElement;
		if (target.closest('input, textarea, select, dialog, [role="dialog"], [contenteditable]'))
			return;
		if (event.key === '+' || event.key === '=') zoomTo(1);
		else if (event.key === '-' || event.key === '_') zoomTo(-1);
		else if (event.key === 'g' || event.key === 'G') jumping = { current: current() };
		else return;
		event.preventDefault();
	}
</script>

<svelte:window {onkeydown} />

<div class="tools" aria-label={m.timeline_toolbar()} role="group">
	<button
		type="button"
		class="jump"
		title={m.timeline_jump_hint_short()}
		aria-keyshortcuts="G"
		onclick={() => (jumping = { current: current() })}
	>
		{m.timeline_jump()}
	</button>
	<ZoomControl
		zoom={view.zoom}
		onchange={(level) => {
			view.setZoom(level);
			announce();
		}}
	/>
	<p class="visually-hidden" role="status">{announcement}</p>
</div>

{#if jumping}
	<JumpToDateDialog
		{months}
		current={jumping.current}
		onclose={() => (jumping = null)}
		onjump={(key) => {
			jumping = null;
			onjump(key);
		}}
	/>
{/if}

<style>
	.tools {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-3);
		min-height: 48px;
		padding: var(--space-2) calc(var(--space-4) + 56px) 0 var(--space-4);
	}

	.jump {
		height: 32px;
		padding: 0 var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.jump:hover {
		background: var(--color-surface);
	}
</style>
