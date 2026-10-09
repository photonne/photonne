<script lang="ts">
	import { untrack } from 'svelte';
	import type { FolderResponse } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { moveDestinations } from './folder-list.js';

	interface Props {
		/** The folders to move (already reduced to the topmost ones). */
		folders: readonly FolderResponse[];
		tree: readonly FolderResponse[];
		busy: boolean;
		onclose: () => void;
		/** `null`: the top of the user's folders. */
		onmove: (parent: { id: string; name: string } | null) => void;
	}

	let { folders, tree, busy, onclose, onmove }: Props = $props();

	const destinations = $derived(moveDestinations(tree, folders));
	// When they all hang from the same folder, start there, as when moving one.
	let parent = $state(
		untrack(() => {
			const parents = [...new Set(folders.map((folder) => folder.parentFolderId ?? ''))];
			const listed = parents[0] === '' || destinations.some((d) => d.folder.id === parents[0]);
			return parents.length === 1 && listed ? parents[0] : '';
		})
	);
	const unchanged = $derived(folders.every((folder) => (folder.parentFolderId ?? '') === parent));

	function submit(event: SubmitEvent) {
		event.preventDefault();
		if (unchanged || busy) return;
		const target = destinations.find((d) => d.folder.id === parent)?.folder;
		onmove(target ? { id: target.id, name: target.name } : null);
	}
</script>

<Dialog open title={m.folders_bulk_move_title({ count: folders.length })} {onclose}>
	<form id="folders-move" class="form" onsubmit={submit}>
		<label class="field">
			<span>{m.folders_field_location()}</span>
			<select bind:value={parent}>
				<option value="">{m.folders_location_root()}</option>
				{#each destinations as { folder, depth } (folder.id)}
					<option value={folder.id}>{`${'   '.repeat(depth)}${folder.name}`}</option>
				{/each}
			</select>
		</label>
		<p class="hint">{m.folders_move_hint()}</p>
	</form>

	{#snippet actions()}
		<button type="button" disabled={busy} onclick={onclose}>{m.dialog_cancel()}</button>
		<button type="submit" form="folders-move" class="primary" disabled={unchanged || busy}>
			{m.folders_bulk_move()}
		</button>
	{/snippet}
</Dialog>

<style>
	.form {
		display: grid;
		gap: var(--space-3);
	}

	.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
