<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { page } from '$app/state';
	import { themeRows } from '#lib/library/memories.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import { memoryFeedOptions } from '#lib/library/memory-feed.js';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import PageHeader from '#lib/library/PageHeader.svelte';
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
	<PageHeader {title} />
	<PageCrumbs href={appHref('/explore')} label={m.nav_explore()} />

	{#if feed.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if feed.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if !row}
		<p class="status">{m.explore_theme_missing()}</p>
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
		padding: var(--space-4);
		list-style: none;
	}

	.status {
		padding: var(--space-6) var(--space-4);
		color: var(--color-text-muted);
	}
</style>
