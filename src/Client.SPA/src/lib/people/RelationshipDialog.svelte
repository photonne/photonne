<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import { putApiPeopleByIdRelationship } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { invalidatePeople } from './cache.js';
	import {
		categoryName,
		labelSuggestions,
		RELATIONSHIP_CATEGORIES,
		relationshipCategory
	} from './relationship.js';

	interface Props {
		/** The person being classified; the dialog is open while there is one. */
		person: {
			id: string;
			name?: string | null;
			relationshipCategory?: string | null;
			relationshipLabel?: string | null;
		} | null;
		onclose: () => void;
	}

	let { person, onclose }: Props = $props();

	const queryClient = useQueryClient();
	const listId = $props.id();
	// Each opening starts from what is saved; choosing overrides it.
	let category = $derived(relationshipCategory(person?.relationshipCategory));
	let label = $derived(person?.relationshipLabel ?? '');
	let saving = $state(false);

	const usual = $derived(labelSuggestions(category));

	async function save(event: SubmitEvent) {
		event.preventDefault();
		if (!person || saving) return;
		saving = true;
		const { error } = await putApiPeopleByIdRelationship({
			path: { id: person.id },
			body: { category, label: category ? label.trim() || null : null }
		});
		saving = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		await invalidatePeople(queryClient);
		toasts.show(category ? m.people_relationship_saved() : m.people_relationship_removed());
		onclose();
	}
</script>

<Dialog
	open={person !== null}
	title={person?.name
		? m.people_relationship_title({ name: person.name })
		: m.people_action_relationship()}
	{onclose}
>
	<form id="person-relationship" onsubmit={save}>
		<fieldset>
			<legend>{m.people_relationship_category()}</legend>
			<div class="options">
				{#each RELATIONSHIP_CATEGORIES as option (option)}
					<button
						type="button"
						class="chip"
						aria-pressed={category === option}
						onclick={() => (category = category === option ? null : option)}
					>
						{categoryName(option)}
					</button>
				{/each}
			</div>
		</fieldset>
		{#if category}
			<label class="field">
				<span>{m.people_relationship_label()}</span>
				<input bind:value={label} maxlength="100" autocomplete="off" list={listId} />
				<span class="hint">{m.people_relationship_label_hint()}</span>
			</label>
			<datalist id={listId}>
				{#each usual as suggestion (suggestion)}
					<option value={suggestion}></option>
				{/each}
			</datalist>
		{:else}
			<p class="hint">{m.people_relationship_none_hint()}</p>
		{/if}
	</form>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_cancel()}</button>
		<button type="submit" form="person-relationship" class="primary" disabled={saving}>
			{m.people_save()}
		</button>
	{/snippet}
</Dialog>

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	fieldset {
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend {
		margin-bottom: var(--space-2);
		padding: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.options {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	p.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
