<script lang="ts">
	import '@fontsource-variable/inter';
	import '../app.css';
	import '#lib/styles/ui.css';
	import { QueryClientProvider } from '@tanstack/svelte-query';
	import { queryClient } from '#lib/api/query-client.js';
	import { configureApiClient } from '#lib/api/index.js';
	import Toaster from '#lib/components/Toaster.svelte';
	import { session } from '#lib/auth/session.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { page } from '$app/state';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	configureApiClient({
		accessToken: () => session.accessToken,
		refresh: () => session.refresh()
	});

	document.documentElement.lang = getLocale();

	// Public shared links (/share/…) work without a session: they don't wait
	// for (or depend on) restoring one. It is still restored, so a signed-in
	// user sees the link inside the app.
	const isPublic = $derived(page.route.id?.startsWith('/share') ?? false);
	session.restore();
</script>

<QueryClientProvider client={queryClient}>
	{#if isPublic}
		{@render children()}
	{:else if session.status === 'restoring'}
		<div class="center" role="status">{m.session_restoring()}</div>
	{:else if session.status === 'unreachable'}
		<div class="center">
			<h1>{m.session_unreachable_title()}</h1>
			<p>{m.session_unreachable_body()}</p>
			<button type="button" class="btn primary" onclick={() => session.restore()}
				>{m.session_retry()}</button
			>
		</div>
	{:else}
		{@render children()}
	{/if}
	<Toaster />
</QueryClientProvider>

<style>
	.center {
		min-height: 100dvh;
		display: grid;
		place-content: center;
		justify-items: center;
		gap: var(--space-3);
		padding: var(--space-6);
		text-align: center;
		color: var(--color-text-muted);
	}

	.center h1 {
		margin: 0;
		font-size: var(--font-size-lg);
		color: var(--color-text);
	}

	.center p {
		margin: 0;
	}
</style>
