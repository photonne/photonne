<script lang="ts">
	import { page } from '$app/state';
	import Logo from '#lib/components/Logo.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const missing = $derived(page.status === 404);
</script>

<svelte:head>
	<title>{missing ? m.error_not_found_title() : m.error_title()} · {m.app_name()}</title>
</svelte:head>

<main class="error">
	<a class="logo" href={appHref('/')} aria-label={m.app_name()}><Logo size={36} /></a>
	<p class="code" aria-hidden="true">{page.status}</p>
	<h1>{missing ? m.error_not_found_title() : m.error_title()}</h1>
	<p>{missing ? m.error_not_found_text() : m.error_text()}</p>
	<a class="home btn primary" href={appHref('/')}>{m.error_home()}</a>
</main>

<style>
	.error {
		min-height: 100vh;
		display: grid;
		place-content: center;
		justify-items: center;
		gap: var(--space-3);
		padding: var(--space-6) var(--space-4);
		text-align: center;
		background: var(--color-bg);
	}

	.logo {
		margin-bottom: var(--space-6);
		color: inherit;
		text-decoration: none;
	}

	.code {
		margin: 0;
		font-size: 4rem;
		font-weight: 700;
		line-height: 1;
		color: var(--color-brand);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	p {
		margin: 0;
		max-width: 40ch;
		color: var(--color-text-muted);
	}

	.home {
		margin-top: var(--space-2);
	}
</style>
