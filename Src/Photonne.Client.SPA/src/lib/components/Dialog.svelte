<script lang="ts">
	import type { Snippet } from 'svelte';
	import { m } from '#lib/paraglide/messages.js';
	import Icon from './Icon.svelte';

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
			<div class="head">
				<h2 id={titleId}>{title}</h2>
				<button type="button" class="close" aria-label={m.dialog_close()} onclick={onclose}>
					<Icon name="close" size={20} />
				</button>
			</div>
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
		background: rgb(10 10 12 / 0.5);
		backdrop-filter: blur(2px);
	}

	.content {
		display: grid;
		gap: var(--space-4);
		padding: var(--space-6);
	}

	.head {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
	}

	h2 {
		flex: 1;
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 650;
		line-height: 1.3;
	}

	.close {
		display: grid;
		place-items: center;
		flex: none;
		width: var(--control-h-sm);
		height: var(--control-h-sm);
		margin: -4px -8px 0 0;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.close:hover {
		background: var(--color-hover);
		color: var(--color-text);
	}

	.body {
		min-width: 0;
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		gap: var(--space-2);
		margin-top: var(--space-2);
	}

	/* The dialog's buttons look like .btn (lib/styles/ui.css), whatever the host wrote. */
	.actions :global(button) {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: var(--control-h);
		padding: 0 var(--space-4);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-control);
		background: transparent;
		color: var(--color-text);
		font-size: var(--font-size-sm);
		font-weight: 500;
		cursor: pointer;
	}

	.actions :global(button:hover:not(:disabled)) {
		background: var(--color-hover);
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
		color: var(--color-bg);
		font-weight: 600;
	}

	.actions :global(button.primary:hover:not(:disabled)),
	.actions :global(button.danger:hover:not(:disabled)) {
		box-shadow: inset 0 0 0 100px rgb(255 255 255 / 0.1);
	}

	/* Muted, not faded: a dimmed gold or red button is unreadable in dark. */
	.actions :global(button:disabled) {
		border-color: var(--color-border);
		background: var(--color-surface);
		color: var(--color-text-muted);
		cursor: not-allowed;
	}
</style>
