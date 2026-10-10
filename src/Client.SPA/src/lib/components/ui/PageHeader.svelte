<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		/** A count or short fact next to the title ("24 personas"). */
		count?: string | null;
		/** One line under the title. */
		subtitle?: string | null;
		/** Page-level buttons, at the right of the title. */
		actions?: Snippet;
		/** Filters and view options, in a row under the title. */
		toolbar?: Snippet;
		/** For pages whose h1 is elsewhere (or a tab's own heading level). */
		level?: 1 | 2;
	}

	let { title, count = null, subtitle = null, actions, toolbar, level = 1 }: Props = $props();
</script>

<header class="page-header">
	<div class="title-row">
		<div class="titles">
			<svelte:element this={level === 1 ? 'h1' : 'h2'} class="title">
				{title}{#if count}<span class="count">{count}</span>{/if}
			</svelte:element>
			{#if subtitle}<p class="subtitle">{subtitle}</p>{/if}
		</div>
		{#if actions}<div class="actions">{@render actions()}</div>{/if}
	</div>
	{#if toolbar}<div class="toolbar">{@render toolbar()}</div>{/if}
</header>

<style>
	.page-header {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-6) var(--page-gutter) var(--space-3);
	}

	.title-row {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-start;
		gap: var(--space-3) var(--space-4);
	}

	.titles {
		display: grid;
		gap: var(--space-1);
		min-width: 0;
	}

	.title {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
		margin: 0;
		font-size: var(--font-size-xl);
		font-weight: 650;
		line-height: 1.2;
		letter-spacing: -0.01em;
	}

	.count {
		font-size: var(--font-size-sm);
		font-weight: 500;
		color: var(--color-text-muted);
		letter-spacing: 0;
	}

	.subtitle {
		margin: 0;
		max-width: 80ch;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.actions {
		margin-left: auto;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.toolbar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
	}
</style>
