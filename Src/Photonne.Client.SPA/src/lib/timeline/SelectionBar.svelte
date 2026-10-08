<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from './selection.svelte.js';

	let { selection, actions }: { selection: Selection; actions?: Snippet } = $props();
</script>

{#if selection.active}
	<div class="bar" role="toolbar" aria-label={m.selection_count({ count: selection.size })}>
		<button
			type="button"
			class="icon"
			aria-label={m.selection_clear()}
			onclick={() => selection.clear()}
		>
			<Icon name="close" />
		</button>
		<span class="count" aria-live="polite">{m.selection_count({ count: selection.size })}</span>
		<div class="actions">{@render actions?.()}</div>
	</div>
{/if}

<style>
	/* Overlays the top of the grid instead of pushing it down: starting a
	   selection must not move the photos under the pointer. */
	.bar {
		position: absolute;
		inset: 0 0 auto 0;
		z-index: 3;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		height: 56px;
		padding: 0 var(--space-4);
		background: var(--color-surface-raised);
		border-bottom: 1px solid var(--color-border);
		box-shadow: var(--shadow-raised);
	}

	.count {
		font-weight: 600;
	}

	.actions {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover {
		background: var(--color-surface);
	}
</style>
