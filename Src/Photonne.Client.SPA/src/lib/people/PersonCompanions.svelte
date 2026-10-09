<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getMemoryFeedOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import MemoryCard from '#lib/library/MemoryCard.svelte';
	import SectionTitle from '#lib/library/SectionTitle.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { companions } from './people.js';

	let { personId }: { personId: string } = $props();

	// Quietly: if the memories fail, the page still has the photos.
	const memories = createQuery(() => getMemoryFeedOptions({ query: { personId, limit: 50 } }));
	const pairs = $derived(companions(memories.data ?? []));
	const id = $props.id();
</script>

{#if pairs.length}
	<section aria-labelledby={id}>
		<SectionTitle {id} title={m.people_together()} />
		<ul class="row">
			{#each pairs as pair (pair.id)}
				<li>
					<MemoryCard
						href={appHref(`/memories/${pair.id}`)}
						title={pair.companionName ?? pair.title}
						subtitle={pair.subtitle}
						coverAssetId={pair.coverAssetId}
						size="small"
					/>
				</li>
			{/each}
		</ul>
	</section>
{/if}

<style>
	section {
		padding: var(--space-3) var(--page-gutter) 0;
	}

	/* One scrolling row, like Explorar's. */
	.row {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: 128px;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-2) 0;
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-snap-type: x proximity;
		list-style: none;
	}

	.row > li {
		scroll-snap-align: start;
	}
</style>
