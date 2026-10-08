<script lang="ts">
	import type { Snippet } from 'svelte';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		title: string;
		confirmLabel: string;
		/** Destructive confirmations get the red button. */
		tone?: 'danger' | 'primary';
		busy?: boolean;
		/** Holds the confirm button back (e.g. until a checkbox is ticked). */
		confirmDisabled?: boolean;
		onconfirm: () => void;
		onclose: () => void;
		children: Snippet;
		width?: string;
	}

	let {
		open,
		title,
		confirmLabel,
		tone = 'danger',
		busy = false,
		confirmDisabled = false,
		onconfirm,
		onclose,
		children,
		width
	}: Props = $props();
</script>

<Dialog {open} {title} onclose={() => !busy && onclose()} {width}>
	<div class="message">{@render children()}</div>
	{#snippet actions()}
		<button type="button" onclick={onclose} disabled={busy}>{m.admin_core_cancel()}</button>
		<button type="button" class={tone} onclick={onconfirm} disabled={busy || confirmDisabled}
			>{confirmLabel}</button
		>
	{/snippet}
</Dialog>

<style>
	.message {
		display: grid;
		gap: var(--space-3);
	}

	.message :global(p) {
		margin: 0;
	}
</style>
