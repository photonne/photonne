<script lang="ts">
	import type { Snippet } from 'svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import './admin.css';

	interface Props {
		title: string;
		description?: string;
		/** The browser tab's title, when it differs from the heading. */
		documentTitle?: string;
		/** Page-level buttons next to the title (New user, Refresh…). */
		actions?: Snippet;
		/** Filters under the title (search, chips…). */
		toolbar?: Snippet;
		children: Snippet;
		/** Pages of a few cards: their grid stays readable on a wide screen. */
		narrow?: boolean;
	}

	let {
		title,
		description,
		documentTitle,
		actions,
		toolbar,
		children,
		narrow = false
	}: Props = $props();
</script>

<svelte:head>
	<title>{documentTitle ?? title} · {m.app_name()}</title>
</svelte:head>

<!-- The shared page header: the h1 sits where every other page has it. -->
<div class="admin-page" class:narrow>
	<PageHeader {title} subtitle={description ?? null} {actions} {toolbar} />
	<div class="admin-body">{@render children()}</div>
</div>

<style>
	.admin-page {
		display: grid;
		align-content: start;
	}

	.narrow {
		max-width: 1600px;
	}

	.admin-body {
		display: grid;
		gap: var(--space-4);
		align-content: start;
		min-width: 0;
		padding: var(--space-3) var(--page-gutter) var(--space-8);
	}
</style>
