<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import { patchApiPeopleById } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { invalidatePeople } from './cache.js';

	interface Props {
		/** The person being renamed; the dialog is open while there is one. */
		person: { id: string; name?: string | null } | null;
		onclose: () => void;
		onrenamed?: () => void;
	}

	let { person, onclose, onrenamed }: Props = $props();

	const queryClient = useQueryClient();
	// Each opening starts from the current name; typing overrides it.
	let name = $derived(person?.name ?? '');
	let saving = $state(false);

	async function save(event: SubmitEvent) {
		event.preventDefault();
		if (!person || saving) return;
		saving = true;
		const { error } = await patchApiPeopleById({
			path: { id: person.id },
			body: { name: name.trim() || null }
		});
		saving = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		await invalidatePeople(queryClient);
		toasts.show(m.people_renamed());
		onrenamed?.();
		onclose();
	}
</script>

<Dialog open={person !== null} title={m.people_rename_title()} {onclose}>
	<form id="person-rename" onsubmit={save}>
		<label class="field">
			<span>{m.people_rename_label()}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={name} maxlength="200" autocomplete="off" autofocus />
			<span class="hint">{m.people_rename_hint()}</span>
		</label>
	</form>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_cancel()}</button>
		<button type="submit" form="person-rename" class="primary" disabled={saving}>
			{m.people_save()}
		</button>
	{/snippet}
</Dialog>
