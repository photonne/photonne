<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import {
		getApiPeople,
		patchApiPeopleById,
		postApiPeopleByIdMergeByOtherId,
		type PersonDto
	} from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { invalidatePeople } from './cache.js';
	import { namesake } from './people.js';

	interface Props {
		/** The person being renamed; the dialog is open while there is one. */
		person: { id: string; name?: string | null } | null;
		onclose: () => void;
		onrenamed?: () => void;
		/** The person was merged into a namesake instead: the page may be gone. */
		onmerged?: (target: PersonDto) => void;
	}

	let { person, onclose, onrenamed, onmerged }: Props = $props();

	const queryClient = useQueryClient();
	// Each opening starts from the current name; typing overrides it.
	let name = $derived(person?.name ?? '');
	let saving = $state(false);
	/** Someone already called that: same person? Asked before saving. */
	let existing = $state<PersonDto | null>(null);

	$effect(() => {
		// A new name (or another person) asks again.
		void name;
		void person;
		existing = null;
	});

	async function save(event: SubmitEvent) {
		event.preventDefault();
		if (!person || saving) return;
		const wanted = name.trim();
		if (wanted && wanted !== (person.name ?? '').trim()) {
			saving = true;
			const { data } = await getApiPeople({ query: { search: wanted, limit: 20 } });
			saving = false;
			const match = namesake(data?.items ?? [], wanted, person.id);
			if (match) {
				existing = match;
				return;
			}
		}
		await rename();
	}

	async function rename() {
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

	/** Joins this person into the one that already has the name. */
	async function merge() {
		const target = existing;
		if (!person || !target || saving) return;
		saving = true;
		const { error } = await postApiPeopleByIdMergeByOtherId({
			path: { id: target.id, otherId: person.id }
		});
		saving = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		onclose();
		toasts.show(m.people_merged({ count: 1, name: target.name ?? '' }));
		onmerged?.(target);
		await invalidatePeople(queryClient);
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
	{#if existing}
		<p class="namesake" role="alert">
			{m.people_rename_namesake({ name: existing.name ?? '' })}
		</p>
	{/if}
	{#snippet actions()}
		{#if existing}
			<button type="button" disabled={saving} onclick={rename}>
				{m.people_rename_keep_apart()}
			</button>
			<button type="button" class="primary" disabled={saving} onclick={merge}>
				{m.people_rename_merge()}
			</button>
		{:else}
			<button type="button" onclick={onclose}>{m.people_cancel()}</button>
			<button type="submit" form="person-rename" class="primary" disabled={saving}>
				{m.people_save()}
			</button>
		{/if}
	{/snippet}
</Dialog>

<style>
	.namesake {
		margin: var(--space-3) 0 0;
		padding: var(--space-3);
		border-radius: var(--radius-md);
		background: var(--color-accent-soft);
		font-size: var(--font-size-sm);
	}
</style>
