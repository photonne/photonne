<script lang="ts" module>
	/** A picked person, or (with `allowCreate`) the name of a new one. */
	export type PersonPick =
		{ kind: 'person'; id: string; name: string | null } | { kind: 'new'; name: string };
</script>

<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getApiPeopleOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { displayName } from './people.js';
	import PersonAvatar from './PersonAvatar.svelte';

	interface Props {
		open: boolean;
		title: string;
		/** People that can't be picked (the one the action starts from). */
		exclude?: readonly string[];
		/** Offers creating a person with the typed name. */
		allowCreate?: boolean;
		onclose: () => void;
		onpick: (pick: PersonPick) => void;
	}

	let { open, title, exclude = [], allowCreate = false, onclose, onpick }: Props = $props();

	let search = $state('');
	let debounced = $state('');

	$effect(() => {
		const value = search.trim();
		const timer = setTimeout(() => (debounced = value), 200);
		return () => clearTimeout(timer);
	});

	$effect.pre(() => {
		if (open) {
			search = '';
			debounced = '';
		}
	});

	// Most faces first: unnamed people (who can't be searched) are told apart
	// by their face, and the busiest are the likeliest targets.
	const people = createQuery(() => ({
		...getApiPeopleOptions({
			query: { limit: 60, ...(debounced ? { search: debounced } : {}) }
		}),
		enabled: open
	}));

	const choices = $derived((people.data?.items ?? []).filter((p) => !exclude.includes(p.id)));
	const typed = $derived(search.trim());
	const exact = $derived(
		choices.some((p) => p.name?.trim().toLocaleLowerCase() === typed.toLocaleLowerCase())
	);
</script>

<Dialog {open} {title} {onclose} width="420px">
	<div class="picker">
		<!-- svelte-ignore a11y_autofocus -->
		<input
			type="search"
			aria-label={m.people_picker_search()}
			placeholder={m.people_picker_search()}
			bind:value={search}
			autofocus
		/>
		<ul aria-label={title}>
			{#if allowCreate && typed && !exact}
				<li>
					<button type="button" class="create" onclick={() => onpick({ kind: 'new', name: typed })}>
						<span class="plus" aria-hidden="true">+</span>
						{m.people_picker_create({ name: typed })}
					</button>
				</li>
			{/if}
			{#each choices as person (person.id)}
				<li>
					<button
						type="button"
						onclick={() => onpick({ kind: 'person', id: person.id, name: person.name })}
					>
						<PersonAvatar faceId={person.coverFaceId} name={person.name} size="36px" />
						<span class="name" class:unnamed={!person.name}>
							{displayName(person, m.people_unnamed())}
						</span>
						<span class="count">{m.people_faces({ count: person.faceCount })}</span>
					</button>
				</li>
			{:else}
				{#if people.isSuccess && !(allowCreate && typed)}
					<li class="empty">{m.people_picker_empty()}</li>
				{/if}
			{/each}
		</ul>
	</div>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_cancel()}</button>
	{/snippet}
</Dialog>

<style>
	.picker {
		display: grid;
		gap: var(--space-3);
	}

	input {
		width: 100%;
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		height: 320px;
		overflow-y: auto;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	li button {
		width: 100%;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-1) var(--space-2);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	li button:hover,
	li button:focus-visible {
		background: var(--color-hover);
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.unnamed,
	.count,
	.empty {
		color: var(--color-text-muted);
	}

	.count,
	.empty {
		font-size: var(--font-size-sm);
	}

	.empty {
		padding: var(--space-2);
	}

	.create {
		min-height: 44px;
		color: var(--color-accent);
		font-weight: 600;
	}

	.plus {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: var(--color-accent-soft);
		font-size: var(--font-size-lg);
	}
</style>
