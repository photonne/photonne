<script lang="ts">
	import { getFavorites } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';

	const list = new PagedList(
		async (cursor) =>
			(await getFavorites({ query: { pageSize: 200, cursor: cursor ?? undefined } })).data
	);
	list.start();

	// Un-hearting a photo here takes it off the page.
	$effect(() => {
		const notFavorite = list.store.items.filter((item) => !item.isFavorite).map((i) => i.id);
		if (notFavorite.length) list.store.remove(notFavorite);
	});
</script>

<svelte:head>
	<title>{m.nav_favorites()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.nav_favorites()}
	status={list.status}
	emptyText={m.favorites_empty()}
	onnearend={() => list.more()}
/>
