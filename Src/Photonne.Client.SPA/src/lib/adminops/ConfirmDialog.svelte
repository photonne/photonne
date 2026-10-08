<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		/** A destructive action: the confirm button is red. */
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
		danger = false,
		busy = false,
		onconfirm,
		onclose
	}: Props = $props();
</script>

<Dialog {open} {title} {onclose}>
	<p class="message">{message}</p>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.ops_cancel()}</button>
		<button type="button" class={danger ? 'danger' : 'primary'} disabled={busy} onclick={onconfirm}
			>{confirmLabel}</button
		>
	{/snippet}
</Dialog>

<style>
	.message {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
