<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		getAllAlbumsOptions,
		getAllFoldersOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import TileGrid, { type Tile } from '#lib/components/ui/TileGrid.svelte';
	import { isSmart } from '#lib/albums/album-list.js';
	import { icons } from '#lib/albums/icons.js';
	import { mergePinned } from '#lib/albums/pinned.js';
	import { appHref } from '#lib/navigation/href.js';
	import { navigation } from '#lib/navigation/sections.js';
	import { m } from '#lib/paraglide/messages.js';

	// The «Colecciones» tab of the phone navigation: the same collections as
	// the island, then the pinned albums and folders.
	const albums = createQuery(() => getAllAlbumsOptions());
	const folders = createQuery(() => getAllFoldersOptions());

	const collections: Tile[] = navigation
		.find((section) => section.id === 'collections')!
		.items.map((item) => ({ href: appHref(item.path), label: item.label(), icon: item.icon }));

	const pinned: Tile[] = $derived(
		mergePinned(albums.data ?? [], folders.data ?? []).map((entry) =>
			entry.kind === 'album'
				? {
						href: appHref(`/albums/${entry.item.id}`),
						label: entry.item.name,
						...(isSmart(entry.item) ? { iconPath: icons.smart } : { icon: 'album' as const })
					}
				: { href: appHref(`/folders/${entry.item.id}`), label: entry.item.name, icon: 'folder' }
		)
	);
</script>

<svelte:head>
	<title>{m.nav_section_collections()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader title={m.nav_section_collections()} />
	<div class="sections">
		<TileGrid tiles={collections} />
		{#if pinned.length > 0}
			<TileGrid title={m.nav_section_pinned()} tiles={pinned} />
		{/if}
	</div>
</div>

<style>
	.sections {
		display: grid;
		gap: var(--space-8);
		padding: var(--space-2) var(--page-gutter) var(--space-8);
	}
</style>
