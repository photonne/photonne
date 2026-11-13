<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getApiPeopleConstellationOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import PageChrome from '#lib/components/PageChrome.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import Constellation from '#lib/people/constellation/Constellation.svelte';
	import { icons } from '#lib/people/icons.js';

	const constellation = createQuery(() => getApiPeopleConstellationOptions());
</script>

<svelte:head>
	<title>{m.people_constellation()} · {m.app_name()}</title>
</svelte:head>

{#if constellation.data && constellation.data.people.length > 0}
	<!-- The sky takes the whole window, as the map does. -->
	<PageChrome bleed wide />
	<Constellation people={constellation.data.people} />
{:else}
	<div class="page">
		<PageCrumbs href={appHref('/people')} label={m.nav_people()} inset />
		<PageHeader title={m.people_constellation()} subtitle={m.people_constellation_subtitle()} />
		<div class="body">
			{#if constellation.isPending}
				<Skeleton variant="cards" />
			{:else if constellation.isError}
				<div role="alert">
					<EmptyState iconPath={icons.sparkles} title={m.people_constellation_error()} />
				</div>
			{:else}
				<EmptyState
					iconPath={icons.sparkles}
					title={m.people_constellation_empty()}
					hint={m.people_constellation_empty_hint()}
				>
					{#snippet action()}
						<a class="btn" href={appHref('/people')}>{m.people_back()}</a>
					{/snippet}
				</EmptyState>
			{/if}
		</div>
	</div>
{/if}

<style>
	.page {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.body {
		flex: 1;
		min-height: 0;
	}
</style>
