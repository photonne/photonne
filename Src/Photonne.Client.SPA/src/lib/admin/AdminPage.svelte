<script lang="ts">
	import type { Snippet } from 'svelte';
	import { m } from '#lib/paraglide/messages.js';
	import './admin.css';

	interface Props {
		title: string;
		description?: string;
		/** Page-level buttons next to the title (New user, Refresh…). */
		actions?: Snippet;
		children: Snippet;
		/** Narrower column for forms and reading pages. */
		narrow?: boolean;
	}

	let { title, description, actions, children, narrow = false }: Props = $props();
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<div class="admin-page" class:narrow>
	<header>
		<div class="heading">
			<h1>{title}</h1>
			{#if description}<p>{description}</p>{/if}
		</div>
		{#if actions}<div class="actions">{@render actions()}</div>{/if}
	</header>
	{@render children()}
</div>

<style>
	.admin-page {
		display: grid;
		gap: var(--space-4);
		align-content: start;
		max-width: 1400px;
		padding: var(--space-4) var(--space-6) var(--space-8);
	}

	.narrow {
		max-width: 1100px;
	}

	header {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-start;
		gap: var(--space-3);
	}

	.heading {
		flex: 1 1 320px;
		min-width: 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	@media (max-width: 960px) {
		.admin-page {
			padding: var(--space-4);
		}
	}
</style>
