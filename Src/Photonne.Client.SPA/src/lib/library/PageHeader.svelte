<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		/** Next to the title: search, sort… */
		tools?: Snippet;
	}

	let { title, tools }: Props = $props();
</script>

<header class="head">
	<h1>{title}</h1>
	{#if tools}<div class="tools">{@render tools()}</div>{/if}
</header>

<style>
	/* Same rhythm as CollectionView's header. */
	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-4) 0;
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
		overflow-wrap: anywhere;
	}

	.tools {
		margin-left: auto;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}
</style>
