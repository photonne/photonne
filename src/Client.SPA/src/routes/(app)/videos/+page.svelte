<script lang="ts">
	import { getVideos } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';

	const list = new PagedList(
		async (cursor) =>
			(await getVideos({ query: { pageSize: 200, cursor: cursor ?? undefined } })).data
	);
	list.start();
</script>

<svelte:head>
	<title>{m.nav_videos()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.nav_videos()}
	status={list.status}
	emptyText={m.videos_empty()}
	emptyIcon="videoLibrary"
	onnearend={() => list.more()}
/>
