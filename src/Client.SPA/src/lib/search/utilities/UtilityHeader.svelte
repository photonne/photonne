<script lang="ts">
	import type { Snippet } from 'svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import PageCrumbs from '#lib/library/PageCrumbs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		title: string;
		/** What the tool does, under the title. */
		lead?: string;
		/** A count or short fact next to the title, once it's known. */
		count?: string | null;
		/** Page-level buttons, right of the title. */
		actions?: Snippet;
		/** Filters and options, in the row under the title. */
		tools?: Snippet;
	}

	let { title, lead, count = null, actions, tools }: Props = $props();
</script>

<svelte:head>
	<title>{title} · {m.nav_utilities()} · {m.app_name()}</title>
</svelte:head>

<PageHeader {title} {count} subtitle={lead} {actions}>
	{#snippet toolbar()}
		<PageCrumbs href={appHref('/utilities')} label={m.nav_utilities()} />
		{#if tools}<span class="tools">{@render tools()}</span>{/if}
	{/snippet}
</PageHeader>

<style>
	.tools {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
		margin-left: auto;
	}
</style>
