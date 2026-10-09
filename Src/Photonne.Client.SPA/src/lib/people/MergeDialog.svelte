<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import { postApiPeopleByIdMergeByOtherId, type PersonDto } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { invalidatePeople } from './cache.js';
	import { defaultMergeTarget, displayName } from './people.js';
	import PersonAvatar from './PersonAvatar.svelte';

	interface Props {
		/** Two or more people to merge into one; the dialog is open while set. */
		people: readonly PersonDto[] | null;
		/** Who is kept by default (else a named one with most faces). */
		preferred?: string;
		onclose: () => void;
		onmerged?: (target: PersonDto) => void;
	}

	let { people, preferred, onclose, onmerged }: Props = $props();

	const queryClient = useQueryClient();
	let keep = $state<string | null>(null);
	let merging = $state(false);

	$effect.pre(() => {
		if (!people) return;
		keep =
			people.find((person) => person.id === preferred)?.id ??
			defaultMergeTarget(people)?.id ??
			null;
	});

	async function merge() {
		const target = people?.find((person) => person.id === keep);
		if (!people || !target || merging) return;
		merging = true;
		const sources = people.filter((person) => person.id !== target.id);
		let merged = 0;
		// One request per source; stop at the first failure so the toast tells
		// the truth about what happened.
		for (const source of sources) {
			const { error } = await postApiPeopleByIdMergeByOtherId({
				path: { id: target.id, otherId: source.id }
			});
			if (error) break;
			merged++;
		}
		merging = false;
		onclose();
		if (merged < sources.length) toasts.error(m.action_failed());
		if (merged > 0) {
			toasts.show(
				m.people_merged({ count: merged, name: displayName(target, m.people_unnamed()) })
			);
			// First leave a page whose person may be gone, then refetch.
			onmerged?.(target);
		}
		await invalidatePeople(queryClient);
	}
</script>

<Dialog open={people !== null} title={m.people_merge_title()} {onclose} width="480px">
	{#if people}
		<fieldset>
			<legend>{m.people_merge_keep()}</legend>
			<div class="options">
				{#each people as person (person.id)}
					<label class:checked={keep === person.id}>
						<input type="radio" name="merge-keep" value={person.id} bind:group={keep} />
						<PersonAvatar faceId={person.coverFaceId} name={person.name} size="56px" />
						<span class="name" class:unnamed={!person.name}>
							{displayName(person, m.people_unnamed())}
						</span>
						<span class="count">{m.people_faces({ count: person.faceCount })}</span>
					</label>
				{/each}
			</div>
		</fieldset>
		<p>{m.people_merge_body()}</p>
	{/if}
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_cancel()}</button>
		<button type="button" class="primary" disabled={!keep || merging} onclick={merge}>
			{m.people_merge_confirm()}
		</button>
	{/snippet}
</Dialog>

<style>
	fieldset {
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend {
		margin-bottom: var(--space-2);
		font-weight: 600;
	}

	.options {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
		gap: var(--space-2);
		max-height: 320px;
		overflow-y: auto;
		padding: 2px;
	}

	label {
		position: relative;
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-3) var(--space-2);
		border: 2px solid var(--color-border);
		border-radius: var(--radius-md);
		text-align: center;
		cursor: pointer;
	}

	label:hover {
		background: var(--color-hover);
	}

	label.checked {
		border-color: var(--color-accent);
		background: var(--color-accent-soft);
	}

	label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	input {
		position: absolute;
		opacity: 0;
		pointer-events: none;
	}

	.name {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: 600;
	}

	.unnamed {
		color: var(--color-text-muted);
		font-weight: 400;
	}

	.count {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	p {
		margin: var(--space-3) 0 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
