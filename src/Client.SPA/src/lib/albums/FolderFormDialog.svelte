<script lang="ts">
	import { untrack } from 'svelte';
	import { apiErrorCode, createFolder, updateFolder, type FolderResponse } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { findFolder, subtreeIds } from './folder-tree.js';

	interface Props {
		/** The folder to rename or move; without it, a new one is created. */
		folder?: FolderResponse;
		/** For a new folder: where it goes by default (the open folder). */
		parentId?: string | null;
		tree: readonly FolderResponse[];
		/** Admins may create top-level folders in the shared space. */
		isAdmin: boolean;
		onclose: () => void;
		onsaved: (folder: { id: string; name: string }) => void;
	}

	let { folder, parentId = null, tree, isAdmin, onclose, onsaved }: Props = $props();

	// Mounted for one folder (or one creation) and closed after. The tree's
	// copy knows a folder at the top of the home has no parent to pick.
	const initial = untrack(() => ({
		folder: folder && (findFolder(tree, folder.id) ?? folder),
		parentId
	}));
	const editing = !!initial.folder;

	let name = $state(initial.folder?.name ?? '');
	let parent = $state(
		initial.folder ? (initial.folder.parentFolderId ?? '') : (initial.parentId ?? '')
	);
	let sharedSpace = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);

	// Where it can go: folders the user may write to, never inside itself.
	const destinations = $derived.by(() => {
		const excluded = initial.folder
			? subtreeIds(findFolder(tree, initial.folder.id) ?? initial.folder)
			: new Set<string>();
		const out: { id: string; label: string }[] = [];
		const walk = (nodes: readonly FolderResponse[], depth: number) => {
			for (const node of nodes) {
				if (excluded.has(node.id)) continue;
				if (node.canWrite && !node.externalLibraryId)
					out.push({ id: node.id, label: `${'   '.repeat(depth)}${node.name}` });
				walk(node.subFolders ?? [], depth + 1);
			}
		};
		walk(tree, 0);
		return out;
	});

	const unchanged = $derived(
		editing &&
			name.trim() === initial.folder?.name &&
			parent === (initial.folder?.parentFolderId ?? '')
	);
	const canSave = $derived(!!name.trim() && !/[\\/]/.test(name) && !saving && !unchanged);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!canSave) return;
		saving = true;
		error = null;
		const parentFolderId = parent || null;
		const result = initial.folder
			? await updateFolder({
					path: { folderId: initial.folder.id },
					body: { name: name.trim(), parentFolderId }
				})
			: await createFolder({
					body: {
						name: name.trim(),
						parentFolderId,
						isSharedSpace: !parentFolderId && isAdmin && sharedSpace
					}
				});
		saving = false;
		if (result.data) {
			onsaved({ id: result.data.id, name: result.data.name });
			return;
		}
		const code = apiErrorCode(result.error);
		error =
			code === 'folder_already_exists'
				? m.folders_error_exists()
				: code === 'invalid_parent_folder'
					? m.folders_error_parent()
					: m.action_failed();
	}
</script>

<Dialog open title={editing ? m.folders_edit_title() : m.folders_new_title()} {onclose}>
	<form id="folder-form" class="form" onsubmit={submit}>
		<label class="field">
			<span>{m.folders_field_name()}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={name} required maxlength="255" autofocus />
		</label>
		{#if /[\\/]/.test(name)}
			<p class="error" role="alert">{m.folders_error_slash()}</p>
		{/if}
		<label class="field">
			<span>{m.folders_field_location()}</span>
			<select bind:value={parent}>
				<option value="">{m.folders_location_root()}</option>
				{#each destinations as destination (destination.id)}
					<option value={destination.id}>{destination.label}</option>
				{/each}
			</select>
		</label>
		{#if !editing && !parent && isAdmin}
			<label class="check">
				<input type="checkbox" bind:checked={sharedSpace} />
				<span>
					{m.folders_shared_space()}
					<span class="hint">{m.folders_shared_space_hint()}</span>
				</span>
			</label>
		{/if}
		{#if editing && parent !== (initial.folder?.parentFolderId ?? '')}
			<p class="hint">{m.folders_move_hint()}</p>
		{/if}
		{#if error}<p class="error" role="alert">{error}</p>{/if}
	</form>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
		<button type="submit" form="folder-form" class="primary" disabled={!canSave}>
			{editing ? m.albums_save() : m.folders_create()}
		</button>
	{/snippet}
</Dialog>

<style>
	.form {
		display: grid;
		gap: var(--space-4);
	}

	.check {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.hint {
		display: block;
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.error {
		margin: 0;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}
</style>
