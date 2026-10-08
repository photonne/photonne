<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		getApiPeopleOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { thumbnailUrl } from '#lib/media.js';
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

	function link(query: Partial<SearchQuery>) {
		return `${appHref('/search')}?${searchParams({ ...emptySearch, ...query })}`;
	}
</script>

<div class="discover">
	{#if topPeople.length > 0}
		<section aria-labelledby="discover-people">
			<h2 id="discover-people">{m.search_discover_people()}</h2>
			<ul class="people">
				{#each topPeople as person (person.id)}
					<li>
						<a href={link({ people: [person.id] })}>
							{#if person.coverFaceId}
								<img src="/api/faces/{person.coverFaceId}/thumbnail" alt="" loading="lazy" />
							{:else}
								<span class="face" aria-hidden="true"></span>
							{/if}
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
				<h2 id="discover-{row.id}">{row.title}</h2>
				<ul class="tiles">
					{#each row.labels as item (item.label)}
						<li>
							<a href={link({ [row.key]: [item.label] })}>
								{#if item.coverAssetId}
									<img src={thumbnailUrl(item.coverAssetId, 'Small')} alt="" loading="lazy" />
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

	{#if topPeople.length === 0 && topScenes.length === 0 && topObjects.length === 0}
		<p class="hint">{m.search_discover_hint()}</p>
	{/if}
</div>

<style>
	.discover {
		display: grid;
		gap: var(--space-6);
		padding: var(--space-4);
	}

	h2 {
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-md);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.people {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-4);
	}

	.people a {
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		width: 88px;
		color: inherit;
		text-decoration: none;
		font-size: var(--font-size-sm);
		text-align: center;
	}

	.people a span {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.people img,
	.face {
		width: 72px;
		height: 72px;
		border-radius: 50%;
		object-fit: cover;
		background: var(--color-placeholder);
	}

	.people a:hover img {
		outline: 2px solid var(--color-accent);
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

	.tiles img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--duration-normal);
	}

	.tiles a:hover img {
		transform: scale(1.04);
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

	.count {
		font-weight: 400;
		opacity: 0.85;
	}

	.hint {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
