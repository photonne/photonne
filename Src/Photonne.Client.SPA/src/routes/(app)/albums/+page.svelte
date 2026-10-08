<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { deleteAlbum, leaveAlbum, type AlbumResponse } from '#lib/api/index.js';
	import { getAllAlbumsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AlbumCard from '#lib/albums/AlbumCard.svelte';
	import AlbumFormDialog from '#lib/albums/AlbumFormDialog.svelte';
	import {
		albumSelectionActions,
		arrangeAlbums,
		groupByYear,
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
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const STORAGE_KEY = 'photonne.albums.list';

	const queryClient = useQueryClient();
	const albums = createQuery(() => getAllAlbumsOptions());

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
	const years = $derived(options.groupByYear ? groupByYear(arranged.others) : []);
	/** Every shown album in screen order, for Shift ranges and Ctrl+A. */
	const shown = $derived([
		...arranged.pinned,
		...(options.groupByYear ? years.flatMap((group) => group.albums) : arranged.others)
	]);
	const order = $derived(shown.map((album) => album.id));
	const filtered = $derived(
		options.query.trim() !== '' || options.scope !== 'all' || options.kind !== 'all'
	);

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
						class="bulk"
						disabled={!allowed.canLeave || busy}
						title={allowed.canLeave ? undefined : m.albums_bulk_cannot_leave()}
						onclick={() => (confirming = 'leave')}
					>
						<Icon path={icons.leave} size={18} />{m.albums_bulk_leave()}
					</button>
				{/if}
				<button
					type="button"
					class="bulk danger"
					disabled={!allowed.canDelete || busy}
					title={allowed.canDelete ? undefined : m.albums_bulk_cannot_delete()}
					onclick={() => (confirming = 'delete')}
				>
					<Icon name="delete" size={18} />{m.albums_bulk_delete()}
				</button>
			{/snippet}
		</SelectionBar>
	</div>

	<header class="head">
		<h1>{m.nav_albums()}</h1>
		<div class="create">
			<button type="button" class="secondary" onclick={() => (creating = 'smart')}>
				<Icon path={icons.smart} size={18} />{m.albums_new_smart()}
			</button>
			<button type="button" class="primary" onclick={() => (creating = 'manual')}>
				<Icon name="add" size={18} />{m.albums_new()}
			</button>
		</div>
	</header>

	<div class="filters" role="search" aria-label={m.albums_filters()}>
		<label class="search">
			<Icon name="search" size={18} />
			<span class="visually-hidden">{m.albums_search()}</span>
			<input type="search" placeholder={m.albums_search()} bind:value={options.query} />
		</label>

		<div class="segmented" role="radiogroup" aria-label={m.albums_scope()}>
			{#each scopes as scope (scope.key)}
				<label class:on={options.scope === scope.key}>
					<input type="radio" name="scope" value={scope.key} bind:group={options.scope} />
					{scope.label()} <span class="count">{counts[scope.key]}</span>
				</label>
			{/each}
		</div>

		<label class="select">
			<span class="visually-hidden">{m.albums_kind()}</span>
			<select bind:value={options.kind}>
				{#each kinds as kind (kind.key)}<option value={kind.key}>{kind.label()}</option>{/each}
			</select>
		</label>

		<div class="sort">
			<label class="select">
				<span class="visually-hidden">{m.albums_sort()}</span>
				<select bind:value={options.sort}>
					{#each sorts as sort (sort.key)}<option value={sort.key}>{sort.label()}</option>{/each}
				</select>
			</label>
			<button
				type="button"
				class="icon"
				aria-label={options.descending ? m.albums_sort_descending() : m.albums_sort_ascending()}
				title={options.descending ? m.albums_sort_descending() : m.albums_sort_ascending()}
				onclick={() => (options.descending = !options.descending)}
			>
				<Icon path={options.descending ? icons.arrowDown : icons.arrowUp} size={18} />
			</button>
		</div>

		<label class="switch">
			<input type="checkbox" role="switch" bind:checked={options.groupByYear} />
			{m.albums_group_by_year()}
		</label>

		<ListViewToggle bind:view={options.view} />
	</div>

	{#if albums.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if albums.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if (albums.data ?? []).length === 0}
		<div class="empty">
			<Icon name="album" size={48} />
			<p class="title">{m.albums_empty_title()}</p>
			<p>{m.albums_empty_body()}</p>
		</div>
	{:else if arranged.pinned.length + arranged.others.length === 0}
		<p class="status">{filtered ? m.albums_no_match() : m.albums_empty_title()}</p>
	{:else}
		{#if arranged.pinned.length}
			<section aria-labelledby="pinned-title">
				<h2 id="pinned-title">{m.nav_section_pinned()}</h2>
				{@render list(arranged.pinned)}
			</section>
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
				<h2 id="all-title" class:visually-hidden={!arranged.pinned.length}>
					{m.albums_section_all()}
				</h2>
				{@render list(arranged.others)}
			</section>
		{/if}
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
		padding: var(--space-4);
		display: grid;
		gap: var(--space-4);
		align-content: start;
	}

	.dock {
		position: sticky;
		top: 0;
		z-index: 3;
		height: 0;
		margin: calc(-1 * var(--space-4)) calc(-1 * var(--space-4)) calc(-1 * var(--space-4));
	}

	.bulk {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-weight: 600;
		cursor: pointer;
	}

	.bulk:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.bulk.danger {
		border-color: var(--color-danger);
		color: var(--color-danger);
	}

	.bulk:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}

	.switch {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.switch input {
		accent-color: var(--color-accent);
	}

	.rows {
		display: grid;
		gap: 2px;
		margin: 0 0 var(--space-6);
		padding: 0;
		list-style: none;
	}

	.head {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	h2 {
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-sm);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}

	.create {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
	}

	.primary,
	.secondary {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-weight: 600;
		cursor: pointer;
	}

	.primary {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.secondary:hover {
		background: var(--color-surface);
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.search {
		flex: 0 1 260px;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
	}

	.search input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	.segmented {
		display: inline-flex;
		padding: 2px;
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	.segmented label {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		height: 32px;
		padding: 0 var(--space-3);
		border-radius: calc(var(--radius-sm) - 2px);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.segmented label.on {
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		font-weight: 600;
	}

	.segmented label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
	}

	/* The radio covers its label: invisible, but it is what gets clicked. */
	.segmented input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		margin: 0;
		opacity: 0;
		cursor: pointer;
	}

	.count {
		color: var(--color-text-muted);
		font-weight: 400;
	}

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}

	select {
		height: 36px;
		padding: 0 var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover {
		background: var(--color-surface);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
		gap: var(--space-6) var(--space-4);
		margin: 0 0 var(--space-6);
		padding: 0;
		list-style: none;
	}

	.status {
		color: var(--color-text-muted);
	}

	.empty {
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-8) var(--space-4);
		color: var(--color-text-muted);
		text-align: center;
	}

	.empty p {
		margin: 0;
	}

	.empty .title {
		font-size: var(--font-size-lg);
		font-weight: 600;
		color: var(--color-text);
	}
</style>
