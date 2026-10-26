<script lang="ts">
	import { formatCount } from '#lib/format.js';
	import { createQuery } from '@tanstack/svelte-query';
	import {
		getApiPeopleOptions,
		listObjectLabelsOptions,
		listPlacesOptions,
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
	import { appHref } from '#lib/navigation/href.js';
	import { searchHref } from './links.js';
	import { recentSearches } from './recent-searches.svelte.js';
	import type { SearchQuery } from './search-query.js';
	import { labelText } from './text.js';

	/**
	 * What the search page offers before anything is typed: the searches
	 * run lately, then the people, places, scenes and objects that appear
	 * most, each one a search. A row without data (ML off, an old server,
	 * photos without GPS) isn't drawn.
	 */

	const ROW = 12;

	const people = createQuery(() => getApiPeopleOptions({ query: { limit: 200 } }));
	const objects = createQuery(() => listObjectLabelsOptions({ query: { limit: 1000 } }));
	const scenes = createQuery(() => listSceneLabelsOptions({ query: { limit: 1000 } }));
	// An older server answers 404: the row just isn't there.
	const places = createQuery(() => ({
		...listPlacesOptions({ query: { limit: ROW } }),
		retry: false
	}));

	const topPeople = $derived(
		(people.data?.items ?? []).filter((person) => person.name && !person.isHidden).slice(0, ROW)
	);
	const topScenes = $derived((scenes.data ?? []).slice(0, ROW));
	const topObjects = $derived((objects.data ?? []).slice(0, ROW));
	const topPlaces = $derived((places.data ?? []).slice(0, ROW));
	const recents = $derived(recentSearches.list);
	const pending = $derived(
		recents.length === 0 && people.isPending && objects.isPending && scenes.isPending
	);
	const nothing = $derived(
		recents.length === 0 &&
			topPeople.length === 0 &&
			topPlaces.length === 0 &&
			topScenes.length === 0 &&
			topObjects.length === 0
	);

	const link = (query: Partial<SearchQuery>) => searchHref(query);
</script>

{#if pending}
	<Skeleton variant="cards" round count={8} />
{:else if nothing}
	<EmptyState icon="search" title={m.search_discover_empty()} hint={m.search_discover_hint()} />
{:else}
	<div class="discover">
		{#if recents.length > 0}
			<section aria-labelledby="discover-recent">
				<div class="head">
					<SectionTitle id="discover-recent" title={m.search_recent_title()} />
					<button type="button" class="link" onclick={() => recentSearches.clear()}>
						{m.search_recent_clear()}
					</button>
				</div>
				<ul class="recent">
					{#each recents as recent (recent.params)}
						<li>
							<a class="chip" href={`${appHref('/search')}?${recent.params}`}>
								<Icon name="history" size={16} />{recent.label}
							</a>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

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

		{#if topPlaces.length > 0}
			<section aria-labelledby="discover-places">
				<SectionTitle id="discover-places" title={m.search_discover_places()} />
				<ul class="tiles">
					{#each topPlaces as place (place.id)}
						<li>
							<a href={link({ place: place.id, placeName: place.name })}>
								<img src={thumbnailUrl(place.coverAssetId, 'Small')} alt="" loading="lazy" />
								<span class="caption">
									<span>{place.name}</span>
									<span class="count">{formatCount(place.assetCount)}</span>
								</span>
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
										<span class="count">{formatCount(item.assetCount)}</span>
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

	.head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.link {
		padding: 0;
		border: 0;
		background: none;
		color: var(--color-accent);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.recent {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.recent .chip {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		max-width: 40ch;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
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
		color: var(--color-on-media);
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
		background: linear-gradient(transparent, var(--color-scrim));
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
