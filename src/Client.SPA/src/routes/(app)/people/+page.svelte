<script lang="ts">
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import type { PersonDto } from '#lib/api/index.js';
	import { getApiPeopleInfiniteOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import SearchField from '#lib/library/SearchField.svelte';
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
				class="icon-btn"
				disabled={selection.size !== 1 || busy}
				title={m.people_action_rename()}
				aria-label={m.people_action_rename()}
				onclick={() => (renaming = selected[0] ?? null)}
			>
				<Icon name="edit" />
			</button>
			<button
				type="button"
				class="icon-btn"
				disabled={selection.size < 2 || busy}
				title={m.people_action_merge()}
				aria-label={m.people_action_merge()}
				onclick={() => (merging = selected)}
			>
				<Icon path={icons.merge} />
			</button>
			<button
				type="button"
				class="icon-btn"
				disabled={busy}
				title={allHidden ? m.people_action_unhide() : m.people_action_hide()}
				aria-label={allHidden ? m.people_action_unhide() : m.people_action_hide()}
				onclick={hideSelected}
			>
				<Icon path={allHidden ? icons.visibility : icons.visibilityOff} />
			</button>
		{/snippet}
	</SelectionBar>

	<PageHeader
		title={m.nav_people()}
		count={people.isSuccess ? m.people_count({ count: total }) : null}
		subtitle={m.people_selection_hint()}
	>
		{#snippet actions()}
			<button type="button" class="btn" onclick={() => (recognitionOpen = true)}>
				<Icon path={icons.face} size={18} />
				{m.people_recognition()}
			</button>
		{/snippet}
		{#snippet toolbar()}
			<SearchField bind:value={search} label={m.people_search()} />
			<label class="sort">
				<span>{m.people_sort()}</span>
				<select bind:value={sort}>
					{#each PEOPLE_SORTS as option (option)}
						<option value={option}>{sortLabels[option]()}</option>
					{/each}
				</select>
			</label>
			<label class="chip toggle" class:active={showHidden}>
				<input type="checkbox" bind:checked={showHidden} />
				<Icon path={showHidden ? icons.visibility : icons.visibilityOff} size={16} />
				{m.people_show_hidden()}
			</label>
		{/snippet}
	</PageHeader>

	<div class="scroll">
		{#if people.isPending}
			<div class="loading"><Skeleton variant="cards" round count={16} /></div>
		{:else if people.isError}
			<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
		{:else if items.length === 0}
			{#if debounced}
				<EmptyState icon="search" title={m.people_search_empty({ query: debounced })} />
			{:else if showHidden}
				<EmptyState icon="people" title={m.people_empty_title()} />
			{:else}
				<EmptyState icon="people" title={m.people_empty_title()} hint={m.people_empty_hint()}>
					{#snippet action()}
						<button type="button" class="btn primary" onclick={() => (recognitionOpen = true)}>
							<Icon path={icons.face} size={18} />
							{m.people_recognition()}
						</button>
					{/snippet}
				</EmptyState>
			{/if}
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
								<PersonAvatar faceId={person.coverFaceId} name={person.name} size="100%" />
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

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	/* A .chip that toggles. The checkbox covers it invisibly, as .segmented's
	   radios do: clicks, keys and assistive tech reach the real input. */
	.toggle {
		position: relative;
		min-height: var(--control-h);
		padding: 0 var(--space-4) 0 var(--space-3);
		border-color: var(--color-border-strong);
	}

	.toggle input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		margin: 0;
		opacity: 0;
	}

	.toggle:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	/* The placeholder takes the people grid's columns (Skeleton's cards are
	   album-sized). */
	.loading :global(.skeleton) {
		grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
		padding-top: var(--space-2);
	}

	.grid {
		list-style: none;
		margin: 0;
		padding: 0 var(--page-gutter);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
		gap: var(--space-4) var(--space-3);
	}

	.card {
		position: relative;
	}

	.card a {
		display: grid;
		justify-items: center;
		gap: 2px;
		padding: var(--space-2) var(--space-1);
		border-radius: var(--radius-lg);
		color: inherit;
		text-align: center;
		text-decoration: none;
	}

	.card a:hover {
		background: var(--color-hover);
	}

	.photo {
		position: relative;
		display: block;
		width: min(100%, 120px);
		aspect-ratio: 1;
		margin-bottom: var(--space-2);
		border-radius: 50%;
		transition: transform var(--duration-fast);
	}

	.selected .photo {
		transform: scale(0.92);
		outline: 3px solid var(--color-accent);
		outline-offset: 3px;
	}

	.hidden .photo :global(.avatar) {
		opacity: 0.45;
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
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.badge {
		position: absolute;
		top: 4%;
		right: 4%;
		min-width: 24px;
		padding: 0 var(--space-1);
		border: 2px solid var(--color-bg);
		border-radius: 12px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		font-weight: 700;
		line-height: 20px;
		text-align: center;
	}

	.hidden-mark {
		position: absolute;
		bottom: 4%;
		right: 4%;
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border: 2px solid var(--color-bg);
		border-radius: 50%;
		background: var(--color-surface-raised);
		color: var(--color-text);
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
		border: 2px solid var(--color-border-strong);
		border-radius: 50%;
		background: var(--color-surface-raised);
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
</style>
