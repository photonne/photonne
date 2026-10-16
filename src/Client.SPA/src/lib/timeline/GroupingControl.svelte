<script lang="ts" module>
	import { m } from '#lib/paraglide/messages.js';
	import type { Grouping } from './zoom.js';

	export const groupingLabels: Record<Grouping, () => string> = {
		year: m.timeline_group_year,
		month: m.timeline_group_month,
		day: m.timeline_group_day
	};
</script>

<script lang="ts">
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import { groupings } from './zoom.js';

	interface Props {
		grouping: Grouping;
		onchange: (grouping: Grouping) => void;
	}

	let { grouping, onchange }: Props = $props();

	const entries: readonly MenuEntry[] = $derived(
		groupings.map((option) => ({
			kind: 'radio' as const,
			label: groupingLabels[option](),
			checked: option === grouping,
			run: () => onchange(option)
		}))
	);
</script>

<!-- The grouping reads as the control's value: a button that opens the list. -->
<PopupMenu
	label={m.timeline_group_current({ grouping: groupingLabels[grouping]() })}
	text={groupingLabels[grouping]()}
	items={entries}
/>
