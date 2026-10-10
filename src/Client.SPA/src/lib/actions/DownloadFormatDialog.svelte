<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import type { AssetDownloader } from './asset-downloader.svelte.js';
	import { formatScope, originalExtension, type DownloadFormat } from './download-format.js';
	import { icons } from './icons.js';

	let { downloader }: { downloader: AssetDownloader } = $props();

	const choice = $derived(downloader.choice);
	const scope = $derived(
		choice ? formatScope(choice.assetIds.length, choice.options.convertibleCount) : null
	);
	const extension = $derived(choice ? originalExtension(choice.options.extensions) : null);

	const options = $derived([
		{
			format: 'original' as DownloadFormat,
			key: 'o',
			icon: icons.file,
			title: extension
				? m.links_format_original_extension({ extension })
				: m.links_format_original(),
			subtitle: m.links_format_original_hint()
		},
		{
			format: 'jpeg' as DownloadFormat,
			key: 'j',
			icon: icons.image,
			title: m.links_format_jpeg(),
			subtitle: m.links_format_jpeg_hint()
		}
	]);

	// O and J answer without reaching for the mouse. Stopped at the window's
	// capture phase so the page behind (the viewer's arrows, a selection's
	// Delete) doesn't act while the question is up.
	function onkeydowncapture(event: KeyboardEvent) {
		if (!choice) return;
		event.stopPropagation();
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		const option = options.find((o) => o.key === event.key.toLowerCase());
		if (option) {
			event.preventDefault();
			downloader.choose(option.format);
		}
	}
</script>

<svelte:window {onkeydowncapture} />

<Dialog open={choice !== null} title={m.links_format_title()} onclose={() => downloader.cancel()}>
	{#if scope && scope.kind !== 'one'}
		<p class="scope">
			{scope.kind === 'all'
				? m.links_format_scope_all({ total: scope.total })
				: m.links_format_scope_some({ count: scope.count, total: scope.total })}
		</p>
	{/if}
	<div class="options" role="group" aria-label={m.links_format_title()}>
		{#each options as option, index (option.format)}
			<!-- svelte-ignore a11y_autofocus -->
			<button
				type="button"
				class="option"
				autofocus={index === 0}
				aria-keyshortcuts={option.key.toUpperCase()}
				onclick={() => downloader.choose(option.format)}
			>
				<Icon path={option.icon} size={28} />
				<span class="text">
					<span class="title">{option.title}</span>
					<span class="subtitle">{option.subtitle}</span>
				</span>
				<kbd aria-hidden="true">{option.key.toUpperCase()}</kbd>
			</button>
		{/each}
	</div>
	{#snippet actions()}
		<button type="button" onclick={() => downloader.cancel()}>{m.dialog_cancel()}</button>
	{/snippet}
</Dialog>

<style>
	.scope {
		margin: 0 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.options {
		display: grid;
		gap: var(--space-2);
	}

	.option {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		width: 100%;
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: transparent;
		color: inherit;
		text-align: left;
		cursor: pointer;
	}

	.option :global(svg) {
		flex: none;
		color: var(--color-accent);
	}

	.option:hover,
	.option:focus-visible {
		background: var(--color-surface);
	}

	.text {
		flex: 1;
		display: grid;
		gap: 2px;
	}

	.title {
		font-weight: 600;
	}

	.subtitle {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	kbd {
		flex: none;
		padding: 0 var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		color: var(--color-text-muted);
		font-family: inherit;
		font-size: var(--font-size-xs);
	}
</style>
