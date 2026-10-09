<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		getApiPeopleOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { icons } from '#lib/library/icons.js';
	import SectionTitle from '#lib/library/SectionTitle.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import PersonAvatar from '#lib/people/PersonAvatar.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { emptySearch, searchParams, type SearchQuery } from './search-query.js';
	import { labelText } from './text.js';
	import { appHref } from '#lib/navigation/href.js';

	/**
	 * What the search page offers before anything is typed: the people,
	 * scenes and objects that appear most, each one a search. A row without
	 * data (ML off, an old server) isn't drawn.
	 */

	const ROW = 12;

	const people = createQuery(() => getApiPeopleOptions({ query: { limit: 200 } }));
	const objects = createQuery(() => listObjectLabelsOptions({ query: { limit: 1000 } }));
	const scenes = createQuery(() => listSceneLabelsOptions({ query: { limit: 1000 } }));

	const topPeople = $derived(
		(people.data?.items ?? []).filter((person) => person.name && !person.isHidden).slice(0, ROW)
	);
	const topScenes = $derived((scenes.data ?? []).slice(0, ROW));
	const topObjects = $derived((objects.data ?? []).slice(0, ROW));
	const pending = $derived(people.isPending && objects.isPending && scenes.isPending);

	function link(query: Partial<SearchQuery>) {
		return `${appHref('/search')}?${searchParams({ ...emptySearch, ...query })}`;
	}
</script>

{#if pending}
	<Skeleton variant="cards" round count={8} />
{:else if topPeople.length === 0 && topScenes.length === 0 && topObjects.length === 0}
	<EmptyState icon="search" title={m.search_discover_empty()} hint={m.search_discover_hint()} />
{:else}
	<div class="discover">
		{#if topPeople.length > 0}
			<section aria-labelledby="discover-people">
				<SectionTitle id="discover-people" title={m.search_discover_people()} />
				<ul class="people">
					{#each topPeople as person (person.id)}
						<li>
							<a href={link({ people: [person.id] })}>
								<PersonAvatar faceId={person.coverFaceId} name={person.name} size="72px" />
								<span>{person.name}</span>
							</a>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		{#each [{ id: 'scenes', title: m.search_discover_scenes(), labels: topScenes, key: 'scenes' as const }, { id: 'objects', title: m.search_discover_objects(), labels: topObjects, key: 'objects' as const }] as row (row.id)}
			{#if row.labels.length > 0}
				<section aria-labelledby="discover-{row.id}">
					<SectionTitle id="discover-{row.id}" title={row.title} />
					<ul class="tiles">
						{#each row.labels as item (item.label)}
							<li>
								<a href={link({ [row.key]: [item.label] })}>
									{#if item.coverAssetId}
										<img src={thumbnailUrl(item.coverAssetId, 'Small')} alt="" loading="lazy" />
									{:else}
										<span class="mark" aria-hidden="true"
											><Icon path={icons.label} size={28} /></span
										>
									{/if}
									<span class="caption">
										<span>{labelText(item.label)}</span>
										<span class="count">{item.assetCount}</span>
									</span>
								</a>
							</li>
						{/each}
					</ul>
				</section>
			{/if}
		{/each}
	</div>
{/if}

<style>
	.discover {
		display: grid;
		gap: var(--space-6);
		padding: var(--space-4) var(--page-gutter) var(--space-8);
	}

	section {
		display: grid;
		gap: var(--space-3);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.people {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	.people a {
		display: grid;
		justify-items: center;
		gap: var(--space-2);
		width: 96px;
		padding: var(--space-2) var(--space-1);
		border-radius: var(--radius-md);
		color: inherit;
		text-decoration: none;
		font-size: var(--font-size-sm);
		text-align: center;
	}

	.people a:hover {
		background: var(--color-hover);
	}

	.people a span:last-child {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		gap: var(--space-3);
	}

	.tiles a {
		position: relative;
		display: block;
		aspect-ratio: 1;
		overflow: hidden;
		border-radius: var(--radius-md);
		background: var(--color-placeholder);
		color: #fff;
		text-decoration: none;
	}

	.tiles a:not(:has(img)) {
		background: var(--color-brand-tile);
		color: var(--color-text);
	}

	.tiles img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--duration-normal);
	}

	.tiles a:hover img {
		transform: scale(1.04);
	}

	.mark {
		position: absolute;
		inset: 0 0 var(--space-8);
		display: grid;
		place-items: center;
		color: var(--color-accent);
	}

	.caption {
		position: absolute;
		inset: auto 0 0 0;
		display: flex;
		justify-content: space-between;
		gap: var(--space-2);
		padding: var(--space-4) var(--space-2) var(--space-2);
		background: linear-gradient(transparent, rgb(0 0 0 / 0.65));
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.tiles a:not(:has(img)) .caption {
		background: none;
	}

	.count {
		font-weight: 400;
		opacity: 0.85;
	}
</style>
