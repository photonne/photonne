<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { getApiPeopleById, type PersonDto } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { setPeopleHidden } from './actions.js';
	import { icons } from './icons.js';
	import MergeDialog from './MergeDialog.svelte';
	import PersonPickerDialog from './PersonPickerDialog.svelte';
	import RenameDialog from './RenameDialog.svelte';

	/** Rename, merge and hide for a person's own pages. */
	let { person }: { person: PersonDto } = $props();

	const queryClient = useQueryClient();
	let renaming = $state<PersonDto | null>(null);
	let picking = $state(false);
	let merging = $state<PersonDto[] | null>(null);
	let busy = $state(false);

	async function pickOther(id: string) {
		picking = false;
		const { data } = await getApiPeopleById({ path: { id } });
		if (!data) {
			toasts.error(m.action_failed());
			return;
		}
		merging = [person, data];
	}

	async function toggleHidden() {
		busy = true;
		await setPeopleHidden(queryClient, [person.id], !person.isHidden);
		busy = false;
	}
</script>

<button
	type="button"
	class="tool"
	title={m.people_action_rename()}
	aria-label={m.people_action_rename()}
	onclick={() => (renaming = person)}
>
	<Icon name="edit" />
</button>
<button
	type="button"
	class="tool"
	title={m.people_action_merge_with()}
	aria-label={m.people_action_merge_with()}
	onclick={() => (picking = true)}
>
	<Icon path={icons.merge} />
</button>
<button
	type="button"
	class="tool"
	disabled={busy}
	title={person.isHidden ? m.people_action_unhide() : m.people_action_hide()}
	aria-label={person.isHidden ? m.people_action_unhide() : m.people_action_hide()}
	onclick={toggleHidden}
>
	<Icon path={person.isHidden ? icons.visibility : icons.visibilityOff} />
</button>

<RenameDialog person={renaming} onclose={() => (renaming = null)} />
<PersonPickerDialog
	open={picking}
	title={m.people_action_merge_with()}
	exclude={[person.id]}
	onclose={() => (picking = false)}
	onpick={(pick) => {
		if (pick.kind === 'person') pickOther(pick.id);
	}}
/>
<MergeDialog
	people={merging}
	preferred={person.id}
	onclose={() => (merging = null)}
	onmerged={(target) => {
		if (target.id !== person.id) goto(appHref(`/people/${target.id}`), { replaceState: true });
	}}
/>

<style>
	.tool {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.tool:hover {
		background: var(--color-surface);
	}

	.tool:disabled {
		opacity: 0.4;
		cursor: progress;
	}
</style>
