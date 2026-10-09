<script lang="ts">
	import { copyText } from '#lib/clipboard.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import PanelSection from './PanelSection.svelte';

	let { text }: { text: string } = $props();

	// A receipt or a page can hold dozens of lines: a few, then the rest on demand.
	const PREVIEW_LINES = 4;

	let expanded = $state(false);
	const lines = $derived(text.split('\n'));
	const long = $derived(lines.length > PREVIEW_LINES || text.length > 280);

	$effect.pre(() => {
		void text;
		expanded = false;
	});

	async function copy() {
		try {
			await copyText(text);
			toasts.show(m.viewer_text_copied());
		} catch {
			toasts.error(m.viewer_text_copy_failed());
		}
	}
</script>

<PanelSection id="text" title={m.viewer_text_title()}>
	{#snippet tools()}
		<button
			type="button"
			class="tool"
			title={m.viewer_text_copy()}
			aria-label={m.viewer_text_copy()}
			onclick={copy}
		>
			<Icon name="copy" size={16} />
		</button>
	{/snippet}
	<p class="text" class:clamped={long && !expanded} style:--lines={PREVIEW_LINES}>{text}</p>
	{#if long}
		<button
			type="button"
			class="more"
			aria-expanded={expanded}
			onclick={() => (expanded = !expanded)}
		>
			{expanded ? m.viewer_text_less() : m.viewer_text_more()}
		</button>
	{/if}
</PanelSection>

<style>
	.text {
		margin: 0;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-size: var(--font-size-sm);
		white-space: pre-line;
		overflow-wrap: anywhere;
		user-select: text;
	}

	.clamped {
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: var(--lines);
		line-clamp: var(--lines);
		overflow: hidden;
	}

	.more {
		justify-self: start;
		padding: 0;
		border: 0;
		background: none;
		color: var(--color-accent);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.tool {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.tool:hover {
		background: var(--color-surface);
		color: var(--color-text);
	}
</style>
