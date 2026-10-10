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
			class="icon-btn"
			title={m.selection_clear()}
			aria-label={m.selection_clear()}
			aria-keyshortcuts="Escape"
			onclick={() => selection.clear()}
		>
			<Icon name="close" />
		</button>
		<span class="count" aria-live="polite">{m.selection_count({ count: selection.size })}</span>
		<div class="actions">{@render actions?.()}</div>
	</div>
{/if}

<style>
	/* Overlays the page's header instead of pushing the grid down: starting a
	   selection must not move the photos under the pointer. The page header is
	   at least as tall, so the bar covers neither the grid nor its scrubber. */
	.bar {
		position: absolute;
		inset: 0 0 auto 0;
		z-index: 3;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-height: 64px;
		padding: var(--space-2) var(--page-gutter) var(--space-2) var(--space-4);
		background: var(--color-surface-raised);
		border-bottom: 1px solid var(--color-border);
		box-shadow: var(--shadow-raised);
		animation: drop var(--duration-normal) ease-out;
	}

	@keyframes drop {
		from {
			opacity: 0;
			transform: translateY(-8px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.bar {
			animation: none;
		}
	}

	.count {
		font-size: var(--font-size-md);
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	.actions {
		margin-left: auto;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-1);
	}
</style>
