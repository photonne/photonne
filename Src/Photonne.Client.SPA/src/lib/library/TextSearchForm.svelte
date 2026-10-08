<script lang="ts">
	import { goto } from '$app/navigation';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { textHref } from './explore-links.js';
	import { icons } from './icons.js';

	/** Searches the text recognised in photos (signs, documents, screenshots). */
	let { value = '', autofocus = false }: { value?: string; autofocus?: boolean } = $props();

	// Starts from the current search; then it's the user's to edit.
	let query = $derived(value);
	let input = $state<HTMLInputElement>();

	$effect(() => {
		if (autofocus) input?.focus();
	});

	function onsubmit(event: SubmitEvent) {
		event.preventDefault();
		const text = query.trim();
		if (text) goto(textHref(text));
	}
</script>

<form class="search" role="search" {onsubmit}>
	<Icon path={icons.textFields} />
	<input
		type="search"
		bind:this={input}
		bind:value={query}
		aria-label={m.explore_text_label()}
		placeholder={m.explore_text_placeholder()}
	/>
	<button type="submit" disabled={!query.trim()}>{m.explore_text_submit()}</button>
</form>

<style>
	.search {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		max-width: 520px;
		padding: var(--space-1) var(--space-1) var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
	}

	input {
		flex: 1;
		min-width: 0;
		padding: var(--space-1) 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	button {
		padding: var(--space-1) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-sm);
		font-weight: 600;
		cursor: pointer;
	}

	button:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
