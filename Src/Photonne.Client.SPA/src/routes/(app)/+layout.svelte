<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import AppShell from '#lib/components/AppShell.svelte';
	import { session } from '#lib/auth/session.svelte.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	// Everything in this group needs a session; the login page brings the
	// user back here afterwards.
	$effect(() => {
		if (session.status === 'signedOut') {
			const returnTo = page.url.pathname + page.url.search;
			goto(resolve('/login') + `?returnTo=${encodeURIComponent(returnTo)}`, { replace: true });
		}
	});
</script>

{#if session.status === 'signedIn'}
	<AppShell>
		{@render children()}
	</AppShell>
{/if}
