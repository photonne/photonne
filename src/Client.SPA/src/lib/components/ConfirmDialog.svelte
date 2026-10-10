<script lang="ts">
	import type { Snippet } from 'svelte';
	import { m } from '#lib/paraglide/messages.js';
	import Dialog from './Dialog.svelte';

	interface Props {
		open: boolean;
		title: string;
		/** The question, as plain text; `children` for anything richer. */
		message?: string;
		children?: Snippet;
		confirmLabel: string;
		/** Destructive: the confirm button is red and Cancel gets the focus. */
		danger?: boolean;
		/** The action is running: both buttons wait and the dialog stays up. */
		busy?: boolean;
		/** Holds the confirm button back (e.g. until a checkbox is ticked). */
		confirmDisabled?: boolean;
		/** Closes on confirm, for actions that report on their own (undo toast). */
		closeOnConfirm?: boolean;
		width?: string;
		onconfirm: () => void;
		onclose: () => void;
	}

	let {
		open,
		title,
		message,
		children,
		confirmLabel,
		danger = false,
		busy = false,
		confirmDisabled = false,
		closeOnConfirm = false,
		width,
		onconfirm,
		onclose
	}: Props = $props();

	// While it's up, the page's own shortcuts (the viewer's arrows and Escape,
	// a selection's Delete) must not act behind it. Stopping the event at the
	// window's capture phase keeps the dialog's defaults (Escape cancels it,
	// Tab moves, Enter presses) and nothing else.
	function onkeydowncapture(event: KeyboardEvent) {
		if (open) event.stopPropagation();
	}

	function confirm() {
		if (closeOnConfirm) onclose();
		onconfirm();
	}
</script>

<svelte:window {onkeydowncapture} />

<Dialog {open} {title} onclose={() => !busy && onclose()} {width}>
	<div class="message">
		{#if message}<p>{message}</p>{/if}
		{@render children?.()}
	</div>
	{#snippet actions()}
		<!-- svelte-ignore a11y_autofocus -->
		<button type="button" autofocus={danger} disabled={busy} onclick={onclose}
			>{m.dialog_cancel()}</button
		>
		<!-- svelte-ignore a11y_autofocus -->
		<button
			type="button"
			class={danger ? 'danger' : 'primary'}
			autofocus={!danger}
			disabled={busy || confirmDisabled}
			onclick={confirm}
		>
			{confirmLabel}
		</button>
	{/snippet}
</Dialog>

<style>
	.message {
		display: grid;
		gap: var(--space-3);
		color: var(--color-text-muted);
	}

	.message :global(p) {
		margin: 0;
	}
</style>
