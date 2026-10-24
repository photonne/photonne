<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getMemoriesOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { memoryBlocks, type MemoryBlock } from '#lib/library/memories.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import { memoryFeedOptions } from '#lib/library/memory-feed.js';
	import SectionTitle from '#lib/library/SectionTitle.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { icons } from '#lib/library/icons.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import Icon from '#lib/components/Icon.svelte';
	import { ICON_MOVIE } from '#lib/summaries/icons.js';
	import VideoSummaryDialog from '#lib/summaries/VideoSummaryDialog.svelte';
	import { getVideoSummariesOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { finished } from '#lib/summaries/summaries.js';

	const feed = createQuery(memoryFeedOptions);
	// "On this day" straight from the photos: what Recuerdos shows before the
	// nightly pass has generated any memory (or with that task switched off).
	const onThisDay = createQuery(() => getMemoriesOptions());

	const blocks = $derived(memoryBlocks(feed.data ?? []));
	const hasToday = $derived(blocks.some((group) => group.block === 'today'));
	const todayAssets = $derived(hasToday ? [] : (onThisDay.data ?? []));

	let summarising = $state(false);

	// The summary videos, made by hand or every December: memories too.
	const summaries = createQuery(() => getVideoSummariesOptions());
	const summaryVideos = $derived(finished(summaries.data));

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
	<PageHeader title={m.nav_memories()}>
		{#snippet actions()}
			<button type="button" class="btn" onclick={() => (summarising = true)}>
				<Icon path={ICON_MOVIE} size={18} />{m.summaries_action_year()}
			</button>
		{/snippet}
	</PageHeader>

	{#if feed.isPending}
		<Skeleton variant="cards" count={6} />
	{:else if feed.isError && todayAssets.length === 0}
		<div role="alert">
			<EmptyState icon="info" title={m.error_loading_title()} hint={m.error_loading_hint()} />
		</div>
	{:else if blocks.length === 0 && todayAssets.length === 0}
		<EmptyState iconPath={icons.sparkle} title={m.memories_empty()} hint={m.memories_empty_hint()}>
			{#snippet action()}
				<a class="btn" href={appHref('/explore')}>{m.nav_explore()}</a>
			{/snippet}
		</EmptyState>
	{:else}
		{#if todayAssets.length > 0}
			<section aria-labelledby="block-today">
				<SectionTitle id="block-today" title={m.memories_block_today()} />
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
				<SectionTitle id="block-{group.block}" title={titles[group.block]()} />
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

	{#if summaryVideos.length > 0}
		<section aria-labelledby="block-summaries">
			<SectionTitle id="block-summaries" title={m.summaries_section()} />
			<ul class="cards" role="list">
				{#each summaryVideos as summary (summary.id)}
					<li>
						<MemoryCard
							href={appHref(`/videos?asset=${summary.assetId}`)}
							title={summary.title}
							subtitle={m.summaries_card_subtitle({ count: summary.clipCount })}
							coverAssetId={summary.assetId!}
						/>
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</div>

{#if summarising}
	<VideoSummaryDialog onclose={() => (summarising = false)} />
{/if}

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	section {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-3) var(--page-gutter);
	}

	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
	}
</style>
