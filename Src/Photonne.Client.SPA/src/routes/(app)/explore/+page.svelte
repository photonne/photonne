<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		getUserTagsOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { labelHref, tagHref } from '#lib/library/explore-links.js';
	import ExploreRow from '#lib/library/ExploreRow.svelte';
	import { icons } from '#lib/library/icons.js';
	import LabelTile from '#lib/library/LabelTile.svelte';
	import { themeRows } from '#lib/library/memories.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import { memoryFeedOptions } from '#lib/library/memory-feed.js';
	import SectionTitle from '#lib/library/SectionTitle.svelte';
	import TextSearchForm from '#lib/library/TextSearchForm.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	// What a row shows before its "see all".
	const ROW_LIMIT = 20;

	const feed = createQuery(memoryFeedOptions);
	const scenes = createQuery(() => listSceneLabelsOptions({ query: { limit: ROW_LIMIT } }));
	const objects = createQuery(() => listObjectLabelsOptions({ query: { limit: ROW_LIMIT } }));
	const tags = createQuery(() => getUserTagsOptions());

	const themes = $derived(themeRows(feed.data ?? []));
	const queries = [feed, scenes, objects, tags];
	const pending = $derived(queries.some((query) => query.isPending));
	const failed = $derived(queries.every((query) => query.isError));
	const empty = $derived(
		themes.length === 0 && !scenes.data?.length && !objects.data?.length && !tags.data?.length
	);
</script>

<svelte:head>
	<title>{m.nav_explore()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader title={m.nav_explore()}>
		{#snippet toolbar()}
			<TextSearchForm />
		{/snippet}
	</PageHeader>

	{#if pending}
		<Skeleton variant="cards" count={12} />
	{:else if failed}
		<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
	{:else if empty}
		<EmptyState icon="explore" title={m.explore_empty()} hint={m.explore_empty_hint()} />
	{:else}
		{#each themes as row (row.key)}
			<ExploreRow
				id="theme-{row.key}"
				title={row.title}
				more={row.memories.length > 4
					? { href: appHref(`/explore/themes/${encodeURIComponent(row.key)}`) }
					: undefined}
			>
				{#each row.memories.slice(0, ROW_LIMIT) as memory (memory.id)}
					<li>
						<MemoryCard
							size="small"
							href={appHref(`/memories/${memory.id}`)}
							title={memory.cardLabel ?? memory.title}
							coverAssetId={memory.coverAssetId}
						/>
					</li>
				{/each}
			</ExploreRow>
		{/each}

		{#if scenes.data?.length}
			<ExploreRow
				id="explore-scenes"
				title={m.explore_scenes()}
				more={{ href: appHref('/explore/scenes') }}
			>
				{#each scenes.data as label (label.label)}
					<li>
						<LabelTile
							href={labelHref('scenes', label.label)}
							label={label.label}
							count={label.assetCount}
							coverAssetId={label.coverAssetId}
						/>
					</li>
				{/each}
			</ExploreRow>
		{/if}

		{#if objects.data?.length}
			<ExploreRow
				id="explore-objects"
				title={m.explore_objects()}
				more={{ href: appHref('/explore/objects') }}
			>
				{#each objects.data as label (label.label)}
					<li>
						<LabelTile
							href={labelHref('objects', label.label)}
							label={label.label}
							count={label.assetCount}
							coverAssetId={label.coverAssetId}
						/>
					</li>
				{/each}
			</ExploreRow>
		{/if}

		{#if tags.data?.length}
			<section class="tags" aria-labelledby="explore-tags">
				<SectionTitle id="explore-tags" title={m.explore_tags()} />
				<ul role="list">
					{#each tags.data as tag (tag)}
						<li>
							<a class="chip" href={tagHref(tag)}>
								<Icon path={icons.label} size={16} />
								{tag}
							</a>
						</li>
					{/each}
				</ul>
			</section>
		{/if}
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.tags {
		display: grid;
		gap: var(--space-2);
		margin-top: var(--space-4);
		padding: 0 var(--page-gutter);
	}

	.tags ul {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.tags a :global(svg) {
		color: var(--color-text-muted);
	}
</style>
