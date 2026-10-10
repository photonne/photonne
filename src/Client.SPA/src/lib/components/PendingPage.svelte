<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';

	// Placeholder for sections whose phase isn't built yet (docs/roadmap-spa.md).
	let { title }: { title: string } = $props();
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<div class="pending">
	<h1>{title}</h1>
	<p>{m.page_pending()}</p>
</div>

<style>
	.pending {
		padding: var(--space-6);
	}

	h1 {
		margin: 0 0 var(--space-2);
		font-size: var(--font-size-xl);
	}

	p {
		color: var(--color-text-muted);
	}
</style>
