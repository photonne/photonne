<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import type { FolderResponse } from '#lib/api/index.js';
	import {
		getApiPeopleOptions,
		getFolderTreeOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { longDate } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import CheckList, { type CheckOption } from './CheckList.svelte';
	import FilterMenu from './FilterMenu.svelte';
	import { PeopleNames } from './people-names.svelte.js';
	import { emptySearch, toggled, type SearchQuery } from './search-query.js';
	import { labelText, lastSegment, matches } from './text.js';

	interface Props {
		query: SearchQuery;
		/** A new search: the host puts it in the URL. */
		onsearch: (query: SearchQuery) => void;
		/** Focus the words field on mount (the empty search page). */
		autofocus?: boolean;
	}

	let { query, onsearch, autofocus = false }: Props = $props();

	// The field follows the URL (Back, a link) but is the user's while typing.
	let text = $derived(query.q);
	let input = $state<HTMLInputElement>();
	let peopleSearch = $state('');
	let peopleSearchTimer: ReturnType<typeof setTimeout> | undefined;
	let ocrText = $derived(query.ocr);
	let folderFilter = $state('');

	$effect(() => {
		if (autofocus) input?.focus();
	});

	const people = createQuery(() => getApiPeopleOptions({ query: { limit: 200 } }));
	const foundPeople = createQuery(() => ({
		...getApiPeopleOptions({ query: { limit: 50, search: peopleSearch } }),
		enabled: peopleSearch.length > 0
	}));
	const objects = createQuery(() => listObjectLabelsOptions({ query: { limit: 1000 } }));
	const scenes = createQuery(() => listSceneLabelsOptions({ query: { limit: 1000 } }));
	const folders = createQuery(() => getFolderTreeOptions());

	const names = new PeopleNames();
	$effect(() => names.learn(people.data?.items));
	$effect(() => names.learn(foundPeople.data?.items));
	$effect(() => names.ensure(query.people));

	const personName = (id: string) => names.name(id) ?? m.search_person_unnamed();

	const peopleOptions = $derived<CheckOption[]>(
		((peopleSearch ? foundPeople.data?.items : people.data?.items) ?? []).map((person) => ({
			value: person.id,
			label: person.name ?? m.search_person_unnamed(),
			hint: String(person.faceCount)
		}))
	);

	function labelOptions(labels: readonly { label: string; assetCount: number }[] | undefined) {
		return (labels ?? []).map((item) => ({
			value: item.label,
			label: labelText(item.label),
			hint: String(item.assetCount)
		}));
	}

	function flatten(nodes: readonly FolderResponse[]): FolderResponse[] {
		return nodes.flatMap((folder) => [folder, ...flatten(folder.subFolders ?? [])]);
	}

	const folderList = $derived(
		flatten(folders.data ?? []).filter((folder) => matches(folder.path, folderFilter))
	);

	const filtersOff = $derived(query.semantic);

	function submit(event: SubmitEvent) {
		event.preventDefault();
		onsearch({ ...query, q: text.trim() });
	}

	function searchPeople(value: string) {
		clearTimeout(peopleSearchTimer);
		peopleSearchTimer = setTimeout(() => (peopleSearch = value), 300);
	}

	function dates(from: string | null, to: string | null) {
		onsearch({ ...query, from: from || null, to: to || null });
	}

	interface Chip {
		key: string;
		label: string;
		remove: () => SearchQuery;
	}

	const chips = $derived.by<Chip[]>(() => {
		const list: Chip[] = [];
		if (query.from || query.to) {
			list.push({
				key: 'dates',
				label:
					query.from && query.to
						? m.search_chip_between({ from: longDate(query.from), to: longDate(query.to) })
						: query.from
							? m.search_chip_from({ date: longDate(query.from) })
							: m.search_chip_to({ date: longDate(query.to!) }),
				remove: () => ({ ...query, from: null, to: null })
			});
		}
		for (const id of query.people) {
			list.push({
				key: `p:${id}`,
				label: personName(id),
				remove: () => toggled(query, 'people', id)
			});
		}
		for (const label of query.objects) {
			list.push({
				key: `o:${label}`,
				label: labelText(label),
				remove: () => toggled(query, 'objects', label)
			});
		}
		for (const label of query.scenes) {
			list.push({
				key: `s:${label}`,
				label: labelText(label),
				remove: () => toggled(query, 'scenes', label)
			});
		}
		if (query.ocr) {
			list.push({
				key: 'ocr',
				label: m.search_chip_ocr({ text: query.ocr }),
				remove: () => ({ ...query, ocr: '' })
			});
		}
		if (query.folder) {
			list.push({
				key: 'folder',
				label: m.search_chip_folder({ folder: lastSegment(query.folder) }),
				remove: () => ({ ...query, folder: '' })
			});
		}
		return list;
	});
</script>

<div class="search-form">
	<form role="search" onsubmit={submit}>
		<div class="query">
			<Icon name="search" size={20} />
			<input
				type="search"
				name="q"
				aria-label={m.search_field_label()}
				placeholder={query.semantic
					? m.search_field_placeholder_semantic()
					: m.search_field_placeholder()}
				bind:value={text}
				bind:this={input}
			/>
		</div>
		<label class="switch" title={m.search_semantic_hint()}>
			<input
				type="checkbox"
				role="switch"
				checked={query.semantic}
				onchange={(event) =>
					onsearch({ ...query, q: text.trim(), semantic: event.currentTarget.checked })}
			/>
			<span>{m.search_semantic()}</span>
		</label>
		<button type="submit" class="btn primary">{m.search_submit()}</button>
	</form>

	<div class="filters" role="group" aria-label={m.search_filters()}>
		<FilterMenu
			label={m.search_filter_dates()}
			count={query.from || query.to ? 1 : 0}
			disabled={filtersOff}
		>
			<div class="dates">
				<label>
					{m.search_filter_from()}
					<input
						type="date"
						value={query.from ?? ''}
						max={query.to ?? undefined}
						onchange={(event) => dates(event.currentTarget.value, query.to)}
					/>
				</label>
				<label>
					{m.search_filter_to()}
					<input
						type="date"
						value={query.to ?? ''}
						min={query.from ?? undefined}
						onchange={(event) => dates(query.from, event.currentTarget.value)}
					/>
				</label>
			</div>
		</FilterMenu>

		<FilterMenu label={m.search_filter_people()} count={query.people.length} disabled={filtersOff}>
			<CheckList
				label={m.search_filter_people()}
				options={peopleOptions}
				selected={query.people}
				loading={peopleSearch ? foundPeople.isPending : people.isPending}
				onsearch={searchPeople}
				ontoggle={(id) => onsearch(toggled(query, 'people', id))}
			/>
		</FilterMenu>

		<FilterMenu
			label={m.search_filter_objects()}
			count={query.objects.length}
			disabled={filtersOff}
		>
			<CheckList
				label={m.search_filter_objects()}
				options={labelOptions(objects.data)}
				selected={query.objects}
				loading={objects.isPending}
				ontoggle={(label) => onsearch(toggled(query, 'objects', label))}
			/>
		</FilterMenu>

		<FilterMenu label={m.search_filter_scenes()} count={query.scenes.length} disabled={filtersOff}>
			<CheckList
				label={m.search_filter_scenes()}
				options={labelOptions(scenes.data)}
				selected={query.scenes}
				loading={scenes.isPending}
				ontoggle={(label) => onsearch(toggled(query, 'scenes', label))}
			/>
		</FilterMenu>

		<FilterMenu label={m.search_filter_ocr()} count={query.ocr ? 1 : 0} disabled={filtersOff}>
			{#snippet children(close)}
				<form
					class="inline"
					onsubmit={(event) => {
						event.preventDefault();
						onsearch({ ...query, ocr: ocrText.trim() });
						close();
					}}
				>
					<input
						type="search"
						aria-label={m.search_filter_ocr()}
						placeholder={m.search_filter_ocr_placeholder()}
						bind:value={ocrText}
					/>
					<button type="submit" class="btn">{m.search_apply()}</button>
				</form>
			{/snippet}
		</FilterMenu>

		<FilterMenu
			label={m.search_filter_folder()}
			count={query.folder ? 1 : 0}
			disabled={filtersOff}
			width="360px"
		>
			{#snippet children(close)}
				<div class="folders">
					<input
						type="search"
						aria-label={m.search_filter_options({ list: m.search_filter_folder() })}
						placeholder={m.search_filter_placeholder()}
						bind:value={folderFilter}
					/>
					{#if folders.isPending}
						<p class="note" role="status">{m.search_loading()}</p>
					{:else if folderList.length === 0}
						<p class="note">{m.search_filter_nothing()}</p>
					{:else}
						<ul aria-label={m.search_filter_folder()}>
							{#each folderList as folder (folder.id)}
								<li>
									<button
										type="button"
										aria-pressed={query.folder === folder.path}
										title={folder.path}
										onclick={() => {
											onsearch({
												...query,
												folder: query.folder === folder.path ? '' : folder.path
											});
											close();
										}}
									>
										<Icon name="folder" size={16} />
										<span class="name">{folder.name}</span>
										<span class="path">{folder.path}</span>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
			{/snippet}
		</FilterMenu>

		{#if filtersOff}
			<span class="note">{m.search_semantic_no_filters()}</span>
		{/if}
	</div>

	{#if chips.length > 0 && !filtersOff}
		<ul class="chips" aria-label={m.search_active_filters()}>
			{#each chips as chip (chip.key)}
				<li class="chip active">
					<span>{chip.label}</span>
					<button
						type="button"
						class="remove"
						aria-label={m.search_remove_filter({ filter: chip.label })}
						onclick={() => onsearch(chip.remove())}
					>
						<Icon name="close" size={14} />
					</button>
				</li>
			{/each}
			{#if chips.length > 1}
				<li>
					<button
						type="button"
						class="btn ghost sm"
						onclick={() => onsearch({ ...emptySearch, q: query.q })}
					>
						{m.search_clear_filters()}
					</button>
				</li>
			{/if}
		</ul>
	{/if}
</div>

<style>
	.search-form {
		display: grid;
		gap: var(--space-3);
	}

	form[role='search'] {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	.query {
		position: relative;
		flex: 1 1 320px;
		display: flex;
		max-width: 720px;
		color: var(--color-text-muted);
	}

	.query :global(svg) {
		position: absolute;
		top: 50%;
		left: var(--space-3);
		transform: translateY(-50%);
		pointer-events: none;
	}

	.query input {
		flex: 1;
		min-width: 0;
		min-height: var(--control-h-lg);
		padding-left: calc(var(--space-3) + 20px + var(--space-2));
		font-size: var(--font-size-md);
	}

	.switch {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		min-height: var(--control-h);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	/* A switch: a checkbox drawn as a track and thumb. The off track keeps
	   3:1 against the page (WCAG 1.4.11). */
	.switch input {
		appearance: none;
		position: relative;
		width: 36px;
		height: 20px;
		border-radius: 999px;
		background: var(--color-border-strong);
		transition: background var(--duration-fast);
	}

	.switch input::after {
		content: '';
		position: absolute;
		top: 2px;
		left: 2px;
		width: 16px;
		height: 16px;
		border-radius: 50%;
		background: #fff;
		transition: transform var(--duration-fast);
	}

	.switch input:checked {
		background: var(--color-accent);
	}

	.switch input:checked::after {
		transform: translateX(16px);
	}

	form[role='search'] .btn {
		min-height: var(--control-h-lg);
	}

	.filters {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.dates {
		display: grid;
		gap: var(--space-3);
	}

	.dates label {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	input[type='date'],
	.inline input,
	.folders input {
		width: 100%;
	}

	.inline {
		display: flex;
		gap: var(--space-2);
	}

	.folders {
		display: grid;
		gap: var(--space-2);
	}

	.folders ul {
		list-style: none;
		margin: 0;
		padding: 0;
		max-height: 300px;
		overflow-y: auto;
	}

	.folders li button {
		display: grid;
		grid-template-columns: auto 1fr;
		column-gap: var(--space-2);
		align-items: center;
		width: 100%;
		padding: var(--space-1) var(--space-2);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	.folders li button:hover {
		background: var(--color-hover);
	}

	.folders li button[aria-pressed='true'] {
		background: var(--color-accent-soft);
		font-weight: 600;
	}

	.folders .path {
		grid-column: 2;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		font-weight: 400;
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	/* An applied filter: an active .chip with its remove button inside. */
	.chips .chip {
		padding-right: 2px;
		cursor: default;
	}

	.remove {
		display: grid;
		place-items: center;
		width: 24px;
		height: 24px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	.remove:hover {
		background: var(--color-hover);
	}
</style>
