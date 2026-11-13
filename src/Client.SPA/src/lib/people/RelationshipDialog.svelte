<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { apiErrorCode } from '#lib/api/errors.js';
	import { getApiPeopleByIdOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { putApiPeopleByIdBranch, putApiPeopleByIdRelationship } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { invalidatePeople } from './cache.js';
	import { displayName } from './people.js';
	import PersonPickerDialog from './PersonPickerDialog.svelte';
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
			branchFromPersonId?: string | null;
		} | null;
		onclose: () => void;
	}

	let { person, onclose }: Props = $props();

	const queryClient = useQueryClient();
	const listId = $props.id();
	// Each opening starts from what is saved; choosing overrides it.
	let category = $derived(relationshipCategory(person?.relationshipCategory));
	let label = $derived(person?.relationshipLabel ?? '');
	// Whose trail they are born from in the constellation; the name comes with
	// the pick, or from the server for the one already saved.
	let branch = $derived<{ id: string; name: string | null | undefined } | null>(
		person?.branchFromPersonId ? { id: person.branchFromPersonId, name: undefined } : null
	);
	const saved = createQuery(() => ({
		...getApiPeopleByIdOptions({ path: { id: branch?.id ?? '' } }),
		enabled: branch !== null && branch.name === undefined
	}));
	const branchName = $derived(
		branch === null
			? null
			: branch.name !== undefined
				? displayName({ name: branch.name }, m.people_unnamed())
				: saved.data
					? displayName(saved.data, m.people_unnamed())
					: '…'
	);
	let picking = $state(false);
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
		let failed: unknown = error;
		if (!failed && (branch?.id ?? null) !== (person.branchFromPersonId ?? null)) {
			const result = await putApiPeopleByIdBranch({
				path: { id: person.id },
				body: { personId: branch?.id ?? null }
			});
			failed = result.error;
		}
		saving = false;
		await invalidatePeople(queryClient);
		if (failed) {
			toasts.error(
				apiErrorCode(failed) === 'branch_cycle' ? m.people_branch_cycle() : m.action_failed()
			);
			return;
		}
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
		<div class="branch">
			<span class="label">{m.people_branch_label()}</span>
			<div class="chosen">
				<span class="who">
					{branchName ??
						(category === 'children' ? m.people_branch_none_children() : m.people_branch_none())}
				</span>
				<button type="button" class="btn sm" onclick={() => (picking = true)}>
					{branch ? m.people_branch_change() : m.people_branch_choose()}
				</button>
				{#if branch}
					<button type="button" class="btn sm ghost" onclick={() => (branch = null)}>
						{m.people_branch_remove()}
					</button>
				{/if}
			</div>
			<span class="hint">{m.people_branch_hint()}</span>
		</div>
	</form>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_cancel()}</button>
		<button type="submit" form="person-relationship" class="primary" disabled={saving}>
			{m.people_save()}
		</button>
	{/snippet}
</Dialog>
<PersonPickerDialog
	open={picking}
	title={m.people_branch_label()}
	exclude={person ? [person.id] : []}
	onclose={() => (picking = false)}
	onpick={(pick) => {
		if (pick.kind === 'person') branch = { id: pick.id, name: pick.name };
		picking = false;
	}}
/>

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

	.branch {
		display: grid;
		gap: var(--space-1);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.chosen {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.who {
		margin-right: auto;
		color: var(--color-text);
		font-size: var(--font-size-md);
	}

	.branch .hint {
		font-size: var(--font-size-xs);
	}

	p.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
