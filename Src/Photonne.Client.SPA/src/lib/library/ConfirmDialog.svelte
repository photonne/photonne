<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		/** Destructive: the confirm button is red and Cancel gets the focus. */
		danger?: boolean;
		onconfirm: () => void;
		onclose: () => void;
	}

	let { open, title, message, confirmLabel, danger = false, onconfirm, onclose }: Props = $props();

	// While it's up, the page's own shortcuts (the viewer's arrows and Escape,
	// a selection's Delete) must not act behind it. Stopping the event at the
	// window's capture phase keeps the dialog's defaults (Escape cancels it,
	// Tab moves, Enter presses) and nothing else.
	function onkeydowncapture(event: KeyboardEvent) {
		if (open) event.stopPropagation();
	}
</script>

<svelte:window {onkeydowncapture} />

<Dialog {open} {title} {onclose}>
	<p class="message">{message}</p>
	{#snippet actions()}
		<!-- svelte-ignore a11y_autofocus -->
		<button type="button" autofocus={danger} onclick={onclose}>{m.dialog_cancel()}</button>
		<!-- svelte-ignore a11y_autofocus -->
		<button
			type="button"
			class={danger ? 'danger' : 'primary'}
			autofocus={!danger}
			onclick={() => {
				onclose();
				onconfirm();
			}}
		>
			{confirmLabel}
		</button>
	{/snippet}
</Dialog>

<style>
	.message {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
