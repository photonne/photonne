<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		open: boolean;
		title: string;
		/** Called on Escape, a click outside, or the host's own close button. */
		onclose: () => void;
		children: Snippet;
		/** Buttons at the bottom; the host decides what they do. */
		actions?: Snippet;
		width?: string;
	}

	let { open, title, onclose, children, actions, width = '440px' }: Props = $props();

	let dialog = $state<HTMLDialogElement>();
	// Several dialogs can share a page (one inside another's flow).
	const titleId = $props.id();

	// The native <dialog> in modal mode brings focus trapping, Escape, the
	// backdrop and `inert` on the rest of the page for free.
	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) dialog.showModal();
		else if (!open && dialog.open) dialog.close();
	});

	function onclick(event: MouseEvent) {
		// A click on the backdrop lands on the dialog element itself.
		if (event.target === dialog) onclose();
	}
</script>

<dialog
	bind:this={dialog}
	style:width
	aria-labelledby={titleId}
	oncancel={(event) => {
		event.preventDefault();
		onclose();
	}}
	{onclick}
>
	{#if open}
		<div class="content">
			<h2 id={titleId}>{title}</h2>
			<div class="body">{@render children()}</div>
			{#if actions}
				<div class="actions">{@render actions()}</div>
			{/if}
		</div>
	{/if}
</dialog>

<style>
	dialog {
		max-width: calc(100vw - 2 * var(--space-4));
		max-height: calc(100vh - 2 * var(--space-8));
		padding: 0;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		color: var(--color-text);
		box-shadow: var(--shadow-raised);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 0.45);
	}

	.content {
		display: grid;
		gap: var(--space-4);
		padding: var(--space-6);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
	}

	.body {
		min-width: 0;
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: var(--space-2);
	}

	/* Shared button looks for dialog actions. */
	.actions :global(button) {
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.actions :global(button.primary) {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	.actions :global(button.danger) {
		border-color: var(--color-danger);
		background: var(--color-danger);
		color: #fff;
		font-weight: 600;
	}

	.actions :global(button:disabled) {
		opacity: 0.5;
		cursor: default;
	}
</style>
