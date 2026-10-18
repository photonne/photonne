<script lang="ts">
	import { untrack } from 'svelte';
	import FolderPickerDialog from '#lib/actions/FolderPickerDialog.svelte';
	import type { FolderResponse } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import { findFolder, subtreeIds } from './folder-tree.js';

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

	// Never into themselves or below.
	const exclude = $derived(
		new Set(folders.flatMap((folder) => [...subtreeIds(findFolder(tree, folder.id) ?? folder)]))
	);
	// When they all hang from the same folder, start there, as when moving one.
	const initial = untrack(() => {
		const parents = [...new Set(folders.map((folder) => folder.parentFolderId ?? null))];
		return parents.length === 1 ? parents[0] : undefined;
	});
</script>

<FolderPickerDialog
	open
	title={m.folders_bulk_move_title({ count: folders.length })}
	confirmLabel={m.folders_bulk_move()}
	root
	{exclude}
	byYearOption={false}
	{initial}
	hint={m.folders_move_hint()}
	{busy}
	unchanged={(target) => folders.every((folder) => (folder.parentFolderId ?? null) === target)}
	{onclose}
	onpick={(parent) => onmove(parent)}
	onpickroot={() => onmove(null)}
/>
