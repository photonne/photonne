<script lang="ts">
	import { apiErrorCode, getMemory, type MemoryDetailResponse } from '#lib/api/index.js';
	import { dateRange } from './dates.js';
	import { icons } from './icons.js';
	import { memoryArea } from './memories.js';
	import PageCrumbs from './PageCrumbs.svelte';
	import StoryPlayer from './StoryPlayer.svelte';
	import ToolButton from './ToolButton.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';

	let { id }: { id: string } = $props();

	let memory = $state<MemoryDetailResponse | null>(null);
	let missing = $state(false);
	let playing = $state(false);

	// The memory comes with its photos in one page, in the order the memory
	// tells them (by time), under the months they span.
	const list = new PagedList(async () => {
		const { data, error } = await getMemory({ path: { id } });
		if (apiErrorCode(error) === 'memory_not_found') missing = true;
		if (!data) return undefined;
		memory = data;
		return { items: data.assets, hasMore: false };
	});
	list.start();

	// Explorar's themes open here too; back goes where the memory lives.
	const parent = $derived(
		memory && memoryArea(memory.kind) === 'explore'
			? { href: appHref('/explore'), label: m.nav_explore() }
			: { href: appHref('/memories'), label: m.nav_memories() }
	);
	const title = $derived(memory?.title ?? m.nav_memories());
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	{title}
	status={missing ? 'ready' : list.status}
	emptyText={missing ? m.memories_missing() : m.memories_no_photos()}
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
		<PageCrumbs href={parent.href} label={parent.label} />
		{#if memory}
			<p class="facts">
				<!-- The server's subtitle is usually the date already ("8 de octubre de 2024"). -->
				<span>{memory.subtitle ?? dateRange(memory.windowStart, memory.windowEnd)}</span>
				<span>{m.memories_photo_count({ count: list.store.items.length })}</span>
			</p>
		{/if}
	{/snippet}
</CollectionView>

{#if playing}
	<StoryPlayer
		{title}
		subtitle={memory?.subtitle}
		items={list.store.items}
		onclose={() => (playing = false)}
	/>
{/if}

<style>
	.facts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-3);
		margin: var(--space-1) 0 0;
		padding: 0 var(--space-4);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.facts span + span::before {
		content: '·';
		margin-right: var(--space-3);
	}
</style>
