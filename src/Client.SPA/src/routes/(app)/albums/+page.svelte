<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { deleteAlbum, leaveAlbum, type AlbumResponse } from '#lib/api/index.js';
	import {
		getAllAlbumsOptions,
		getAllFoldersOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AlbumCard from '#lib/albums/AlbumCard.svelte';
	import AlbumFormDialog from '#lib/albums/AlbumFormDialog.svelte';
	import {
		albumSelectionActions,
		arrangeAlbums,
		groupByYear,
		isFiltered,
		parseListOptions,
		scopeCounts,
		type AlbumKindFilter,
		type AlbumScope,
		type AlbumSort
	} from '#lib/albums/album-list.js';
	import { runBulk } from '#lib/albums/bulk.js';
	import { invalidateAlbums, toggleAlbumPin } from '#lib/albums/cache.js';
	import { icons } from '#lib/albums/icons.js';
	import ListViewToggle from '#lib/albums/ListViewToggle.svelte';
	import { mergePinned } from '#lib/albums/pinned.js';
	import PinnedSection from '#lib/albums/PinnedSection.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const STORAGE_KEY = 'photonne.albums.list';

	const queryClient = useQueryClient();
	const albums = createQuery(() => getAllAlbumsOptions());
	// Pinned folders join the pinned albums, as in the sidebar.
	const folders = createQuery(() => getAllFoldersOptions());

	let options = $state(parseListOptions(readSaved()));
	let creating = $state<'manual' | 'smart' | null>(null);

	function readSaved() {
		try {
			return localStorage.getItem(STORAGE_KEY);
		} catch {
			return null;
		}
	}

	// Remember how the list was arranged (not the search text).
	$effect(() => {
		const { scope, kind, sort, descending, view, groupByYear } = options;
		try {
			localStorage.setItem(
				STORAGE_KEY,
				JSON.stringify({ scope, kind, sort, descending, view, groupByYear })
			);
		} catch {
			// Storage may be unavailable (private mode); the defaults are fine.
		}
	});

	const arranged = $derived(arrangeAlbums(albums.data ?? [], options, getLocale()));
	const counts = $derived(scopeCounts(albums.data ?? []));
	// Hidden while filtering: the matching albums are one list, pinned or not.
	const pinned = $derived(
		isFiltered(options) ? [] : mergePinned(arranged.pinned, folders.data ?? [])
	);
	const years = $derived(options.groupByYear ? groupByYear(arranged.others) : []);
	/** Every shown album in screen order, for Shift ranges and Ctrl+A. */
	const shown = $derived([
		...pinned.flatMap((entry) => (entry.kind === 'album' ? [entry.item] : [])),
		...(options.groupByYear ? years.flatMap((group) => group.albums) : arranged.others)
	]);
	const order = $derived(shown.map((album) => album.id));
	/** Loaded, and the user has no album at all: nothing to filter. */
	const empty = $derived(albums.isSuccess && albums.data.length === 0);
	const filtered = $derived(isFiltered(options));

	const scopes: { key: AlbumScope; label: () => string }[] = [
		{ key: 'all', label: m.albums_scope_all },
		{ key: 'mine', label: m.albums_scope_mine },
		{ key: 'shared', label: m.albums_scope_shared }
	];
	const sorts: { key: AlbumSort; label: () => string }[] = [
		{ key: 'updated', label: m.albums_sort_updated },
		{ key: 'created', label: m.albums_sort_created },
		{ key: 'name', label: m.albums_sort_name },
		{ key: 'count', label: m.albums_sort_count }
	];
	const kinds: { key: AlbumKindFilter; label: () => string }[] = [
		{ key: 'all', label: m.albums_kind_all },
		{ key: 'manual', label: m.albums_kind_manual },
		{ key: 'smart', label: m.albums_kind_smart }
	];

	// --- Selection: Ctrl/Shift click or the check on a card; Escape clears ---

	const selection = new Selection();
	const selected = $derived(shown.filter((album) => selection.has(album.id)));
	const allowed = $derived(albumSelectionActions(selected));
	let confirming = $state<'delete' | 'leave' | null>(null);
	let busy = $state(false);

	// What a filter hides leaves the selection: actions apply to what is seen.
	$effect(() => {
		const visible = new Set(order);
		const hidden = [...selection.ids].filter((id) => !visible.has(id));
		if (hidden.length) selection.set(hidden, false);
	});

	function select(album: AlbumResponse, mode: 'toggle' | 'range') {
		if (mode === 'range') selection.selectRange(album.id, order);
		else selection.toggle(album.id);
	}

	function onkeydown(event: KeyboardEvent) {
		if (confirming || creating || event.defaultPrevented) return;
		const target = event.target as HTMLElement;
		if (target.closest('input, textarea, select, dialog, [role="dialog"]')) return;
		if (event.key === 'Escape' && selection.active) {
			selection.clear();
		} else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
			selection.set(order, true);
		} else if (event.key === 'Delete' && selection.active && allowed.canDelete) {
			confirming = 'delete';
		} else return;
		event.preventDefault();
	}

	async function runSelected(kind: 'delete' | 'leave') {
		const targets = selected.map((album) => album.id);
		busy = true;
		const outcome = await runBulk(targets, async (albumId) => {
			const options = { path: { albumId } };
			const { error } = kind === 'delete' ? await deleteAlbum(options) : await leaveAlbum(options);
			return !error;
		});
		busy = false;
		confirming = null;
		selection.set(outcome.succeeded, false);
		invalidateAlbums(queryClient);
		const done = outcome.succeeded.length;
		if (outcome.failed.length) {
			toasts.error(m.albums_bulk_partial({ done, total: targets.length }));
		} else {
			toasts.show(
				kind === 'delete'
					? m.albums_bulk_deleted({ count: done })
					: m.albums_bulk_left({ count: done })
			);
		}
	}

	function clearFilters() {
		options.query = '';
		options.scope = 'all';
		options.kind = 'all';
	}

	function saved(album: { id: string; name: string }) {
		creating = null;
		invalidateAlbums(queryClient);
		toasts.show(m.albums_created({ name: album.name }));
		goto(appHref(`/albums/${album.id}`));
	}
</script>

<svelte:head>
	<title>{m.nav_albums()} · {m.app_name()}</title>
</svelte:head>

<svelte:window {onkeydown} />

<div class="page">
	<!-- Sticky and zero-height: the bar overlays the top of the view wherever it is scrolled. -->
	<div class="dock">
		<SelectionBar {selection}>
			{#snippet actions()}
				{#if selected.some((album) => !album.isOwner)}
					<button
						type="button"
						class="btn"
						disabled={!allowed.canLeave || busy}
						title={allowed.canLeave ? undefined : m.albums_bulk_cannot_leave()}
						onclick={() => (confirming = 'leave')}
					>
						<Icon path={icons.leave} size={18} />{m.albums_bulk_leave()}
					</button>
				{/if}
				<button
					type="button"
					class="btn danger"
					disabled={!allowed.canDelete || busy}
					title={allowed.canDelete ? undefined : m.albums_bulk_cannot_delete()}
					onclick={() => (confirming = 'delete')}
				>
					<Icon name="delete" size={18} />{m.albums_bulk_delete()}
				</button>
			{/snippet}
		</SelectionBar>
	</div>

	<PageHeader title={m.nav_albums()} toolbar={empty ? undefined : toolbar}>
		{#snippet actions()}
			{#if !empty}
				<div class="search" role="search" aria-label={m.albums_filters()}>
					<Icon name="search" size={18} />
					<input
						type="search"
						aria-label={m.albums_search()}
						placeholder={m.albums_search()}
						bind:value={options.query}
					/>
				</div>
			{/if}
			<button type="button" class="btn" onclick={() => (creating = 'smart')}>
				<Icon path={icons.smart} size={18} />{m.albums_new_smart()}
			</button>
			<button type="button" class="btn primary" onclick={() => (creating = 'manual')}>
				<Icon name="add" size={18} />{m.albums_new()}
			</button>
		{/snippet}
	</PageHeader>

	{#snippet toolbar()}
		<div class="filters" role="group" aria-label={m.albums_filters()}>
			<div class="segmented" role="radiogroup" aria-label={m.albums_scope()}>
				{#each scopes as scope (scope.key)}
					<label>
						<input type="radio" name="scope" value={scope.key} bind:group={options.scope} />
						{scope.label()}
						<!-- Counts wait for the list: "0" while loading reads as "none". -->
						{#if albums.isSuccess}<span class="count">{counts[scope.key]}</span>{/if}
					</label>
				{/each}
			</div>

			<label>
				<span class="visually-hidden">{m.albums_kind()}</span>
				<select bind:value={options.kind}>
					{#each kinds as kind (kind.key)}<option value={kind.key}>{kind.label()}</option>{/each}
				</select>
			</label>

			<div class="sort">
				<label>
					<span class="visually-hidden">{m.albums_sort()}</span>
					<select bind:value={options.sort}>
						{#each sorts as sort (sort.key)}<option value={sort.key}>{sort.label()}</option>{/each}
					</select>
				</label>
				<button
					type="button"
					class="icon-btn"
					aria-label={options.descending ? m.albums_sort_descending() : m.albums_sort_ascending()}
					title={options.descending ? m.albums_sort_descending() : m.albums_sort_ascending()}
					onclick={() => (options.descending = !options.descending)}
				>
					<Icon path={options.descending ? icons.arrowDown : icons.arrowUp} size={18} />
				</button>
			</div>

			<button
				type="button"
				class="chip toggle"
				class:active={options.groupByYear}
				role="switch"
				aria-checked={options.groupByYear}
				onclick={() => (options.groupByYear = !options.groupByYear)}
			>
				{#if options.groupByYear}<Icon name="check" size={16} />{/if}
				{m.albums_group_by_year()}
			</button>

			<span class="view"><ListViewToggle bind:view={options.view} /></span>
		</div>
	{/snippet}

	{#if albums.isPending}
		<Skeleton variant="cards" count={10} />
	{:else if albums.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if (albums.data ?? []).length === 0}
		<EmptyState icon="album" title={m.albums_empty_title()} hint={m.albums_empty_body()}>
			{#snippet action()}
				<div class="empty-actions">
					<button type="button" class="btn primary" onclick={() => (creating = 'manual')}>
						<Icon name="add" size={18} />{m.albums_new()}
					</button>
					<button type="button" class="btn" onclick={() => (creating = 'smart')}>
						<Icon path={icons.smart} size={18} />{m.albums_new_smart()}
					</button>
				</div>
			{/snippet}
		</EmptyState>
	{:else if arranged.pinned.length + arranged.others.length === 0}
		<EmptyState
			compact
			icon="search"
			title={filtered ? m.albums_no_match() : m.albums_empty_title()}
		>
			{#snippet action()}
				{#if filtered}
					<button type="button" class="btn" onclick={clearFilters}
						>{m.albums_clear_filters()}</button
					>
				{/if}
			{/snippet}
		</EmptyState>
	{:else}
		<div class="content">
			{#if pinned.length}
				<PinnedSection
					entries={pinned}
					view={options.view}
					albumSelection={{ selection, select }}
				/>
			{/if}
			{#if options.groupByYear}
				{#each years as group (group.year)}
					<section aria-labelledby="year-{group.year}">
						<h2 id="year-{group.year}" aria-label={m.albums_year_section({ year: group.year })}>
							{group.year}
						</h2>
						{@render list(group.albums)}
					</section>
				{/each}
			{:else if arranged.others.length}
				<section aria-labelledby="all-title">
					<h2 id="all-title" class:visually-hidden={!pinned.length}>
						{m.albums_section_all()}
					</h2>
					{@render list(arranged.others)}
				</section>
			{/if}
		</div>
	{/if}
</div>

{#snippet list(items: AlbumResponse[])}
	<ul class={options.view === 'grid' ? 'grid' : 'rows'}>
		{#each items as album (album.id)}
			<AlbumCard
				{album}
				view={options.view}
				selected={selection.has(album.id)}
				selecting={selection.active}
				onselect={(mode) => select(album, mode)}
				ontogglepin={(a) => toggleAlbumPin(queryClient, a)}
			/>
		{/each}
	</ul>
{/snippet}

<ConfirmDialog
	open={confirming !== null}
	title={confirming === 'leave'
		? m.albums_bulk_leave_title({ count: selected.length })
		: m.albums_bulk_delete_title({ count: selected.length })}
	message={confirming === 'leave'
		? m.albums_bulk_leave_message({ count: selected.length })
		: m.albums_bulk_delete_message({ count: selected.length })}
	confirmLabel={confirming === 'leave' ? m.albums_bulk_leave() : m.albums_bulk_delete()}
	danger
	{busy}
	onconfirm={() => confirming && runSelected(confirming)}
	onclose={() => (confirming = null)}
/>

{#if creating}
	<AlbumFormDialog smart={creating === 'smart'} onclose={() => (creating = null)} onsaved={saved} />
{/if}

<style>
	.page {
		display: grid;
		align-content: start;
		padding-bottom: var(--space-8);
	}

	.dock {
		position: sticky;
		top: 0;
		z-index: 3;
		height: 0;
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
		width: 100%;
	}

	.search {
		position: relative;
		display: flex;
		align-items: center;
		color: var(--color-text-muted);
	}

	.search :global(svg) {
		position: absolute;
		left: var(--space-3);
		pointer-events: none;
	}

	.search input {
		width: 240px;
		padding-left: calc(var(--space-3) + 26px);
	}

	.count {
		color: var(--color-text-muted);
		font-weight: 400;
		font-variant-numeric: tabular-nums;
	}

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}

	/* The toggle sits in the row as tall as its neighbours. */
	.toggle {
		min-height: var(--control-h);
		padding: 0 var(--space-4);
	}

	.view {
		margin-left: auto;
	}

	.content {
		display: grid;
		gap: var(--space-6);
		padding: var(--space-2) var(--page-gutter) 0;
	}

	h2 {
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-lg);
		font-weight: 600;
		line-height: 1.3;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: var(--space-6) var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.rows {
		display: grid;
		gap: 2px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.status {
		margin: 0;
		padding: var(--space-6) var(--page-gutter);
		color: var(--color-text-muted);
	}

	.empty-actions {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: var(--space-2);
	}
</style>
