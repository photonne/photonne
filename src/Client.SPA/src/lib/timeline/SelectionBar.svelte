<script lang="ts">
	import { untrack, type Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { chrome } from '#lib/navigation/chrome.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from './selection.svelte.js';

	let { selection, actions }: { selection: Selection; actions?: Snippet } = $props();

	// While selecting, the actions take the place of the phone's tabs.
	$effect(() => {
		if (!selection.active) return;
		untrack(() => chrome.selecting++);
		return () => untrack(() => chrome.selecting--);
	});
</script>

{#if selection.active}
	<!-- Two glass capsules over the page, as in the native app: the count on
	     top, over the search and the tools, and the actions at the bottom.
	     Both float, so starting a selection never moves the photos. -->
	<div class="bar" role="toolbar" aria-label={m.selection_count({ count: selection.size })}>
		<div class="top glass">
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
		</div>
		{#if actions}
			<div class="actions glass">{@render actions()}</div>
		{/if}
	</div>
{/if}

<style>
	/* No box of its own: its two capsules are fixed to the window. */
	.bar {
		display: contents;
	}

	.top {
		position: fixed;
		top: calc(env(safe-area-inset-top, 0px) + var(--float-margin));
		left: calc(var(--stage-left, 0px) + var(--float-margin));
		right: var(--float-margin);
		z-index: 31;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		height: var(--chrome-h);
		padding: 0 var(--space-4) 0 4px;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
		animation: drop var(--duration-chrome) var(--ease-spring);
	}

	/* Centred on the page (the window right of the island), above the
	   bottom edge where the phone's tabs float. */
	.actions {
		position: fixed;
		inset: auto 0 calc(env(safe-area-inset-bottom, 0px) + 8px + var(--float-margin))
			var(--stage-left, 0px);
		z-index: 31;
		width: max-content;
		margin-inline: auto;
		display: flex;
		align-items: center;
		gap: 4px;
		max-width: calc(100vw - var(--stage-left, 0px) - 2 * var(--float-margin));
		min-height: var(--nav-pill-h);
		padding: 0 var(--space-3);
		overflow-x: auto;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-nav);
		scrollbar-width: none;
		animation: rise var(--duration-chrome) var(--ease-spring);
	}

	@media (max-width: 700px) {
		.actions {
			bottom: calc(env(safe-area-inset-bottom, 0px) + 8px);
		}
	}

	@keyframes drop {
		from {
			opacity: 0;
			transform: translateY(-16px);
		}
	}

	@keyframes rise {
		from {
			opacity: 0;
			transform: translateY(24px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.top,
		.actions {
			animation: none;
		}
	}

	.count {
		font-size: var(--font-size-md);
		font-weight: 650;
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	/* The host's buttons, whatever they are, sit flat in the capsule. */
	.actions :global(.icon-btn) {
		flex: none;
		width: 44px;
		height: 44px;
	}

	.actions :global(.btn) {
		flex: none;
		border-color: transparent;
		background: transparent;
	}

	.actions :global(.btn:hover:not(:disabled)) {
		background: var(--color-hover);
	}

	.actions :global(.icon-btn.danger:hover:not(:disabled)),
	.actions :global(.btn.danger) {
		color: var(--color-danger);
	}
</style>
