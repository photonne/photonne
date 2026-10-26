<script lang="ts">
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import SuggestionQueue from '#lib/people/SuggestionQueue.svelte';
</script>

<svelte:head>
	<title>{m.people_queue_title()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageCrumbs href={appHref('/people')} label={m.nav_people()} inset />
	<PageHeader title={m.people_queue_title()} />
	<div class="scroll">
		<SuggestionQueue />
	</div>
</div>

<style>
	.page {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}
</style>
