<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getMemoriesOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { memoryBlocks, type MemoryBlock } from '#lib/library/memories.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import { memoryFeedOptions } from '#lib/library/memory-feed.js';
	import PageHeader from '#lib/library/PageHeader.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const feed = createQuery(memoryFeedOptions);
	// "On this day" straight from the photos: what Recuerdos shows before the
	// nightly pass has generated any memory (or with that task switched off).
	const onThisDay = createQuery(() => getMemoriesOptions());

	const blocks = $derived(memoryBlocks(feed.data ?? []));
	const hasToday = $derived(blocks.some((group) => group.block === 'today'));
	const todayAssets = $derived(hasToday ? [] : (onThisDay.data ?? []));

	const titles: Record<MemoryBlock, () => string> = {
		today: m.memories_block_today,
		month: m.memories_block_month,
		years: m.memories_block_years
	};
</script>

<svelte:head>
	<title>{m.nav_memories()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader title={m.nav_memories()} />

	{#if feed.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if feed.isError && todayAssets.length === 0}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if blocks.length === 0 && todayAssets.length === 0}
		<div class="empty">
			<p class="lead">{m.memories_empty()}</p>
			<p>{m.memories_empty_hint()}</p>
		</div>
	{:else}
		{#if todayAssets.length > 0}
			<section aria-labelledby="block-today">
				<h2 id="block-today">{m.memories_block_today()}</h2>
				<ul class="cards" role="list">
					<li>
						<MemoryCard
							href={appHref('/memories/on-this-day')}
							title={m.memories_on_this_day()}
							subtitle={m.memories_photo_count({ count: todayAssets.length })}
							coverAssetId={todayAssets[0].id}
						/>
					</li>
				</ul>
			</section>
		{/if}
		{#each blocks as group (group.block)}
			<section aria-labelledby="block-{group.block}">
				<h2 id="block-{group.block}">{titles[group.block]()}</h2>
				<ul class="cards" role="list">
					{#each group.memories as memory (memory.id)}
						<li>
							<MemoryCard
								href={appHref(`/memories/${memory.id}`)}
								title={group.block === 'years' ? (memory.cardLabel ?? memory.title) : memory.title}
								subtitle={memory.subtitle ?? m.memories_photo_count({ count: memory.assetCount })}
								coverAssetId={memory.coverAssetId}
							/>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	section {
		padding: 0 var(--space-4);
	}

	h2 {
		margin: var(--space-6) 0 var(--space-3);
		font-size: var(--font-size-md);
		color: var(--color-text-muted);
	}

	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.status,
	.empty {
		padding: var(--space-6) var(--space-4);
		color: var(--color-text-muted);
	}

	.empty p {
		margin: 0 0 var(--space-2);
		max-width: 60ch;
	}

	.empty .lead {
		color: var(--color-text);
		font-size: var(--font-size-lg);
	}
</style>
