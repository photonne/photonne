<script lang="ts">
	import type { Snippet } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import type { PersonDto } from '#lib/api/index.js';
	import { getApiPeopleByIdOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { displayName } from './people.js';
	import PersonTabs, { type PersonTab } from './PersonTabs.svelte';
	import PersonToolbar from './PersonToolbar.svelte';

	interface Props {
		id: string;
		tab: PersonTab;
		/**
		 * Draws the title, tools and tabs above `children`; off when the
		 * content brings its own heading (the photo grid's CollectionView).
		 */
		header?: boolean;
		children: Snippet<[PersonDto]>;
	}

	let { id, tab, header = true, children }: Props = $props();

	const person = createQuery(() => getApiPeopleByIdOptions({ path: { id } }));
	const name = $derived(person.data ? displayName(person.data, m.people_unnamed()) : '');
</script>

<svelte:head>
	<title>{name || m.nav_people()} · {m.app_name()}</title>
</svelte:head>

{#if person.isPending}
	<Skeleton variant={tab === 'photos' ? 'grid' : 'cards'} />
{:else if person.isError || !person.data}
	<div role="alert">
		<EmptyState icon="person" title={m.people_not_found()}>
			{#snippet action()}
				<a class="btn" href={appHref('/people')}>{m.people_back()}</a>
			{/snippet}
		</EmptyState>
	</div>
{:else}
	<!-- Another person (after a merge) is another page: fresh state, fresh lists. -->
	{#key id}
		{#if header}
			<div class="page">
				<PageHeader title={name}>
					{#snippet actions()}
						<PersonToolbar person={person.data} />
					{/snippet}
				</PageHeader>
				<PersonTabs person={person.data} current={tab} />
				<div class="body">{@render children(person.data)}</div>
			</div>
		{:else}
			{@render children(person.data)}
		{/if}
	{/key}
{/if}

<style>
	.page {
		position: relative;
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	/* Scrolls on its own so the header stays. Not positioned: a selection
	   bar inside overlays the title row, as on the photo grid, instead of
	   the content under the pointer. */
	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}
</style>
