<script lang="ts">
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import type { PersonDto } from '#lib/api/index.js';
	import { getApiPeopleInfiniteOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { setPeopleHidden } from '#lib/people/actions.js';
	import FaceRecognitionDialog from '#lib/people/FaceRecognitionDialog.svelte';
	import { gridKeys } from '#lib/people/grid-keys.js';
	import { icons } from '#lib/people/icons.js';
	import MergeDialog from '#lib/people/MergeDialog.svelte';
	import {
		displayName,
		nextOffset,
		parseSort,
		peopleQuery,
		PEOPLE_SORTS,
		type PeopleSort
	} from '#lib/people/people.js';
	import PersonAvatar from '#lib/people/PersonAvatar.svelte';
	import RenameDialog from '#lib/people/RenameDialog.svelte';
	import { whenVisible } from '#lib/people/visible.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import type { Snapshot } from './$types';

	const queryClient = useQueryClient();

	let search = $state('');
	let debounced = $state('');
	let sort = $state<PeopleSort>('faces');
	let showHidden = $state(false);

	// Back from a person's page lands on the same search, order and filter.
	export const snapshot: Snapshot<{ search: string; sort: PeopleSort; showHidden: boolean }> = {
		capture: () => ({ search, sort, showHidden }),
		restore: (value) => {
			search = debounced = value.search;
			sort = parseSort(value.sort);
			showHidden = value.showHidden;
		}
	};

	$effect(() => {
		const value = search.trim();
		const timer = setTimeout(() => (debounced = value), 250);
		return () => clearTimeout(timer);
	});

	const people = createInfiniteQuery(() => ({
		...getApiPeopleInfiniteOptions({
			query: peopleQuery({ sort, search: debounced, includeHidden: showHidden })
		}),
		initialPageParam: 0,
		getNextPageParam: (last, pages) => nextOffset(pages, last.total)
	}));

	const items = $derived(people.data?.pages.flatMap((page) => page.items) ?? []);
	const total = $derived(people.data?.pages[0]?.total ?? 0);
	const order = $derived(items.map((person) => person.id));

	const selection = new Selection();
	const selected = $derived(items.filter((person) => selection.has(person.id)));
	const allHidden = $derived(selected.length > 0 && selected.every((person) => person.isHidden));

	let renaming = $state<PersonDto | null>(null);
	let merging = $state<PersonDto[] | null>(null);
	let recognitionOpen = $state(false);
	let busy = $state(false);

	// A person that left the list (merged away, hidden) can't stay selected.
	$effect(() => {
		const present = new Set(order);
		const gone = [...selection.ids].filter((id) => !present.has(id));
		if (gone.length) selection.set(gone, false);
	});

	const sortLabels: Record<PeopleSort, () => string> = {
		faces: m.people_sort_faces,
		name: m.people_sort_name,
		unnamed: m.people_sort_unnamed
	};

	/** "Ana, 14 caras, 3 sugerencias por revisar, Oculta": all a card says, in one name. */
	function cardLabel(person: PersonDto, name: string) {
		return [
			name,
			m.people_faces({ count: person.faceCount }),
			person.pendingSuggestionsCount > 0
				? m.people_pending_badge({ count: person.pendingSuggestionsCount })
				: null,
			person.isHidden ? m.people_hidden() : null
		]
			.filter(Boolean)
			.join(', ');
	}

	function onCardClick(event: MouseEvent, person: PersonDto) {
		if (event.shiftKey) {
			event.preventDefault();
			selection.selectRange(person.id, order);
		} else if (event.ctrlKey || event.metaKey || selection.active) {
			event.preventDefault();
			selection.toggle(person.id);
		}
	}

	async function hideSelected() {
		busy = true;
		const ids = selected.map((person) => person.id);
		if (await setPeopleHidden(queryClient, ids, !allHidden)) selection.clear();
		busy = false;
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key !== 'Escape' || !selection.active || renaming || merging || recognitionOpen) {
			return;
		}
		if ((event.target as HTMLElement).closest('input, select, dialog')) return;
		selection.clear();
	}
</script>

<svelte:head>
	<title>{m.nav_people()} · {m.app_name()}</title>
</svelte:head>

<svelte:window {onkeydown} />

<div class="page">
	<SelectionBar {selection}>
		{#snippet actions()}
			<button
				type="button"
				class="action"
				disabled={selection.size !== 1 || busy}
				title={m.people_action_rename()}
				aria-label={m.people_action_rename()}
				onclick={() => (renaming = selected[0] ?? null)}
			>
				<Icon name="edit" />
			</button>
			<button
				type="button"
				class="action"
				disabled={selection.size < 2 || busy}
				title={m.people_action_merge()}
				aria-label={m.people_action_merge()}
				onclick={() => (merging = selected)}
			>
				<Icon path={icons.merge} />
			</button>
			<button
				type="button"
				class="action"
				disabled={busy}
				title={allHidden ? m.people_action_unhide() : m.people_action_hide()}
				aria-label={allHidden ? m.people_action_unhide() : m.people_action_hide()}
				onclick={hideSelected}
			>
				<Icon path={allHidden ? icons.visibility : icons.visibilityOff} />
			</button>
		{/snippet}
	</SelectionBar>

	<header class="head">
		<h1>{m.nav_people()}</h1>
		{#if people.isSuccess}
			<span class="total">{m.people_count({ count: total })}</span>
		{/if}
		<button type="button" class="tool" onclick={() => (recognitionOpen = true)}>
			<Icon path={icons.face} size={18} />
			{m.people_recognition()}
		</button>
	</header>

	<div class="filters">
		<label class="search">
			<Icon name="search" size={18} />
			<input
				type="search"
				placeholder={m.people_search()}
				aria-label={m.people_search()}
				bind:value={search}
			/>
		</label>
		<label class="field">
			<span>{m.people_sort()}</span>
			<select bind:value={sort}>
				{#each PEOPLE_SORTS as option (option)}
					<option value={option}>{sortLabels[option]()}</option>
				{/each}
			</select>
		</label>
		<label class="check">
			<input type="checkbox" bind:checked={showHidden} />
			{m.people_show_hidden()}
		</label>
		<p class="hint">{m.people_selection_hint()}</p>
	</div>

	<div class="scroll">
		{#if people.isPending}
			<p class="status" role="status">{m.session_restoring()}</p>
		{:else if people.isError}
			<p class="status" role="alert">{m.error_loading()}</p>
		{:else if items.length === 0}
			<p class="status">
				{debounced ? m.people_search_empty({ query: debounced }) : m.people_empty()}
			</p>
		{:else}
			<ul class="grid" aria-label={m.people_grid()} {@attach gridKeys}>
				{#each items as person (person.id)}
					{@const name = displayName(person, m.people_unnamed())}
					{@const isSelected = selection.has(person.id)}
					<li class="card" class:selected={isSelected} class:hidden={person.isHidden}>
						<a
							href={appHref(`/people/${person.id}`)}
							data-cell
							aria-label={cardLabel(person, name)}
							onclick={(event) => onCardClick(event, person)}
						>
							<span class="photo">
								<PersonAvatar
									faceId={person.coverFaceId}
									name={person.name}
									size="100%"
									shape="rounded"
								/>
								{#if person.pendingSuggestionsCount > 0}
									<span
										class="badge"
										title={m.people_pending_badge({ count: person.pendingSuggestionsCount })}
									>
										{person.pendingSuggestionsCount}
									</span>
								{/if}
								{#if person.isHidden}
									<span class="hidden-mark" title={m.people_hidden()}>
										<Icon path={icons.visibilityOff} size={16} />
									</span>
								{/if}
							</span>
							<span class="name" class:unnamed={!person.name}>{name}</span>
							<span class="count">{m.people_faces({ count: person.faceCount })}</span>
						</a>
						<button
							type="button"
							class="pick"
							role="checkbox"
							aria-checked={isSelected}
							aria-label={m.people_select({ name })}
							onclick={(event) =>
								event.shiftKey
									? selection.selectRange(person.id, order)
									: selection.toggle(person.id)}
						>
							<Icon name="check" size={16} />
						</button>
					</li>
				{/each}
			</ul>
			{#if people.hasNextPage}
				{#key people.data?.pages.length}
					<div
						class="sentinel"
						{@attach whenVisible(() => {
							if (!people.isFetchingNextPage) people.fetchNextPage();
						})}
					></div>
				{/key}
			{/if}
		{/if}
	</div>
</div>

<RenameDialog
	person={renaming}
	onclose={() => (renaming = null)}
	onrenamed={() => selection.clear()}
/>
<MergeDialog people={merging} onclose={() => (merging = null)} onmerged={() => selection.clear()} />
<FaceRecognitionDialog open={recognitionOpen} onclose={() => (recognitionOpen = false)} />

<style>
	/* Header and filters stay; the people scroll under them (and under the
	   selection bar, which overlays the title row). */
	.page {
		position: relative;
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--space-2) 0 var(--space-8);
	}

	.head {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-4) 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.total {
		color: var(--color-text-muted);
	}

	.tool {
		margin-left: auto;
		align-self: center;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.tool:hover {
		background: var(--color-surface);
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-4);
		padding: var(--space-4);
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		width: min(320px, 100%);
		padding: 0 var(--space-3);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
	}

	.search input {
		flex: 1;
		min-width: 0;
		padding: var(--space-2) 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	.field {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	select {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.check {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.hint {
		margin: 0 0 0 auto;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.status {
		padding: var(--space-6);
		color: var(--color-text-muted);
	}

	.grid {
		list-style: none;
		margin: 0;
		padding: 0 var(--space-4);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
		gap: var(--space-4) var(--space-3);
	}

	.card {
		position: relative;
	}

	.card a {
		display: grid;
		gap: 2px;
		padding: var(--space-1);
		border-radius: var(--radius-lg);
		color: inherit;
		text-decoration: none;
	}

	.card a:hover .photo {
		filter: brightness(1.05);
	}

	.photo {
		position: relative;
		display: block;
		aspect-ratio: 1;
		margin-bottom: var(--space-1);
		border-radius: var(--radius-md);
		transition: transform var(--duration-fast);
	}

	.selected .photo {
		transform: scale(0.92);
		outline: 3px solid var(--color-accent);
		outline-offset: 2px;
	}

	.hidden .photo :global(.avatar) {
		opacity: 0.45;
	}

	.name {
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
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.badge {
		position: absolute;
		top: var(--space-1);
		right: var(--space-1);
		min-width: 22px;
		padding: 0 var(--space-1);
		border-radius: 11px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		font-weight: 700;
		line-height: 22px;
		text-align: center;
	}

	.hidden-mark {
		position: absolute;
		bottom: var(--space-1);
		right: var(--space-1);
		display: grid;
		place-items: center;
		width: 26px;
		height: 26px;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
	}

	.pick {
		position: absolute;
		top: var(--space-2);
		left: var(--space-2);
		display: grid;
		place-items: center;
		width: 26px;
		height: 26px;
		padding: 0;
		border: 2px solid #fff;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.35);
		color: transparent;
		opacity: 0;
		cursor: pointer;
		transition: opacity var(--duration-fast);
	}

	.card:hover .pick,
	.pick:focus-visible,
	.page:has(.selected) .pick {
		opacity: 1;
	}

	.selected .pick {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		opacity: 1;
	}

	.sentinel {
		height: 1px;
	}

	.action {
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

	.action:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.action:disabled {
		opacity: 0.35;
		cursor: default;
	}
</style>
