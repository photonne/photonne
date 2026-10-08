<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		/** How many values of this filter are on (shown on the button). */
		count?: number;
		disabled?: boolean;
		/** Panel content; `close` puts focus back on the button. */
		children: Snippet<[close: () => void]>;
		width?: string;
		/** What the menu is about when `label` alone doesn't say ("3 chosen"). */
		name?: string;
	}

	let { label, count = 0, disabled = false, children, width = '300px', name }: Props = $props();

	const id = $props.id();
	let open = $state(false);
	let root = $state<HTMLDivElement>();
	let button = $state<HTMLButtonElement>();
	let panel = $state<HTMLDivElement>();

	function close() {
		open = false;
		button?.focus();
	}

	// Focus goes into the panel when it opens, onto its first field.
	$effect(() => {
		if (!open || !panel) return;
		panel.querySelector<HTMLElement>('input, select, button, [tabindex="0"]')?.focus();
	});

	function onpointerdown(event: PointerEvent) {
		if (open && root && !root.contains(event.target as Node)) open = false;
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && open) {
			event.preventDefault();
			event.stopPropagation();
			close();
		}
	}

	function onfocusout(event: FocusEvent) {
		// Tabbing out of the panel closes it, like a menu.
		const next = event.relatedTarget as Node | null;
		if (open && next && root && !root.contains(next)) open = false;
	}
</script>

<svelte:document {onpointerdown} />

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="menu" bind:this={root} {onkeydown} {onfocusout}>
	<button
		type="button"
		class="trigger"
		class:on={count > 0}
		aria-label={name ? `${name}: ${label}` : undefined}
		aria-expanded={open}
		aria-controls="{id}-panel"
		{disabled}
		bind:this={button}
		onclick={() => (open = !open)}
	>
		{label}
		{#if count > 0}<span class="count">{count}</span>{/if}
		<svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
			<path d="M7 10l5 5 5-5z" fill="currentColor" />
		</svg>
	</button>
	{#if open}
		<div
			class="panel"
			id="{id}-panel"
			role="group"
			aria-label={name ?? label}
			style:width
			bind:this={panel}
		>
			{@render children(close)}
		</div>
	{/if}
</div>

<style>
	.menu {
		position: relative;
	}

	.trigger {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: 999px;
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
		white-space: nowrap;
	}

	.trigger:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.trigger.on {
		border-color: var(--color-accent);
		color: var(--color-accent);
		font-weight: 600;
	}

	.trigger:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.count {
		min-width: 1.25rem;
		padding: 0 var(--space-1);
		border-radius: 999px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		text-align: center;
	}

	.panel {
		position: absolute;
		top: calc(100% + var(--space-1));
		left: 0;
		z-index: 6;
		max-width: calc(100vw - 2 * var(--space-4));
		padding: var(--space-3);
		background: var(--color-surface-raised);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-raised);
	}
</style>
