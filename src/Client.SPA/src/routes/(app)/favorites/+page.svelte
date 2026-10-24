<script lang="ts">
	import { getFavorites } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const list = new PagedList(
		async (cursor) =>
			(await getFavorites({ query: { pageSize: 200, cursor: cursor ?? undefined } })).data
	);
	list.start();

	const viewer = new ViewerRoute();

	// Un-hearting a photo here takes it off the page, once the viewer closes:
	// while it's open the photo keeps its place, and the arrows their way.
	$effect(() => {
		if (viewer.openId) return;
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
	emptyHint={m.favorites_empty_hint()}
	emptyIcon="favoriteOutline"
	onnearend={() => list.more()}
/>
