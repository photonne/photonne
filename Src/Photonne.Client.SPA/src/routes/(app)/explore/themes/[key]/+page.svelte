<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { page } from '$app/state';
	import { themeRows } from '#lib/library/memories.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import { memoryFeedOptions } from '#lib/library/memory-feed.js';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	// A theme of Explorar in full: its years (or trips) as big cards.
	const feed = createQuery(memoryFeedOptions);
	const key = $derived(page.params.key ?? '');
	const row = $derived(themeRows(feed.data ?? []).find((theme) => theme.key === key));
	const title = $derived(row?.title ?? m.nav_explore());
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader {title} count={row ? m.explore_theme_count({ count: row.memories.length }) : null}>
		{#snippet toolbar()}
			<PageCrumbs href={appHref('/explore')} label={m.nav_explore()} />
		{/snippet}
	</PageHeader>

	{#if feed.isPending}
		<Skeleton variant="cards" count={6} />
	{:else if feed.isError}
		<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
	{:else if !row}
		<EmptyState icon="explore" title={m.explore_theme_missing()}>
			{#snippet action()}
				<a class="btn" href={appHref('/explore')}>{m.nav_explore()}</a>
			{/snippet}
		</EmptyState>
	{:else}
		<ul class="cards" role="list">
			{#each row.memories as memory (memory.id)}
				<li>
					<MemoryCard
						href={appHref(`/memories/${memory.id}`)}
						title={memory.cardLabel ?? memory.title}
						subtitle={memory.subtitle ?? m.memories_photo_count({ count: memory.assetCount })}
						coverAssetId={memory.coverAssetId}
					/>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: var(--space-4);
		margin: 0;
		padding: var(--space-2) var(--page-gutter);
		list-style: none;
	}
</style>
