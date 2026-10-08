<script lang="ts">
	import '../app.css';
	import { QueryClient, QueryClientProvider } from '@tanstack/svelte-query';
	import { configureApiClient } from '#lib/api/index.js';
	import Toaster from '#lib/components/Toaster.svelte';
	import favicon from '#lib/assets/favicon.svg';
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

	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {
				// Library data changes when the user acts on it, and those actions
				// invalidate what they touch; no need to refetch on every focus.
				staleTime: 60_000,
				refetchOnWindowFocus: false,
				retry: 1
			}
		}
	});

	document.documentElement.lang = getLocale();

	// Public shared links (/share/…) work without a session: they don't wait
	// for (or depend on) restoring one.
	const isPublic = $derived(page.route.id?.startsWith('/share') ?? false);
	if (!page.route.id?.startsWith('/share')) session.restore();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<QueryClientProvider client={queryClient}>
	{#if isPublic}
		{@render children()}
	{:else if session.status === 'restoring'}
		<div class="center" role="status">{m.session_restoring()}</div>
	{:else if session.status === 'unreachable'}
		<div class="center">
			<h1>{m.session_unreachable_title()}</h1>
			<p>{m.session_unreachable_body()}</p>
			<button type="button" onclick={() => session.restore()}>{m.session_retry()}</button>
		</div>
	{:else}
		{@render children()}
	{/if}
	<Toaster />
</QueryClientProvider>

<style>
	.center {
		min-height: 100vh;
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

	button {
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		cursor: pointer;
	}
</style>
