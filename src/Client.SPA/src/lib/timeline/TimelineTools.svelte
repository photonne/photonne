<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import type { MonthCount } from './jump.js';
	import JumpToDateDialog from './JumpToDateDialog.svelte';
	import type { TimelineView } from './timeline-view.svelte.js';
	import GroupingControl, { groupingLabels } from './GroupingControl.svelte';
	import SizeControl from './SizeControl.svelte';
	import { isFreeKeyPress } from './zoom.js';

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

	// Material "event" (Apache 2.0): a calendar day.
	const EVENT =
		'M17 12h-5v5h5v-5zM16 1v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2h-1V1h-2zm3 18H5V8h14v11z';

	let jumping = $state<{ current: string | null } | null>(null);
	let announcement = $state('');

	function zoomTo(direction: 1 | -1) {
		const before = view.size;
		view.step(direction);
		if (view.size !== before) announcement = m.size_changed({ percent: view.size });
	}

	// + / - resize and G opens "go to a date", as long as nothing else has the
	// keyboard: a text field, a dialog, or a modifier shortcut (Ctrl + + is
	// the browser's own zoom).
	function onkeydown(event: KeyboardEvent) {
		if (paused || jumping || !isFreeKeyPress(event)) return;
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
		class="btn sm"
		title={m.timeline_jump_hint_short()}
		aria-keyshortcuts="G"
		onclick={() => (jumping = { current: current() })}
	>
		<Icon path={EVENT} size={18} />
		{m.timeline_jump()}
	</button>
	<GroupingControl
		grouping={view.grouping}
		onchange={(grouping) => {
			view.setGrouping(grouping);
			announcement = m.timeline_group_changed({ grouping: groupingLabels[grouping]() });
		}}
	/>
	<SizeControl value={view.size} onchange={(size) => view.setSize(size)} />
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
		flex-wrap: wrap;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-2);
	}
</style>
