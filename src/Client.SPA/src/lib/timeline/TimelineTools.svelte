<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import type { MonthCount } from './jump.js';
	import JumpToDateDialog from './JumpToDateDialog.svelte';
	import type { TimelineView } from './timeline-view.svelte.js';
	import { sizeName } from './SizeControl.svelte';
	import TimelineFilters from './TimelineFilters.svelte';
	import ViewOptions from './ViewOptions.svelte';
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

	// The days the library spans, for the date pickers' bounds.
	const days = $derived.by(() => {
		const keys = months
			.filter((month) => month.count > 0)
			.map((month) => month.key)
			.sort();
		if (keys.length === 0) return { first: null, last: null };
		const [year, month] = keys.at(-1)!.split('-').map(Number);
		const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
		return { first: `${keys[0]}-01`, last: `${keys.at(-1)}-${String(lastDay).padStart(2, '0')}` };
	});

	let jumping = $state<{ current: string | null } | null>(null);
	let announcement = $state('');

	function zoomTo(direction: 1 | -1) {
		const before = view.size;
		view.step(direction);
		if (view.size !== before) announcement = m.size_changed({ level: sizeName(view.size) });
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

<!-- In the floating chrome (PageChrome): the filters and the view options. -->
<TimelineFilters
	{view}
	first={days.first}
	last={days.last}
	onjump={() => (jumping = { current: current() })}
	onannounce={(text) => (announcement = text)}
/>
<ViewOptions size={view.size} onsize={(size) => view.setSize(size)} />
<p class="visually-hidden" role="status">{announcement}</p>

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
