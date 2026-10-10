<script lang="ts">
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import PageCrumbs from './PageCrumbs.svelte';
	import TextSearchForm from './TextSearchForm.svelte';

	// The text search before anything has been searched for.
</script>

<svelte:head>
	<title>{m.explore_text()} · {m.app_name()}</title>
</svelte:head>

<PageHeader title={m.explore_text()} subtitle={m.explore_text_hint()}>
	{#snippet toolbar()}
		<PageCrumbs href={appHref('/explore')} label={m.nav_explore()} />
	{/snippet}
</PageHeader>
<div class="form">
	<TextSearchForm autofocus />
</div>

<style>
	.form {
		padding: var(--space-2) var(--page-gutter);
	}
</style>
