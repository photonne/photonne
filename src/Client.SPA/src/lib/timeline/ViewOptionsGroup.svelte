<script lang="ts">
	import type { Snippet } from 'svelte';

	/** One titled group of a view options menu (ViewOptions.svelte). */
	let { label, children }: { label: string; children: Snippet } = $props();
</script>

<div class="group" role="group" aria-label={label}>
	<span class="caption" aria-hidden="true">{label}</span>
	{@render children()}
</div>

<style>
	.group {
		display: grid;
		gap: var(--space-2);
	}

	.caption {
		font-size: var(--font-size-2xs);
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--color-text-muted);
	}

	/* A segmented control takes the menu's width, its options shared. */
	.group :global(.segmented) {
		display: flex;
	}

	.group :global(.segmented > label) {
		flex: 1;
		justify-content: center;
	}
</style>
