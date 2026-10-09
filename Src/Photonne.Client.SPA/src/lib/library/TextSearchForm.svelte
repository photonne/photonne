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
	<span class="query">
		<Icon path={icons.textFields} size={18} />
		<input
			type="search"
			bind:this={input}
			bind:value={query}
			aria-label={m.explore_text_label()}
			placeholder={m.explore_text_placeholder()}
		/>
	</span>
	<button type="submit" class="btn primary" disabled={!query.trim()}>
		{m.explore_text_submit()}
	</button>
</form>

<style>
	.search {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		width: min(520px, 100%);
	}

	.query {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		color: var(--color-text-muted);
	}

	.query :global(svg) {
		position: absolute;
		top: 50%;
		left: var(--space-3);
		transform: translateY(-50%);
		pointer-events: none;
	}

	input {
		flex: 1;
		min-width: 0;
		padding-left: calc(var(--space-3) + 18px + var(--space-2));
	}
</style>
