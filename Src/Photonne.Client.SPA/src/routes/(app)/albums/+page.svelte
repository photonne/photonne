<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { getAllAlbumsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AlbumCard from '#lib/albums/AlbumCard.svelte';
	import AlbumFormDialog from '#lib/albums/AlbumFormDialog.svelte';
	import {
		arrangeAlbums,
		parseListOptions,
		scopeCounts,
		type AlbumKindFilter,
		type AlbumScope,
		type AlbumSort
	} from '#lib/albums/album-list.js';
	import { invalidateAlbums, toggleAlbumPin } from '#lib/albums/cache.js';
	import { icons } from '#lib/albums/icons.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
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
		const { scope, kind, sort, descending } = options;
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify({ scope, kind, sort, descending }));
		} catch {
			// Storage may be unavailable (private mode); the defaults are fine.
		}
	});

	const arranged = $derived(arrangeAlbums(albums.data ?? [], options, getLocale()));
	const counts = $derived(scopeCounts(albums.data ?? []));
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

<div class="page">
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
				<ul class="grid">
					{#each arranged.pinned as album (album.id)}
						<AlbumCard {album} ontogglepin={(a) => toggleAlbumPin(queryClient, a)} />
					{/each}
				</ul>
			</section>
		{/if}
		{#if arranged.others.length}
			<section aria-labelledby="all-title">
				<h2 id="all-title" class:visually-hidden={!arranged.pinned.length}>
					{m.albums_section_all()}
				</h2>
				<ul class="grid">
					{#each arranged.others as album (album.id)}
						<AlbumCard {album} ontogglepin={(a) => toggleAlbumPin(queryClient, a)} />
					{/each}
				</ul>
			</section>
		{/if}
	{/if}
</div>

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
