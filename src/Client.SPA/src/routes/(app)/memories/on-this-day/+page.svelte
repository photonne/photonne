<script lang="ts">
	import { getMemories } from '#lib/api/index.js';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import StoryPlayer from '#lib/library/StoryPlayer.svelte';
	import ToolButton from '#lib/library/ToolButton.svelte';
	import { icons } from '#lib/library/icons.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';

	// One page: the photos of this day in earlier years, newest year first, so
	// the month headers read as the years ("octubre de 2024", "… de 2023").
	const list = new PagedList(async () => {
		const { data } = await getMemories();
		if (!data) return undefined;
		const items = [...data].sort((a, b) => b.fileCreatedAt.localeCompare(a.fileCreatedAt));
		return { items, hasMore: false };
	});
	list.start();

	let playing = $state(false);
</script>

<svelte:head>
	<title>{m.memories_on_this_day()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.memories_on_this_day()}
	status={list.status}
	emptyText={m.memories_on_this_day_empty()}
>
	{#snippet toolbar()}
		<ToolButton
			label={m.memories_play()}
			icon={{ path: icons.slideshow }}
			disabled={list.store.items.length === 0}
			onclick={() => (playing = true)}
		/>
	{/snippet}
	{#snippet header()}
		<PageCrumbs href={appHref('/memories')} label={m.nav_memories()} inset />
	{/snippet}
</CollectionView>

{#if playing}
	<StoryPlayer
		title={m.memories_on_this_day()}
		items={list.store.items}
		onclose={() => (playing = false)}
	/>
{/if}
