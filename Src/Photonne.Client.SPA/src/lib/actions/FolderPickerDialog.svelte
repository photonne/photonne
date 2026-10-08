<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import type { FolderResponse } from '#lib/api/index.js';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		onclose: () => void;
		onpick: (folder: { id: string; name: string }, organizeByCaptureYear: boolean) => void;
	}

	let { open, onclose, onpick }: Props = $props();

	const tree = createQuery(() => ({ ...getFolderTreeOptions(), enabled: open }));

	let chosen = $state<FolderResponse | null>(null);
	let byYear = $state(false);

	/** The tree flattened in display order, with its depth, keeping writable folders. */
	function flatten(
		nodes: readonly FolderResponse[],
		depth = 0
	): { folder: FolderResponse; depth: number }[] {
		return nodes.flatMap((folder) => [
			...(folder.canWrite ? [{ folder, depth }] : []),
			...flatten(folder.subFolders ?? [], depth + 1)
		]);
	}

	const folders = $derived(flatten(tree.data ?? []));
</script>

<Dialog {open} title={m.folder_picker_title()} {onclose} width="520px">
	{#if tree.isSuccess && folders.length === 0}
		<p class="empty">{m.folder_picker_empty()}</p>
	{:else}
		<ul role="listbox" aria-label={m.folder_picker_title()}>
			{#each folders as { folder, depth } (folder.id)}
				<li role="option" aria-selected={chosen?.id === folder.id}>
					<button
						type="button"
						style:padding-left="calc(var(--space-3) + {depth} * var(--space-4))"
						class:chosen={chosen?.id === folder.id}
						onclick={() => (chosen = folder)}
					>
						{folder.name}
					</button>
				</li>
			{/each}
		</ul>
	{/if}
	<label class="by-year">
		<input type="checkbox" bind:checked={byYear} />
		{m.folder_picker_by_year()}
	</label>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
		<button
			type="button"
			class="primary"
			disabled={!chosen}
			onclick={() => chosen && onpick({ id: chosen.id, name: chosen.name }, byYear)}
		>
			{m.folder_picker_move()}
		</button>
	{/snippet}
</Dialog>

<style>
	ul {
		list-style: none;
		margin: 0 0 var(--space-3);
		padding: 0;
		max-height: 360px;
		overflow-y: auto;
		display: grid;
		gap: 2px;
	}

	li button {
		width: 100%;
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	li button:hover {
		background: var(--color-surface);
	}

	li button.chosen {
		background: color-mix(in srgb, var(--color-accent) 18%, transparent);
		font-weight: 600;
	}

	.by-year {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.empty {
		color: var(--color-text-muted);
	}
</style>
