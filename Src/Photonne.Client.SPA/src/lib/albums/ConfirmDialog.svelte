<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		/** Destructive actions get the danger button. */
		danger?: boolean;
		busy?: boolean;
		onconfirm: () => void;
		onclose: () => void;
	}

	let {
		open,
		title,
		message,
		confirmLabel,
		danger = true,
		busy = false,
		onconfirm,
		onclose
	}: Props = $props();
</script>

<Dialog {open} {title} {onclose}>
	<p>{message}</p>
	{#snippet actions()}
		<!-- Cancel comes first so Enter on an untouched dialog is the safe choice. -->
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
		<button type="button" class={danger ? 'danger' : 'primary'} disabled={busy} onclick={onconfirm}>
			{confirmLabel}
		</button>
	{/snippet}
</Dialog>

<style>
	p {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
