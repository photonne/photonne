<script lang="ts">
	import type { Snippet } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import type { PersonDto } from '#lib/api/index.js';
	import { getApiPeopleByIdOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
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
	<p class="status" role="status">{m.session_restoring()}</p>
{:else if person.isError || !person.data}
	<div class="status">
		<p role="alert">{m.people_not_found()}</p>
		<a href={appHref('/people')}>{m.people_back()}</a>
	</div>
{:else}
	<!-- Another person (after a merge) is another page: fresh state, fresh lists. -->
	{#key id}
		{#if header}
			<div class="page">
				<header class="head">
					<h1 class:unnamed={!person.data.name}>{name}</h1>
					<div class="toolbar"><PersonToolbar person={person.data} /></div>
				</header>
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

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-4) 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	h1.unnamed {
		color: var(--color-text-muted);
	}

	.toolbar {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
	}

	/* Scrolls on its own so the header stays. Not positioned: a selection
	   bar inside overlays the title row, as on the photo grid, instead of
	   the content under the pointer. */
	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}

	.status {
		padding: var(--space-6);
		color: var(--color-text-muted);
	}

	.status p {
		margin: 0 0 var(--space-2);
	}
</style>
