<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import type { FolderResponse } from '#lib/api/index.js';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { browsableRoots, groupFolders } from '#lib/albums/folder-groups.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	interface Props {
		open: boolean;
		onclose: () => void;
		onpick: (folder: { id: string; name: string }, organizeByCaptureYear: boolean) => void;
	}

	let { open, onclose, onpick }: Props = $props();

	const tree = createQuery(() => ({ ...getFolderTreeOptions(), enabled: open }));

	let chosen = $state<FolderResponse | null>(null);
	let byYear = $state(false);
	const name = $props.id();

	// Each opening starts afresh: no folder or option left from the last move.
	$effect(() => {
		if (!open) return;
		chosen = null;
		byYear = false;
	});

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

	const folders = $derived(
		flatten(
			browsableRoots(groupFolders(tree.data ?? [], session.user?.username ?? '', getLocale()))
		)
	);
</script>

<Dialog {open} title={m.folder_picker_title()} {onclose} width="520px">
	{#if tree.isPending}
		<Skeleton variant="rows" count={6} flush />
	{:else if tree.isError}
		<p class="status" role="alert">
			{m.error_loading()}
			<button type="button" class="btn sm" onclick={() => tree.refetch()}
				>{m.session_retry()}</button
			>
		</p>
	{:else if folders.length === 0}
		<p class="status">{m.folder_picker_empty()}</p>
	{:else}
		<!-- Native radios: one choice, arrow keys between them, confirmed with Mover. -->
		<div class="list" role="radiogroup" aria-label={m.folder_picker_title()}>
			{#each folders as { folder, depth } (folder.id)}
				<label style:padding-left="calc(var(--space-3) + {depth} * var(--space-4))">
					<input
						type="radio"
						{name}
						value={folder.id}
						checked={chosen?.id === folder.id}
						onchange={() => (chosen = folder)}
					/>
					{folder.name}
				</label>
			{/each}
		</div>
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
	.list {
		margin: 0 0 var(--space-3);
		max-height: 360px;
		overflow-y: auto;
		display: grid;
		gap: 2px;
	}

	.list label {
		position: relative;
		display: block;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		cursor: pointer;
	}

	.list label:hover {
		background: var(--color-surface);
	}

	/* The row is the radio: the circle itself stays for screen readers only. */
	.list input {
		position: absolute;
		opacity: 0;
		pointer-events: none;
	}

	.list label:has(:checked) {
		background: color-mix(in srgb, var(--color-accent) 18%, transparent);
		font-weight: 600;
	}

	.list label:has(:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.by-year {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.status {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
		color: var(--color-text-muted);
	}
</style>
