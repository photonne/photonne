<script lang="ts">
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import PageCrumbs from './PageCrumbs.svelte';
	import PageHeader from './PageHeader.svelte';
	import TextSearchForm from './TextSearchForm.svelte';

	// The text search before anything has been searched for.
</script>

<svelte:head>
	<title>{m.explore_text()} · {m.app_name()}</title>
</svelte:head>

<PageHeader title={m.explore_text()} />
<PageCrumbs href={appHref('/explore')} label={m.nav_explore()} />
<div class="form">
	<TextSearchForm autofocus />
	<p class="hint">{m.explore_text_hint()}</p>
</div>

<style>
	.form {
		padding: var(--space-2) var(--space-4) 0;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
