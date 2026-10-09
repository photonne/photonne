<script lang="ts">
	import type { Snippet } from 'svelte';
	import { searchAssets, type SearchAssetsData } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { offsetPage } from './pages.js';
	import PageCrumbs from './PageCrumbs.svelte';

	type Filter = Omit<NonNullable<SearchAssetsData['query']>, 'pageSize' | 'offset'>;

	interface Props {
		title: string;
		/** The search that finds the photos (a scene, an object, a tag, a text). */
		filter: Filter;
		emptyText: string;
		crumb: { href: string; label: string };
		/** Under the crumb: a note on what's being matched, a search box… */
		children?: Snippet;
	}

	let { title, filter, emptyText, crumb, children }: Props = $props();

	const PAGE_SIZE = 200;

	// The page keys this component by its filter, so the filter is fixed here.
	const list = new PagedList((cursor) =>
		offsetPage(
			cursor,
			PAGE_SIZE,
			async (offset) =>
				(await searchAssets({ query: { ...filter, pageSize: PAGE_SIZE, offset } })).data
		)
	);
	list.start();
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	{title}
	status={list.status}
	{emptyText}
	onnearend={() => list.more()}
>
	{#snippet header()}
		<PageCrumbs href={crumb.href} label={crumb.label} inset />
		{@render children?.()}
	{/snippet}
</CollectionView>
