<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		title: string;
		lead?: string;
		/** Page-level buttons, right of the title. */
		toolbar?: Snippet;
	}

	let { title, lead, toolbar }: Props = $props();
</script>

<svelte:head>
	<title>{title} · {m.nav_utilities()} · {m.app_name()}</title>
</svelte:head>

<header>
	<nav aria-label={m.utilities_breadcrumb()}>
		<a href={appHref('/utilities')}>
			<Icon name="chevronLeft" size={18} />
			{m.nav_utilities()}
		</a>
	</nav>
	<div class="row">
		<h1>{title}</h1>
		{#if toolbar}<div class="toolbar">{@render toolbar()}</div>{/if}
	</div>
	{#if lead}<p>{lead}</p>{/if}
</header>

<style>
	header {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-4) var(--space-2);
	}

	nav a {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		color: var(--color-text-muted);
		text-decoration: none;
		font-size: var(--font-size-sm);
	}

	nav a:hover {
		color: var(--color-text);
	}

	.row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.toolbar {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		margin-left: auto;
	}

	p {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
